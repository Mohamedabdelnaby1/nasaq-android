package com.yawmi.diabetes

import android.content.Context
import android.content.Intent
import android.net.Uri

internal object WidgetDestination {
    fun intent(context: Context, widgetId: Int, part: String, action: String): Intent {
        val destination = Uri.Builder().scheme("yawmi").authority("widget")
            .appendPath(widgetId.toString()).appendPath(part).appendPath(action).build()
        val intent = Intent(context, MainActivity::class.java).setData(destination)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val screen = when (part) {
            "glucose" -> "glucose_log"
            "water" -> "hydration"
            "medication" -> "medications"
            "day_summary" -> "day_summary"
            else -> "today"
        }
        intent.putExtra("screen", screen)
        if (action == "add" && part in listOf("glucose", "water")) intent.putExtra("entry", part)
        return intent
    }
}
