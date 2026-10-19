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
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.example.noteapp.MainActivity
import com.example.noteapp.R
import com.example.noteapp.data.settings.AppSettingsManager
import com.example.noteapp.data.settings.WidgetDesignPreset
import com.example.noteapp.data.settings.WidgetFabBehavior
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
        when (intent.action) {
            ACTION_REFRESH_WIDGET -> {
                updateAllWidgets(context)
            }
            ACTION_OPEN_POPUP -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    setPopupOpen(context, widgetId, true)
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateAppWidget(context, appWidgetManager, widgetId)
                }
            }
            ACTION_CLOSE_POPUP -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    setPopupOpen(context, widgetId, false)
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateAppWidget(context, appWidgetManager, widgetId)
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.noteapp.ACTION_REFRESH_WIDGET"
        const val ACTION_OPEN_POPUP = "com.example.noteapp.ACTION_OPEN_POPUP"
        const val ACTION_CLOSE_POPUP = "com.example.noteapp.ACTION_CLOSE_POPUP"
        const val EXTRA_NEW_NOTE = "extra_new_note"
        const val EXTRA_ACTION_TYPE = "extra_action_type"
        const val ACTION_TYPE_TEXT = "ACTION_TEXT"
        const val ACTION_TYPE_CHECKLIST = "ACTION_CHECKLIST"
        const val ACTION_TYPE_IMAGE = "ACTION_IMAGE"
        const val ACTION_TYPE_VOICE = "ACTION_VOICE"
        const val ACTION_TYPE_DRAW = "ACTION_DRAW"
        const val ACTION_TYPE_SETTINGS = "ACTION_SETTINGS"

        private const val WIDGET_POPUP_PREFS = "widget_popup_state"

        private fun isPopupOpen(context: Context, widgetId: Int): Boolean =
            context.getSharedPreferences(WIDGET_POPUP_PREFS, Context.MODE_PRIVATE)
                .getBoolean("open_$widgetId", false)

        private fun setPopupOpen(context: Context, widgetId: Int, isOpen: Boolean) {
            context.getSharedPreferences(WIDGET_POPUP_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("open_$widgetId", isOpen)
                .apply()
        }

        fun closeAllPopups(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val compNotes = ComponentName(context, NotesWidgetProvider::class.java)
            val notesIds = appWidgetManager.getAppWidgetIds(compNotes)
            if (notesIds != null && notesIds.isNotEmpty()) {
                notesIds.forEach { id ->
                    if (isPopupOpen(context, id)) {
                        setPopupOpen(context, id, false)
                        updateAppWidget(context, appWidgetManager, id)
                    }
                }
            }
        }

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
                val viewsBar = buildBarViews(context, isDark, appWidgetId)
                val viewsList = buildListViews(context, isDark, appWidgetId)

                val viewMapping = mapOf(
                    SizeF(120f, 60f) to viewsBar,
                    SizeF(120f, 110f) to viewsList
                )
                appWidgetManager.updateAppWidget(appWidgetId, RemoteViews(viewMapping))
            } else {
                val views = if (minHeight < 110) {
                    buildBarViews(context, isDark, appWidgetId)
                } else {
                    buildListViews(context, isDark, appWidgetId)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            try {
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_notes_list)
            } catch (e: Exception) {
                // Ignore if in bar mode or view not in layout
            }
        }

        internal fun build1x1Views(context: Context, isDark: Boolean, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_1x1_quick)
            val preset = AppSettingsManager.getWidgetDesignPreset(context)
            val bgRes = if (isDark) preset.darkBgDrawable else preset.lightBgDrawable
            views.setInt(R.id.widget_1x1_root, "setBackgroundResource", bgRes)
            views.setInt(R.id.widget_1x1_btn, "setBackgroundResource", preset.fabDrawable)
            val labelColor = if (isDark) preset.headerTextColorDark.toInt() else preset.headerTextColorLight.toInt()
            views.setTextColor(R.id.widget_1x1_label, labelColor)

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

        internal fun buildBarViews(context: Context, isDark: Boolean, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bar_actions)
            val preset = AppSettingsManager.getWidgetDesignPreset(context)
            val bgRes = if (isDark) preset.darkBgDrawable else preset.lightBgDrawable
            views.setInt(R.id.widget_bar_root, "setBackgroundResource", bgRes)
            views.setInt(R.id.widget_bar_btn_text, "setBackgroundResource", preset.fabDrawable)

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

        internal fun buildListViews(context: Context, isDark: Boolean, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_notes)
            val preset = AppSettingsManager.getWidgetDesignPreset(context)
            val fabBehavior = AppSettingsManager.getWidgetFabBehavior(context)

            val bgRes = if (isDark) preset.darkBgDrawable else preset.lightBgDrawable
            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)

            val titleColor = if (isDark) preset.headerTextColorDark.toInt() else preset.headerTextColorLight.toInt()
            val accentColor = if (isDark) preset.accentColorDark.toInt() else preset.accentColorLight.toInt()

            views.setTextColor(R.id.widget_title, titleColor)
            views.setTextColor(R.id.widget_subtitle_filter, accentColor)

            if (isDark) {
                views.setInt(R.id.widget_btn_settings, "setBackgroundResource", R.drawable.widget_action_bg_dark)
                views.setInt(R.id.widget_btn_settings, "setColorFilter", accentColor)
                views.setInt(R.id.widget_btn_refresh, "setBackgroundResource", R.drawable.widget_action_bg_dark)
                views.setInt(R.id.widget_btn_refresh, "setColorFilter", accentColor)
                views.setTextColor(R.id.widget_empty_view, 0xFFCAC4D0.toInt())
                views.setInt(R.id.widget_popup_container, "setBackgroundResource", R.drawable.widget_popup_bg_dark)
                views.setTextColor(R.id.widget_popup_text_image, 0xFFFFFFFF.toInt())
                views.setTextColor(R.id.widget_popup_text_draw, 0xFFFFFFFF.toInt())
                views.setTextColor(R.id.widget_popup_text_voice, 0xFFFFFFFF.toInt())
                views.setTextColor(R.id.widget_popup_text_todo, 0xFFFFFFFF.toInt())
                views.setTextColor(R.id.widget_popup_text_text, 0xFFFFFFFF.toInt())
            } else {
                views.setInt(R.id.widget_btn_settings, "setBackgroundResource", R.drawable.widget_action_bg)
                views.setInt(R.id.widget_btn_settings, "setColorFilter", accentColor)
                views.setInt(R.id.widget_btn_refresh, "setBackgroundResource", R.drawable.widget_action_bg)
                views.setInt(R.id.widget_btn_refresh, "setColorFilter", accentColor)
                views.setTextColor(R.id.widget_empty_view, 0xFF79747E.toInt())
                views.setInt(R.id.widget_popup_container, "setBackgroundResource", R.drawable.widget_popup_bg)
                views.setTextColor(R.id.widget_popup_text_image, 0xFF1C1B1F.toInt())
                views.setTextColor(R.id.widget_popup_text_draw, 0xFF1C1B1F.toInt())
                views.setTextColor(R.id.widget_popup_text_voice, 0xFF1C1B1F.toInt())
                views.setTextColor(R.id.widget_popup_text_todo, 0xFF1C1B1F.toInt())
                views.setTextColor(R.id.widget_popup_text_text, 0xFF1C1B1F.toInt())
            }

            // FAB Arka Planı (Seçili Tasarım Şablonu)
            views.setInt(R.id.widget_fab_main, "setBackgroundResource", preset.fabDrawable)

            // FAB Davranışı ve Menü Durumu
            if (fabBehavior == WidgetFabBehavior.DIRECT_NEW_NOTE) {
                // DOĞRUDAN NOT OLUŞTURMA: Tek dokunuşla %100 kesin anında açılış (0 gecikme, 0 bug)
                views.setViewVisibility(R.id.widget_popup_container, View.GONE)
                views.setViewVisibility(R.id.widget_scrim, View.GONE)
                views.setImageViewResource(R.id.widget_fab_icon, R.drawable.ic_widget_add_white)

                val newNoteIntent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_TYPE_TEXT
                    putExtra(EXTRA_ACTION_TYPE, ACTION_TYPE_TEXT)
                    data = Uri.parse("noteapp://widget/direct_new_note/$appWidgetId")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val directPendingIntent = PendingIntent.getActivity(
                    context,
                    6000 + appWidgetId,
                    newNoteIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_fab_main, directPendingIntent)
            } else {
                // GENİŞLEYEN POPUP MENÜ
                val isPopupOpen = isPopupOpen(context, appWidgetId)
                if (isPopupOpen) {
                    views.setViewVisibility(R.id.widget_popup_container, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_scrim, View.VISIBLE)
                    views.setImageViewResource(R.id.widget_fab_icon, R.drawable.ic_widget_close)
                } else {
                    views.setViewVisibility(R.id.widget_popup_container, View.GONE)
                    views.setViewVisibility(R.id.widget_scrim, View.GONE)
                    views.setImageViewResource(R.id.widget_fab_icon, R.drawable.ic_widget_add_white)
                }

                // Dışına tıklandığında menüyü kapat
                val scrimIntent = Intent(context, NotesWidgetProvider::class.java).apply {
                    action = ACTION_CLOSE_POPUP
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("noteapp://widget/scrim_close/$appWidgetId")
                }
                val scrimPendingIntent = PendingIntent.getBroadcast(
                    context,
                    7000 + appWidgetId,
                    scrimIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_scrim, scrimPendingIntent)

                // FAB Tıklaması -> Popup Menüsünü Aç / Kapat
                val popupIntent = Intent(context, NotesWidgetProvider::class.java).apply {
                    action = if (isPopupOpen) ACTION_CLOSE_POPUP else ACTION_OPEN_POPUP
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("noteapp://widget/toggle/${appWidgetId}_${if (isPopupOpen) "close" else "open"}")
                }
                val popupPendingIntent = PendingIntent.getBroadcast(
                    context,
                    5000 + appWidgetId,
                    popupIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_fab_main, popupPendingIntent)

                // Popup Menü Seçeneklerini Bağla
                wireActionButtons(
                    context = context,
                    views = views,
                    appWidgetId = appWidgetId,
                    btnTextId = R.id.widget_popup_item_text,
                    btnTodoId = R.id.widget_popup_item_todo,
                    btnImageId = R.id.widget_popup_item_image,
                    btnVoiceId = R.id.widget_popup_item_voice,
                    btnDrawId = R.id.widget_popup_item_draw
                )
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

            val settingsIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_TYPE_SETTINGS
                data = Uri.parse("noteapp://widget/settings/$appWidgetId")
                putExtra(EXTRA_ACTION_TYPE, ACTION_TYPE_SETTINGS)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val settingsPendingIntent = PendingIntent.getActivity(
                context,
                9000 + appWidgetId,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_settings, settingsPendingIntent)

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
            btnTodoId: Int? = null,
            btnImageId: Int,
            btnVoiceId: Int,
            btnDrawId: Int? = null
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

            if (btnTodoId != null) {
            val todoIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_ACTION_TYPE, ACTION_TYPE_CHECKLIST)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            views.setOnClickPendingIntent(
                btnTodoId,
                PendingIntent.getActivity(context, baseReq + 2, todoIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            }

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

            if (btnDrawId != null) {
            val drawIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_ACTION_TYPE, ACTION_TYPE_DRAW)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            views.setOnClickPendingIntent(
                btnDrawId,
                PendingIntent.getActivity(context, baseReq + 5, drawIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            }
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
