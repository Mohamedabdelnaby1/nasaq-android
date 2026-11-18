package com.yawmi.diabetes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable fun SettingsScreen(repo:Repository,activity:MainActivity,backup:(String)->Unit,info:(String)->Unit){
    var section by rememberSaveable{mutableStateOf<String?>(null)}
    LaunchedEffect(activity.openNotifications){if(activity.openNotifications){section="notifications";activity.openNotifications=false}}
    BackHandler(enabled=section!=null){section=null}
    val w=LocalWords.current
    if(section==null)SettingsOverview(repo.state.preferences){section=it}
    else Column(Modifier.fillMaxSize()){
        TextButton(onClick={section=null},modifier=Modifier.padding(horizontal=16.dp)){Glyph("back",Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text(w["back_settings"])}
        Box(Modifier.weight(1f)){SettingsDetail(repo,activity,backup,info,section!!)}
    }
}

@Composable fun SettingsOverview(p:Preferences,onSection:(String)->Unit){
    val w=LocalWords.current;val c=MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        PageIntro(w["settings"],w["settings_subtitle"],"settings")
        Surface(color=LocalBrand.current,shape=RoundedCornerShape(24.dp)){Box(Modifier.fillMaxWidth()){
            Pattern(Modifier.matchParentSize())
            Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)){
                Surface(color=Gold,shape=CircleShape){Text(p.name.firstOrNull()?.toString() ?: w["app"].take(1),Modifier.padding(horizontal=18.dp,vertical=12.dp),style=MaterialTheme.typography.headlineSmall,color=Forest,fontWeight=FontWeight.Bold)}
                Column(Modifier.weight(1f)){Text(p.name.ifBlank{w["friend"]},style=MaterialTheme.typography.titleLarge,color=Color.White,fontWeight=FontWeight.Bold);Text(w["local_privacy_short"],style=MaterialTheme.typography.labelSmall,color=Color(0xFFCEE0D6))}
                IconButton(onClick={onSection("personal")}){Glyph("edit",Modifier.size(20.dp),Color.White)}
            }
        }}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Surface(onClick={onSection("personal")},modifier=Modifier.weight(1f).height(124.dp),color=c.surfaceContainerLow,shape=RoundedCornerShape(19.dp)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Glyph("water",Modifier.size(21.dp),Aqua);Text(w["daily_target"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant);Text(if(p.waterGoal>0)number(p.waterGoal/1000.0,w,2)+" "+w["litres_short"]else "—",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}}
            Surface(onClick={onSection("personal")},modifier=Modifier.weight(1f).height(124.dp),color=c.surfaceContainerLow,shape=RoundedCornerShape(19.dp)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Glyph("moon",Modifier.size(21.dp));Text(w["sleep_card"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant);Text(p.quietStart+" – "+p.quietEnd,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}}
        }
        SectionHeading(w["personalise"])
        Panel{
            SettingsRow("settings",w["appearance"],w["settings_style_body"]){onSection("appearance")}
            HorizontalDivider(color=c.outlineVariant.copy(alpha=.3f))
            SettingsRow("today",w["home_cards"],w["settings_home_body"]){onSection("home_cards")}
            HorizontalDivider(color=c.outlineVariant.copy(alpha=.3f))
            SettingsRow("glucose",w["settings_care"],w["settings_care_body"]){onSection("personal")}
        }
        Panel{
            SettingsRow("bell",w["notification_hub"],w["settings_alerts_body"]){onSection("notifications")}
            HorizontalDivider(color=c.outlineVariant.copy(alpha=.3f))
            SettingsRow("widgets",w["widgets"],w["settings_widgets_body"]){onSection("integrations")}
        }
        Panel{
            SettingsRow("shield",w["privacy"],w["settings_data_body"]){onSection("privacy")}
            HorizontalDivider(color=c.outlineVariant.copy(alpha=.3f))
            SettingsRow("backup",w["backup"]){onSection("backup")}
            HorizontalDivider(color=c.outlineVariant.copy(alpha=.3f))
            SettingsRow("info",w["about"]){onSection("about")}
        }
        Text(w["version"]+" · Tajawal",style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
    }
}
