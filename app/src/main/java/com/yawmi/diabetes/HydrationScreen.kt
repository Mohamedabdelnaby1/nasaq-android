package com.yawmi.diabetes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun HydrationScreen(
    state: State,
    busy: Boolean,
    onBack: () -> Unit,
    add: (String) -> Unit,
    portion: (Double, String, String) -> Unit
) {
    val w = LocalWords.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - now % 60_000)
            now = System.currentTimeMillis()
        }
    }
    val overview = remember(state, now) { dayOverview(state, now) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = w["back"] }) {
                Glyph("back", Modifier.size(22.dp))
            }
            Text(w["water"], style = MaterialTheme.typography.headlineSmall)
        }
        DetailedWater(state, overview.waterMl, busy, add, portion)
        Panel(w["day_water_average"]) {
            val value = overview.waterAverageMl?.let { number(it / 1000, w, 2) + " " + w["litres_short"] } ?: "—"
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(w.f("day_water_coverage", "count" to number(overview.waterDays.toDouble(), w)))
        }
        Panel(w["day_activity"]) {
            val records = overview.entries.filter { it.kind == "water" }
            if (records.isEmpty()) EmptyState(w["story_empty"])
            records.forEachIndexed { index, entry ->
                ActivityRow(entry, state)
                if (index < records.lastIndex) HorizontalDivider()
            }
        }
    }
}
