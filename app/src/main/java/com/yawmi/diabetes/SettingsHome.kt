package com.yawmi.diabetes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsOverview(p: Preferences, onSection: (String) -> Unit) {
    val w = LocalWords.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenGutters),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle(w["settings"])
        NasaqBoard {
            SettingsRow("glucose", w["tracking_settings"], w["tracking_settings_note"]) { onSection("tracking_settings") }
            HorizontalDivider()
            SettingsRow("bell", w["notification_hub"], w["settings_alerts_body"]) { onSection("notifications") }
            HorizontalDivider()
            SettingsRow("shield", w["settings_care"], p.name.ifBlank { w["settings_care_body"] }) { onSection("personal") }
        }
        NasaqBoard {
            SettingsRow("settings", w["appearance"], w["appearance_options_note"]) { onSection("appearance") }
            HorizontalDivider()
            SettingsRow("widgets", w["widgets"]) { onSection("integrations") }
        }
        NasaqBoard {
            SettingsRow("backup", w["data_settings"]) { onSection("data_settings") }
            HorizontalDivider()
            SettingsRow("info", w["about"], w.f("version", "version" to BuildConfig.VERSION_NAME)) { onSection("about") }
        }
    }
}

