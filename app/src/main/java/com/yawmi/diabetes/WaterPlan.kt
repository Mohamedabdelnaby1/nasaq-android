@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import java.time.*
import kotlinx.coroutines.delay
import kotlin.math.ceil

data class WaterPortion(val index:Int,val capacity:Double,val filled:Double,val context:String)
fun litresToMl(raw:String):Int {
    if(raw.isBlank())return 0
    val litres=com.yawmi.diabetes.core.Planner.parseNumber(raw)
    require(litres in 0.0..10.0)
    return kotlin.math.round(litres*1000).toInt()
}
fun smartWaterSlots(p:Preferences,from:Long,until:Long,zone:ZoneId=ZoneId.systemDefault()):List<Reminder>{
    if(p.waterGoal<=0)return emptyList()
    val slots=mutableListOf<Reminder>()
    val count=ceil(p.waterGoal.toDouble()/p.cup).toInt().coerceAtMost(1000)
    val wake=LocalTime.parse(p.quietEnd);val sleep=LocalTime.parse(p.quietStart)
    var day=Instant.ofEpochMilli(from).atZone(zone).toLocalDate().minusDays(1)
    val endDay=Instant.ofEpochMilli(until).atZone(zone).toLocalDate()
    while(!day.isAfter(endDay)){
        val awake=day.atTime(wake).atZone(zone)
        val end=(if(sleep.isAfter(wake))day else day.plusDays(1)).atTime(sleep).atZone(zone)
        val start=awake.plusMinutes(p.waterOffset.toLong()).takeIf{it.isBefore(end)} ?: awake
        val span=Duration.between(start,end).toMillis()
        val reminders=minOf(count,(span/1800000).toInt().coerceAtLeast(1))
        for(i in 0 until reminders){val due=start.toInstant().toEpochMilli()+span*i/reminders
            val offset=if(p.waterOffset==0)""else "_shift${p.waterOffset}"
            if(due>=from && due<until)slots+=Reminder("water_plan_${day}_${p.quietEnd}_${p.quietStart}_${p.waterGoal}_${p.cup}$offset" + "_$i","water",due)
        };day=day.plusDays(1)
    }
    return slots
}
fun waterPortions(entries:List<Entry>,goal:Int,cup:Int,day:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault()):List<WaterPortion>{
    if(goal<=0 || cup<=0)return emptyList()
    val prefix="cup_${day}_${goal}_${cup}_"
    val today=entries.filter{it.kind=="water" && Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate()==day}
    var pool=today.filterNot{it.context.startsWith(prefix)}.sumOf{it.value}
    return (0 until ceil(goal.toDouble()/cup).toInt()).map{i->
        val capacity=minOf(cup.toDouble(),goal-i*cup.toDouble())
        val context=prefix+i
        val anchored=today.filter{it.context==context}.sumOf{it.value}.coerceAtMost(capacity)
        val allocated=minOf(pool,(capacity-anchored).coerceAtLeast(0.0));pool-=allocated
        WaterPortion(i,capacity,anchored+allocated,context)
    }
}

@Composable fun WaterCups(s:State,busy:Boolean,record:(Double,String,String)->Unit){
    val w=LocalWords.current;val p=s.preferences
    val feedback=rememberFeedback();val motion=LocalMotion.current
    var day by remember{mutableStateOf(LocalDate.now())}
    LaunchedEffect(Unit){while(true){delay(60000);day=LocalDate.now()}}
    val cups=remember(s.revision,s.entries,p.waterGoal,p.cup,day){waterPortions(s.entries,p.waterGoal,p.cup,day)}
    var page by remember(p.waterGoal,p.cup){mutableIntStateOf(0)}
    if(cups.isEmpty())return
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            cups.drop(page*15).take(15).forEach{portion->
                val missing=(portion.capacity-portion.filled).coerceAtLeast(0.0)
                val full=missing<.001
                val tone by animateColorAsState(if(full)MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,if(motion)tween(300)else snap(),label="cup-tone")
                val cupLabel=w["water"]+" · "+number(portion.capacity,w)+" "+w["ml"]
                val stateLabel=if(full) w["cup_full"] else w.f("cup_remaining","amount" to number(missing,w))
                Surface(onClick={if(!busy && missing>=1){feedback();record(missing,portion.context,portion.context+"_"+portion.filled.toInt())}},enabled=!busy && missing>=1,
                    shape=RoundedCornerShape(17.dp),color=tone,
                    modifier=Modifier.width(56.dp).testTag("water-cup-${portion.index}").semantics { contentDescription="$cupLabel · $stateLabel" }){
                    Column(Modifier.padding(horizontal=3.dp,vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
                        WaterGlass((portion.filled/portion.capacity).toFloat(),Modifier.size(31.dp,37.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
                            if(full) Glyph("check",Modifier.size(11.dp))
                            Text(number(portion.capacity,w),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Medium)
                        }
                    }
                }
            }
        }
        if(cups.size>15)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            TextButton(onClick={page--},enabled=page>0){Text(w["back"])}
            Text("${page+1} / ${(cups.size+14)/15}",style=MaterialTheme.typography.labelSmall)
            TextButton(onClick={page++},enabled=(page+1)*15<cups.size){Text(w["next"])}
        }
    }
}
