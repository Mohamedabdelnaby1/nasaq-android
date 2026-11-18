package com.yawmi.diabetes

import android.graphics.RectF

internal val widgetParts = listOf("glucose", "water", "medication")
internal val widgetModes = listOf("summary") + widgetParts + "day_summary"
internal val widgetStyles = listOf("auto", "forest", "cream", "blue", "widget_rose", "widget_midnight")
internal val widgetLayouts = listOf("widget_focus", "widget_tiles", "widget_lines")
internal val widgetWaterVisuals = listOf("ring", "bottle", "widget_bars")

internal enum class WidgetBody { SINGLE, FOCUS, ROWS, TILES }

internal data class WidgetLayout(
    val body: WidgetBody,
    val header: Boolean,
    val cycle: Boolean,
    val footer: Boolean,
    val footerKind: String,
    val undo: Boolean,
    val parts: List<String>
) {
    fun cells(width: Float, height: Float): List<RectF> {
        val top = if (header) 44f else 0f
        val bottom = height - if (footer) 46f else 0f
        val middle = (top + bottom) / 2f
        return when (body) {
            WidgetBody.SINGLE -> listOf(RectF(0f, top, width, bottom))
            WidgetBody.FOCUS -> listOf(RectF(0f, top, width, middle)) + List(parts.size - 1) { i ->
                val step = width / (parts.size - 1)
                RectF(i * step, middle, (i + 1) * step, bottom)
            }
            WidgetBody.ROWS -> List(parts.size) { i ->
                val step = (bottom - top) / parts.size
                RectF(0f, top + i * step, width, top + (i + 1) * step)
            }
            WidgetBody.TILES -> List(parts.size) { i ->
                val step = width / parts.size
                RectF(i * step, top, (i + 1) * step, bottom)
            }
        }
    }

    companion object {
        fun calculate(width: Int, height: Int, look: WidgetLook, rtl: Boolean): WidgetLayout {
            val parts = if (look.mode == "summary") look.parts.filter { it in widgetParts }.distinct().ifEmpty { widgetParts }
                else listOf(look.mode.takeIf { it in widgetModes } ?: "glucose")
            fun fit(space: Int): WidgetBody? {
                if (parts.size == 1) return WidgetBody.SINGLE.takeIf { space >= 66 }
                val rows = space >= parts.size * 40
                val tiles = width >= parts.size * 82 && space >= 60
                val focus = width >= 196 && space >= 108
                return when {
                    look.layout == "widget_lines" && rows -> WidgetBody.ROWS
                    look.layout == "widget_tiles" && tiles -> WidgetBody.TILES
                    look.layout == "widget_focus" && focus -> WidgetBody.FOCUS
                    tiles -> WidgetBody.TILES
                    rows -> WidgetBody.ROWS
                    focus -> WidgetBody.FOCUS
                    else -> null
                }
            }
            val hasRoomForAll = fit(height) != null
            val canAddFooter = look.action && width >= 145
            val priorityFooter = canAddFooter && look.quickWater && "water" in parts && fit(height - 46) != null
            val header = if (hasRoomForAll) width >= 145 && (look.title || parts.size > 1) &&
                fit(height - 44 - if (priorityFooter) 46 else 0) != null
            else width >= 110 && height >= 80 && parts.size > 1
            val bodyHeight = height - if (header) 44 else 0
            val footer = canAddFooter && (priorityFooter || fit(bodyHeight - 46) != null)
            val body = fit(bodyHeight - if (footer) 46 else 0) ?: WidgetBody.SINGLE
            val displayed = when {
                body == WidgetBody.SINGLE -> parts.take(1)
                body == WidgetBody.TILES && rtl -> parts.reversed()
                body == WidgetBody.FOCUS && rtl -> parts.take(1) + parts.drop(1).reversed()
                else -> parts
            }
            val footerKind = if (look.quickWater && "water" in displayed) "water" else parts.first()
            return WidgetLayout(body, header, header && parts.size > 1, footer, footerKind,
                footer && footerKind == "water" && look.quickWater && look.undoWater, displayed)
        }
    }
}
