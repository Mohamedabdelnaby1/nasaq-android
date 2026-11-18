package com.yawmi.diabetes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
    val w = LocalWords.current
    val motion = LocalMotion.current
    AnimatedContent(section, modifier = Modifier.fillMaxSize(), transitionSpec = {
        if (!motion) (EnterTransition.None togetherWith ExitTransition.None).using(null)
        else {
            val direction = (if (targetState == null) -1 else 1) * (if (w.rtl) -1 else 1)
            ((fadeIn(tween(260, 70)) + slideInHorizontally(tween(470, easing = NasaqEase)) { direction * it / 5 }) togetherWith
                (fadeOut(tween(170)) + slideOutHorizontally(tween(350, easing = NasaqEase)) { -direction * it / 9 })).using(null)
        }
    }, label = "settings-section") { current ->
        if (current == null) SettingsOverview(repo.state.preferences) { section = it }
        else Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { section = null }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Glyph("back", Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(w["back_settings"])
            }
            Box(Modifier.weight(1f)) { SettingsDetail(repo, activity, backup, info, current) }
        }
    }
}

@Composable fun SettingsOverview(p:Preferences,onSection:(String)->Unit){
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenGutters),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        ScreenTitle(w["settings"], w["settings_subtitle"]) { DomainMark("settings") }
        Surface(onClick = { onSection("personal") }, color = c.primaryContainer, contentColor = c.onPrimaryContainer, shape = BoardShape) {
            Box(Modifier.fillMaxWidth()) {
                Contours(Modifier.matchParentSize(), c.onPrimaryContainer)
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = c.onPrimaryContainer, contentColor = c.primaryContainer, shape = CircleShape) {
                            Text(p.name.firstOrNull()?.toString() ?: w["app"].take(1), Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p.name.ifBlank { w["friend"] }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(w["local_privacy_short"], style = MaterialTheme.typography.labelMedium)
                        }
                        Glyph("edit", Modifier.size(20.dp), c.onPrimaryContainer)
                    }
                    HorizontalDivider(color = c.onPrimaryContainer.copy(alpha = .18f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(w["daily_target"], style = MaterialTheme.typography.labelMedium)
                            Text(if (p.waterGoal > 0) number(p.waterGoal / 1000.0, w, 2) + " " + w["litres_short"] else "—",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(w["sleep_card"], style = MaterialTheme.typography.labelMedium)
                            Text(clockText(p.quietStart, w) + " – " + clockText(p.quietEnd, w), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Reveal(1) {
            NasaqBoard {
                Text(w["personalise"], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SettingsRow("settings", w["appearance"], w["settings_style_body"]) { onSection("appearance") }
                HorizontalDivider(color = c.outlineVariant)
                SettingsRow("today", w["home_cards"], w["settings_home_body"]) { onSection("home_cards") }
                HorizontalDivider(color = c.outlineVariant)
                SettingsRow("glucose", w["settings_care"], w["settings_care_body"]) { onSection("personal") }
            }
        }
        Reveal(2) {
            NasaqBoard {
                SettingsRow("bell", w["notification_hub"], w["settings_alerts_body"]) { onSection("notifications") }
                HorizontalDivider(color = c.outlineVariant)
                SettingsRow("widgets", w["widgets"], w["settings_widgets_body"]) { onSection("integrations") }
            }
        }
        NasaqBoard {
            SettingsRow("shield", w["privacy"], w["settings_data_body"]) { onSection("privacy") }
            HorizontalDivider(color = c.outlineVariant)
            SettingsRow("backup", w["backup"]) { onSection("backup") }
            HorizontalDivider(color = c.outlineVariant)
            SettingsRow("info", w["about"]) { onSection("about") }
        }
        Text(w.f("version", "version" to BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
    }
}
