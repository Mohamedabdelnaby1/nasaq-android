package com.yawmi.diabetes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
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
        HydrationInstrument(state, overview.waterMl, busy) { add("water") }
        if (state.preferences.waterGoal > 0) {
            Reveal(1) {
                NasaqBoard(color = c.tertiaryContainer, contentColor = c.onTertiaryContainer, outlined = false) {
                    SectionHeading(w["tap_cup_short"])
                    WaterCups(state, busy, portion)
                }
            }
        }
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


@Composable
private fun HydrationInstrument(state: State, water: Double, busy: Boolean, record: () -> Unit) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val goal = state.preferences.waterGoal
    val progress = if (goal > 0) (water / goal).toFloat().coerceIn(0f, 1f) else 0f
    Surface(Modifier.sharedDesk("water"), color = c.primary, contentColor = c.onPrimary, shape = RoundedCornerShape(32.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Contours(Modifier.matchParentSize(), c.onPrimary)
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Glyph("water", Modifier.size(25.dp), c.onPrimary)
                        Text(w["water_today"], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AnimatedNumber(water / 1000, digits = 2, color = c.onPrimary)
                        Text(w["litres_short"], style = MaterialTheme.typography.labelLarge)
                        if (goal > 0) Text(w["daily_target"] + " · " + number(goal / 1000.0, w, 2) + " " + w["litres_short"],
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (goal > 0) {
                        Box(Modifier.size(163.dp, 177.dp), contentAlignment = Alignment.Center) {
                            MeterRing(progress, Modifier.size(145.dp), color = c.onPrimary.copy(alpha = .78f))
                            WaterVessel(progress, Modifier.size(70.dp, 128.dp))
                        }
                    }
                }
                Button(onClick = record, enabled = !busy, shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(containerColor = c.secondaryContainer, contentColor = c.onSecondaryContainer),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Glyph("plus", Modifier.size(18.dp), c.onSecondaryContainer)
                    Spacer(Modifier.width(6.dp))
                    Text(w["custom_amount"])
                }
            }
        }
    }
}
