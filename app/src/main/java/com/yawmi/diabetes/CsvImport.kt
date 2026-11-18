package com.yawmi.diabetes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yawmi.diabetes.core.Planner
import java.io.Reader
import java.time.Instant
import java.util.Locale
import java.util.UUID

internal data class CsvPreview(val entries: List<Entry>, val invalid: Int) {
    fun additions(state: State): List<Entry> {
        val ids = state.entries.mapTo(mutableSetOf()) { it.id }
        val facts = state.entries.mapTo(mutableSetOf(), ::fingerprint)
        return entries.filter { entry -> ids.add(entry.id) && facts.add(fingerprint(entry)) }
    }

    private fun fingerprint(entry: Entry) = listOf(entry.kind, entry.time, entry.value, entry.second,
        entry.unit, entry.context, entry.status, entry.note, entry.planned)
}

internal object CsvImport {
    fun read(input: Reader, now: Long = System.currentTimeMillis()): CsvPreview {
        val rows = records(input)
        val headers = rows.firstOrNull()?.map { it.removePrefix("\uFEFF").trim().lowercase(Locale.ROOT) }.orEmpty()
        require(headers.distinct() == headers && headers.size <= 32)
        val columns = headers.withIndex().associate { it.value to it.index }
        require("measured_at_iso" in columns && "kind" in columns && ("value_original" in columns || "value" in columns))
        var invalid = 0
        val entries = rows.drop(1).mapNotNull { row ->
            runCatching {
                require(row.size == headers.size)
                fun get(key: String) = columns[key]?.let(row::get).orEmpty()
                fun numeric(key: String) = get(key).trim().takeIf(String::isNotEmpty)?.let(Planner::parseNumber)
                fun date(key: String) = get(key).trim().takeIf(String::isNotEmpty)?.let { Instant.parse(it).toEpochMilli() }
                val time = requireNotNull(date("measured_at_iso"))
                val kind = get("kind").trim().lowercase(Locale.ROOT)
                val value = numeric("value_original") ?: numeric("value") ?: 0.0
                val unit = get("unit").trim()
                val id = get("id").trim().ifBlank { UUID.randomUUID().toString() }
                val note = get("note")
                val status = get("status")
                val second = numeric("second_value") ?: 0.0
                require(time in 1..now && id.length in 1..120 && note.length <= 4000)
                require(kind in setOf("glucose", "water", "meal", "symptom", "dose", "weight", "pressure", "a1c"))
                when (kind) {
                    "glucose" -> require(unit in setOf("mg/dL", "mmol/L") && value > 0 && Planner.toMg(value, unit) <= 5000)
                    "water" -> require(unit == "mL" && value in 1.0..5000.0)
                    "dose" -> require(status in setOf("taken", "skipped"))
                }
                Entry(id = id, kind = kind, time = time, value = value, second = second, unit = unit,
                    context = get("context"), note = note, status = status, created = date("entered_at_iso") ?: time,
                    planned = date("planned_at_iso") ?: 0)
            }.getOrElse { invalid++; null }
        }
        return CsvPreview(entries, invalid)
    }

    private fun records(input: Reader): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val reader = input.buffered()
        val cell = StringBuilder()
        val row = mutableListOf<String>()
        var quoted = false
        var closed = false
        var chars = 0
        var skipLineFeed = false
        fun field() {
            row.add(cell.toString()); cell.clear(); closed = false
            require(row.size <= 32)
        }
        fun record() {
            field()
            if (row.size > 1 || row[0].isNotBlank()) rows.add(row.toList())
            row.clear(); require(rows.size <= 20_001)
        }
        while (true) {
            val next = reader.read()
            if (next < 0) break
            require(++chars <= 8_000_000)
            val char = next.toChar()
            if (chars == 1 && char == '\uFEFF') continue
            if (skipLineFeed) {
                skipLineFeed = false
                if (char == '\n') continue
            }
            if (quoted) {
                if (char == '"') { quoted = false; closed = true } else cell.append(char)
            } else when (char) {
                '"' -> {
                    require(closed || cell.isEmpty())
                    if (closed) cell.append('"')
                    quoted = true; closed = false
                }
                ',' -> field()
                '\n', '\r' -> { record(); skipLineFeed = char == '\r' }
                else -> { require(!closed); cell.append(char) }
            }
            require(cell.length <= 16_384)
        }
        require(!quoted)
        if (closed || cell.isNotEmpty() || row.isNotEmpty()) record()
        return rows
    }
}

@Composable
internal fun CsvReview(preview: CsvPreview, state: State, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val w = LocalWords.current
    val pending = preview.additions(state)
    BrandedDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(w["csv_import"]) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(w.f("csv_import_count", "added" to number(pending.size.toDouble(), w),
                "duplicate" to number((preview.entries.size - pending.size).toDouble(), w),
                "invalid" to number(preview.invalid.toDouble(), w)))
            pending.take(3).forEach { entry ->
                Text(dateText(entry.time, w) + " · " + timeText(entry.time, w), style = MaterialTheme.typography.labelMedium)
                ActivityRow(entry, state)
            }
        }
    }, confirmButton = { TextAction(onConfirm, enabled = pending.isNotEmpty() && !busy) { Text(w["csv_confirm"]) } },
        dismissButton = { TextAction(onDismiss, enabled = !busy) { Text(w["close"]) } })
}
