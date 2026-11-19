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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.KeyStore
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Store private constructor(private val context: Context) {
    private val file=AtomicFile(File(context.filesDir,"yawmi-state.aes"))
    private val commits = MutableStateFlow<State?>(null)
    internal val changes = commits.asStateFlow()
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
        val encoded = Codec.encode(state)
        require(encoded.length <= 12_000_000)
        val bytes = cipher.iv + cipher.doFinal(encoded.toByteArray(Charsets.UTF_8))
        require(bytes.size <= 16_000_000)
        val stream=file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch(e:Exception) { file.failWrite(stream); throw e }
        commits.value = state
        runCatching{Widgets.updateAll(context,state)}
        return state
    }
    @Synchronized internal fun refreshWidgets(ids: Iterable<Int> = Widgets.ids(context)) {
        Widgets.update(context, read(), ids)
    }
    @Synchronized internal fun renderWidget(id: Int) {
        GraphicalWidgets.render(context, read(), id)
    }
    @Synchronized fun update(change: (State)->State): State {
        val current = read()
        val next = change(current)
        return if (next == current) current else write(next.copy(revision = current.revision + 1))
    }

    @Synchronized internal fun restore(backup: State, settings: Boolean, recovery: Boolean,
                                       expected: RestoreCounts, expectedRevision: Long): State {
        val current = try { read() } catch (error: Exception) {
            if (!recovery) throw error
            State()
        }
        val plan = prepareRestore(current, backup, settings)
        if (plan.counts != expected || (!recovery && current.revision != expectedRevision)) throw StaleRestorePreview()
        return if (plan.state == current && !recovery) current else write(plan.state.copy(revision = current.revision + 1))
    }

    companion object {
        @Volatile private var instance: Store?=null
        fun get(context:Context):Store=instance ?: synchronized(this) { instance ?: Store(context.applicationContext).also { instance=it } }
    }
}

private class StaleRestorePreview : IllegalStateException()
internal class RecordChanged : IllegalStateException()

class Repository(private val context:Context) {
    var state by mutableStateOf(State()); private set
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private val executor=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val store = Store.get(context)
    private val observer = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    init {
        observer.launch {
            store.changes.collect { next ->
                if (next != null && ready && error != "load_error" && next.revision >= state.revision) state = next
            }
        }
    }
    private var pendingWrites=0
    fun refresh() { executor.execute {
        try { val loaded=Store.get(context).read();runCatching{PhoneShortcuts.publish(context,loaded.preferences);Store.get(context).refreshWidgets()}
            runCatching { Reminders.reconcile(context) }
            main.post {
                val latest = store.changes.value?.takeIf { it.revision >= loaded.revision } ?: loaded
                if (!ready || error == "load_error" || latest.revision >= state.revision) state = latest
                ready = true
                error = null
            } }
        catch(e:Exception) { main.post { error="load_error"; ready=true } }
    } }
    fun change(onSuccess:(State)->Unit={},onFailure:((String)->Unit)?=null,block:(State)->State) {
        if(!ready || error=="load_error") return
        submit(onSuccess, onFailure = onFailure) { Store.get(context).update(block) }
    }

    internal fun restore(backup: State, useSettings: Boolean, expected: RestoreCounts, revision: Long, onSuccess: () -> Unit) {
        if (!ready || busy) return
        val recovery = error == "load_error"
        submit({ onSuccess() }, failure = if (recovery) "load_error" else "backup_error") {
            Store.get(context).restore(backup, useSettings, recovery, expected, revision)
        }
    }

    private fun submit(onSuccess: (State) -> Unit, failure: String = "save_error", onFailure: ((String) -> Unit)? = null, write: () -> State) {
        pendingWrites++
        busy = true
        executor.execute {
            try {
                val next = write()
                main.post {
                    if (next.revision >= state.revision || error == "load_error") state = next
                    pendingWrites--
                    busy = pendingWrites > 0
                    error = null
                    onSuccess(next)
                }
                try { Reminders.reschedule(context) } catch (_: Exception) { main.post { error = "reminder_error" } }
            } catch (_: StaleRestorePreview) {
                val latest = runCatching { Store.get(context).read() }.getOrNull()
                main.post { latest?.let { state = it }; pendingWrites--; busy = pendingWrites > 0; error = "restore_changed" }
            } catch (exception: Exception) {
                val reason = if (exception is RecordChanged) "record_changed" else failure
                main.post {
                    pendingWrites--
                    busy = pendingWrites > 0
                    if (onFailure == null) error = reason else onFailure(reason)
                }
            }
        }
    }
    fun preferences(block:(Preferences)->Preferences)=change { it.copy(preferences=block(it.preferences)) }
    fun saveLayout(p:Preferences,layouts:List<DayLayout>)=change{state->
        state.copy(preferences=state.preferences.copy(order=p.order,hiddenCards=p.hiddenCards,compactHome=p.compactHome,
            cardSizes=p.cardSizes,theme=p.theme,palette=p.palette,dynamicColors=p.dynamicColors),
            care=state.care.copy(layouts=layouts))
    }
    fun add(entry:Entry,onSuccess:(State)->Unit={})=change(onSuccess) { s -> Records.add(s,entry) }
    fun put(m:Medication,onSuccess:(State)->Unit={},onFailure:((String)->Unit)?=null)=change(onSuccess,onFailure) { s -> s.copy(medications=s.medications.filterNot { it.id==m.id }+m) }
    fun delete(id:String)=change { s -> Records.remove(s,id) }
    fun clearError() { if(error!="load_error") error=null }
    fun close() { observer.cancel(); executor.shutdown() }
}

