package com.yawmi.diabetes

import android.Manifest
import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.yawmi.diabetes.core.BackupCipher
import java.util.concurrent.Executors

class MainActivity:ComponentActivity() {
    lateinit var repository:Repository;private set
    var requestedScreen by mutableStateOf("")
    var requestedEntry by mutableStateOf("")
    var openNotifications by mutableStateOf(false)
    var permissionRevision by mutableIntStateOf(0);private set
    var locked by mutableStateOf(true);private set
    private var authenticated=false
    private var authenticating=false
    private val io=Executors.newSingleThreadExecutor()
    private var backupPassword:CharArray?=null
    private var reportView:WebView?=null
    private val permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestPermission()){permissionRevision++;reschedule()}
    private val unlockLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        authenticating=false;authenticated=result.resultCode==RESULT_OK;locked=!authenticated
        if(!authenticated)message("lock_failure")
    }
    private val backupCreate=registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri->
        val password=backupPassword;backupPassword=null
        if(uri==null || password==null) {password?.fill('\u0000');return@registerForActivityResult}
        io.execute {try {
            val state=Store.get(this).read();val encrypted=BackupCipher.encrypt(Codec.encode(state).toByteArray(Charsets.UTF_8),password)
            contentResolver.openOutputStream(uri,"wt")?.use {it.write(encrypted)} ?: error("No output")
            runOnUiThread {message("backup_saved")}
        }catch(e:Exception){runOnUiThread {message("backup_error")}}finally{password.fill('\u0000')}}
    }
    private val backupOpen=registerForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        val password=backupPassword;backupPassword=null
        if(uri==null || password==null) {password?.fill('\u0000');return@registerForActivityResult}
        io.execute {try {
            val bytes=contentResolver.openInputStream(uri)?.use {stream->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var total=0
                while(true){val n=stream.read(buffer);if(n<0)break;total+=n;require(total<=16_000_000);output.write(buffer,0,n)};output.toByteArray()
            } ?: error("No input")
            val restored=Codec.decode(String(BackupCipher.decrypt(bytes,password),Charsets.UTF_8))
            // Device authentication and zone acknowledgement are deliberately re-established on this phone.
            Store.get(this).write(restored.copy(preferences=restored.preferences.copy(lock=false,lastZone=java.time.ZoneId.systemDefault().id),revision=restored.revision+1))
            Reminders.clearAll(this);Reminders.reschedule(this)
            runOnUiThread {authenticated=false;locked=false;repository.refresh();message("restore_done")}
        }catch(e:Exception){runOnUiThread {message("backup_error")}}finally{password.fill('\u0000')}}
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        repository=Repository(applicationContext);requestedScreen=intent.getStringExtra("screen").orEmpty();requestedEntry=intent.getStringExtra("entry").orEmpty()
        setContent {
            val p=repository.state.preferences
            LaunchedEffect(repository.ready,p.lock,p.secureScreen,p.theme) {if(repository.ready && repository.error!="load_error")applySecurity(p)}
            YawmiApp(repository,this)
        }
        repository.refresh()
    }
    override fun onNewIntent(intent:Intent) {super.onNewIntent(intent);setIntent(intent);requestedScreen=intent.getStringExtra("screen").orEmpty();requestedEntry=intent.getStringExtra("entry").orEmpty()}
    override fun onResume() {
        super.onResume()
        permissionRevision++
        if(::repository.isInitialized && repository.ready)runCatching{PhoneShortcuts.publish(this,repository.state.preferences)}
        if(::repository.isInitialized) {repository.refresh();if(repository.ready)applySecurity(repository.state.preferences)}
    }
    override fun onStop() {
        super.onStop()
        if(::repository.isInitialized && repository.state.preferences.lock && !isChangingConfigurations && !authenticating) {authenticated=false;locked=true}
    }
    override fun onDestroy() {backupPassword?.fill('\u0000');reportView?.destroy();repository.close();io.shutdown();super.onDestroy()}
    fun applySecurity(p:Preferences) {
        if(p.secureScreen)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        locked=p.lock && !authenticated
        val dark=p.theme=="dark" || (p.theme=="system" && resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES)
        androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
            isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark
        }
    }
    fun canLock()=getSystemService(KeyguardManager::class.java).isDeviceSecure
    fun unlock() {
        val w=Words(repository.state.preferences.language)
        val intent=getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent(w["app"],w["unlock"])
        if(intent==null){message("set_device_lock");return};authenticating=true;unlockLauncher.launch(intent)
    }
    fun message(key:String) {Toast.makeText(this,Words(if(::repository.isInitialized)repository.state.preferences.language else "system")[key],Toast.LENGTH_LONG).show()}
    fun notificationPermission() {
        val prompts=getSharedPreferences("permission-prompts",MODE_PRIVATE)
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED &&
            (!prompts.getBoolean("notifications",false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))){
            prompts.edit().putBoolean("notifications",true).apply();permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
    }
    fun exactPermission() {if(Build.VERSION.SDK_INT>=31)startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))}
    fun channel(id:String) {startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName).putExtra(Settings.EXTRA_CHANNEL_ID,id))}
    fun testNotification(kind:String="glucose") {Reminders.worker.execute {try{Reminders.test(this,kind);runOnUiThread{repository.refresh();message("notification_test_sent")}}catch(e:Exception){runOnUiThread{message("reminder_error")}}}}
    fun reschedule() {Reminders.worker.execute {try{Reminders.reschedule(this);runOnUiThread{repository.refresh()}}catch(e:Exception){runOnUiThread{message("reminder_error")}}}}
    fun oneShot(kind:String,minutes:Int) {Reminders.worker.execute {try{Reminders.once(this,kind,minutes);runOnUiThread{message(if(kind=="symptom")"followup_scheduled"else"recheck_scheduled")}}catch(e:Exception){runOnUiThread{message("reminder_error")}}}}
    fun dial(phone:String) {startActivity(Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",phone,null)))}
    fun batterySettings() {
        if (!runCatching { startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }.isSuccess) {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    }
    fun openSource(url:String) {if(url.startsWith("https://"))startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}
    fun backup(mode:String,password:CharArray) {
        backupPassword?.fill('\u0000');backupPassword=password
        if(mode=="create")backupCreate.launch("Yawmi-${java.time.LocalDate.now()}.yawmi")else backupOpen.launch(arrayOf("*/*"))
    }
    fun export(s:State,days:Int,type:String) {
        if(locked)return
        if(type=="csv") io.execute {try{val file=Reports.csv(this,s,days);runOnUiThread{
            val uri=androidx.core.content.FileProvider.getUriForFile(this,"$packageName.exports",file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM,uri)
                .apply {clipData=android.content.ClipData.newRawUri("Yawmi report",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},Words(s.preferences.language)["export"]))
        }}catch(e:Exception){runOnUiThread{message("backup_error")}}}
        else io.execute {try{val html=Reports.html(this,s,days);runOnUiThread{
            if(isFinishing || locked)return@runOnUiThread
            reportView?.destroy();val view=WebView(this);reportView=view
            view.settings.javaScriptEnabled=false;view.settings.allowFileAccess=false;view.settings.allowContentAccess=false
            view.webViewClient=object:WebViewClient(){override fun onPageFinished(web:WebView,url:String){
                getSystemService(android.print.PrintManager::class.java).print("Yawmi-${java.time.LocalDate.now()}",web.createPrintDocumentAdapter("Yawmi care report"),
                    android.print.PrintAttributes.Builder().setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4).build())
            }}
            view.loadDataWithBaseURL("https://yawmi.local/report/",html,"text/html","UTF-8",null)
        }}catch(e:Exception){runOnUiThread{message("backup_error")}}}
    }
}
