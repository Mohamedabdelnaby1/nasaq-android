@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.drawscope.Stroke
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
        AnimatedContent(page, transitionSpec = {
            val direction = (if (targetState > initialState) 1 else -1) * (if (w.rtl) -1 else 1)
            if (motion) (fadeIn(tween(220, 35)) + slideInHorizontally(tween(320, easing=NasaqEase)) { direction * it / 7 }) togetherWith fadeOut(tween(110))
            else EnterTransition.None togetherWith ExitTransition.None
        }, label = "cup-page") { visiblePage ->
            val visibleCups = cups.drop(visiblePage * 15).take(15)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
              val gap = 7.dp
              val capacity = ((maxWidth + gap) / (48.dp + gap)).toInt().coerceAtLeast(1)
              val rows = ((visibleCups.size + capacity - 1) / capacity).coerceAtLeast(1)
              val columns = ((visibleCups.size + rows - 1) / rows).coerceAtLeast(1)
              val cellWidth = ((maxWidth - gap * (columns - 1)) / columns).coerceAtMost(65.dp)
              FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = columns,
                  horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                  verticalArrangement = Arrangement.spacedBy(gap)) {
            visibleCups.forEach { portion ->
                val missing=(portion.capacity-portion.filled).coerceAtLeast(0.0)
                val full=missing<.001
                val c=MaterialTheme.colorScheme
                val tone by animateColorAsState(if(full)c.tertiaryContainer else c.surfaceContainerLow,if(motion)tween(320,120,NasaqEase)else snap(),label="cup-tone")
                val ripple = remember(portion.index) { Animatable(1f) }
                var wasFull by remember(portion.index) { mutableStateOf(full) }
                LaunchedEffect(full, motion) {
                    if (motion && full && !wasFull) {
                        wasFull = full
                        ripple.snapTo(0f)
                        ripple.animateTo(1f, tween(680, easing = NasaqEase))
                    } else {
                        wasFull = full
                        ripple.snapTo(1f)
                    }
                }
                val cupLabel=w["water"]+" · "+number(portion.capacity,w)+" "+w["ml"]
                val stateLabel=if(full) w["cup_full"] else w.f("cup_remaining","amount" to number(missing,w))
                Surface(onClick={if(!busy && missing>=1){feedback();record(missing,portion.context,portion.context+"_"+portion.filled.toInt())}},enabled=!busy && missing>=1,
                    shape=RoundedCornerShape(13.dp),color=tone,border=BorderStroke(1.dp,if(full)c.tertiary.copy(alpha=.45f)else c.outlineVariant),
                    modifier=Modifier.width(cellWidth).testTag("water-cup-${portion.index}").semantics { contentDescription="$cupLabel · $stateLabel" }){
                    Column(Modifier.padding(horizontal=3.dp,vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Box(Modifier.size(45.dp, 43.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.matchParentSize()) {
                                val phase = ripple.value
                                if (phase < 1f) {
                                    drawCircle(c.tertiary.copy(alpha = (1 - phase) * .55f),
                                        (10 + 10 * phase).dp.toPx(), style = Stroke(1.5.dp.toPx()))
                                }
                            }
                            WaterGlass((portion.filled / portion.capacity).toFloat(), Modifier.size(34.dp, 39.dp))
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
                            if(full) Glyph("check",Modifier.size(11.dp))
                            Text(number(portion.capacity,w),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Medium)
                        }
                    }
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
