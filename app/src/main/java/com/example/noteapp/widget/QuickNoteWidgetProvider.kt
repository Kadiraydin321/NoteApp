package com.example.noteapp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.example.noteapp.R
import com.example.noteapp.data.settings.AppSettingsManager

class QuickNoteWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == NotesWidgetProvider.ACTION_REFRESH_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val comp = ComponentName(context, QuickNoteWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(comp)
            if (ids != null && ids.isNotEmpty()) {
                for (id in ids) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val isDark = AppSettingsManager.isWidgetDarkTheme(context)
            val views = NotesWidgetProvider.build1x1Views(context, isDark, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
