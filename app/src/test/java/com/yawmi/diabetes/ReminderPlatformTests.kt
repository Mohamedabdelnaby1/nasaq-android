package com.yawmi.diabetes

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderPlatformTests {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun reschedulingRebuildsAnEmptyAndroidAlarmQueueAndThenRemovesDisabledItems() {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val shadow = shadowOf(alarm)
        val tomorrowClock = LocalTime.now().plusHours(1).withSecond(0).withNano(0).toString()
        val preferences = Preferences(glucoseEnabled = true, glucoseTimes = listOf(tomorrowClock), quietStart = "00:00", quietEnd = "00:00")
        val state = State(preferences = preferences)
        Reminders.reschedule(context, state)
        val initial = Reminders.upcoming(context)
        assertTrue(initial.any { it.kind == "glucose" })
        assertEquals(initial.size + 1, shadow.scheduledAlarms.size)

        alarm.cancelAll()
        assertTrue(shadow.scheduledAlarms.isEmpty())
        Reminders.reschedule(context, state)
        assertEquals(initial.map(Reminder::id), Reminders.upcoming(context).map(Reminder::id))
        assertEquals(initial.size + 1, shadow.scheduledAlarms.size)

        Reminders.reschedule(context, State())
        assertTrue(Reminders.upcoming(context).isEmpty())
        assertEquals(1, shadow.scheduledAlarms.size)
    }

    @Test fun aBlockedTestReportsTheActualPermissionState() {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        assertEquals("reminder_no_permission", Reminders.test(context))
    }
}
