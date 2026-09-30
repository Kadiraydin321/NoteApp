package com.example.noteapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.noteapp.MainActivity
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

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_1x1_quick)
            val isDark = AppSettingsManager.isWidgetDarkTheme(context)

            if (isDark) {
                views.setInt(R.id.widget_1x1_root, "setBackgroundResource", R.drawable.widget_background_dark)
                views.setInt(R.id.widget_1x1_btn, "setBackgroundResource", R.drawable.widget_fab_bg_dark)
                views.setTextColor(R.id.widget_1x1_label, 0xFFFFFFFF.toInt())
            } else {
                views.setInt(R.id.widget_1x1_root, "setBackgroundResource", R.drawable.widget_background)
                views.setInt(R.id.widget_1x1_btn, "setBackgroundResource", R.drawable.widget_fab_bg)
                views.setTextColor(R.id.widget_1x1_label, 0xFFFFFFFF.toInt())
            }

            val clickIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(NotesWidgetProvider.EXTRA_ACTION_TYPE, NotesWidgetProvider.ACTION_TYPE_TEXT)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                200 + appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_1x1_btn, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_1x1_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
