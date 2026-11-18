@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun QuickActionsSheet(onDismiss: () -> Unit, choose: (String) -> Unit) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val feedback = rememberFeedback()
    val motion = LocalMotion.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selection by remember { mutableStateOf<String?>(null) }
    fun chooseAction(kind: String) {
        if (selection != null) return
        selection = kind
        feedback()
        if (!motion) choose(kind)
        else scope.launch {
            sheet.hide()
            choose(kind)
        }
    }
    ModalBottomSheet(onDismissRequest = { if (selection == null) onDismiss() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(w["quick_add"], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Surface(
                onClick = { chooseAction("assistant_title") }, shape = RoundedCornerShape(22.dp),
                color = LocalBrand.current, contentColor = Color.White, modifier = Modifier.fillMaxWidth().pressResponse()
            ) {
                Row(Modifier.padding(17.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("spark", Modifier.size(27.dp), Gold)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(w["assistant_title"], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(w["assistant_subtitle"], style = MaterialTheme.typography.bodySmall, color = Color(0xFFD7E9DE))
                    }
                    Glyph("arrow", Modifier.size(18.dp), Gold)
                }
            }
            FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf("glucose", "water", "meal", "symptom").forEach { kind ->
                    val tone = when (kind) {
                        "water" -> c.tertiaryContainer
                        "meal" -> c.secondaryContainer
                        else -> c.primaryContainer
                    }
                    Surface(
                        onClick = { chooseAction(kind) }, shape = RoundedCornerShape(18.dp),
                        color = c.surfaceContainerLow, modifier = Modifier.weight(1f).pressResponse()
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
}