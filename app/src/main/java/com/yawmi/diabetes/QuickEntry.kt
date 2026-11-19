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
            if (page == "quick") QuickActions(state, onDraft, onQuickWater?.let { save -> { close(save) } }, onHideWater) { kind ->
                if (kind == "glucose" || kind == "water") onSelect(kind) else close { onSelect(kind) }
            } else editor(page, ::close)
        }
    }
}

@Composable
private fun QuickActions(state: State?, onDraft: ((Entry) -> Unit)?, onQuickWater: (() -> Unit)?, onHideWater: ((Double) -> Unit)?, choose: (String) -> Unit) {
    val w = LocalWords.current
    val feedback = rememberFeedback()
    fun chooseAction(kind: String) { feedback(); choose(kind) }
    val habits = remember(state) { state?.let { journalHabits(it, System.currentTimeMillis()) } }
    var showTools by rememberSaveable { mutableStateOf(false) }
    var phrase by rememberSaveable { mutableStateOf("") }
    val request = remember(phrase, state?.preferences) {
        state?.let { JournalLanguage.parse(phrase, it.preferences) as? JournalRequest.Draft }
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 17.dp).padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        ScreenTitle(w["quick_add"])
        val primary = (habits?.actions ?: journalActions).filter { it in primaryJournalActions }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            primary.forEach { kind ->
                val tone = domainColors(kind)
                Surface({ chooseAction(kind) }, Modifier.weight(1f).fillMaxHeight(), shape = ControlShape,
                    color = tone.backdrop, contentColor = tone.ink) {
                    Row(Modifier.heightIn(min = 58.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Glyph(kind, Modifier.size(21.dp), tone.ink)
                        Text(w[kind], Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Glyph("arrow", Modifier.size(13.dp), tone.ink)
                    }
                }
            }
        }
        if (state != null && onQuickWater != null) {
            val amount = number(state.preferences.cup.toDouble(), w)
            OutlineAction(onQuickWater, Modifier.fillMaxWidth()) {
                Glyph("plus", Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(w.f("quick_log_cup", "amount" to amount))
            }
        }
        (habits?.actions ?: journalActions).filterNot { it in primary }.forEach { kind ->
            SettingsRow(kind, w[kind]) { chooseAction(kind) }
        }
        HorizontalDivider()
        SettingsRow("more", w["more_tools"], w[if (showTools) "less" else "details"], expanded = showTools) { showTools = !showTools }
        MotionExpand(showTools) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsRow("spark", w["assistant_title"]) { chooseAction("assistant_title") }
                SettingsRow("shield", w["care_tools"]) { chooseAction("care_tools") }
                if (state != null && onDraft != null) {
                    OutlinedTextField(phrase, { phrase = it.take(250) }, modifier = Modifier.fillMaxWidth(),
                        shape = ControlShape, label = { Text(w["quick_phrase"]) }, singleLine = true)
                    request?.let { draft ->
                        TonalAction({ onDraft(draft.entry) }, Modifier.fillMaxWidth()) {
                            Text(w["assistant_edit_draft"])
                        }
                    }
                    habits?.waterAmount?.takeIf { phrase.isBlank() }?.let { amount ->
                        NasaqBoard {
                            TonalAction({ onDraft(Entry(kind = "water", value = amount, unit = "mL")) }) {
                                Text(w.f("habit_water", "amount" to number(amount, w, 2)))
                            }
                            Text(w.f("habit_water_reason", "count" to number(habits.waterMatches.toDouble(), w)),
                                style = MaterialTheme.typography.bodySmall)
                            if (onHideWater != null) TextAction({ onHideWater(amount) }) { Text(w["hide_suggestion"]) }
                        }
                    }
                }
            }
        }
    }
}
