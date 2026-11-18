package com.yawmi.diabetes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.ZoneId

internal data class RestoreCounts(val entries: Int, val medicines: Int, val care: Int, val layouts: Int, val duplicates: Int, val conflicts: Int)

internal data class RestorePlan(val state: State, val counts: RestoreCounts)

internal fun prepareRestore(current: State, backup: State, useSettings: Boolean): RestorePlan {
    var duplicates = 0
    var conflicts = 0
    fun <T> merge(local: List<T>, incoming: List<T>, key: (T) -> String): List<T> {
        val result = local.associateByTo(linkedMapOf(), key)
        incoming.forEach { item ->
            val id = key(item)
            val before = result[id]
            if (before == null) result[id] = item
            else if (before == item) duplicates++ else conflicts++
        }
        return result.values.toList()
    }
    val byId = current.entries.associateByTo(linkedMapOf()) { it.id }
    val byDose = current.entries.filter { it.kind == "dose" && it.occurrence.isNotBlank() }.associateByTo(mutableMapOf()) { it.occurrence }
    backup.entries.forEach { entry ->
        val dose = entry.kind == "dose" && entry.occurrence.isNotBlank()
        val before = byId[entry.id] ?: if (dose) byDose[entry.occurrence] else null
        if (before == null) {
            byId[entry.id] = entry
            if (dose) byDose[entry.occurrence] = entry
        } else if (before == entry || before.copy(id = entry.id, created = entry.created) == entry) duplicates++
        else conflicts++
    }
    val medicines = merge(current.medications, backup.medications, Medication::id)
    val care = merge(current.care.items, backup.care.items, CareItem::id)
    val layouts = merge(current.care.layouts, backup.care.layouts, DayLayout::name)
    val settings = if (useSettings) backup.preferences.copy(lock = current.preferences.lock,
        lastZone = ZoneId.systemDefault().id) else current.preferences
    // The audit belongs to the Android installation. Imported dose history never changes inventory a second time.
    val planned = current.copy(preferences = settings, entries = byId.values.toList(), medications = medicines,
        care = CareData(care, layouts), audit = current.audit.ifEmpty { backup.audit })
    val counts = RestoreCounts(planned.entries.size - current.entries.size, medicines.size - current.medications.size,
        care.size - current.care.items.size, layouts.size - current.care.layouts.size, duplicates, conflicts)
    return RestorePlan(planned, counts)
}

@Composable
internal fun BackupRestoreReview(current: State, backup: State, recovery: Boolean, busy: Boolean,
    onDismiss: () -> Unit, onRestore: (Boolean, RestoreCounts) -> Unit) {
    val w = LocalWords.current
    var useSettings by remember(backup) { mutableStateOf(current.entries.isEmpty() && current.medications.isEmpty() && current.care.items.isEmpty()) }
    val preview = remember(current, backup, useSettings) { prepareRestore(current, backup, useSettings) }
    val acceptable = remember(preview) { runCatching { Codec.validate(preview.state) }.isSuccess }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(w["restore_preview"]) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val n = preview.counts
            Text(w.f("restore_added", "entries" to number(n.entries.toDouble(), w),
                "medicine" to number(n.medicines.toDouble(), w), "care" to number(n.care.toDouble(), w),
                "layouts" to number(n.layouts.toDouble(), w)))
            Text(w.f("restore_duplicates", "duplicates" to number(n.duplicates.toDouble(), w),
                "conflicts" to number(n.conflicts.toDouble(), w)), style = MaterialTheme.typography.bodySmall)
            Toggle(w["restore_settings"], useSettings, w["restore_settings_hint"]) { useSettings = it }
            if (recovery) Text(w["restore_recovery"], color = MaterialTheme.colorScheme.error)
            if (!acceptable) Text(w["restore_limit"], color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        Button(onClick = { onRestore(useSettings, preview.counts) }, enabled = acceptable && !busy) { Text(w["restore_confirm"]) }
    }, dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(w["cancel"]) } })
}
