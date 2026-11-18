@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun RecordingSheet(
    page: String, onDismiss: () -> Unit, onSelect: (String) -> Unit,
    state: State? = null, onDraft: ((Entry) -> Unit)? = null, onQuickWater: (() -> Unit)? = null, onHideWater: ((Double) -> Unit)? = null,
    editor: @Composable (String, close: (() -> Unit) -> Unit) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    fun close(action: () -> Unit) {
        if (closing) return
        closing = true
        if (!motion) action() else scope.launch {
            try { sheet.hide() } finally { action() }
        }
    }
    ModalBottomSheet(
        onDismissRequest = { if (!closing) onDismiss() },
        sheetState = sheet,
        containerColor = colors.surfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = .26f),
        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 23.dp),
        tonalElevation = 0.dp
    ) {
        if (motion) AnimatedContent(page, transitionSpec = {
            (fadeIn(tween(270,65)) + slideInVertically(tween(540,easing=NasaqEase)) { it / 4 } +
                scaleIn(tween(540,easing=NasaqEase), initialScale = .93f) togetherWith
                (fadeOut(tween(190)) + scaleOut(tween(285), targetScale = .93f))).using(SizeTransform(clip = true) { _, _ -> tween(510,easing=NasaqEase) })
        }, label = "quick-record-page") { current ->
            if (current == "quick") QuickActions(state, onDraft, onQuickWater?.let { save -> { close(save) } }, onHideWater) { kind ->
                if (kind == "glucose" || kind == "water") onSelect(kind) else close { onSelect(kind) }
            } else editor(current, ::close)
        } else key(page) {
            if (page == "quick") QuickActions(state, onDraft, onQuickWater?.let { save -> { close(save) } }, onHideWater, onSelect) else editor(page, ::close)
        }
    }
}

@Composable
private fun QuickActions(state: State?, onDraft: ((Entry) -> Unit)?, onQuickWater: (() -> Unit)?, onHideWater: ((Double) -> Unit)?, choose: (String) -> Unit) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val feedback = rememberFeedback()
    fun chooseAction(kind: String) { feedback(); choose(kind) }
    val habits = remember(state) { state?.let { journalHabits(it, System.currentTimeMillis()) } }
    var phrase by rememberSaveable { mutableStateOf("") }
    val request = remember(phrase, state?.preferences) {
        state?.let { JournalLanguage.parse(phrase, it.preferences) as? JournalRequest.Draft }
    }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionBanner("today", w["quick_add"])
            if (state != null && onQuickWater != null) {
                val amount = number(state.preferences.cup.toDouble(), w)
                val water = domainColors("water")
                ActionButton(onClick = onQuickWater, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = water.backdrop, contentColor = water.ink)) {
                    Glyph("water", Modifier.size(20.dp), water.ink)
                    Spacer(Modifier.width(9.dp))
                    Text(w.f("quick_log_cup", "amount" to amount))
                    Spacer(Modifier.weight(1f))
                    Glyph("plus", Modifier.size(18.dp), water.ink)
                }
            }
            if (state != null && onDraft != null) {
                OutlinedTextField(phrase, { phrase = it.take(250) }, modifier = Modifier.fillMaxWidth(),
                    shape = ControlShape, label = { Text(w["quick_phrase"]) }, singleLine = true,
                    leadingIcon = { Glyph("spark", Modifier.size(19.dp)) })
                MotionExpand(request != null) {
                    request?.let { draft ->
                        NasaqBoard {
                            Text(w[draft.entry.kind] + " · " + entryTitle(draft.entry, state, w),
                                style = MaterialTheme.typography.titleMedium)
                            ActionButton(onClick = { onDraft(draft.entry) }) { Text(w["assistant_edit_draft"]) }
                        }
                    }
                }
                MotionExpand(phrase.isBlank() && habits?.waterAmount != null) {
                    habits?.waterAmount?.let { amount ->
                        NasaqBoard {
                            SuggestionChip(onClick = { onDraft(Entry(kind = "water", value = amount, unit = "mL")) },
                                label = { Text(w.f("habit_water", "amount" to number(amount, w, 2))) })
                            Text(w.f("habit_water_reason", "count" to number(habits.waterMatches.toDouble(), w)),
                                style = MaterialTheme.typography.bodySmall)
                            if (onHideWater != null) TextAction(onClick = { onHideWater(amount) }) { Text(w["hide_suggestion"]) }
                        }
                    }
                }
            }
            Surface(
                onClick = { chooseAction("assistant_title") }, shape = RoundedCornerShape(22.dp),
                color = c.primaryContainer, contentColor = c.onPrimaryContainer, modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(17.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("spark", Modifier.size(27.dp), c.onPrimaryContainer)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(w["assistant_title"], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(w["assistant_subtitle"], style = MaterialTheme.typography.bodySmall, color = c.onPrimaryContainer)
                    }
                    Glyph("arrow", Modifier.size(18.dp), c.onPrimaryContainer)
                }
            }
            FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                (habits?.actions ?: journalActions).forEach { kind ->
                    val tone = domainColors(kind)
                    Surface(
                        onClick = { chooseAction(kind) }, shape = BoardShape,
                        color = tone.backdrop, contentColor = tone.ink, modifier = Modifier.weight(1f)
                    ) {
                        Box {
                            SectionArt(kind, Modifier.matchParentSize(), tone.ink)
                            Column(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(11.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Glyph(kind, Modifier.size(24.dp), tone.ink)
                                    Glyph("arrow", Modifier.size(15.dp), tone.ink)
                                }
                                Text(w[kind], style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Surface(
                onClick = { chooseAction("care_tools") }, shape = RoundedCornerShape(18.dp),
                color = c.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().pressResponse()
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("medication", Modifier.size(21.dp))
                    Text(w["care_tools"], Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Glyph("arrow", Modifier.size(17.dp))
                }
            }
        }
}
