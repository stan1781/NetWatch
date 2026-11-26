package com.netwatch.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll

object WidgetUpdater {
    suspend fun update(context: Context) {
        runCatching {
            GlanceAppWidgetManager(context)
            NetWatchWidget().updateAll(context)
        }
    }
}
