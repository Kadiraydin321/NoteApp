package com.example.noteapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.example.noteapp.MainActivity
import com.example.noteapp.R
import com.example.noteapp.data.settings.AppSettingsManager
import com.example.noteapp.data.settings.WidgetFilterMode

class NotesWidgetProvider : AppWidgetProvider() {

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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.noteapp.ACTION_REFRESH_WIDGET"
        const val EXTRA_NEW_NOTE = "extra_new_note"
        const val EXTRA_ACTION_TYPE = "extra_action_type"
        const val ACTION_TYPE_TEXT = "ACTION_TEXT"
        const val ACTION_TYPE_CHECKLIST = "ACTION_CHECKLIST"
        const val ACTION_TYPE_IMAGE = "ACTION_IMAGE"
        const val ACTION_TYPE_VOICE = "ACTION_VOICE"
        const val ACTION_TYPE_DRAW = "ACTION_DRAW"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val isDark = AppSettingsManager.isWidgetDarkTheme(context)
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 200) ?: 200
            val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150) ?: 150

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val views1x1 = build1x1Views(context, isDark, appWidgetId)
                val viewsBar = buildBarViews(context, isDark, appWidgetId)
                val viewsList = buildListViews(context, isDark, appWidgetId)

                val viewMapping = mapOf(
                    SizeF(60f, 60f) to views1x1,
                    SizeF(160f, 50f) to viewsBar,
                    SizeF(160f, 130f) to viewsList
                )
                appWidgetManager.updateAppWidget(appWidgetId, RemoteViews(viewMapping))
            } else {
                val views = when {
                    minWidth < 120 && minHeight < 110 -> build1x1Views(context, isDark, appWidgetId)
                    minHeight < 120 -> buildBarViews(context, isDark, appWidgetId)
                    else -> buildListViews(context, isDark, appWidgetId)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_notes_list)
        }

        private fun build1x1Views(context: Context, isDark: Boolean, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_1x1_quick)
            if (isDark) {
                views.setInt(R.id.widget_1x1_root, "setBackgroundResource", R.drawable.widget_background_dark)
                views.setInt(R.id.widget_1x1_btn, "setBackgroundResource", R.drawable.widget_fab_bg_dark)
                views.setTextColor(R.id.widget_1x1_label, 0xFFFFFFFF.toInt())
            } else {
                views.setInt(R.id.widget_1x1_root, "setBackgroundResource", R.drawable.widget_background)
                views.setInt(R.id.widget_1x1_btn, "setBackgroundResource", R.drawable.widget_fab_bg)
                views.setTextColor(R.id.widget_1x1_label, 0xFFFFFFFF.toInt())
            }

            val textIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_ACTION_TYPE, ACTION_TYPE_TEXT)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                1000 + appWidgetId,
                textIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_1x1_btn, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_1x1_root, pendingIntent)
            return views
        }

        private fun buildBarViews(context: Context, isDark: Boolean, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bar_actions)
            if (isDark) {
                views.setInt(R.id.widget_bar_root, "setBackgroundResource", R.drawable.widget_background_dark)
                views.setInt(R.id.widget_bar_btn_text, "setBackgroundResource", R.drawable.widget_fab_bg_dark)
            } else {
                views.setInt(R.id.widget_bar_root, "setBackgroundResource", R.drawable.widget_background)
                views.setInt(R.id.widget_bar_btn_text, "setBackgroundResource", R.drawable.widget_fab_bg)
            }

            wireActionButtons(
                context = context,
                views = views,
                appWidgetId = appWidgetId,
                btnTextId = R.id.widget_bar_btn_text,
                btnTodoId = R.id.widget_bar_btn_todo,
                btnImageId = R.id.widget_bar_btn_image,
                btnVoiceId = R.id.widget_bar_btn_voice,
                btnDrawId = R.id.widget_bar_btn_draw
            )
            return views
        }

        private fun buildListViews(context: Context, isDark: Boolean, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_notes)

            if (isDark) {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background_dark)
                views.setTextColor(R.id.widget_title, 0xFFFFFFFF.toInt())
                views.setTextColor(R.id.widget_subtitle_filter, 0xFFD0BCFF.toInt())
                views.setTextColor(R.id.widget_empty_view, 0xFFCAC4D0.toInt())
                views.setInt(R.id.widget_floating_bar, "setBackgroundResource", R.drawable.widget_floating_bar_bg_dark)
                views.setInt(R.id.widget_btn_add_text, "setBackgroundResource", R.drawable.widget_fab_bg_dark)
            } else {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background)
                views.setTextColor(R.id.widget_title, 0xFF1C1B1F.toInt())
                views.setTextColor(R.id.widget_subtitle_filter, 0xFF6750A4.toInt())
                views.setTextColor(R.id.widget_empty_view, 0xFF79747E.toInt())
                views.setInt(R.id.widget_floating_bar, "setBackgroundResource", R.drawable.widget_floating_bar_bg)
                views.setInt(R.id.widget_btn_add_text, "setBackgroundResource", R.drawable.widget_fab_bg)
            }

            val filterMode = AppSettingsManager.getWidgetFilterMode(context)
            val subtitle = if (filterMode == WidgetFilterMode.FAVORITES) {
                "★ Favori / Sabitlenenler"
            } else {
                "Tüm Notlar"
            }
            views.setTextViewText(R.id.widget_subtitle_filter, subtitle)

            val serviceIntent = Intent(context, NotesWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_notes_list, serviceIntent)
            views.setEmptyView(R.id.widget_notes_list, R.id.widget_empty_view)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            views.setOnClickPendingIntent(
                R.id.widget_title,
                PendingIntent.getActivity(context, 1, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )

            val clickIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val clickPendingIntent = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_notes_list, clickPendingIntent)

            wireActionButtons(
                context = context,
                views = views,
                appWidgetId = appWidgetId,
                btnTextId = R.id.widget_btn_add_text,
                btnTodoId = R.id.widget_btn_add_todo,
                btnImageId = R.id.widget_btn_add_image,
                btnVoiceId = R.id.widget_btn_add_voice,
                btnDrawId = R.id.widget_btn_add_draw
            )

            val refreshIntent = Intent(context, NotesWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                20,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

            return views
        }

        private fun wireActionButtons(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            btnTextId: Int,
            btnTodoId: Int,
            btnImageId: Int,
            btnVoiceId: Int,
            btnDrawId: Int
        ) {
            val baseReq = 100 + appWidgetId * 10

            val textIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_ACTION_TYPE, ACTION_TYPE_TEXT)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            views.setOnClickPendingIntent(
                btnTextId,
                PendingIntent.getActivity(context, baseReq + 1, textIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )

            val todoIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_ACTION_TYPE, ACTION_TYPE_CHECKLIST)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            views.setOnClickPendingIntent(
                btnTodoId,
                PendingIntent.getActivity(context, baseReq + 2, todoIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )

            val imageIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_ACTION_TYPE, ACTION_TYPE_IMAGE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            views.setOnClickPendingIntent(
                btnImageId,
                PendingIntent.getActivity(context, baseReq + 3, imageIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )

            val voiceIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_ACTION_TYPE, ACTION_TYPE_VOICE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            views.setOnClickPendingIntent(
                btnVoiceId,
                PendingIntent.getActivity(context, baseReq + 4, voiceIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )

            val drawIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_ACTION_TYPE, ACTION_TYPE_DRAW)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            views.setOnClickPendingIntent(
                btnDrawId,
                PendingIntent.getActivity(context, baseReq + 5, drawIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // 1. Ana Boyutlandırılabilir Widget'ları Güncelle
            val compNotes = ComponentName(context, NotesWidgetProvider::class.java)
            val notesIds = appWidgetManager.getAppWidgetIds(compNotes)
            if (notesIds != null && notesIds.isNotEmpty()) {
                for (id in notesIds) {
                    updateAppWidget(context, appWidgetManager, id)
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(notesIds, R.id.widget_notes_list)
            }

            // 2. Özel 1x1 Hızlı Not Widget'larını Güncelle
            val compQuick = ComponentName(context, QuickNoteWidgetProvider::class.java)
            val quickIds = appWidgetManager.getAppWidgetIds(compQuick)
            if (quickIds != null && quickIds.isNotEmpty()) {
                for (id in quickIds) {
                    QuickNoteWidgetProvider.updateAppWidget(context, appWidgetManager, id)
                }
            }
        }
    }
}
