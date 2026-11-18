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
    state: State? = null, onDraft: ((Entry) -> Unit)? = null,
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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        if (motion) AnimatedContent(page, transitionSpec = {
            (fadeIn(tween(270,100)) + slideInVertically(tween(580,easing=NasaqEase)) { it / 6 } togetherWith
                fadeOut(tween(185))).using(SizeTransform(clip = true) { _, _ -> tween(510,easing=NasaqEase) })
        }, label = "quick-record-page") { current ->
            if (current == "quick") QuickActions(state, onDraft) { kind ->
                if (kind == "glucose" || kind == "water") onSelect(kind) else close { onSelect(kind) }
            } else editor(current, ::close)
        } else key(page) {
            if (page == "quick") QuickActions(state, onDraft, onSelect) else editor(page, ::close)
        }
    }
}

@Composable
private fun QuickActions(state: State?, onDraft: ((Entry) -> Unit)?, choose: (String) -> Unit) {
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
            ScreenTitle(w["quick_add"]) { DomainMark("plus") }
            if (state != null && onDraft != null) {
                OutlinedTextField(phrase, { phrase = it.take(250) }, modifier = Modifier.fillMaxWidth(),
                    shape = ControlShape, label = { Text(w["quick_phrase"]) }, singleLine = true,
                    leadingIcon = { Glyph("spark", Modifier.size(19.dp)) })
                MotionExpand(request != null) {
                    request?.let { draft ->
                        NasaqBoard {
                            Text(w[draft.entry.kind] + " · " + entryTitle(draft.entry, state, w),
                                style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { onDraft(draft.entry) }) { Text(w["assistant_edit_draft"]) }
                        }
                    }
                }
                if (phrase.isBlank()) habits?.waterAmount?.let { amount ->
                    SuggestionChip(onClick = { onDraft(Entry(kind = "water", value = amount, unit = "mL")) },
                        label = { Text(w.f("habit_water", "amount" to number(amount, w, 2))) })
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
                    val tone = when (kind) {
                        "water" -> c.tertiaryContainer
                        "meal" -> c.secondaryContainer
                        else -> c.primaryContainer
                    }
                    Surface(
                        onClick = { chooseAction(kind) }, shape = RoundedCornerShape(18.dp),
                        color = tone, modifier = Modifier.weight(1f)
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(12.dp), color = tone) {
                                Glyph(kind, Modifier.padding(8.dp).size(20.dp), if (kind == "water") c.onTertiaryContainer else c.onPrimaryContainer)
                            }
                            Text(w[kind], Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
