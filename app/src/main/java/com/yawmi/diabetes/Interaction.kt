package com.yawmi.diabetes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color

val LocalFeedback=staticCompositionLocalOf{true}

@Composable fun rememberFeedback():()->Unit{
    val haptic=LocalHapticFeedback.current;val enabled=LocalFeedback.current
    return remember(haptic,enabled){{if(enabled)haptic.performHapticFeedback(HapticFeedbackType.LongPress)}}
}

@Composable
fun Modifier.pressResponse(enabled: Boolean = true, compression: Float = .96f): Modifier {
    if (!enabled || !LocalMotion.current) return this
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) compression else 1f,
        if (pressed) spring(dampingRatio = .95f, stiffness = 900f)
        else spring(dampingRatio = .57f, stiffness = 460f),
        label = "control-press"
    )
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pressed = true
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val touch = event.changes.find { it.id == down.id }
                    val stayPressed = touch != null && touch.pressed &&
                        (touch.position - down.position).getDistance() < viewConfiguration.touchSlop
                } while (stayPressed)
            } finally {
                pressed = false
            }
        }
    }.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable fun MotionExpand(visible:Boolean,content:@Composable ()->Unit){
    AnimatedVisibility(visible,enter=if(LocalMotion.current)fadeIn(tween(180))+expandVertically(tween(280,easing=FastOutSlowInEasing))else EnterTransition.None,
        exit=if(LocalMotion.current)fadeOut(tween(120))+shrinkVertically(tween(220))else ExitTransition.None){content()}
}

@Composable fun AnimatedNumber(value:Double,digits:Int=0,suffix:String="",style:TextStyle=MaterialTheme.typography.headlineLarge,color:Color=MaterialTheme.colorScheme.onSurface){
    val motion=LocalMotion.current;val w=LocalWords.current
    var started by remember{mutableStateOf(!motion)}
    LaunchedEffect(value){started=true}
    val animated by animateFloatAsState(if(started)value.toFloat()else 0f,if(motion)tween(650,easing=FastOutSlowInEasing)else snap(),label="value-counter")
    Text(number(animated.toDouble(),w,digits)+suffix,style=style,color=color,fontWeight=FontWeight.Bold)
}

object RecordUndo{
    /** Do not undo over a newer edit, or a different occurrence already recorded. */
    fun revert(s:State,recorded:Entry,previous:Entry?):State{
        if(s.entries.find{it.id==recorded.id}!=recorded)return s
        val removed=Records.remove(s,recorded.id)
        return if(previous==null)removed else Records.add(removed,previous)
    }
}
