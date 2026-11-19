package com.yawmi.diabetes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun nextCareAction(s:State,now:Long=System.currentTimeMillis()):Reminder? = Reminders.plan(s,now,now+172800000).firstOrNull()

@Composable fun NextActionCard(s:State,now:Long,onAction:(Reminder)->Unit,onSetup:()->Unit,showWhenEmpty:Boolean=true){
    val w=LocalWords.current;val c=MaterialTheme.colorScheme
    val next=remember(s.revision,s.preferences,now/60000){nextCareAction(s,now)}
    if (next == null && !showWhenEmpty) return
    val kind=next?.kind ?: "bell"
    Surface(onClick={if(next!=null)onAction(next)else onSetup()},shape=RoundedCornerShape(20.dp),color=c.primaryContainer.copy(alpha=.55f),modifier=Modifier.pressResponse(compression=.98f)){
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Surface(shape=RoundedCornerShape(14.dp),color=c.surfaceContainerLow){Glyph(kind,Modifier.padding(11.dp).size(22.dp),if(kind=="water")Aqua else c.primary)}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                Text(w["next_from_plan"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
                Text(next?.let{if(it.kind=="medication")s.medications.find{m->m.id==it.medicationId}?.name ?: w["medication"]else w[it.kind]} ?: w["configure_your_day"],style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Bold)
                if(next!=null)Text(dateText(next.due,w)+" · "+timeText(next.due,w),style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
            }
            Glyph("arrow",Modifier.size(17.dp))
        }
    }
}
