package com.buildorbreak.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The platform's handles on the widget.
 *
 * Three receivers, one widget. The picker lists a receiver per entry, and
 * each of these only differs in the size it opens at: the strip, the card,
 * the tall one. Everything the widget knows how to do lives in
 * [TodayWidget], which is a plain class and can be exercised without a
 * launcher, and resizing any of the three walks it through the same faces.
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** Opens as a strip: the next step and a tick. */
class NextStepWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** Opens tall: the card with the day under it. */
class WholeDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
