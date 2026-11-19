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
            TextAction(onClick = { section = null }, modifier = Modifier.padding(horizontal = 16.dp)) {
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
        ScreenTitle(w["settings"])
        val groups = listOf(
            "settings_daily" to listOf(
                Triple("glucose_view_options", "glucose", "glucose_view_options"),
                Triple("water_view_options", "water", "water_view_options"),
                Triple("personal", "today", "settings_care"),
                Triple("notifications", "bell", "notification_hub"),
            ),
            "settings_display" to listOf(
                Triple("appearance", "settings", "appearance"),
                Triple("home_cards", "today", "home_cards"),
                Triple("integrations", "widgets", "widgets"),
            ),
            "settings_storage" to listOf(
                Triple("privacy", "shield", "privacy"),
                Triple("backup", "backup", "backup"),
                Triple("about", "info", "about"),
            ),
        )
        groups.forEachIndexed { index, (title, rows) ->
            Reveal(index) {
                NasaqBoard {
                    SectionHeading(w[title])
                    rows.forEachIndexed { item, (key, icon, label) ->
                        if (item > 0) HorizontalDivider(color = c.outlineVariant)
                        val subtitle = when (key) {
                            "glucose_view_options" -> trackingPeriod(p.glucoseViewDays, w)
                            "water_view_options" -> trackingPeriod(p.waterViewDays, w)
                            "personal" -> p.name.takeIf(String::isNotBlank) ?: w["settings_care_body"]
                            "notifications" -> w["settings_alerts_body"]
                            "appearance" -> w["settings_style_body"]
                            "home_cards" -> w["settings_home_body"]
                            "about" -> w.f("version", "version" to BuildConfig.VERSION_NAME)
                            else -> null
                        }
                        SettingsRow(icon, w[label], subtitle) { onSection(key) }
                    }
                }
            }
        }
    }
}
