package com.yawmi.diabetes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun TrackingOptions(s: State, kind: String, update: ((Preferences) -> Preferences) -> Unit) {
    val w = LocalWords.current
    val p = s.preferences
    val glucose = kind == "glucose"
    val tone = domainColors(kind)
    val now = trackingTime()
    val stats = remember(s.entries, p, now) { TrackingStats(s, now) }
    val shown = if (glucose) stats.glucose(w) else stats.water(w)
    val defaults = remember { Preferences() }
    TrackingCard(kind, shown)
    NasaqBoard {
        val modes = if (glucose) glucoseMetrics else waterMetrics
        ChoiceRow(w["tracking_metric"], modes, if (glucose) p.glucoseViewMetric else p.waterViewMetric, { mode ->
            w[when (mode) {
                "average" -> "widget_glucose_average"
                "latest" -> "latest_glucose_view"
                "daily_average" -> "water_daily_average_view"
                else -> "water_total_view"
            }]
        }) { mode ->
            update { if (glucose) it.copy(glucoseViewMetric = mode) else it.copy(waterViewMetric = mode) }
        }
        val selected = if (glucose) p.glucoseViewDays else p.waterViewDays
        ChoiceRow(w["tracking_period"], trackingDays.map(Int::toString), selected.toString(),
            { trackingPeriod(it.toInt(), w) }) { days ->
            update { if (glucose) it.copy(glucoseViewDays = days.toInt()) else it.copy(waterViewDays = days.toInt()) }
        }
        if (glucose) {
            ChoiceRow(w["reading_filter"], glucoseContexts, p.glucoseViewContext, { w[it] }) { choice ->
                update { it.copy(glucoseViewContext = choice) }
            }
            ChoiceRow(w["unit"], listOf("mg/dL", "mmol/L"), p.unit, { it }) { unit ->
                update { it.copy(unit = unit) }
            }
        } else ChoiceRow(w["unit"], listOf("L", "mL"), p.waterViewUnit, { w[if (it == "mL") "ml" else "litres_short"] }) { unit ->
            update { it.copy(waterViewUnit = unit) }
        }
        Text(w[if (glucose) "glucose_view_hint" else "water_view_hint"],
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(w["tracking_display_hint"], color = tone.ink, style = MaterialTheme.typography.bodySmall)
        OutlineAction(onClick = {
            update {
                if (glucose) it.copy(glucoseViewDays = defaults.glucoseViewDays,
                    glucoseViewMetric = defaults.glucoseViewMetric, glucoseViewContext = defaults.glucoseViewContext)
                else it.copy(waterViewDays = defaults.waterViewDays,
                    waterViewMetric = defaults.waterViewMetric, waterViewUnit = defaults.waterViewUnit)
            }
        }) { Text(w["tracking_reset"]) }
    }
}

@Composable
internal fun TrackingCard(kind: String, item: MetricDisplay) {
    val tone = domainColors(kind)
    NasaqBoard(color = tone.backdrop, contentColor = tone.ink, outlined = false) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Glyph(kind, Modifier.size(24.dp), tone.ink)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(item.value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (item.period.isNotEmpty()) Text(item.period, style = MaterialTheme.typography.labelLarge)
                if (item.detail.isNotEmpty()) Text(item.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun DoseTrackingReference(s: State) {
    val w = LocalWords.current
    val now = trackingTime()
    val stats = remember(s.entries, s.preferences, now) { TrackingStats(s, now) }
    if (stats.glucoseReadings.isNotEmpty()) {
        val glucose = stats.glucose(w)
        HorizontalDivider()
        Text(glucose.label + " · " + glucose.period, style = MaterialTheme.typography.labelMedium)
        Text(glucose.value, style = MaterialTheme.typography.titleMedium)
    }
}
