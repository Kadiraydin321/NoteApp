package com.example.noteapp.domain.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.noteapp.MainActivity
import com.example.noteapp.R
import com.example.noteapp.widget.NotesWidgetProvider

/**
 * Android Launcher Dinamik Kısayollarını (App Shortcuts) ve
 * Ana Ekrana Not Sabitleme (Pin Note to Home Screen) işlemlerini yöneten motor.
 */
object AppShortcutManager {

    const val SHORTCUT_NEW_NOTE = "shortcut_new_note"
    const val SHORTCUT_CHECKLIST = "shortcut_checklist"
    const val SHORTCUT_VOICE = "shortcut_voice"
    const val SHORTCUT_DRAW = "shortcut_draw"

    /**
     * Uygulama ikonuna basılı tutulduğunda beliren dinamik kısayolları (Launcher Dynamic Shortcuts) kaydeder.
     */
    fun initDynamicShortcuts(context: Context) {
        try {
            val shortcuts = listOf(
                ShortcutInfoCompat.Builder(context, SHORTCUT_NEW_NOTE)
                    .setShortLabel("Yeni Not")
                    .setLongLabel("Yeni Not Oluştur")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_widget_text))
                    .setIntent(buildShortcutIntent(context, NotesWidgetProvider.ACTION_TYPE_TEXT))
                    .setRank(0)
                    .build(),

                ShortcutInfoCompat.Builder(context, SHORTCUT_CHECKLIST)
                    .setShortLabel("Yapılacaklar")
                    .setLongLabel("Yeni Kontrol Listesi Aç")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_widget_check))
                    .setIntent(buildShortcutIntent(context, NotesWidgetProvider.ACTION_TYPE_CHECKLIST))
                    .setRank(1)
                    .build(),

                ShortcutInfoCompat.Builder(context, SHORTCUT_VOICE)
                    .setShortLabel("Ses Kaydı")
                    .setLongLabel("Hızlı Sesli Not Kaydet")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_widget_mic))
                    .setIntent(buildShortcutIntent(context, NotesWidgetProvider.ACTION_TYPE_VOICE))
                    .setRank(2)
                    .build(),

                ShortcutInfoCompat.Builder(context, SHORTCUT_DRAW)
                    .setShortLabel("Çizim Yap")
                    .setLongLabel("El Çizimi Notu Aç")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_widget_draw))
                    .setIntent(buildShortcutIntent(context, NotesWidgetProvider.ACTION_TYPE_DRAW))
                    .setRank(3)
                    .build()
            )

            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Kısayol tetiklendiğinde MainActivity'yi uygun eylemle başlatacak Intent'i üretir.
     */
    fun buildShortcutIntent(context: Context, actionType: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotesWidgetProvider.EXTRA_ACTION_TYPE, actionType)
        }
    }

    /**
     * Seçili bir notu Android Ana Ekranına (Home Screen) doğrudan açılabilir pinli kısayol olarak ekler.
     */
    fun pinNoteToHomeScreen(context: Context, noteId: Long, noteTitle: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return false
        }

        val displayTitle = noteTitle.ifBlank { "Not #$noteId" }
        val noteIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("noteId", noteId)
        }

        val pinShortcutInfo = ShortcutInfoCompat.Builder(context, "pinned_note_$noteId")
            .setShortLabel(displayTitle.take(20))
            .setLongLabel(displayTitle)
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_widget_pin))
            .setIntent(noteIntent)
            .build()

        return ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, null)
    }

    /**
     * Gelen aksiyon tipine karşılık gelen Compose navigasyon rotasını döner (Birim testleri ve doğrulama için).
     */
    fun resolveActionRoute(actionType: String?): String {
        return when (actionType) {
            NotesWidgetProvider.ACTION_TYPE_TEXT -> "note_detail_screen"
            NotesWidgetProvider.ACTION_TYPE_CHECKLIST -> "note_detail_screen?autoAction=checklist"
            NotesWidgetProvider.ACTION_TYPE_VOICE -> "note_detail_screen?autoAction=voice"
            NotesWidgetProvider.ACTION_TYPE_IMAGE -> "note_detail_screen?autoAction=image"
            NotesWidgetProvider.ACTION_TYPE_DRAW -> "note_detail_screen?autoAction=draw"
            NotesWidgetProvider.ACTION_TYPE_SETTINGS -> "settings_screen"
            else -> "notes_screen"
        }
    }
}
