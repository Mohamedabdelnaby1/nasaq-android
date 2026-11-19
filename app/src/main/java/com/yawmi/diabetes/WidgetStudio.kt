package com.yawmi.diabetes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class WidgetPreset(val name: String, val look: WidgetLook)

internal val widgetPresets = listOf(
    WidgetPreset("preset_balance", WidgetLook()),
    WidgetPreset("preset_water", WidgetLook(mode = "water", style = "blue", waterVisual = "bottle", quickWater = true)),
    WidgetPreset("preset_glucose", WidgetLook(mode = "glucose", style = "cream", radius = 28)),
    WidgetPreset("preset_medicine", WidgetLook(mode = "medication", style = "widget_rose")),
    WidgetPreset("preset_water_mini", WidgetLook(mode = "water", style = "blue", title = false, quickWater = true)),
    WidgetPreset("preset_day", WidgetLook(mode = "day_summary", style = "forest", radius = 28))
)

private data class PreviewSize(val label: String, val width: Int, val height: Int)
private val exampleSizes = listOf(
    PreviewSize("widget_size_compact", 110, 80), PreviewSize("wide", 320, 152), PreviewSize("large", 320, 242)
)
private val draftSaver = Saver<WidgetLook, android.os.Bundle>({ it.toBundle() }, { widgetLook(it) })
private val palette = listOf(
    "auto" to "", "widget_ink_teal" to "#244C49", "blue" to "#204B80", "widget_rose" to "#964764",
    "widget_ink_ochre" to "#975324", "widget_midnight" to "#413A73"
)

