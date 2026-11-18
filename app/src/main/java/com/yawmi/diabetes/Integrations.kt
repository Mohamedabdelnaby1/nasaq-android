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
                .setIntent(Intent(c,MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("entry",kind)).build()
        }
    }
}

@Composable fun IntegrationPanel(repo:Repository,activity:MainActivity){
    val w=LocalWords.current
    Panel(w["widget_designs"]){
        Text(w["widget_designs_body"],style=MaterialTheme.typography.bodySmall)
        WidgetPreview(repo.state,WidgetLook())
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){Glyph("today",Modifier.size(30.dp));Column(Modifier.weight(1f)){Text(w["widgets"],fontWeight=FontWeight.Bold);Text(w["widget_size_hint"],style=MaterialTheme.typography.bodySmall)}}
        Button(onClick={if(!Widgets.pin(activity))activity.message("widget_manual")},modifier=Modifier.fillMaxWidth()){Text(w["widget_pin"])}
        Text(w["widget_manual"],style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(w["shortcuts"],fontWeight=FontWeight.Bold);Text(w["shortcuts_body"],style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={repo.preferences{it.copy(setupVersion=0)}}){Text(w["setup_again"])}
    }
}
