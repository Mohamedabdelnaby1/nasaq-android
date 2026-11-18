@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable fun SetupFlow(repo:Repository,activity:MainActivity) {
    val p=repo.state.preferences;val w=LocalWords.current
    val permissionAllowed=remember(activity.permissionRevision){Reminders.permitted(activity)}
    val exactAllowed=remember(activity.permissionRevision){Reminders.exact(activity)}
    var step by rememberSaveable{mutableIntStateOf(0)}
    var name by rememberSaveable{mutableStateOf(p.name)}
    var birth by rememberSaveable{mutableStateOf(p.birthYear.takeIf{it>0}?.toString().orEmpty())}
    var weight by rememberSaveable{mutableStateOf(p.profileWeight.takeIf{it>0}?.toString().orEmpty())}
    var restricted by rememberSaveable{mutableStateOf(p.fluidRestricted)}
    var waterEnabled by rememberSaveable{mutableStateOf(p.waterEnabled)}
    var glucoseEnabled by rememberSaveable{mutableStateOf(p.glucoseEnabled)}
    var litres by rememberSaveable{mutableStateOf(if(p.waterGoal>0)(p.waterGoal/1000.0).toString()else "")}
    var cup by rememberSaveable{mutableIntStateOf(p.cup)}
    var wake by rememberSaveable{mutableStateOf(p.quietEnd)}
    var sleep by rememberSaveable{mutableStateOf(p.quietStart)}
    var invalid by remember{mutableStateOf(false)}
    val pages=listOf("setup_welcome","setup_profile","setup_routine","appearance","setup_permissions")
    fun validProfile():Boolean=runCatching{
        val year=if(birth.isBlank())0 else whole(birth);val kg=if(weight.isBlank())0.0 else com.yawmi.diabetes.core.Planner.parseNumber(weight)
        val goal=if(litres.isBlank())0 else litresToMl(litres);require(goal in 0..10000)
        Codec.validate(State(preferences=p.copy(name=name.trim(),birthYear=year,profileWeight=kg)));true
    }.getOrDefault(false)
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            Text(w["app"],style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("${step+1} / ${pages.size}",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){repeat(pages.size){i->Surface(color=if(i<=step)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f),shape=RoundedCornerShape(50),modifier=Modifier.weight(1f).height(4.dp)){} }}
        Box(Modifier.weight(1f)) {
            AnimatedContent(targetState=step,transitionSpec={fadeIn(tween(220))+slideInHorizontally{it/8} togetherWith fadeOut(tween(150))},label="setup") {page->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    Spacer(Modifier.height(4.dp))
                    if(page!=0)Text(w[pages[page]],style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                    if(page!=0)Text(w[when(page){0->"setup_welcome_body";1->"setup_profile_body";2->"setup_routine_body";3->"setup_style_body";else->"setup_permissions_body"}],style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    when(page) {
                        0->{
                            WelcomeHero()
                            ChoiceRow(w["language"],listOf("system","ar","en"),p.language,{when(it){"ar"->"العربية";"en"->"English";else->w["system"]}}){choice->repo.preferences{it.copy(language=choice)}}
                            Notice(w["local_privacy"])
                        }
                        1->{Field(w["your_name"],name,{name=it});Field(w["birth_year"],birth,{birth=it},true);Field(w["weight_optional"],weight,{weight=it},true)
                            Toggle(w["fluid_restricted"],restricted){restricted=it};Text(w["profile_no_prescription"],style=MaterialTheme.typography.bodySmall)}
                        2->{
                            Panel(w["sleep_routine"]){TimesEditor(w["wake_time"],wake,{wake=it},true);TimesEditor(w["sleep_time"],sleep,{sleep=it},true)}
                            Field(w["daily_litres"],litres,{litres=it},true)
                            Toggle(w["water_reminders"],waterEnabled){waterEnabled=it}
                            ChoiceRow(w["cup_size"],listOf("150","250","350","500"),cup.toString(),{it+" "+w["ml"]}){cup=it.toInt()}
                            Toggle(w["glucose_reminders"],glucoseEnabled){glucoseEnabled=it}
                            if(glucoseEnabled)Text(w["setup_glucose_hint"],style=MaterialTheme.typography.bodySmall)
                            Notice(w["goal_hint"])
                        }
                        3->{
                            ChoiceRow(w["theme"],listOf("system","light","dark"),p.theme,{w[it]}){v->repo.preferences{it.copy(theme=v)}}
                            ChoiceRow(w["palette"],listOf("mint","violet","coral","blue"),p.palette,{w[it]}){v->repo.preferences{it.copy(palette=v,dynamicColors=false)}}
                            Toggle(w["large_text"],p.largeText){v->repo.preferences{it.copy(largeText=v)}}
                            Toggle(w["reduce_motion"],p.reduceMotion){v->repo.preferences{it.copy(reduceMotion=v)}}
                            Panel(w["setup_promise"]){Text(w["setup_style_preview"],style=MaterialTheme.typography.titleMedium);Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){MeterRing(.7f,Modifier.size(46.dp));Text(w["setup_adapts"],Modifier.weight(1f))}}
                        }
                        else->{
                            PermissionTile(w["permission"],permissionAllowed,w["permission_reason"]){activity.notificationPermission()}
                            PermissionTile(w["exact"],exactAllowed,w["exact_reason"]){activity.exactPermission()}
                            OutlinedButton(onClick={activity.testNotification()},modifier=Modifier.fillMaxWidth()){Text(w["test_notification"])}
                            Notice(w["permission_optional"])
                            Text(w["medical_notice"],style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    if(invalid)Notice(w["invalid"],true)
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(step>0)OutlinedButton(onClick={step--;invalid=false},modifier=Modifier.weight(1f)){Text(w["back"])}
            Button(onClick={
                if(!validProfile()){invalid=true;return@Button}
                if(step<4){step++;invalid=false}else{
                    repo.preferences{current->current.copy(name=name.trim(),birthYear=if(birth.isBlank())0 else whole(birth),
                        profileWeight=if(weight.isBlank())0.0 else com.yawmi.diabetes.core.Planner.parseNumber(weight),fluidRestricted=restricted,
                        waterGoal=if(litres.isBlank())0 else litresToMl(litres),cup=cup,waterEnabled=waterEnabled,glucoseEnabled=glucoseEnabled,
                        quietStart=sleep,quietEnd=wake,onboarded=true,setupVersion=2)}
                }
            },enabled=!repo.busy,modifier=Modifier.weight(1.6f).heightIn(min=54.dp)){Text(w[if(step==4)"setup_finish" else "next"])}
        }
    }
}

@Composable fun PermissionTile(title:String,enabled:Boolean,detail:String,action:()->Unit){
    val w=LocalWords.current
    Panel{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(w[if(enabled)"enabled"else"disabled"],style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)}
        TextButton(onClick=action){Text(w[if(enabled)"open"else"allow"])}}
        Text(detail,style=MaterialTheme.typography.bodySmall)}
}
