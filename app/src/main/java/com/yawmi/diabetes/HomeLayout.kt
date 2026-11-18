@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.*
import kotlin.math.abs

fun movedOrder(order:List<String>,key:String,delta:Int):List<String>{
    val index=order.indexOf(key);val target=index+delta
    if(index<0 || target !in order.indices)return order
    return order.toMutableList().apply{removeAt(index);add(target,key)}
}

@Composable fun HomeLayoutEditor(s:State,onDismiss:()->Unit,onSave:(Preferences)->Unit,onLayouts:((List<DayLayout>)->Unit)?=null){
    var draft by remember{mutableStateOf(s.preferences)}
    val w=LocalWords.current
    var layouts by remember{mutableStateOf(s.care.layouts)}
    var name by remember{mutableStateOf("")}
    EditorShell(w["home_cards"],onDismiss,{onLayouts?.invoke(layouts);onSave(draft)}){
        Panel(w["saved_layouts"]){
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("morning_layout","evening_layout","travel_layout").forEach{key->
                    FilterChip(selected=false,onClick={
                        draft=when(key){
                            "morning_layout"->draft.copy(order=Preferences().order,compactHome=false,theme="light",palette="mint",dynamicColors=false)
                            "evening_layout"->draft.copy(order=listOf("water","medications","glucose","meals","symptoms"),compactHome=false,theme="dark",palette="blue",dynamicColors=false)
                            else->draft.copy(order=listOf("medications","water","glucose","meals","symptoms"),compactHome=true,theme="system",palette="mint",dynamicColors=false)
                        }
                    },label={Text(w[key])})
                }
            }
            if(onLayouts!=null){
                Field(w["layout_name"],name,{name=it.take(40)})
                FilledTonalButton(enabled=name.isNotBlank() && (layouts.size<12 || layouts.any{it.name==name.trim()}),onClick={
                    layouts=layouts.filterNot{it.name==name.trim()}+DayLayout.capture(name.trim(),draft);name=""
                }){Text(w["save_layout"])}
                layouts.forEach{layout->
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        TextButton(onClick={draft=layout.apply(draft)},modifier=Modifier.weight(1f)){Text(layout.name)}
                        TextButton(onClick={layouts=layouts.filterNot{it.name==layout.name}}){Text(w["delete"])}
                    }
                }
            }
        }
        HomeLayoutControls(s.copy(preferences=draft)){draft=it}
    }
}

@Composable fun HomeLayoutControls(s:State,onChange:(Preferences)->Unit){
    val w=LocalWords.current;val p=s.preferences
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text(w["layout_preview"],style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        HomeLayoutPreview(s)
        ChoiceRow(w["home_density"],listOf("compact","detailed"),if(p.compactHome)"compact"else"detailed",{w[if(it=="compact")"compact_home"else it]}){onChange(p.copy(compactHome=it=="compact"))}
        Text(w["drag_cards_hint"],style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        ReorderCards(p.order,p.hiddenCards,onMove={key,delta->onChange(p.copy(order=movedOrder(p.order,key,delta)))},onVisible={key,show->onChange(p.copy(hiddenCards=if(show)p.hiddenCards-key else p.hiddenCards+key))})
        Panel(w["card_sizes"]){
            p.order.forEach{key->ChoiceRow(w[key],listOf("small","standard","large"),p.cardSizes[key] ?: "standard",{w[it]}){
                onChange(p.copy(cardSizes=p.cardSizes+(key to it)))
            }}
        }
    }
}

@Composable fun ReorderCards(order:List<String>,hidden:Set<String>,onMove:(String,Int)->Unit,onVisible:(String,Boolean)->Unit,showVisibility:Boolean=true){
    val w=LocalWords.current;val c=MaterialTheme.colorScheme;val feedback=rememberFeedback()
    var dragging by remember{mutableStateOf<String?>(null)}
    val threshold=with(LocalDensity.current){68.dp.toPx()}
    val currentMove by rememberUpdatedState(onMove)
    val motion=LocalMotion.current
    LazyColumn(Modifier.height((order.size*80+(order.size-1).coerceAtLeast(0)*8).dp),userScrollEnabled=false,verticalArrangement=Arrangement.spacedBy(8.dp)){
        items(order,key={it}){kind->
            val active=dragging==kind
            Surface(modifier=Modifier.animateItem(fadeInSpec=null,placementSpec=if(motion)spring(stiffness=500f)else snap(),fadeOutSpec=null),color=if(active)c.primaryContainer else c.surfaceContainerLow,shape=RoundedCornerShape(18.dp),tonalElevation=if(active)5.dp else 0.dp){
                Row(Modifier.fillMaxWidth().height(80.dp).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Box(Modifier.size(48.dp).semantics{contentDescription=w["reorder"]+" "+w[kind]}.pointerInput(kind){var distance=0f
                        detectDragGesturesAfterLongPress(onDragStart={dragging=kind;distance=0f;feedback()},onDragEnd={dragging=null},onDragCancel={dragging=null}){change,amount->
                            change.consume();distance+=amount.y
                            if(abs(distance)>=threshold*.65f){val delta=if(distance>0)1 else -1;currentMove(kind,delta);distance=0f}
                        }
                    },contentAlignment=Alignment.Center){Glyph("drag",Modifier.size(21.dp))}
                    Text(w[kind],Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)
                    IconButton(onClick={onMove(kind,-1)},enabled=order.first()!=kind,modifier=Modifier.size(40.dp).semantics{contentDescription=w["move_up"]+" "+w[kind]}){Glyph("up",Modifier.size(16.dp))}
                    IconButton(onClick={onMove(kind,1)},enabled=order.last()!=kind,modifier=Modifier.size(40.dp).semantics{contentDescription=w["move_down"]+" "+w[kind]}){Glyph("down",Modifier.size(16.dp))}
                    if(showVisibility)Switch(checked=kind !in hidden,onCheckedChange={onVisible(kind,it)})
                }
            }
        }
    }
}

@Composable fun HomeLayoutPreview(s:State){
    val p=s.preferences;val w=LocalWords.current;val c=MaterialTheme.colorScheme
    Panel{
        Text(w["app"],style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
        p.order.filterNot{it in p.hiddenCards || (p.compactHome && it in setOf("meals","symptoms"))}.forEach{kind->
            Surface(color=if(kind=="glucose")c.primary else c.surfaceContainer,shape=RoundedCornerShape(13.dp)){
                Row(Modifier.fillMaxWidth().heightIn(min=when(p.cardSizes[kind]){"small"->36.dp;"large"->65.dp;else->48.dp}).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Glyph(if(kind=="medications")"medication"else kind,Modifier.size(19.dp),if(kind=="glucose")c.onPrimary else c.primary)
                    Text(w[kind],Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=if(kind=="glucose")c.onPrimary else c.onSurface)
                    if(kind=="water")MeterRing(.4f,Modifier.size(24.dp),Aqua)
                    else Text("•••",color=if(kind=="glucose")c.onPrimary else c.onSurface,style=MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
