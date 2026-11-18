package com.yawmi.diabetes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.*

val LocalMotion=staticCompositionLocalOf{true}
val LocalBrand=staticCompositionLocalOf{Color(0xFF154F42)}

@Composable fun Reveal(index:Int=0,content:@Composable ()->Unit) {
    val motion=LocalMotion.current
    var entered by remember {mutableStateOf(!motion)}
    LaunchedEffect(Unit){if(motion)delay((index*45L).coerceAtMost(225));entered=true}
    AnimatedVisibility(visible=entered,enter=fadeIn(tween(380))+slideInVertically(tween(420,easing=FastOutSlowInEasing))){Column(Modifier.fillMaxWidth()){content()}}
}

@Composable fun WaterVessel(fraction:Float,modifier:Modifier=Modifier) {
    val motion=LocalMotion.current
    val amount = fraction.coerceIn(0f, 1f)
    val fill by animateFloatAsState(amount, if (motion) spring(dampingRatio = .78f, stiffness = 115f) else snap(), label = "water-level")
    var previous by remember { mutableFloatStateOf(amount) }
    var direction by remember { mutableFloatStateOf(1f) }
    val splash = remember { Animatable(1f) }
    LaunchedEffect(amount, motion) {
        val changed = amount != previous
        direction = if (amount > previous) 1f else -1f
        previous = amount
        if (motion && changed) {
            splash.snapTo(0f)
            splash.animateTo(1f, tween(970, easing = LinearEasing))
        } else if (!motion) splash.snapTo(1f)
    }
    val phase = splash.value
    val impact = (1f - phase).pow(2)
    Canvas(modifier) {
        val w=size.width;val h=size.height
        val vessel=Path().apply{
            moveTo(w*.34f,h*.09f)
            lineTo(w*.66f,h*.09f)
            lineTo(w*.66f,h*.19f)
            cubicTo(w*.66f,h*.25f,w*.85f,h*.24f,w*.85f,h*.39f)
            lineTo(w*.85f,h*.85f)
            quadraticTo(w*.85f,h*.94f,w*.68f,h*.95f)
            lineTo(w*.32f,h*.95f)
            quadraticTo(w*.15f,h*.94f,w*.15f,h*.85f)
            lineTo(w*.15f,h*.39f)
            cubicTo(w*.15f,h*.25f,w*.34f,h*.25f,w*.34f,h*.19f)
            close()
        }
        drawOval(Color(0xFF175E6A).copy(alpha=.18f),Offset(w*.17f,h*.92f),Size(w*.66f,h*.07f))
        drawPath(vessel,Brush.horizontalGradient(listOf(Color(0xFFA4C8CB),Color(0xFFEBF9F7),Color(0xFFC2E2E3),Color(0xFF84B7BE))))
        clipPath(vessel){
            val level = h * (.95f - fill.coerceIn(0f,1f) * .72f)
            if (fill > .001f) repeat(2) { layer ->
                val wave = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, level)
                    for (sample in 0..40) {
                        val x = sample / 40f
                        val ripple = sin(x * 7f - direction * phase * 16f + layer * 1.8f)
                        val tilt = (x - .5f) * sin(phase * 19f) * impact * .24f
                        lineTo(w * x, level + h * (ripple * (.012f + impact * .058f) + tilt))
                    }
                    lineTo(w, h)
                    close()
                }
                val upper = if (layer == 0) Color(0xFF8CD7DD) else Color(0xFF339BA9).copy(alpha = .82f)
                drawPath(wave, Brush.verticalGradient(listOf(upper, Color(0xFF196777)), startY = level.coerceAtMost(h - 1), endY = h))
            }
            if (fill > .06f && impact > .01f && direction > 0) repeat(6) { index ->
                val spread = (index * .173f + .17f) % .66f
                val y = h * (.92f - phase * (.25f + index * .065f))
                if (y > level + h * .035f) drawCircle(Color.White.copy(alpha = .68f * (1f - phase)), w * (.025f + (index % 3) * .007f), Offset(w * spread, y))
            }
        }
        drawPath(vessel,Color(0xFF245E68),style=Stroke(1.25.dp.toPx()))
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha=.82f),Color.White.copy(alpha=.13f)),h*.32f,h*.81f),
            Offset(w*.216f,h*.33f),Size(w*.065f,h*.46f),CornerRadius(w*.04f))
        repeat(5) { i ->
            val y=h*(.38f+i*.09f)
            drawLine(Color(0xFF254E56).copy(alpha=.65f),Offset(w*(if(i%2==0).63f else .69f),y),Offset(w*.76f,y),strokeWidth=1.dp.toPx(),cap=StrokeCap.Round)
        }
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF8DE9E),Color(0xFFC18B37)),h*.022f,h*.117f),
            Offset(w*.27f,h*.022f),Size(w*.46f,h*.105f),CornerRadius(w*.065f))
        repeat(6) { i ->
            val x=w*(.33f+i*.068f)
            drawLine(Color(0xFF7E521F).copy(alpha=.30f),Offset(x,h*.058f),Offset(x,h*.103f),strokeWidth=1.dp.toPx(),cap=StrokeCap.Round)
        }
    }
}

