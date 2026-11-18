@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId

data class WidgetPreset(val name:String,val look:WidgetLook)
val widgetPresets=listOf(
    WidgetPreset("preset_balance",WidgetLook()),
    WidgetPreset("preset_water",WidgetLook(mode="water",style="blue",waterVisual="bottle",radius=28)),
    WidgetPreset("preset_glucose",WidgetLook(mode="glucose",style="cream",radius=18)),
    WidgetPreset("preset_medicine",WidgetLook(mode="medication",style="forest",radius=24)),
    WidgetPreset("preset_grid",WidgetLook(style="widget_midnight",layout="widget_tiles",radius=22)),
    WidgetPreset("preset_rows",WidgetLook(style="widget_rose",layout="widget_lines",radius=20)),
    WidgetPreset("preset_day",WidgetLook(mode="day_summary",style="cream",radius=26))
)

@Composable fun WidgetStudio(s:State,initial:WidgetLook,onDismiss:()->Unit,onSave:(WidgetLook)->Unit){
    val w=LocalWords.current;val c=MaterialTheme.colorScheme;val feedback=rememberFeedback()
    var look by remember{mutableStateOf(initial)}
    var tab by remember{mutableStateOf("templates")};var size by remember{mutableStateOf("large")}
    var sampleNumbers by remember { mutableStateOf(false) }
    var error by remember{mutableStateOf(false)}
    val sample = remember(s.preferences.language, s.preferences.unit) {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val readings = listOf(106.0, 133.0, 118.0, 127.0).mapIndexed { index, value ->
            Entry(kind="glucose", unit="mg/dL", value=value, time=start+index*60_000L)
        }
        State(
            preferences=Preferences(language=s.preferences.language, unit=s.preferences.unit, waterGoal=2000),
            entries=readings + Entry(kind="water", unit="mL", value=1250.0, time=start) +
                Entry(kind="dose", time=start, status="taken") +
                Entry(kind="water", unit="mL", value=1500.0, time=start-3_600_000L)
        )
    }
    val small=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp<650
    Scaffold(containerColor=c.background,bottomBar={Surface(color=c.surfaceContainerLow){
        Button(onClick={try{require(look.parts.isNotEmpty());if(look.background.isNotBlank())android.graphics.Color.parseColor(look.background);if(look.accent.isNotBlank())android.graphics.Color.parseColor(look.accent);onSave(look)}catch(e:Exception){error=true}},shape=RoundedCornerShape(15.dp),modifier=Modifier.navigationBarsPadding().imePadding().fillMaxWidth().padding(16.dp).heightIn(min=52.dp).pressResponse()){Text(w["save_widget"])}
    }}){insets->
        Column(Modifier.fillMaxSize().padding(insets).safeDrawingPadding()){
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onDismiss){Glyph("back",Modifier.size(21.dp))}
                Text(w["widget_studio"],Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            }
            Surface(
                modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp),
                color=c.surfaceContainerLow,
                shape=RoundedCornerShape(24.dp),
                border=BorderStroke(1.dp,c.outlineVariant.copy(alpha=.6f))
            ) {
                Box(Modifier.fillMaxWidth().height(if(small)147.dp else 219.dp).padding(12.dp),contentAlignment=Alignment.Center){
                    val dimensions=when(size){"compact"->154 to 162;"wide"->320 to 152;else->320 to 242}
                    WidgetPreview(if(sampleNumbers) sample else s, if(sampleNumbers) look.copy(visible=true) else look,
                        dimensions.first, dimensions.second, fitBox=true)
                }
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                listOf("compact","wide","large").forEach { key ->
                    FilterChip(selected=size==key,onClick={size=key},label={Text(w[if(key=="compact")"widget_size_compact"else key])},shape=RoundedCornerShape(50))
                }
                FilterChip(selected=sampleNumbers,onClick={sampleNumbers=!sampleNumbers},label={Text(w["widget_sample"])},shape=RoundedCornerShape(50))
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("templates","widget_content","appearance").forEach{key->FilterChip(selected=tab==key,onClick={tab=key},label={Text(w[if(key=="appearance")"widget_style"else key])},shape=RoundedCornerShape(50))}}
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                if(tab=="templates"){
                    Text(w["choose_template"],style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                    widgetPresets.chunked(2).forEach { group ->
                      Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)){
                       group.forEach { preset ->
                        val selected = look.copy(visible=false, undoWater=false) == preset.look
                        Surface(
                            onClick={look=preset.look.copy(visible=look.visible);feedback()},
                            shape=RoundedCornerShape(16.dp), modifier=Modifier.weight(1f).pressResponse(compression=.975f), color=if(selected)c.primaryContainer else c.surfaceContainerLow,
                            border=BorderStroke(1.dp,if(selected)c.primary else c.outlineVariant.copy(alpha=.55f))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(9.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.fillMaxWidth().height(78.dp)) {
                                    val compact = preset.look.mode in widgetParts
                                    WidgetPreview(sample, preset.look.copy(visible=true), if(compact) 155 else 300,
                                        if(compact) 182 else 195,fitBox=true,announce=false)
                                }
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text(w[preset.name],Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
                                    if(selected) Glyph("check",Modifier.size(15.dp))
                                }
                            }
                        }
                       }
                       if(group.size==1) Spacer(Modifier.weight(1f))
                      }
                    }
                    Text(w["widget_size_hint"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
                }
                if(tab=="widget_content"){
                    ChoiceRow(w["widget_content"],widgetModes,look.mode,{w[when(it){"summary"->"summary_widget";"day_summary"->"day_overview";else->it}]}){look=look.copy(mode=it)}
                    Toggle(w["widget_show_values"],look.visible,w["widget_privacy"]){look=look.copy(visible=it)}
                    if(look.mode=="water" || (look.mode=="summary" && "water" in look.parts))Toggle(w["widget_quick_water"],look.quickWater,w["widget_quick_note"]){look=look.copy(quickWater=it)}
                    if(look.mode=="summary"){
                        Text(w["widget_parts"],style=MaterialTheme.typography.titleSmall)
                        widgetParts.forEach{kind->Toggle(w[kind],kind in look.parts){show->if(show)look=look.copy(parts=look.parts+kind)else if(look.parts.size>1)look=look.copy(parts=look.parts-kind)}}
                        ReorderCards(look.parts,emptySet(),onMove={kind,delta->look=look.copy(parts=movedOrder(look.parts,kind,delta))},onVisible={_,_->},showVisibility=false)
                    }
                    Toggle(w["widget_title_toggle"],look.title){look=look.copy(title=it)}
                    Toggle(w["widget_time_toggle"],look.details){look=look.copy(details=it)}
                    Toggle(w["widget_action_toggle"],look.action){look=look.copy(action=it)}
                }
                if(tab=="appearance"){
                    ChoiceRow(w["theme"],widgetStyles,look.style,{w[it]}){look=look.copy(style=it,background="")}
                    if(look.mode=="summary") ChoiceRow(w["widget_layout"],widgetLayouts,look.layout,{w[it]}){look=look.copy(layout=it)}
                    if(look.mode=="water" || look.mode=="summary") ChoiceRow(w["water_visual"],widgetWaterVisuals,look.waterVisual,{w[it]}){look=look.copy(waterVisual=it)}
                    ChoiceRow(w["widget_font"],listOf("compact","comfortable","large"),look.font,{w[it]}){look=look.copy(font=it)}
                    Text(w["corner_radius"]+" · "+look.radius,style=MaterialTheme.typography.labelLarge)
                    Slider(value=look.radius.toFloat(),onValueChange={look=look.copy(radius=it.toInt())},valueRange=12f..32f,steps=3)
                    Text(w["widget_opacity"]+" · "+look.opacity+"%",style=MaterialTheme.typography.labelLarge)
                    Slider(value=look.opacity.toFloat(),onValueChange={look=look.copy(opacity=it.toInt())},valueRange=0f..100f)
                    Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){listOf("#E9BD50","#8CCBD0","#B9D4A8","#F1B49C").forEach{hex->Surface(onClick={look=look.copy(accent=hex)},color=Color(android.graphics.Color.parseColor(hex)),shape=CircleShape,modifier=Modifier.size(44.dp),border=if(look.accent==hex)BorderStroke(2.dp,c.primary)else null){}}}
                    Field(w["widget_custom_background"],look.background,{look=look.copy(background=it);error=false})
                    Field(w["widget_custom_accent"],look.accent,{look=look.copy(accent=it);error=false})
                }
                if(error)Notice(w["invalid_colour"],true)
            }
        }
    }
}