@Composable
fun WidgetStudio(
    s: State, initial: WidgetLook, onDismiss: () -> Unit, onSave: (WidgetLook) -> Unit,
    pinning: Boolean = false, actualSize: Pair<Int, Int>? = null, saving: Boolean = false
) {
    val w = LocalWords.current
    val colors = MaterialTheme.colorScheme
    val feedback = rememberFeedback()
    var look by rememberSaveable(stateSaver = draftSaver) { mutableStateOf(initial) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val sizes = remember(actualSize) {
        if (actualSize == null) exampleSizes else
            listOf(PreviewSize("widget_actual_size", actualSize.first, actualSize.second)) + exampleSizes
    }
    var size by rememberSaveable { mutableIntStateOf(if (actualSize != null || initial.mode == "water" && !initial.title) 0 else 2) }
    var sampleNumbers by rememberSaveable { mutableStateOf(false) }
    var showCustomColors by rememberSaveable { mutableStateOf(initial.background.isNotEmpty() || initial.accent.isNotEmpty()) }
    val preview = sizes[size.coerceIn(sizes.indices)]
    val sample = remember(s.preferences) {
        val now = System.currentTimeMillis()
        val midnight = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val readings = listOf(105.0, 138.0, 117.0, 128.0, 115.0).mapIndexed { index, value ->
            val context = s.preferences.glucoseViewContext.takeUnless { it == "all" } ?: "fasting"
            Entry(kind = "glucose", unit = "mg/dL", value = value,
                time = maxOf(midnight, now - (4 - index) * 10 * 60_000), context = context)
        }
        State(preferences = s.preferences.copy(lock = false, waterGoal = 2000),
            entries = readings + Entry(kind = "water", unit = "mL", value = 950.0, time = now) +
                Entry(kind = "dose", status = "taken", time = now) +
                Entry(kind = "water", unit = "mL", value = 1750.0, time = midnight - 43_200_000))
    }
    val parts = if (look.mode == "summary") look.parts else listOf(look.mode)
    val allowsWater = "water" in parts
    val target = look.actionTarget.takeIf { it in parts } ?: "auto"
    val output = look.copy(quickWater = look.quickWater && (sampleNumbers || !s.preferences.lock),
        visible = sampleNumbers || look.visible)
    val geometry = WidgetLayout.calculate(preview.width, preview.height, output, w.rtl)
    val canSave = widgetColor(look.background) && widgetColor(look.accent)
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    Scaffold(containerColor = colors.background, contentWindowInsets = WindowInsets.safeDrawing, bottomBar = {
        Surface(color = colors.surface, tonalElevation = 2.dp) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 18.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!canSave) Text(w["invalid_colour"], color = colors.error, style = MaterialTheme.typography.bodySmall)
                ActionButton(onClick = {
                    onSave(look.copy(background = look.background.trim(), accent = look.accent.trim()))
                }, enabled = canSave && !saving, shape = ControlShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                    if (saving) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else Text(w[if (pinning) "widget_pin" else "save_widget"])
                }
            }
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenTitle(w["widget_studio"]) {
                IconAction(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = w["close"] }) {
                    Glyph("close", Modifier.size(22.dp))
                }
            }
            val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
            val stageHeight = if (windowHeight < 680.dp) 108.dp else 144.dp
            Surface(color = colors.surfaceContainerHigh, shape = RoundedCornerShape(23.dp)) {
                Column(Modifier.fillMaxWidth().padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(w[if (sampleNumbers) "widget_sample_active" else "widget_preview"], Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        ChoiceChip(sampleNumbers, { sampleNumbers = !sampleNumbers }, label = { Text(w["widget_sample"]) })
                    }
                    Box(Modifier.fillMaxWidth().height(stageHeight), contentAlignment = Alignment.Center) {
                        WidgetPreview(if (sampleNumbers) sample else s, output, preview.width, preview.height,
                            fitBox = true, animate = false)
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sizes.forEachIndexed { i, frame ->
                            ChoiceChip(i == size, { size = i }, label = { Text(w[frame.label], maxLines = 1) })
                        }
                    }
                }
            }
            SegmentedRail(listOf(w["templates"], w["widget_content"], w["widget_style"]), page, { page = it })
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                when (page) {
                    0 -> widgetPresets.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { preset ->
                                val selected = look.copy(visible = false, undoWater = false) == preset.look
                                val mini = preset.name == "preset_water_mini"
                                Surface(onClick = {
                                    feedback()
                                    look = preset.look.copy(visible = look.visible)
                                    if (actualSize == null) size = if (mini) 0 else 2
                                }, modifier = Modifier.weight(1f).semantics { this.selected = selected },
                                    shape = ControlShape,
                                    color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
                                    border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant)) {
                                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(Modifier.fillMaxWidth().height(53.dp)) {
                                            WidgetPreview(sample, preset.look.copy(visible = true), if (mini) 110 else 280,
                                                if (mini) 80 else 190, fitBox = true, announce = false)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(w[preset.name], Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                            if (selected) Glyph("check", Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        NasaqBoard {
                            ChoiceRow(w["widget_type"], widgetModes, look.mode, { w[when (it) {
                                "summary" -> "summary_widget"; "day_summary" -> "day_overview"; else -> it
                            }] }) { look = look.copy(mode = it) }
                            if (look.mode == "summary") {
                                Text(w["widget_parts"], style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                WidgetPartPicker(look.parts) { look = look.copy(parts = it) }
                                if (geometry.parts.size < parts.size) Text(w["widget_rotate_hint"], style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        NasaqBoard {
                            Text(w["widget_controls"], style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Toggle(w["widget_action_toggle"], look.action) { look = look.copy(action = it) }
                            if (look.action && parts.size > 1) {
                                val targets = widgetActionTargets.filter { it == "auto" || it in parts }
                                ChoiceRow(w["widget_action_target"], targets, target,
                                    { w[if (it == "auto") "widget_action_auto" else it] }) { look = look.copy(actionTarget = it) }
                            }
                            if (allowsWater && look.action && target in listOf("auto", "water")) {
                                Toggle(w["widget_quick_water"], look.quickWater, w["widget_quick_note"]) { look = look.copy(quickWater = it) }
                            }
                            if (look.action && !geometry.footer && !geometry.compactAction)
                                Text(w["widget_action_space"], style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            Toggle(w["widget_title_toggle"], look.title) { look = look.copy(title = it) }
                            Toggle(w["widget_time_toggle"], look.details) { look = look.copy(details = it) }
                        }
                        NasaqBoard {
                            Toggle(w["widget_show_values"], look.visible, w["widget_privacy"]) { look = look.copy(visible = it) }
                            if (!sampleNumbers && s.preferences.lock && look.visible)
                                Text(w["widget_locked_preview"], style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                    else -> {
                        NasaqBoard {
                            if (look.mode == "summary" && parts.size > 1) {
                                ChoiceRow(w["widget_layout"], widgetLayouts, look.layout, { w[it] }) { look = look.copy(layout = it) }
                                val body = when (look.layout) {
                                    "widget_tiles" -> WidgetBody.TILES; "widget_lines" -> WidgetBody.ROWS; else -> WidgetBody.FOCUS
                                }
                                if (geometry.body != body) Text(w["widget_layout_space"], style = MaterialTheme.typography.bodySmall)
                            }
                            if (allowsWater) {
                                ChoiceRow(w["water_visual"], widgetWaterVisuals, look.waterVisual, { w[it] }) {
                                    look = look.copy(waterVisual = it)
                                }
                                val waterIndex = geometry.parts.indexOf("water")
                                val width = preview.width.toFloat()
                                val height = preview.height.toFloat()
                                val frame = WidgetFrame(width, height, look.radius)
                                val card = geometry.cells(width, height).getOrNull(waterIndex)?.let { frame.inset(it) }
                                if (card == null || !WidgetArt.showsWaterGraphic(card)) {
                                    Text(w["widget_water_visual_space"], style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            ChoiceRow(w["theme"], widgetStyles, look.style, { w[it] }) { look = look.copy(style = it, background = "") }
                            Text(w["widget_button_color"], style = MaterialTheme.typography.titleSmall)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                palette.forEach { (name, hex) ->
                                    val selected = hex.equals(look.accent, ignoreCase = true)
                                    val swatch = if (hex.isEmpty()) colors.surface else Color(android.graphics.Color.parseColor(hex))
                                    Surface(onClick = { look = look.copy(accent = hex) },
                                        modifier = Modifier.size(43.dp).semantics {
                                            this.selected = selected
                                            contentDescription = w[name]
                                        }, shape = CircleShape, color = swatch,
                                        contentColor = if (hex.isEmpty()) colors.onSurface else Color.White,
                                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (selected || hex.isEmpty()) Glyph(if (selected) "check" else "settings", Modifier.size(17.dp))
                                        }
                                    }
                                }
                            }
                        }
                        NasaqBoard {
                            ChoiceRow(w["widget_font"], listOf("compact", "comfortable", "large"), look.font, { w[it] }) {
                                look = look.copy(font = it)
                            }
                            Text(w["corner_radius"] + " · " + number(look.radius.toDouble(), w))
                            Slider(look.radius.toFloat(), { look = look.copy(radius = kotlin.math.round(it).toInt()) }, valueRange = 12f..32f)
                            Text(w["widget_opacity"] + " · " + number(look.opacity.toDouble(), w) + "%")
                            Slider(look.opacity.toFloat(), { look = look.copy(opacity = kotlin.math.round(it).toInt()) }, valueRange = 0f..100f)
                            TextAction(onClick = { showCustomColors = !showCustomColors }) { Text(w["widget_custom_colors"]) }
                            if (showCustomColors) {
                                Field(w["widget_custom_background"], look.background, { look = look.copy(background = it.take(12)) })
                                Field(w["widget_custom_accent"], look.accent, { look = look.copy(accent = it.take(12)) })
                            }
                            TextAction(onClick = {
                                val d = WidgetLook()
                                look = look.copy(style = d.style, layout = d.layout, waterVisual = d.waterVisual, font = d.font,
                                    radius = d.radius, opacity = d.opacity, background = d.background, accent = d.accent)
                            }) { Text(w["widget_reset_style"]) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPartPicker(parts: List<String>, onChange: (List<String>) -> Unit) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    (parts + (widgetParts - parts.toSet())).forEach { kind ->
        val index = parts.indexOf(kind)
        val included = index >= 0
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(included, onCheckedChange = { if (it) onChange(parts + kind) else onChange(parts - kind) },
                enabled = !included || parts.size > 1, modifier = Modifier.semantics { contentDescription = w[kind] })
            Text(w[kind], Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = if (included) c.onSurface else c.onSurfaceVariant)
            if (included && index > 0) IconButton(onClick = { onChange(movedOrder(parts, kind, -1)) },
                modifier = Modifier.semantics { contentDescription = w["move_up"] + " " + w[kind] }) {
                Glyph("up", Modifier.size(20.dp))
            }
            if (included && index < parts.lastIndex) IconButton(onClick = { onChange(movedOrder(parts, kind, 1)) },
                modifier = Modifier.semantics { contentDescription = w["move_down"] + " " + w[kind] }) {
                Glyph("down", Modifier.size(20.dp))
            }
        }
    }
}

