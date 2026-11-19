package com.yawmi.diabetes

internal val mainSections = listOf("today", "log", "medications", "insights", "settings")
internal val settingsSections = setOf("tracking_settings", "glucose_view_options", "water_view_options",
    "personal", "notifications", "appearance", "home_cards", "shortcuts", "integrations", "data_settings", "about")

internal fun settingsRoute(section: String) = "settings/$section"

internal fun sectionGroup(section: String): String = when {
    section.startsWith("settings/") -> "settings"
    section == "hydration" || section == "day_summary" -> "today"
    section == "glucose_log" -> "log"
    else -> section
}

internal fun pushRoute(path: List<String>, route: String): List<String> {
    val current = path.indexOf(route)
    return if (current == -1) path + route else path.take(current + 1)
}

internal data class AppLaunch(val path: List<String>?, val editor: String?)

internal fun requestedLaunch(screen: String, entry: String): AppLaunch {
    val destination = when {
        entry == "day_summary" -> "day_summary"
        screen in mainSections || screen in setOf("hydration", "day_summary", "glucose_log") -> screen
        screen.startsWith("settings/") && screen.substringAfter('/') in settingsSections -> screen
        else -> null
    }
    val path = destination?.let {
        when {
            it in mainSections -> listOf(it)
            it.startsWith("settings/") -> listOf("settings", it)
            else -> listOf("today", it)
        }
    }
    val editor = entry.takeIf { it in journalActions || it == "care_tools" }
    return AppLaunch(path, editor)
}

