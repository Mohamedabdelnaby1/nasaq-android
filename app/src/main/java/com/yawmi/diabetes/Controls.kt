package com.yawmi.diabetes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ActionButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ControlShape, colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 17.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    Button(onClick, modifier.heightIn(min = 46.dp), enabled, shape, colors, elevation = null,
        contentPadding = contentPadding) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) { content() }
    }
}

@Composable
internal fun OutlineAction(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ControlShape, content: @Composable RowScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(onClick, modifier.heightIn(min = 46.dp), enabled, shape,
        border = BorderStroke(1.dp, if (enabled) colors.outline else colors.outlineVariant),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) { content() }
    }
}

@Composable
internal fun TonalAction(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ControlShape, content: @Composable RowScope.() -> Unit
) {
    ActionButton(onClick, modifier, enabled, shape, ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer), content = content)
}

@Composable
internal fun TextAction(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    TextButton(onClick, modifier.heightIn(min = 42.dp), enabled, ControlShape, colors,
        contentPadding = contentPadding) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) { content() }
    }
}

@Composable
internal fun IconAction(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val ink = LocalContentColor.current
    IconButton(onClick, modifier, enabled, colors = IconButtonDefaults.iconButtonColors(
        containerColor = ink.copy(alpha = .085f), contentColor = ink,
        disabledContainerColor = ink.copy(alpha = .035f), disabledContentColor = ink.copy(alpha = .35f)), content = content)
}

@Composable
internal fun ChoiceChip(
    selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, shape: Shape = ControlShape
) {
    val c = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    val background by animateColorAsState(if (selected) c.primary else c.surfaceContainerLow,
        if (motion) tween(280, easing = NasaqEase) else snap(), label = "selection tone")
    val ink = if (selected) c.onPrimary else c.onSurface
    FilterChip(selected, onClick, label = {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)) { label() }
    }, modifier = modifier, shape = shape,
        colors = FilterChipDefaults.filterChipColors(containerColor = background, labelColor = ink,
            selectedContainerColor = background, selectedLabelColor = ink, selectedLeadingIconColor = ink),
        border = FilterChipDefaults.filterChipBorder(true, selected,
            borderColor = c.outlineVariant, selectedBorderColor = c.primary),
        leadingIcon = if (selected) {{ Glyph("check", Modifier.size(13.dp), ink) }} else null)
}

@Composable
internal fun BrandedDialog(
    onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null, title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    val c = MaterialTheme.colorScheme
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, dismissButton = dismissButton,
        shape = BoardShape, title = title, text = text, containerColor = c.surfaceContainerLow,
        titleContentColor = c.onSurface, textContentColor = c.onSurfaceVariant, tonalElevation = 0.dp)
}
