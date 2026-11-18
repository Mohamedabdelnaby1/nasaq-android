package com.yawmi.diabetes

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.security.KeyStore
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Store private constructor(private val context: Context) {
    private val file=AtomicFile(File(context.filesDir,"yawmi-state.aes"))
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("yawmi.local.v1",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("yawmi.local.v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(): State {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return State()
        val bytes=file.readFully(); require(bytes.size in 29..16_000_000)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        return Codec.decode(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
    }
    @Synchronized fun write(state: State): State {
        Codec.validate(state)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key())
        val bytes=cipher.iv+cipher.doFinal(Codec.encode(state).toByteArray(Charsets.UTF_8))
        val stream=file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch(e:Exception) { file.failWrite(stream); throw e }
        runCatching{Widgets.updateAll(context,state)}
        return state
    }
    @Synchronized fun update(change: (State)->State): State {
        val current=read(); return write(change(current).copy(revision=current.revision+1))
    }
    companion object {
        @Volatile private var instance: Store?=null
        fun get(context:Context):Store=instance ?: synchronized(this) { instance ?: Store(context.applicationContext).also { instance=it } }
    }
}

class Repository(private val context:Context) {
    var state by mutableStateOf(State()); private set
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private val executor=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private var pendingWrites=0
    fun refresh() { executor.execute {
        try { val loaded=Store.get(context).read();runCatching{PhoneShortcuts.publish(context,loaded.preferences);Widgets.updateAll(context,loaded)};main.post { state=loaded; ready=true; error=null } }
        catch(e:Exception) { main.post { error="load_error"; ready=true } }
    } }
    fun change(onSuccess:(State)->Unit={},block:(State)->State) {
        if(!ready || error=="load_error") return
        pendingWrites++;busy=true
        executor.execute {
            try {
                val next=Store.get(context).update(block)
                main.post { state=next; pendingWrites--;busy=pendingWrites>0; error=null;onSuccess(next) }
                try { Reminders.reschedule(context,next) } catch(e:Exception) { main.post { error="reminder_error" } }
            } catch(e:Exception) { main.post { error="save_error"; pendingWrites--;busy=pendingWrites>0 } }
        }
    }
    fun preferences(block:(Preferences)->Preferences)=change { it.copy(preferences=block(it.preferences)) }
    fun saveLayout(p:Preferences,layouts:List<DayLayout>)=change{state->
        state.copy(preferences=state.preferences.copy(order=p.order,hiddenCards=p.hiddenCards,compactHome=p.compactHome,
            cardSizes=p.cardSizes,theme=p.theme,palette=p.palette,dynamicColors=p.dynamicColors),
            care=state.care.copy(layouts=layouts))
    }
    fun add(entry:Entry,onSuccess:(State)->Unit={})=change(onSuccess) { s -> Records.add(s,entry) }
    fun put(m:Medication)=change { s -> s.copy(medications=s.medications.filterNot { it.id==m.id }+m) }
    fun delete(id:String)=change { s -> Records.remove(s,id) }
    fun clearError() { if(error!="load_error") error=null }
    fun close() { executor.shutdown() }
}
