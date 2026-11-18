@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable fun CareTools(s:State,onClose:()->Unit,onChange:((State)->State)->Unit){
    val w=LocalWords.current
    var editing by remember{mutableStateOf<CareItem?>(null)}
    var removing by remember{mutableStateOf<CareItem?>(null)}
    val kinds=listOf("visit","lab","question","sleep","refill")
    val lowStock=s.medications.filter{it.active && it.stock in 0..it.refillAt}
    EditorShell(w["care_tools"],onClose,onClose,saveLabel=w["close"]){
        PageIntro(w["care_tools"],w["care_tools_subtitle"],"medication")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            kinds.forEach{kind->
                val now=System.currentTimeMillis()
                FilledTonalButton(onClick={
                    editing=CareItem(kind=kind,title=if(kind=="sleep")w["actual_sleep"]else"",
                        time=if(kind=="question")0 else if(kind=="sleep")now-8*3_600_000L else now+3_600_000L,
                        end=if(kind=="sleep")now else 0,alert=kind in setOf("visit","lab","refill"))
                }){Text("+ "+w[kind])}
            }
        }
        if(lowStock.isNotEmpty())Panel(w["refill"]){
            lowStock.forEach{med->
                Text(med.name+" · "+w["stock"]+" "+number(med.stock.toDouble(),w),fontWeight=FontWeight.Medium)
                OutlinedButton(onClick={editing=CareItem(kind="refill",title=med.name,time=System.currentTimeMillis()+3600000,alert=true)}){Text(w["plan_refill"])}
            }
        }
        kinds.forEach{kind->
            val items=s.care.items.filter{it.kind==kind}.sortedWith(compareBy<CareItem>{it.done}.thenByDescending{it.time})
            if(items.isNotEmpty())Panel(w[kind]){
                items.forEach{item->
                    Text(item.title,fontWeight=FontWeight.Bold)
                    if(item.time>0)Text(dateText(item.time,w)+" · "+timeText(item.time,w))
                    if(kind=="sleep"){
                        Text(dateText(item.end,w)+" · "+timeText(item.end,w)+" · "+number((item.end-item.time)/3600000.0,w,1)+" "+w["hours"])
                    }
                    if(item.alert && !item.done)Text(w["care_alert"],style=MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                        if(kind!="sleep")TextButton(onClick={onChange{state->state.copy(care=state.care.copy(items=state.care.items.map{if(it.id==item.id)it.copy(done=!it.done)else it}))}}){Text(w[if(item.done)"reopen_item"else"mark_done"])}
                        TextButton(onClick={editing=item}){Text(w["edit"])}
                        TextButton(onClick={removing=item}){Text(w["delete"])}
                    }
                    HorizontalDivider()
                }
            }
        }
        if(s.care.items.isEmpty() && lowStock.isEmpty())EmptyState(w["empty"])
    }
    editing?.let{item->
        CareItemEditor(item,onClose={editing=null},onSave={next->
            onChange{state->state.copy(care=state.care.copy(items=state.care.items.filterNot{it.id==next.id}+next))}
            editing=null
        })
    }
    removing?.let{item->AlertDialog(onDismissRequest={removing=null},title={Text(w["delete"])},text={Text(item.title)},
        confirmButton={TextButton(onClick={onChange{state->state.copy(care=state.care.copy(items=state.care.items.filterNot{it.id==item.id}))};removing=null}){Text(w["delete"])}},
        dismissButton={TextButton(onClick={removing=null}){Text(w["cancel"])}})}
}

@Composable private fun CareItemEditor(item:CareItem,onClose:()->Unit,onSave:(CareItem)->Unit){
    val w=LocalWords.current
    var title by remember{mutableStateOf(item.title)}
    var start by remember{mutableLongStateOf(item.time)}
    var end by remember{mutableLongStateOf(item.end)}
    var alert by remember{mutableStateOf(item.alert)}
    var invalid by remember{mutableStateOf(false)}
    val sleep=item.kind=="sleep"
    EditorShell(w[item.kind],onClose,onSave={
        val next=item.copy(title=title.trim(),time=start,end=end,alert=alert)
        invalid=runCatching{Codec.validate(State(care=CareData(items=listOf(next))))}.isFailure ||
            (alert && !item.done && start<System.currentTimeMillis()) || (sleep && end>System.currentTimeMillis()+60000)
        if(!invalid)onSave(next)
    }){
        Field(w[if(item.kind=="question")"question"else"item_title"],title,{title=it.take(240)})
        if(item.kind!="question")StampPicker(start,{start=it},allowFuture=!sleep,label=w[if(sleep)"sleep_time"else"date_time"])
        if(sleep)StampPicker(end,{end=it},label=w["wake_time"])
        if(item.kind in setOf("visit","lab","refill"))Toggle(w["care_alert"],alert){alert=it}
        if(invalid)Notice(w["care_date_error"],true)
    }
}
