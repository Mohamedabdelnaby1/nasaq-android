@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.time.*

@Composable fun Journal(s:State,onEdit:(Entry)->Unit,onDelete:(Entry)->Unit,initialFilter:String="all",onAddReading:(()->Unit)?=null){
    val w=LocalWords.current;val c=MaterialTheme.colorScheme
    var filter by rememberSaveable(initialFilter){mutableStateOf(initialFilter)};var query by rememberSaveable{mutableStateOf("")}
    val entries=s.entries.filter{(filter=="all" || it.kind==filter) && (query.isBlank() || it.note.contains(query,true) || w[it.context].contains(query,true))}.sortedByDescending{it.time}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{PageIntro(w[if(initialFilter=="glucose")"glucose"else"log"],w["journal_subtitle"],"log")}
        if(initialFilter=="glucose" && onAddReading!=null) item {
            ReadingHero(dayOverview(s,System.currentTimeMillis()),s.preferences,onAdd=onAddReading)
        }
        item{OutlinedTextField(value=query,onValueChange={query=it},placeholder={Text(w["search"])},leadingIcon={Glyph("search",Modifier.size(18.dp))},shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth(),singleLine=true,colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=c.surfaceContainerLow,unfocusedBorderColor=Color.Transparent))}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("all","glucose","dose","water","meal","symptom","weight","pressure","a1c").forEach{key->FilterChip(selected=filter==key,onClick={filter=key},label={Text(w[if(key=="dose")"medication"else key])},shape=RoundedCornerShape(50))}}}
        if(entries.isEmpty())item{Panel{EmptyState(w["empty"])}}
        entries.groupBy{Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault()).toLocalDate()}.forEach{(_,group)->
            item(key="date_"+group.first().time){SectionHeading(dateText(group.first().time,w));Spacer(Modifier.height(7.dp))
                Panel{group.forEachIndexed{i,e->
                    var expanded by remember{mutableStateOf(false)}
                    Column(Modifier.fillMaxWidth().clickable{expanded=!expanded}){
                        ActivityRow(e,s)
                        MotionExpand(expanded){Column(Modifier.padding(start=48.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                            if(e.note.isNotBlank())Text(e.note,style=MaterialTheme.typography.bodySmall)
                            if(e.kind=="dose")Text(w[e.status],style=MaterialTheme.typography.labelMedium)
                            Row{if(e.kind!="dose")TextButton(onClick={onEdit(e)}){Text(w["edit"])};TextButton(onClick={onDelete(e)},colors=ButtonDefaults.textButtonColors(contentColor=c.error)){Text(w["delete"])}}
                        }}
                    }
                    if(i<group.lastIndex)HorizontalDivider(color=c.outlineVariant.copy(alpha=.3f))
                }}
            }
        }
    }
}