@Composable
internal fun WaterGlass(amount: Float, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val fill by animateFloatAsState(amount.coerceIn(0f,1f), if(motion) tween(460,easing=FastOutSlowInEasing) else snap(), label="glass-fill")
    val ripple = remember { Animatable(1f) }
    var previous by remember { mutableFloatStateOf(amount) }
    LaunchedEffect(amount,motion) {
        val changed=amount!=previous
        previous=amount
        if(motion && changed) {
            ripple.snapTo(0f)
            ripple.animateTo(1f,tween(560,easing=LinearEasing))
        } else if(!motion) ripple.snapTo(1f)
    }
    Canvas(modifier) {
        val w=size.width
        val h=size.height
        val glass=Path().apply {
            moveTo(w*.11f,h*.14f)
            cubicTo(w*.12f,h*.03f,w*.88f,h*.03f,w*.89f,h*.14f)
            lineTo(w*.79f,h*.82f)
            quadraticTo(w*.77f,h*.92f,w*.64f,h*.92f)
            lineTo(w*.36f,h*.92f)
            quadraticTo(w*.23f,h*.92f,w*.21f,h*.82f)
            close()
        }
        drawOval(Color(0xFF1A6170).copy(alpha=.18f),Offset(w*.18f,h*.86f),Size(w*.64f,h*.12f))
        drawPath(glass,Brush.horizontalGradient(listOf(Color(0xFFABCAD0),Color(0xFFF1FCFC),Color(0xFFA8CED4))))
        if(fill>.001f) clipPath(glass) { drawGlassWater(fill,ripple.value) }
        drawPath(glass,Color(0xFF376D77),style=Stroke(1.dp.toPx()))
        drawOval(Color.White.copy(alpha=.76f),Offset(w*.13f,h*.08f),Size(w*.74f,h*.15f),style=Stroke(1.dp.toPx()))
        drawLine(Color.White.copy(alpha=.72f),Offset(w*.27f,h*.32f),Offset(w*.31f,h*.71f),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)
    }
}

private fun DrawScope.drawGlassWater(fill: Float, phase: Float) {
    val w=size.width
    val h=size.height
    val level=h*(.91f-fill*.70f)
    val ripple=sin(phase*PI.toFloat()*5f)*(1f-phase)*h*.14f
    val liquid=Path().apply {
        moveTo(0f,level+ripple)
        cubicTo(w*.31f,level-h*.08f,w*.58f,level+h*.06f,w,level-ripple)
        lineTo(w,h)
        lineTo(0f,h)
        close()
    }
    drawPath(liquid,Brush.verticalGradient(listOf(Color(0xFF65C7CF),Color(0xFF287995)),level,h))
    drawOval(Color(0xFFCCFBF6).copy(alpha=.40f),Offset(w*.29f,level),Size(w*.42f,h*.035f))
}

@Composable fun MeterRing(value:Float,modifier:Modifier=Modifier,color:Color=MaterialTheme.colorScheme.primary) {
    val motion=LocalMotion.current
    val fraction by animateFloatAsState(value.coerceIn(0f,1f),if(motion)tween(800)else snap(),label="ring")
    val track=MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier){val stroke=Stroke(7.dp.toPx(),cap=StrokeCap.Round);drawArc(track,0f,360f,false,style=stroke);drawArc(color,-90f,360f*fraction,false,style=stroke)}
}
