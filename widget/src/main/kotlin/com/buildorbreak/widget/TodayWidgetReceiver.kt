package com.buildorbreak.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The platform's handle on the widget.
 *
 * Nothing but a pointer. Everything the widget knows how to do lives in
 * [TodayWidget], which is a plain class and can be exercised without a launcher.
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
