@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.yawmi.diabetes

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

internal data class DeskTransition(val shared: SharedTransitionScope, val route: AnimatedVisibilityScope)

internal val LocalDeskTransition = compositionLocalOf<DeskTransition?> { null }

@Composable
internal fun Modifier.sharedDesk(kind: String): Modifier {
    if (kind !in listOf("glucose", "water")) return this
    val transition = LocalDeskTransition.current ?: return this
    return with(transition.shared) {
        sharedBounds(rememberSharedContentState("daily-$kind"), animatedVisibilityScope = transition.route,
            boundsTransform = { _, _ -> tween(560, easing = NasaqEase) },
            enter = fadeIn(tween(265, 95)), exit = fadeOut(tween(240)))
    }
}
