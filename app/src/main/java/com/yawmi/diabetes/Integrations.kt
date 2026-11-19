package com.yawmi.diabetes

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object PhoneShortcuts {
    fun publish(c:Context,p:Preferences){
        val w=Words(p.language)
        c.getSystemService(ShortcutManager::class.java).dynamicShortcuts=listOf("glucose","water","meal","symptom").map{kind->
            ShortcutInfo.Builder(c,"yawmi.$kind").setShortLabel(w[kind]).setLongLabel(w["add"]+" "+w[kind])
                .setIcon(Icon.createWithResource(c,R.drawable.ic_launcher))
                .setIntent(WidgetDestination.intent(c, 0, kind, "add").setAction(Intent.ACTION_VIEW)
                    .putExtra("entry", kind)).build()
        }
    }
}

@Composable
fun IntegrationPanel(repo: Repository, activity: MainActivity) {
    val w = LocalWords.current
    var studio by remember { mutableStateOf<WidgetLook?>(null) }
    val resume = activity.permissionRevision
    val ids = remember(resume) { Widgets.ids(activity) }
    NasaqBoard {
        Text(w["widget_designs_body"], style = MaterialTheme.typography.bodySmall)
        ActionButton(onClick = { studio = WidgetLook() }, modifier = Modifier.fillMaxWidth()) { Text(w["widget_preview_pin"]) }
        ids.forEachIndexed { index, id ->
            val look = Widgets.look(activity, id)
            val label = when (look.mode) { "summary" -> "summary_widget"; "day_summary" -> "day_overview"; else -> look.mode }
            SettingsRow("settings", w.f("widget_manage_number", "number" to number((index + 1).toDouble(), w)),
                subtitle = w[label]) {
                activity.startActivity(Widgets.configIntent(activity, id))
            }
        }
        Text(w["widget_manual"], style = MaterialTheme.typography.bodySmall)
    }
    studio?.let { initial ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { studio = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            WidgetStudio(repo.state, initial, { studio = null }, onSave = { look ->
                if (Widgets.pin(activity, look)) studio = null else activity.message("widget_manual")
            }, pinning = true)
        }
    }
}
