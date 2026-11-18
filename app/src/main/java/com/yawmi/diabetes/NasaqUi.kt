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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal val BoardShape = RoundedCornerShape(topStart = 30.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 30.dp)
internal val ControlShape = RoundedCornerShape(16.dp)
internal val ScreenGutters = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 24.dp)

internal data class DomainColors(val backdrop: Color, val ink: Color)

@Composable
internal fun domainColors(kind: String): DomainColors {
    val c = MaterialTheme.colorScheme
    val dark = c.background.luminance() < .28f
    fun tone(light: Long, black: Long, deep: Long, pale: Long) =
        if (dark) DomainColors(Color(deep), Color(pale)) else DomainColors(Color(light), Color(black))
    return when (kind) {
        "glucose", "log" -> tone(0xFFE7DCFC, 0xFF342267, 0xFF392C54, 0xFFECE1FF)
        "water", "hydration" -> tone(0xFFC9EEFD, 0xFF123D53, 0xFF183E4D, 0xFFC1EDFC)
        "medication", "medications", "dose", "bell" -> tone(0xFFFFDCC8, 0xFF54281C, 0xFF553527, 0xFFFFDECC)
        "meal", "meals" -> tone(0xFFFFE9A7, 0xFF533901, 0xFF4B3B17, 0xFFFFE9A7)
        "symptom", "symptoms", "shield" -> tone(0xFFFFDBE3, 0xFF63253B, 0xFF563241, 0xFFFFDEE7)
        "insights", "export" -> tone(0xFFDDECA5, 0xFF31441A, 0xFF34482B, 0xFFE3F4C8)
        else -> DomainColors(c.primaryContainer, c.onPrimaryContainer)
    }
}

@Composable
internal fun DomainMark(kind: String, modifier: Modifier = Modifier) {
    val tone = domainColors(kind)
    Surface(modifier = modifier, shape = RoundedCornerShape(topStart = 15.dp, topEnd = 11.dp, bottomStart = 11.dp, bottomEnd = 15.dp), color = tone.backdrop) {
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
    BoxWithConstraints(modifier.fillMaxWidth().clip(ControlShape).background(c.surfaceContainerHigh).padding(4.dp)) {
        val cell = maxWidth / labels.size
        val position by animateDpAsState(cell * selected.coerceIn(labels.indices), if (motion) tween(480, easing = NasaqEase) else snap(), label = "rail-indicator")
        Box(Modifier.offset(x = position).width(cell).height(42.dp).background(c.primary, RoundedCornerShape(12.dp)))
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            labels.forEachIndexed { index, label ->
                val active = selected == index
                val ink by animateColorAsState(if (active) c.onPrimary else c.onSurfaceVariant,
                    if (motion) tween(235) else snap(), label = "rail-text")
                Box(Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(12.dp))
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

@Composable
internal fun SectionArt(kind: String, modifier: Modifier = Modifier, color: Color) {
    val rtl = LocalWords.current.rtl
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val origin = if (rtl) 0f else width
        val sign = if (rtl) 1f else -1f
        fun p(x: Float, y: Float) = Offset(origin + width * x * sign, height * y)
        when (kind) {
            "water", "hydration" -> repeat(5) { row ->
                val y = .44f + row * .14f
                val wave = Path().apply {
                    moveTo(p(0f, y).x, p(0f, y).y)
                    cubicTo(p(.16f,y-.48f).x,p(.16f,y-.48f).y,p(.26f,y+.47f).x,p(.26f,y+.47f).y,
                        p(.55f,y-.06f).x,p(.55f,y-.06f).y)
                }
                drawPath(wave, color.copy(alpha = .16f - row * .018f), style = Stroke(1.25.dp.toPx()))
            }
            "medication", "medications", "dose" -> repeat(7) { index ->
                val dot = p(.06f + index % 3 * .065f, .09f + index / 3 * .31f)
                drawRoundRect(color.copy(alpha = .12f), dot, Size(width*.035f, height*.40f), CornerRadius(14.dp.toPx()))
            }
            "insights", "export" -> repeat(5) { index ->
                val point = p(.018f + index*.062f, .23f + index*.11f)
                drawRoundRect(color.copy(alpha = .14f), point, Size(width*.035f, height*.85f), CornerRadius(8.dp.toPx()))
            }
            else -> {
                val center = p(.07f, .67f)
                repeat(4) { index ->
                    val radius = size.minDimension * (.25f + .22f * index)
                    drawCircle(color.copy(alpha = .19f - index*.038f), radius, center, style = Stroke(1.dp.toPx()))
                    if (index == 1 || index == 3)
                        drawCircle(color.copy(alpha = .24f), 3.dp.toPx(), center + Offset(radius * sign * .63f, -radius * .77f))
                }
            }
        }
    }
}

@Composable
internal fun SectionBanner(
    kind: String, title: String, detail: String? = null,
    action: @Composable (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val tone = domainColors(kind)
    Surface(Modifier.fillMaxWidth(), shape = BoardShape, color = tone.backdrop, contentColor = tone.ink) {
        Box {
            SectionArt(kind, Modifier.matchParentSize(), tone.ink)
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = ControlShape, color = tone.ink, contentColor = tone.backdrop) {
                        Glyph(kind, Modifier.padding(11.dp).size(22.dp), tone.backdrop)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall)
                        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    action?.invoke()
                }
                content?.invoke(this)
            }
        }
    }
}
