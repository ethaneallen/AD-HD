package com.addhd.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Manifest-registered host for [QuestWidget]. The framework routes APPWIDGET_UPDATE broadcasts here;
 * GlanceAppWidgetReceiver forwards them to the widget's provideGlance. Kept trivial — all layout and
 * data lives in [QuestWidget], all reward logic in the shared RewardEngine.
 */
class QuestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuestWidget()
}