@Composable fun Medicines(s:State,add:()->Unit,edit:(Medication)->Unit,dose:(Reminder)->Unit,manual:()->Unit){
    val device=androidx.compose.ui.platform.LocalContext.current;val w=LocalWords.current;val c=MaterialTheme.colorScheme
    var archived by rememberSaveable{mutableStateOf(false)}
    val now=System.currentTimeMillis();val upcoming=remember(s.revision){Reminders.plan(s,now-2*3600000,now+172800000).filter{it.kind=="medication"}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{PageIntro(w["medications"],w["medicines_subtitle"],"medication")}
        item{Surface(color=LocalBrand.current,shape=RoundedCornerShape(24.dp)){Box(Modifier.fillMaxWidth()){
            Pattern(Modifier.matchParentSize())
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(w["next_reminder"],style=MaterialTheme.typography.labelLarge,color=Color(0xFFCEE0D6));Glyph("bell",Modifier.size(20.dp),Gold)}
                val next=upcoming.firstOrNull()
                Text(next?.let{timeText(it.due,w)} ?: "—",color=Color.White,style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold)
                Text(next?.let{r->s.medications.find{it.id==r.medicationId}?.name} ?: w["no_doses"],color=Color.White,style=MaterialTheme.typography.titleMedium)
                Button(onClick={if(next!=null)dose(next)else add()},shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Forest),modifier=Modifier.fillMaxWidth()){Text(w[if(next!=null)"record_dose"else"add_med"]) }
            }
        }}}
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=!archived,onClick={archived=false},label={Text(w["active"])},shape=RoundedCornerShape(50));FilterChip(selected=archived,onClick={archived=true},label={Text(w["archived"])},shape=RoundedCornerShape(50));Spacer(Modifier.weight(1f));FilledTonalIconButton(onClick=add){Glyph("plus",Modifier.size(21.dp))}
        }}
        val meds=s.medications.filter{it.active!=archived}
        if(meds.isEmpty())item{Panel{EmptyState(w["no_meds"])}}
        items(meds,key={it.id}){med->
            var details by remember{mutableStateOf(false)}
            Panel{
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Surface(shape=RoundedCornerShape(15.dp),color=c.primaryContainer){Glyph("medication",Modifier.padding(12.dp).size(25.dp))}
                    Column(Modifier.weight(1f)){Text(med.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text(listOf(med.strength,med.dose).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
                    IconButton(onClick={edit(med)}){Glyph("edit",Modifier.size(20.dp))}
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    if(med.mode=="interval")StatusPill(w.f("hours_every","n" to number(med.intervalHours.toDouble(),w)))
                    else if(med.mode=="meal")StatusPill(w[med.meal])else med.times.forEach{StatusPill(it)}
                    if(med.relation!="none")StatusPill(w[med.relation],c.onSurfaceVariant,c.surfaceContainer)
                }
                if(med.stock>=0 && med.stock<=med.refillAt)StatusPill(w["refill"],c.error,c.errorContainer)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    TextButton(onClick={details=!details},contentPadding=PaddingValues(0.dp)){Text(w[if(details)"less"else"details"],style=MaterialTheme.typography.labelMedium)}
                    val next=upcoming.firstOrNull{it.medicationId==med.id}
                    if(next!=null && med.active)FilledTonalButton(onClick={dose(next)},shape=RoundedCornerShape(12.dp)){Text(w["record_dose"],style=MaterialTheme.typography.labelMedium)}
                }
                MotionExpand(details){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    if(med.mode=="weekly")Text(med.weekdays.sorted().joinToString(" · "){w[dayKey(it)]},style=MaterialTheme.typography.bodySmall)
                    if(med.mode=="meal")Text(w[if(med.relation=="before")"planned_time"else"wait_meal"],style=MaterialTheme.typography.bodySmall)
                    if(med.stock>=0)Text(w["remaining"]+": "+number(med.stock.toDouble(),w),style=MaterialTheme.typography.labelMedium)
                    if(med.instructions.isNotBlank())Text(med.instructions,style=MaterialTheme.typography.bodySmall)
                    if(med.customAlert)TextButton(onClick={Reminders.medicationChannel(device,med,s.preferences);device.startActivity(android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,device.packageName).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,med.channelId()))}){Text(w["channel"])}
                    if(med.active)TextButton(onClick=manual){Text(w["manual_dose"])}
                }}
            }
        }
        item{Text(w["therapy_warning"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
    }
}

@Composable fun MetricTile(label:String,value:String,modifier:Modifier=Modifier,tone:Color=MaterialTheme.colorScheme.surfaceContainerLow){
    Surface(modifier.height(90.dp),shape=RoundedCornerShape(18.dp),color=tone){Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.SpaceBetween){Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2);Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,maxLines=1)}}
}

