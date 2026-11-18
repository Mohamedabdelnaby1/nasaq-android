package com.yawmi.diabetes

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.math.*

val LocalMotion=staticCompositionLocalOf{true}
val LocalBrand=staticCompositionLocalOf{Color(0xFF154F42)}
internal val NasaqEase = CubicBezierEasing(.22f, 1f, .36f, 1f)

@Composable fun Reveal(index:Int=0,content:@Composable ()->Unit) {
    val motion=LocalMotion.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) progress.animateTo(1f, tween(400, (index * 36).coerceAtMost(145), NasaqEase))
        else progress.snapTo(1f)
    }
    Column(Modifier.fillMaxWidth().graphicsLayer {
        alpha = progress.value
        translationY = 16.dp.toPx() * (1 - progress.value)
    }) { content() }
}

@Composable
fun WaterVessel(fraction: Float, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val amount = fraction.coerceIn(0f, 1f)
    val fill = animateFloatAsState(amount, if (motion) tween(780, 100, NasaqEase) else snap(), label = "bottle-fill")
    val wave = remember { Animatable(1f) }
    var previous by remember { mutableFloatStateOf(amount) }
    var direction by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(amount, motion) {
        val difference = amount - previous
        previous = amount
        if (motion && difference != 0f) {
            direction = if (difference > 0f) 1f else -1f
            wave.snapTo(0f)
            wave.animateTo(1f, tween(970, easing = LinearEasing))
        } else if (!motion) wave.snapTo(1f)
    }
    Canvas(modifier) {
        val h = size.height
        val w = min(size.width, h * .65f)
        val left = (size.width - w) / 2
        fun at(x: Float, y: Float) = Offset(left + w * x, h * y)
        val outline = Path().apply {
            moveTo(at(.33f, .14f).x, h * .14f)
            lineTo(at(.67f, .14f).x, h * .14f)
            lineTo(at(.67f, .22f).x, h * .22f)
            cubicTo(at(.68f, .28f).x, h*.28f, at(.87f, .25f).x, h*.25f, at(.87f, .40f).x, h*.40f)
            lineTo(at(.87f, .86f).x, h*.86f)
            quadraticTo(at(.87f, .95f).x, h*.95f, at(.72f, .95f).x, h*.95f)
            lineTo(at(.28f, .95f).x, h*.95f)
            quadraticTo(at(.13f, .95f).x, h*.95f, at(.13f, .86f).x, h*.86f)
            lineTo(at(.13f, .40f).x, h*.40f)
            cubicTo(at(.13f, .25f).x, h*.25f, at(.32f, .28f).x, h*.28f, at(.33f, .22f).x, h*.22f)
            close()
        }
        val body = listOf(Color(0xFF789FAA), Color(0xFFD5F0F6), Color(0xFFEEF9FA), Color(0xFFA1CEDB))
        drawPath(outline, Brush.horizontalGradient(body, at(.13f, 0f).x, at(.87f, 0f).x))
        val phase = wave.value
        val level = h * (.94f - fill.value * .67f)
        val surge = (1 - phase).pow(2)
        clipPath(outline) {
            if (fill.value > .001f) {
                val edge = Path().apply {
                    moveTo(left, level)
                    for (step in 0..28) {
                        val x = step / 28f
                        val oscillation = sin(x * 8f - phase * 19f * direction) * surge * .063f
                        val incline = (x - .5f) * sin(phase * 15f) * surge * .17f
                        lineTo(at(x, 0f).x, level + h * (oscillation + incline))
                    }
                }
                val liquid = Path().apply { addPath(edge); lineTo(left+w, h); lineTo(left, h); close() }
                drawPath(liquid, Brush.verticalGradient(listOf(Color(0xFF53C6CD), Color(0xFF147E99), Color(0xFF155874)), level, h))
                drawPath(edge, Color(0xC7C8FCFC), style = Stroke(1.65.dp.toPx()))
                if (direction > 0 && surge > .02f) repeat(4) { index ->
                    val point = Offset(at(.28f + index * .135f, 0f).x, level + h * (.07f + index * .035f - phase * .09f))
                    if (point.y > level) drawCircle(Color.White.copy(alpha = surge * .70f), (2 + index % 2).dp.toPx(), point,
                        style = Stroke(1.dp.toPx()))
                }
            }
            val side = Path().apply {
                moveTo(at(.22f, .76f).x, h * .76f)
                lineTo(at(.22f, .44f).x, h * .44f)
                quadraticTo(at(.22f, .33f).x, h * .33f, at(.38f, .30f).x, h * .30f)
            }
            drawPath(side, Color.White.copy(alpha = .58f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawRoundRect(Color(0xFF073A54).copy(alpha = .16f), at(.67f, .33f), Size(w*.14f, h*.52f), CornerRadius(w*.06f))
            val pour = surge * direction.coerceAtLeast(0f)
            if (pour > .014f) {
                val stream = Path().apply {
                    moveTo(at(.51f, .23f).x, h * .23f)
                    quadraticTo(at(.46f, .40f).x, h * .40f, at(.53f, .52f).x, max(level, h*.34f))
                }
                drawPath(stream, Color(0xFF96EFEE).copy(alpha = pour * .8f),
                    style = Stroke((1.5f + 2f * pour).dp.toPx(), cap = StrokeCap.Round))
            }
        }
        val ink = Color(0xFF184E62)
        drawPath(outline, Color(0xFFB5E1EE), style = Stroke(3.dp.toPx()))
        drawPath(outline, ink, style = Stroke(1.4.dp.toPx()))
        repeat(4) { index ->
            val y = .40f + index * .122f
            drawLine(ink.copy(alpha = .75f), at(if (index % 2 == 0) .63f else .68f, y), at(.78f, y),
                1.dp.toPx(), StrokeCap.Round)
        }
        drawRoundRect(ink, at(.265f, .06f), Size(w*.47f, h*.10f), CornerRadius(w*.055f))
        drawRoundRect(Color(0xFFA7E9EE), at(.30f, .065f), Size(w*.40f, h*.021f), CornerRadius(w*.016f))
        repeat(5) { index ->
            val x = .36f + index * .07f
            drawLine(Color(0xFF5C8C9A), at(x, .11f), at(x, .142f), 1.dp.toPx(), StrokeCap.Round)
        }
        drawLine(ink, at(.34f, .192f), at(.66f, .192f), 1.dp.toPx())
    }
}

@Composable
internal fun WaterGlass(amount: Float, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val fill = animateFloatAsState(amount.coerceIn(0f, 1f), if (motion) tween(530, easing = NasaqEase) else snap(), label = "glass-fill")
    val ripple = remember { Animatable(1f) }
    var previous by remember { mutableFloatStateOf(amount) }
    LaunchedEffect(amount, motion) {
        val changed = previous != amount
        previous = amount
        if (motion && changed) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(690, easing = LinearEasing))
        } else if (!motion) ripple.snapTo(1f)
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val glass = Path().apply {
            moveTo(w*.12f,h*.17f)
            quadraticTo(w*.50f,h*.06f,w*.88f,h*.17f)
            lineTo(w*.77f,h*.81f)
            quadraticTo(w*.76f,h*.90f,w*.64f,h*.90f)
            lineTo(w*.36f,h*.90f)
            quadraticTo(w*.24f,h*.90f,w*.23f,h*.81f)
            close()
        }
        val ink = Color(0xFF285975)
        val ice = Color(0xFFB3E0F2)
        drawPath(glass, Brush.horizontalGradient(listOf(Color(0xFFB6D6DF), Color(0xFFF3FBFC), ice)))
        if (fill.value > .001f) clipPath(glass) {
            val level = h * (.885f - fill.value * .64f)
            val phase = ripple.value
            val swing = sin(phase * PI.toFloat() * 5f) * (1 - phase) * h * .15f
            val surface = Path().apply {
                moveTo(0f, level + swing)
                cubicTo(w*.32f, level - h*.07f, w*.66f, level + h*.06f, w, level - swing)
            }
            val water = Path().apply { addPath(surface); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(water, Brush.verticalGradient(listOf(Color(0xFF7BE8ED), Color(0xFF208CBD)), level, h))
            if (phase < .90f) repeat(3) { index ->
                drawCircle(Color.White.copy(alpha = (1f - phase) * .65f), w * (.038f + index*.009f),
                    Offset(w * (.36f + index*.14f), level + h * (.13f - phase*.105f + index*.029f)),
                    style = Stroke(.7.dp.toPx()))
            }
            drawPath(surface, Color(0xE4C9FBFB), style = Stroke(1.dp.toPx()))
        }
        drawPath(glass, ink, style = Stroke(1.25.dp.toPx()))
        val rim = Path().apply { moveTo(w*.12f,h*.17f); quadraticTo(w*.50f,h*.29f,w*.88f,h*.17f) }
        drawPath(rim, ink, style = Stroke(1.dp.toPx()))
        drawLine(Color.White.copy(alpha=.8f), Offset(w*.28f,h*.37f), Offset(w*.33f,h*.70f), 1.5.dp.toPx(), StrokeCap.Round)
    }
}

@Composable fun MeterRing(value:Float,modifier:Modifier=Modifier,color:Color=MaterialTheme.colorScheme.primary) {
    val motion=LocalMotion.current
    val fraction by animateFloatAsState(value.coerceIn(0f,1f),if(motion)tween(800)else snap(),label="ring")
    val track=MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier){val stroke=Stroke(7.dp.toPx(),cap=StrokeCap.Round);drawArc(track,0f,360f,false,style=stroke);drawArc(color,-90f,360f*fraction,false,style=stroke)}
}
