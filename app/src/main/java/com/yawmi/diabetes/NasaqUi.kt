package com.yawmi.diabetes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal val BoardShape = RoundedCornerShape(27.dp)
internal val ControlShape = RoundedCornerShape(15.dp)
internal val ScreenGutters = PaddingValues(start = 20.dp, end = 20.dp, top = 15.dp, bottom = 24.dp)

internal data class DomainColors(val backdrop: Color, val ink: Color)

@Composable
internal fun domainColors(kind: String): DomainColors {
    val c = MaterialTheme.colorScheme
    return when (kind) {
        "water" -> DomainColors(c.tertiaryContainer, c.onTertiaryContainer)
        "medication", "medications", "dose", "meal" -> DomainColors(c.secondaryContainer, c.onSecondaryContainer)
        else -> DomainColors(c.primaryContainer, c.onPrimaryContainer)
    }
}

@Composable
internal fun DomainMark(kind: String, modifier: Modifier = Modifier) {
    val tone = domainColors(kind)
    Surface(modifier = modifier, shape = CircleShape, color = tone.backdrop) {
        Glyph(kind, Modifier.padding(10.dp).size(20.dp), tone.ink)
    }
}

@Composable
internal fun ScreenTitle(
    title: String,
    context: String? = null,
    action: @Composable (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (context != null) Text(context, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        action?.invoke()
    }
}

@Composable
internal fun NasaqBoard(
    modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface, outlined: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier.fillMaxWidth(), shape = BoardShape, color = color, contentColor = contentColor,
        border = if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun SegmentedRail(
    labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val c = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    BoxWithConstraints(modifier.fillMaxWidth().clip(CircleShape).background(c.surfaceContainerHigh).padding(4.dp)) {
        val cell = maxWidth / labels.size
        val position by animateDpAsState(cell * selected.coerceIn(labels.indices), if (motion) tween(480, easing = NasaqEase) else snap(), label = "rail-indicator")
        Box(Modifier.offset(x = position).width(cell).height(42.dp).background(c.primary, CircleShape))
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            labels.forEachIndexed { index, label ->
                val active = selected == index
                val ink by animateColorAsState(if (active) c.onPrimary else c.onSurfaceVariant,
                    if (motion) tween(235) else snap(), label = "rail-text")
                Box(Modifier.weight(1f).heightIn(min = 42.dp).clip(CircleShape)
                    .selectable(active, role = Role.Tab, onClick = { onSelect(index) }).padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center) {
                    Text(label, color = ink, style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun Contours(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val center = Offset(size.width * .16f, size.height * .57f)
        repeat(4) { index ->
            drawCircle(color.copy(alpha = .07f + index * .012f), size.minDimension * (.23f + index * .15f),
                center, style = Stroke(1.dp.toPx()))
        }
    }
}
