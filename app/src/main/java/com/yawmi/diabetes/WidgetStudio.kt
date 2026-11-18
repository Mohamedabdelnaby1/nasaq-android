package com.yawmi.diabetes

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId

data class WidgetPreset(val name: String, val look: WidgetLook)

val widgetPresets = listOf(
    WidgetPreset("preset_balance", WidgetLook()),
    WidgetPreset("preset_water", WidgetLook(mode = "water", style = "blue", waterVisual = "bottle", radius = 28)),
    WidgetPreset("preset_water_mini", WidgetLook(mode = "water", style = "blue", title = false, quickWater = true, waterVisual = "ring")),
    WidgetPreset("preset_glucose", WidgetLook(mode = "glucose", style = "cream", radius = 18)),
    WidgetPreset("preset_medicine", WidgetLook(mode = "medication", style = "forest", radius = 24)),
    WidgetPreset("preset_grid", WidgetLook(style = "widget_midnight", layout = "widget_tiles", radius = 22)),
    WidgetPreset("preset_rows", WidgetLook(style = "widget_rose", layout = "widget_lines", radius = 20)),
    WidgetPreset("preset_day", WidgetLook(mode = "day_summary", style = "cream", radius = 26))
)

private data class PreviewSize(val name: String, val width: Int, val height: Int)
private val previewSizes = listOf(PreviewSize("widget_size_compact", 110, 80), PreviewSize("wide", 320, 152), PreviewSize("large", 320, 242))

