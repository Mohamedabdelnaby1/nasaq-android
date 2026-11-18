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
internal fun ReportComposer(initialDays: Int, onDismiss: () -> Unit, onCreate: (Int, ReportOptions) -> Unit) {
    val w = LocalWords.current
    var days by remember { mutableIntStateOf(initialDays) }
    var options by remember { mutableStateOf(ReportOptions()) }
    val ranges = listOf(1, 7, 14, 30)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScreenTitle(w["report_options"]) { DomainMark("export") }
            SegmentedRail(ranges.map { w[if (it == 1) "today_only" else "last$it"] }, ranges.indexOf(days), { days = ranges[it] })
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
            Button(onClick = { onCreate(days, options) }, modifier = Modifier.fillMaxWidth().heightIn(min = 49.dp),
                enabled = options.summary || options.chart || options.journal || options.plans) { Text(w["export_pdf"]) }
        }
    }
}
