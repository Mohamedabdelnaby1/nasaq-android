@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.yawmi.diabetes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ReportComposer(state: State, initialDays: Int, onDismiss: () -> Unit, onCreate: (Int, ReportOptions, Long) -> Unit) {
    val w = LocalWords.current
    var days by remember { mutableIntStateOf(initialDays) }
    val brief = ReportOptions(showName = false, chart = false, journal = false, plans = false, notes = false)
    val detailed = ReportOptions(showName = false, notes = false)
    var options by remember { mutableStateOf(detailed) }
    val ranges = listOf(1, 7, 14, 30)
    val minute = trackingTime()
    val snapshot = remember(state, days, minute) { Reports.snapshot(state, days, System.currentTimeMillis()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BoardShape, containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionBanner("export", w["report_options"])
            SegmentedRail(ranges.map { w[if (it == 1) "today_only" else "last$it"] }, ranges.indexOf(days), { days = ranges[it] })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(options == brief, { options = brief }, label = { Text(w["report_visit_brief"]) })
                ChoiceChip(options == detailed, { options = detailed }, label = { Text(w["report_full"]) })
            }
            NasaqBoard {
                SectionHeading(w["report_preview_data"])
                Text(snapshot.dateRange(w), style = MaterialTheme.typography.labelLarge)
                if (options.summary || options.chart) {
                    val readings = w.f("day_reading_count", "count" to number(snapshot.glucose.size.toDouble(), w))
                    val glucose = snapshot.averageMg?.let { value ->
                        val unit = state.preferences.unit
                        number(com.yawmi.diabetes.core.Planner.fromMg(value, unit), w, if (unit == "mmol/L") 1 else 0) + " " + unit
                    } ?: "—"
                    Text(w["widget_glucose_average"] + " · " + glucose + " · " + readings)
                }
                if (options.summary) {
                    val (amount, unit) = waterVolume(snapshot.waterMl, state.preferences.waterViewUnit, w)
                    Text(w["water_total_view"] + " · " + amount + " " + unit)
                    Text(w["report_doses_taken"] + " · " + number(snapshot.dosesTaken.toDouble(), w))
                }
            }
            NasaqBoard {
                Toggle(w["report_show_name"], options.showName) { options = options.copy(showName = it) }
                Toggle(w["report_section_summary"], options.summary) { options = options.copy(summary = it) }
                Toggle(w["glucose_trend"], options.chart) { options = options.copy(chart = it) }
                Toggle(w["log"], options.journal) { options = options.copy(journal = it) }
                Toggle(w["current_plans"], options.plans) { options = options.copy(plans = it) }
                Toggle(w["report_include_notes"], options.notes) { options = options.copy(notes = it) }
                MotionExpand(options.notes) {
                    Field(w["report_message"], options.message, { options = options.copy(message = it.take(1400)) }, multiline = true)
                }
            }
            ActionButton(onClick = { onCreate(days, options, snapshot.until) }, modifier = Modifier.fillMaxWidth().heightIn(min = 49.dp),
                enabled = options.summary || options.chart || options.journal || options.plans) { Text(w["export_pdf"]) }
        }
    }
}