@Composable
fun WidgetStudio(s: State, initial: WidgetLook, onDismiss: () -> Unit, onSave: (WidgetLook) -> Unit, pinning: Boolean = false) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    var look by remember { mutableStateOf(initial) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var size by rememberSaveable { mutableIntStateOf(if (initial.mode == "water" && initial.quickWater) 0 else 2) }
    var sampleNumbers by rememberSaveable { mutableStateOf(false) }
    val preview = previewSizes[size]
    val sample = remember(s.preferences.language, s.preferences.unit) {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val readings = listOf(108.0, 129.0, 117.0, 136.0).mapIndexed { index, value ->
            Entry(kind = "glucose", unit = "mg/dL", value = value, time = start + index * 60_000)
        }
        State(preferences = Preferences(language = s.preferences.language, unit = s.preferences.unit, waterGoal = 2000),
            entries = readings + Entry(kind = "water", unit = "mL", value = 950.0, time = start) +
                Entry(kind = "dose", status = "taken", time = start) +
                Entry(kind = "water", unit = "mL", value = 1750.0, time = start - 43_200_000))
    }
    fun validColor(value: String) = value.isBlank() || runCatching { android.graphics.Color.parseColor(value.trim()) }.isSuccess
    val canSave = validColor(look.background) && validColor(look.accent)
    val geometry = WidgetLayout.calculate(preview.width, preview.height, look, w.rtl)
    Scaffold(containerColor = c.background, bottomBar = {
        Surface(color = c.surfaceContainerLow) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 17.dp, vertical = 8.dp)) {
                if (!canSave) Text(w["invalid_colour"], color = c.error, style = MaterialTheme.typography.labelMedium)
                ActionButton(onClick = { onSave(look.copy(background = look.background.trim(), accent = look.accent.trim())) },
                    shape = ControlShape, enabled = canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 49.dp)) {
                    Text(w[if (pinning) "widget_pin" else "save_widget"])
                }
            }
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).safeDrawingPadding().padding(horizontal = 17.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ScreenTitle(w["widget_studio"]) {
                IconAction(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = w["close"] }) {
                    Glyph("close", Modifier.size(22.dp))
                }
            }
            val previewHeight = if (LocalConfiguration.current.screenHeightDp < 700) 84.dp else 110.dp
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val previewColor = domainColors(when (look.mode) {
                    "day_summary" -> "insights"
                    "summary" -> "widgets"
                    else -> look.mode
                })
                val canvasColor by androidx.compose.animation.animateColorAsState(previewColor.backdrop,
                    if (motion) tween(430, easing = NasaqEase) else androidx.compose.animation.core.snap(), label = "widget preset stage")
                NasaqBoard(color = canvasColor, contentColor = previewColor.ink, outlined = false) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(w["widget_preview"], style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        ChoiceChip(selected = sampleNumbers, onClick = { sampleNumbers = !sampleNumbers },
                            label = { Text(w["widget_sample"]) })
                    }
                    Box(Modifier.fillMaxWidth().height(previewHeight), contentAlignment = Alignment.Center) {
                        SectionArt(look.mode, Modifier.matchParentSize(), previewColor.ink)
                        AnimatedContent(size to look, contentAlignment = Alignment.Center,
                            contentKey = { (index, draft) -> listOf(index, draft.mode, draft.layout, draft.style) }, transitionSpec = {
                            if (!motion) (EnterTransition.None togetherWith ExitTransition.None).using(null)
                            else ((fadeIn(tween(240, 45)) + scaleIn(tween(510, easing = NasaqEase), initialScale = .76f)) togetherWith
                                (fadeOut(tween(160)) + scaleOut(tween(350), targetScale = .86f))).using(null)
                        }, label = "widget-canvas-size") { (index, draft) ->
                            val target = previewSizes[index]
                            WidgetPreview(if (sampleNumbers) sample else s, if (sampleNumbers) draft.copy(visible = true) else draft,
                                target.width, target.height, fitBox = true)
                        }
                    }
                    SegmentedRail(previewSizes.map { w[it.name] }, size, { size = it })
                }
            }
            SegmentedRail(listOf(w["templates"], w["widget_content"], w["widget_style"]), page, { page = it })
            val scroll = rememberScrollState()
            LaunchedEffect(page) { scroll.scrollTo(0) }
            Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (look.title && !geometry.header || look.action && !geometry.footer && !geometry.compactAction) {
                    Text(w["widget_space_hint"], style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
                if (!sampleNumbers && s.preferences.lock && look.visible) {
                    Text(w["widget_locked_preview"], style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
                AnimatedContent(page, transitionSpec = {
                    if (!motion) (EnterTransition.None togetherWith ExitTransition.None).using(null)
                    else ((fadeIn(tween(240, 60)) + slideInVertically(tween(450, easing = NasaqEase)) { it / 8 })
                        togetherWith fadeOut(tween(165))).using(SizeTransform(clip = true))
                }, label = "widget-studio-controls") { tab ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (tab) {
                            0 -> widgetPresets.chunked(2).forEach { presets ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    presets.forEach { preset ->
                                        val selected = look.copy(visible = false, undoWater = false) == preset.look
                                        Surface(onClick = {
                                            look = preset.look.copy(visible = look.visible)
                                            size = if (preset.name == "preset_water_mini") 0 else 2
                                        }, modifier = Modifier.weight(1f), shape = ControlShape,
                                            color = if (selected) c.primaryContainer else c.surfaceContainerLow,
                                            border = BorderStroke(1.dp, if (selected) c.primary else c.outlineVariant)) {
                                            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                Box(Modifier.fillMaxWidth().height(67.dp)) {
                                                    val mini = preset.name == "preset_water_mini"
                                                    WidgetPreview(sample, preset.look.copy(visible = true), if (mini) 110 else 260,
                                                        if (mini) 80 else 170, fitBox = true, announce = false)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(w[preset.name], Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                                    if (selected) Glyph("check", Modifier.size(15.dp))
                                                }
                                            }
                                        }
                                    }
                                    if (presets.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            1 -> NasaqBoard {
                                ChoiceRow(w["widget_content"], widgetModes, look.mode,
                                    { w[when(it) { "summary" -> "summary_widget"; "day_summary" -> "day_overview"; else -> it }] }) {
                                    look = look.copy(mode = it)
                                }
                                Toggle(w["widget_show_values"], look.visible, w["widget_privacy"]) { look = look.copy(visible = it) }
                                if (look.mode == "water" || look.mode == "summary" && "water" in look.parts) {
                                    Toggle(w["widget_quick_water"], look.quickWater && look.action, w["widget_quick_note"]) {
                                        look = look.copy(quickWater = it, action = look.action || it)
                                    }
                                }
                                if (look.mode == "summary") {
                                    Text(w["widget_parts"], style = MaterialTheme.typography.titleSmall)
                                    widgetParts.forEach { kind ->
                                        Toggle(w[kind], kind in look.parts) { show ->
                                            if (show && kind !in look.parts) look = look.copy(parts = look.parts + kind)
                                            if (!show && look.parts.size > 1) look = look.copy(parts = look.parts - kind)
                                        }
                                    }
                                    ReorderCards(look.parts, emptySet(), onMove = { kind, delta ->
                                        look = look.copy(parts = movedOrder(look.parts, kind, delta))
                                    }, onVisible = { _, _ -> }, showVisibility = false)
                                }
                                Toggle(w["widget_title_toggle"], look.title) { look = look.copy(title = it) }
                                Toggle(w["widget_time_toggle"], look.details) { look = look.copy(details = it) }
                                Toggle(w["widget_action_toggle"], look.action) { look = look.copy(action = it, quickWater = it && look.quickWater) }
                            }
                            else -> NasaqBoard {
                                ChoiceRow(w["theme"], widgetStyles, look.style, { w[it] }) { look = look.copy(style = it, background = "") }
                                if (look.mode == "summary") ChoiceRow(w["widget_layout"], widgetLayouts, look.layout, { w[it] }) { look = look.copy(layout = it) }
                                if (look.mode == "water" || look.mode == "summary" && "water" in look.parts)
                                    ChoiceRow(w["water_visual"], widgetWaterVisuals, look.waterVisual, { w[it] }) { look = look.copy(waterVisual = it) }
                                ChoiceRow(w["widget_font"], listOf("compact", "comfortable", "large"), look.font, { w[it] }) { look = look.copy(font = it) }
                                Text(w["corner_radius"] + " · " + number(look.radius.toDouble(), w))
                                Slider(value = look.radius.toFloat(), onValueChange = { look = look.copy(radius = it.toInt()) }, valueRange = 12f..32f)
                                Text(w["widget_opacity"] + " · " + number(look.opacity.toDouble(), w) + "%")
                                Slider(value = look.opacity.toFloat(), onValueChange = { look = look.copy(opacity = it.toInt()) }, valueRange = 0f..100f)
                                Field(w["widget_custom_background"], look.background, { look = look.copy(background = it.take(12)) })
                                Field(w["widget_custom_accent"], look.accent, { look = look.copy(accent = it.take(12)) })
                            }
                        }
                    }
                }
            }
        }
    }
}