@Composable fun Insights(s:State,add:(String)->Unit,export:(Int,String)->Unit){
    val w=LocalWords.current;val c=MaterialTheme.colorScheme;val p=s.preferences
    var days by rememberSaveable{mutableIntStateOf(14)}
    var context by rememberSaveable{mutableStateOf("all")}
    val entries=s.entries.filter{it.time>=periodStart(days) && it.time<=System.currentTimeMillis()}
    val glucose=entries.filter{it.kind=="glucose" && (context=="all" || it.context==context)}
    val range=if(glucose.isEmpty())0.0 else glucose.count{mg(it) in p.lower..p.upper}*100.0/glucose.size
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        PageIntro(w["insights"],w["insights_subtitle"],"insights")
        WeeklyInsights(s)
        ChoiceRow(w["reading_filter"],listOf("all","fasting","not_fasting","before","after"),context,{w[it]}){context=it}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(1,7,14,30).forEach{n->FilterChip(selected=days==n,onClick={days=n},label={Text(w[if(n==1)"today_only"else"last$n"])},shape=RoundedCornerShape(50),modifier=Modifier.weight(1f))}}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            MetricTile(w["average_short"],if(glucose.isEmpty())"—"else number(com.yawmi.diabetes.core.Planner.fromMg(glucose.map{mg(it)}.average(),p.unit),w,1),Modifier.weight(1f),c.primaryContainer.copy(alpha=.6f))
            MetricTile(w["readings"],number(glucose.size.toDouble(),w),Modifier.weight(1f))
        }
        Panel{
            SectionHeading(w["glucose_trend"]);Text(p.unit,style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
            GlucoseChart(glucose,p)
        }
        Panel{
            SectionHeading(w["range_distribution"])
            if(glucose.isNotEmpty()){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)){
                    Box(Modifier.size(110.dp),contentAlignment=Alignment.Center){MeterRing((range/100).toFloat(),Modifier.fillMaxSize(),c.primary);Column(horizontalAlignment=Alignment.CenterHorizontally){Text(number(range,w)+"%",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(w["in_range_short"],style=MaterialTheme.typography.labelSmall,maxLines=1)}}
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp)){
                        listOf("below_target" to glucose.count{mg(it)<p.lower},"in_range_short" to glucose.count{mg(it) in p.lower..p.upper},"above_target" to glucose.count{mg(it)>p.upper}).forEachIndexed{i,(label,count)->
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){Surface(shape=CircleShape,color=if(i==1)c.primary else if(i==0)Gold else c.error,modifier=Modifier.size(7.dp)){};Text(w[label],Modifier.weight(1f),style=MaterialTheme.typography.labelSmall);Text(number(count.toDouble(),w),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)}
                        }
                    }
                }
            }else Text(w["no_analysis"],style=MaterialTheme.typography.bodySmall)
            Text(w["range_hint"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
        }
        if(glucose.isNotEmpty())Panel(w["context_analysis"]){glucose.groupBy{it.context}.forEach{(context,list)->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(w[context],Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);StatusPill(number(com.yawmi.diabetes.core.Planner.fromMg(list.map{mg(it)}.average(),p.unit),w,1)+" "+p.unit)}
        };Text(w["average_context_hint"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
        Surface(color=LocalBrand.current,shape=RoundedCornerShape(23.dp)){Box{Pattern(Modifier.matchParentSize());Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Glyph("export",Modifier.size(26.dp),Gold);Text(w["report_ready"],style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Color.White);Text(w["report_ready_body"],style=MaterialTheme.typography.bodySmall,color=Color(0xFFCEE0D6))
            Button(onClick={export(days,"pdf")},enabled=entries.isNotEmpty(),colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Forest),shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){Text(w["export_pdf"])}
            TextButton(onClick={export(days,"csv")},enabled=entries.isNotEmpty(),colors=ButtonDefaults.textButtonColors(contentColor=Color.White)){Text(w["export_csv"])}
        }}}
        Panel(w["other_tracking"]){FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("weight","pressure","a1c").forEach{kind->OutlinedButton(onClick={add(kind)}){Text(w[kind])}}};listOf("weight","pressure","a1c").forEach{kind->s.entries.filter{it.kind==kind}.maxByOrNull{it.time}?.let{e->Text(w[kind]+" · "+entryTitle(e,s,w)+" · "+dateText(e.time,w),style=MaterialTheme.typography.labelSmall)}}}
        Text(w["correlation_hint"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
    }
}
