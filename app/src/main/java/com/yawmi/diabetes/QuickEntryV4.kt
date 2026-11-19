@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import com.yawmi.diabetes.core.Planner

fun quickEntryError(raw:String,water:Boolean,unit:String,time:Long,now:Long=System.currentTimeMillis()):String?{
    if(raw.isBlank())return "value_required"
    val amount=runCatching{Planner.parseNumber(raw)}.getOrNull()
    if(amount==null || !amount.isFinite() || if(water)amount !in 1.0..5000.0 else amount<=0 || Planner.toMg(amount,unit)>5000)return if(water)"water_value_error"else"glucose_value_error"
    return if(time<=0 || time>now+60000)"time_value_error"else null
}

@Composable
fun QuickEntrySheet(kind: String, existing: Entry?, s: State, onDismiss: () -> Unit, onSave: (Entry) -> Unit, draft: Entry? = null) {
    RecordingSheet(kind, onDismiss, {}) { current, close ->
        QuickEntryContent(current, existing, s, onDismiss = { close(onDismiss) }, onSave = onSave, draft = draft)
    }
}

@Composable internal fun QuickEntryContent(kind:String,existing:Entry?,s:State,onDismiss:()->Unit,onSave:(Entry)->Unit,draft:Entry?=null,saving:Boolean=false,saveError:String?=null){
    val w=LocalWords.current;val p=s.preferences;val c=MaterialTheme.colorScheme;val water=kind=="water";val feedback=rememberFeedback()
    val initial=existing ?: draft
    val id=remember{initial?.id ?: java.util.UUID.randomUUID().toString()}
    val last=remember(s.entries){s.entries.filter{it.kind=="water"}.maxByOrNull{it.time}?.value}
    var value by remember{mutableStateOf(initial?.value?.takeIf { it > 0 }?.toString() ?: if(water)p.cup.toString()else "")}
    var unit by remember{mutableStateOf(initial?.unit ?: p.unit)}
    var context by remember{mutableStateOf(initial?.context ?: "other")}
    var note by remember{mutableStateOf(initial?.note.orEmpty())}
    var time by remember{mutableLongStateOf(initial?.time ?: System.currentTimeMillis())}
    var details by remember{mutableStateOf(existing != null || draft?.time?.let {
        !isToday(it) || System.currentTimeMillis() - it >= 30_000L
    } == true)};var error by remember{mutableStateOf<String?>(null)}
    var unusual by remember{mutableStateOf<Entry?>(null)}
    val maxHeight=(LocalConfiguration.current.screenHeightDp-64).coerceAtLeast(240).dp
    fun save(){
        error=quickEntryError(value,water,unit,time)
        if(error!=null){if(error=="time_value_error")details=true;return}
        val entry=Entry(id=id,kind=kind,time=time,value=Planner.parseNumber(value),unit=if(water)"mL"else unit,context=if(water) existing?.context.orEmpty() else context,note=note,created=existing?.created ?: System.currentTimeMillis())
        if(!water && (mg(entry)<20 || mg(entry)>600))unusual=entry else onSave(entry)
    }
        Column(Modifier.fillMaxWidth().heightIn(max=maxHeight).imePadding()){
            Column(Modifier.weight(1f,fill=false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    DomainMark(kind)
                    Column(Modifier.weight(1f)){Text(w[if(water)"water_quick_title"else"glucose_quick_title"],style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(w[if(water)"water_quick_subtitle"else"glucose_quick_subtitle"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
                    IconAction(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = w["close"] }) {
                        Glyph("close", Modifier.size(20.dp), c.onSurfaceVariant)
                    }
                }
                if(water){
                    val amount=runCatching{Planner.parseNumber(value)}.getOrNull()?.takeIf(Double::isFinite) ?: 0.0
                    Surface(color=c.tertiaryContainer, shape=BoardShape, modifier=Modifier.fillMaxWidth()){
                        Box(Modifier.fillMaxWidth().padding(8.dp),contentAlignment=Alignment.Center) {
                            Contours(Modifier.matchParentSize(), c.onTertiaryContainer)
                            WaterGlass((amount / 500).toFloat(), Modifier.size(110.dp, 115.dp))
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)){
                        IconAction(onClick={value=(amount-50).coerceAtLeast(50.0).toInt().toString();error=null;feedback()},modifier=Modifier.size(48.dp).semantics { contentDescription = w["water_decrease"] }) { Glyph("minus", Modifier.size(22.dp)) }
                        Column(horizontalAlignment=Alignment.CenterHorizontally){AnimatedNumber(amount,style=MaterialTheme.typography.displaySmall);Text(w["ml"],style=MaterialTheme.typography.labelMedium)}
                        IconAction(onClick={value=(amount+50).coerceAtMost(5000.0).toInt().toString();error=null;feedback()},modifier=Modifier.size(48.dp).semantics { contentDescription = w["water_increase"] }) { Glyph("plus", Modifier.size(22.dp)) }
                    }
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(150,250,500).forEach{n->ChoiceChip(selected=amount==n.toDouble(),onClick={value=n.toString();error=null;feedback()},label={Text(number(n.toDouble(),w)+" "+w["ml"])},shape=RoundedCornerShape(50))}}
                    if(last!=null)TextAction(onClick={value=last.toString();error=null;feedback()}){Text(w["last_water_amount"]+": "+number(last,w)+" "+w["ml"])}
                    if(error!=null && error!="time_value_error")Text(w[error!!],color=c.error,style=MaterialTheme.typography.bodySmall)
                }else{
                    OutlinedTextField(value=value,onValueChange={if(it.length<=8)value=it;error=null},label={Text(w["glucose"])},singleLine=true,isError=error!=null && error!="time_value_error",
                        supportingText=if(error!=null && error!="time_value_error"){{Text(w[error!!])}}else null,
                        textStyle=MaterialTheme.typography.displayMedium.copy(textAlign=TextAlign.Center,fontWeight=FontWeight.Bold),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),suffix={Text(unit)},shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("fasting","not_fasting","before","after","other").forEach{key->ChoiceChip(selected=context==key,onClick={context=key;feedback()},label={Text(w[key])},shape=RoundedCornerShape(50))}}
                }
                TextAction(onClick={details=!details}){Text(w[if(details)"less"else"optional_short"])}
                MotionExpand(details){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                    if(water)Field(w["water_amount"],value,{value=it;error=null},true)
                    else ChoiceRow(w["unit"],listOf("mg/dL","mmol/L"),unit,{it}){selected->
                        if(value.isNotBlank())runCatching{value=String.format(java.util.Locale.ROOT,if(selected=="mmol/L")"%.1f"else"%.0f",Planner.fromMg(Planner.toMg(Planner.parseNumber(value),unit),selected))};unit=selected;error=null
                    }
                    StampPicker(time,{time=it;error=null})
                    if(error=="time_value_error")Text(w["time_value_error"],color=c.error,style=MaterialTheme.typography.bodySmall)
                    Field(w["notes"],note,{note=it},multiline=true)
                }}
            }
            Surface(color=c.surfaceContainerLow){Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(top=8.dp,bottom=14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                if (saveError != null) Notice(w[saveError], true)
                ActionButton(onClick={save()},enabled=!saving,shape=CircleShape,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text(w[if(water)"water_record_button"else"save_reading"],style=MaterialTheme.typography.titleMedium)}
                if (existing == null && !details) Text(w["recorded_now_hint"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
            }}
        }
    unusual?.let{entry->BrandedDialog(onDismissRequest={unusual=null},title={Text(w["unusual"])},text={Text("${entry.value} ${entry.unit}")},confirmButton={TextAction(onClick={unusual=null;onSave(entry)}){Text(w["confirm"])}},dismissButton={TextAction(onClick={unusual=null}){Text(w["cancel"])}})}
}
