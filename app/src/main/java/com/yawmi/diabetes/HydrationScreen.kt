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

@Composable
internal fun HydrationScreen(
    state: State,
    busy: Boolean,
    onBack: () -> Unit,
    add: (String) -> Unit,
    portion: (Double, String, String) -> Unit,
    onTrackingOptions: (() -> Unit)? = null
) {
    val w = LocalWords.current
    val now = trackingTime()
    val stats = remember(state.entries, state.preferences, now) { TrackingStats(state, now) }
    val overview = remember(state, now) { dayOverview(state, now) }
    val c = MaterialTheme.colorScheme
    val records = remember(overview.entries) { overview.entries.filter { it.kind == "water" } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenGutters),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionBanner("water", w["water"], dateText(now, w), action = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onTrackingOptions != null) IconAction(onTrackingOptions, Modifier.semantics { contentDescription = w["tracking_options"] }) {
                    Glyph("settings", Modifier.size(19.dp), c.onTertiaryContainer)
                }
                IconAction(onClick = onBack, modifier = Modifier.semantics { contentDescription = w["back"] }) {
                    Glyph("back", Modifier.size(21.dp), c.onTertiaryContainer)
                }
            }
        })
        ActionButton({ add("water") }, Modifier.fillMaxWidth(), enabled = !busy) {
            Glyph("plus", Modifier.size(18.dp), LocalContentColor.current)
            Spacer(Modifier.width(7.dp))
            Text(w["custom_amount"])
        }
        if (state.preferences.waterViewDays != 1 || state.preferences.waterViewMetric != "total")
            TrackingCard("water", stats.water(w))
        HydrationInstrument(state, stats.todayWaterMl)
        if (state.preferences.waterGoal > 0) {
            Reveal(1) {
                NasaqBoard(color = c.tertiaryContainer, contentColor = c.onTertiaryContainer, outlined = false) {
                    SectionHeading(w["tap_cup_short"])
                    WaterCups(state, busy, portion)
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
private fun HydrationInstrument(state: State, water: Double) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val goal = state.preferences.waterGoal
    val progress = if (goal > 0) (water / goal).toFloat().coerceIn(0f, 1f) else 0f
    val reservoir = androidx.compose.ui.graphics.Color(0xFF103E50)
    val pale = androidx.compose.ui.graphics.Color(0xFFCCF3FE)
    Surface(Modifier.sharedDesk("water"), color = reservoir, contentColor = pale, shape = BoardShape) {
        Box(Modifier.fillMaxWidth()) {
            SectionArt("water", Modifier.matchParentSize(), pale)
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(w["water_today"], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val inMl = state.preferences.waterViewUnit == "mL"
                        AnimatedNumber(if (inMl) water else water / 1000, digits = if (inMl) 1 else 2,
                            style = MaterialTheme.typography.displayMedium, color = pale)
                        Text(w[if (inMl) "ml" else "litres_short"], style = MaterialTheme.typography.labelLarge)
                        if (goal > 0) {
                            Spacer(Modifier.height(3.dp))
                            Text(w["daily_target"], style = MaterialTheme.typography.labelMedium)
                            val (amount, unit) = waterVolume(goal.toDouble(), state.preferences.waterViewUnit, w)
                            Text("$amount $unit", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    WaterVessel(progress, Modifier.width(108.dp).height(182.dp))
                }
                if (goal > 0) {
                    val animated by androidx.compose.animation.core.animateFloatAsState(progress, if (LocalMotion.current)
                        androidx.compose.animation.core.tween(750, easing = NasaqEase) else androidx.compose.animation.core.snap(), label = "goal fill")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(progress = { animated }, modifier = Modifier.weight(1f).height(8.dp),
                            color = pale, trackColor = pale.copy(alpha = .20f))
                        AnimatedNumber(progress * 100.0, suffix = "%", style = MaterialTheme.typography.labelLarge, color = pale)
                    }
                }

            }
        }
    }
}
