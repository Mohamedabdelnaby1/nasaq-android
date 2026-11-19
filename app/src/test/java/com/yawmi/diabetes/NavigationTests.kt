package com.yawmi.diabetes

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTests {
    @Test fun nestedSettingsReturnsToTheScreenThatOpenedIt() {
        val home = listOf("today")
        val hydration = pushRoute(home, "hydration")
        val waterSettings = pushRoute(hydration, settingsRoute("water_view_options"))
        assertEquals(hydration, waterSettings.dropLast(1))
        assertEquals(home, pushRoute(waterSettings, "today"))
    }

    @Test fun installedWidgetRoutesAndLegacyDailyActionRemainCompatible() {
        assertEquals(AppLaunch(listOf("today", "hydration"), null), requestedLaunch("hydration", ""))
        assertEquals(AppLaunch(listOf("today", "glucose_log"), "glucose"), requestedLaunch("glucose_log", "glucose"))
        assertEquals(AppLaunch(listOf("today", "day_summary"), null), requestedLaunch("today", "day_summary"))
        assertEquals(AppLaunch(listOf("settings", "settings/notifications"), null), requestedLaunch("settings/notifications", ""))
    }

    @Test fun invalidLinksCannotOpenUnknownPagesOrEditors() {
        assertEquals(AppLaunch(null, null), requestedLaunch("old_settings", "unknown"))
    }
}

