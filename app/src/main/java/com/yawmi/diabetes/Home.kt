@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.drawscope.clipRect

@Composable fun DoseRow(r:Reminder,s:State,onClick:(Reminder)->Unit) {
    val w=LocalWords.current;val med=s.medications.find {it.id==r.medicationId} ?: return
    val now=System.currentTimeMillis();val old=now-r.original>2*3_600_000
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.primaryContainer) {Glyph("medication",Modifier.padding(10.dp).size(24.dp))}
        Column(Modifier.weight(1f)) {Text(med.name,fontWeight=FontWeight.SemiBold);Text("${med.dose} · ${timeText(r.due,w)}",style=MaterialTheme.typography.bodySmall)
            if(old) Text(w["not_recorded"],style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        TextAction(onClick={onClick(r)}) {Text(w["record_dose"])}
    }
}

@Composable
fun GlucoseChart(entries: List<Entry>, p: Preferences, onOpenDay: ((java.time.LocalDate) -> Unit)? = null) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val points = remember(entries) { entries.filter { it.kind == "glucose" }.sortedBy { it.time }.takeLast(120) }
    if (points.isEmpty()) { EmptyState(w["no_analysis"]); return }
    val motion = LocalMotion.current
    val reveal = remember(points) { Animatable(if (motion) 0f else 1f) }
    var selected by remember(points) { mutableIntStateOf(-1) }
    val values = remember(points) { points.map(::mg) }
    val low = minOf(values.min(), p.lower) * .86
    val high = maxOf(values.max(), p.upper) * 1.08
    val duration = points.last().time - points.first().time
    val fractions = remember(points, duration) {
        points.mapIndexed { index, entry ->
            when {
                points.size == 1 -> .5f
                duration <= 0 -> index.toFloat() / points.lastIndex
                else -> (entry.time - points.first().time).toFloat() / duration
            }
        }
    }
    fun nearest(x: Float, width: Float, inset: Float): Int {
        val fraction = (x - inset) / (width - inset * 2).coerceAtLeast(1f)
        return fractions.indices.minBy { kotlin.math.abs(fractions[it] - fraction) }
    }
    LaunchedEffect(points, motion) {
        if (motion) reveal.animateTo(1f, tween(860, 90, NasaqEase)) else reveal.snapTo(1f)
    }
    val cursorTarget = fractions.getOrElse(selected) { 0f }
    val cursor by animateFloatAsState(cursorTarget,
        if (motion && selected >= 0) tween(150, easing = NasaqEase) else snap(), label = "chart-cursor")
    val point = points.getOrNull(selected)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 35.dp), contentAlignment = Alignment.CenterStart) {
            if (point != null) {
                val measurement = com.yawmi.diabetes.core.Planner.fromMg(mg(point), p.unit)
                val value = number(measurement, w, if (p.unit == "mmol/L") 1 else 0) + " " + p.unit
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Glyph("clock", Modifier.size(15.dp), c.onPrimaryContainer)
                    Text(w.f("chart_current_point", "value" to value, "time" to timeText(point.time, w)),
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            } else Text(w["chart_explore"], style = MaterialTheme.typography.bodySmall)
        }
        if (point != null && onOpenDay != null) TextAction(onClick = {
            onOpenDay(java.time.Instant.ofEpochMilli(point.time).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
        }) { Text(w["open_selected_day"]) }
        Canvas(Modifier.fillMaxWidth().height(163.dp)
            .pointerInput(points) {
                detectTapGestures { position -> selected = nearest(position.x, size.width.toFloat(), 10.dp.toPx()) }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { position -> selected = nearest(position.x, size.width.toFloat(), 10.dp.toPx()) }
                ) { change, _ -> selected = nearest(change.position.x, size.width.toFloat(), 10.dp.toPx()); change.consume() }
            }
            .semantics {
                contentDescription = w["chart_description"]
                customActions = listOf(
                    CustomAccessibilityAction(w["back"]) {
                        selected = if (selected < 0) points.lastIndex else (selected - 1).coerceAtLeast(0)
                        true
                    },
                    CustomAccessibilityAction(w["next"]) {
                        selected = (selected + 1).coerceAtMost(points.lastIndex)
                        true
                    }
                )
            }) {
            val bottom = size.height - 9.dp.toPx()
            val top = 10.dp.toPx()
            val left = 10.dp.toPx()
            val width = (size.width - left * 2).coerceAtLeast(1f)
            val ink = c.onPrimaryContainer
            fun y(v: Double) = (bottom - (v - low) / (high - low).coerceAtLeast(1.0) * (bottom - top)).toFloat()
            val coordinates = fractions.mapIndexed { index, fraction -> Offset(left + fraction * width, y(values[index])) }
            val bandTop = y(p.upper)
            val bandHeight = (y(p.lower) - bandTop).coerceAtLeast(0f)
            drawRect(ink.copy(alpha = .06f), Offset(left, bandTop), androidx.compose.ui.geometry.Size(width, bandHeight))
            repeat(4) { index ->
                val yy = top + (bottom - top) * index / 3
                drawLine(ink.copy(alpha = .16f), Offset(left, yy), Offset(left + width, yy), 1.dp.toPx())
            }
            clipRect(right = size.width * reveal.value) {
                if (coordinates.size > 1) {
                    val trace = Path().apply {
                        moveTo(coordinates.first().x, coordinates.first().y)
                        coordinates.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    val area = Path().apply {
                        addPath(trace)
                        lineTo(coordinates.last().x, bottom)
                        lineTo(coordinates.first().x, bottom)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(ink.copy(alpha = .24f), Color.Transparent), startY = top, endY = bottom))
                    drawPath(trace, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                coordinates.forEach { position -> drawCircle(ink, 2.6.dp.toPx(), position) }
            }
            if (selected in coordinates.indices) {
                val x = left + cursor * width
                val selectedPoint = coordinates[selected]
                drawLine(ink.copy(alpha = .48f), Offset(x, top), Offset(x, bottom), 1.dp.toPx())
                drawCircle(c.primaryContainer, 6.dp.toPx(), selectedPoint)
                drawCircle(ink, 6.dp.toPx(), selectedPoint, style = Stroke(2.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dateText(points.first().time, w), style = MaterialTheme.typography.labelSmall)
            if (points.size > 1) Text(dateText(points.last().time, w), style = MaterialTheme.typography.labelSmall)
        }
        Text(w["chart_not_prediction"], style = MaterialTheme.typography.bodySmall)
    }
}
