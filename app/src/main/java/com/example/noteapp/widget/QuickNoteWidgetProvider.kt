package com.example.noteapp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.example.noteapp.R
import com.example.noteapp.data.settings.AppSettingsManager

class QuickNoteWidgetProvider : AppWidgetProvider() {

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
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 60) ?: 60
            val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 60) ?: 60

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val views1x1 = NotesWidgetProvider.build1x1Views(context, isDark, appWidgetId)
                val viewsBar = NotesWidgetProvider.buildBarViews(context, isDark, appWidgetId)
                val viewsList = NotesWidgetProvider.buildListViews(context, isDark, appWidgetId)

                val viewMapping = mapOf(
                    SizeF(60f, 60f) to views1x1,
                    SizeF(160f, 50f) to viewsBar,
                    SizeF(160f, 130f) to viewsList
                )
                appWidgetManager.updateAppWidget(appWidgetId, RemoteViews(viewMapping))
            } else {
                val views = when {
                    minWidth < 120 && minHeight < 110 -> NotesWidgetProvider.build1x1Views(context, isDark, appWidgetId)
                    minHeight < 120 -> NotesWidgetProvider.buildBarViews(context, isDark, appWidgetId)
                    else -> NotesWidgetProvider.buildListViews(context, isDark, appWidgetId)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_notes_list)
        }
    }
}
