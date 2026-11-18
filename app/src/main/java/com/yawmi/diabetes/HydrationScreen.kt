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
    val c = MaterialTheme.colorScheme
    val records = remember(overview.entries) { overview.entries.filter { it.kind == "water" } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenGutters),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle(w["water"], dateText(now, w)) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = w["back"] }) {
                Glyph("back", Modifier.size(21.dp))
            }
        }
        DetailedWater(state, overview.waterMl, busy, add, portion, expanded = true)
        Reveal(1) {
            NasaqBoard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DomainMark("water")
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(w["day_water_average"], style = MaterialTheme.typography.titleMedium)
                        Text(w.f("day_water_coverage", "count" to number(overview.waterDays.toDouble(), w)),
                            style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                    }
                    val value = overview.waterAverageMl?.let { number(it / 1000, w, 2) + " " + w["litres_short"] } ?: "—"
                    Text(value, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Reveal(2) {
            NasaqBoard {
                Text(w["recorded_today"], style = MaterialTheme.typography.titleMedium)
                if (records.isEmpty()) EmptyState(w["story_empty"])
                records.forEachIndexed { index, entry ->
                    ActivityRow(entry, state)
                    if (index < records.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}
