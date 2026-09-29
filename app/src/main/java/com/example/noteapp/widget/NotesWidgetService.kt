package com.example.noteapp.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.room.Room
import com.example.noteapp.R
import com.example.noteapp.data.local.NoteDatabase
import com.example.noteapp.data.settings.AppSettingsManager
import com.example.noteapp.data.settings.WidgetFilterMode
import com.example.noteapp.domain.model.Note
import kotlinx.coroutines.runBlocking

class NotesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NotesRemoteViewsFactory(applicationContext)
    }
}

class NotesRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var notes: List<Note> = emptyList()

    override fun onCreate() {
        // İlk yükleme
    }

    override fun onDataSetChanged() {
        // Binder iş parçacığında çalışır
        runBlocking {
            try {
                val db = NoteDatabase.getInstance(context)
                val filterMode = AppSettingsManager.getWidgetFilterMode(context)
                val showLocked = AppSettingsManager.getWidgetShowLocked(context)

                val rawList = if (filterMode == WidgetFilterMode.FAVORITES) {
                    db.noteDao.getPinnedNotesList()
                } else {
                    db.noteDao.getActiveNotesList()
                }

                notes = if (!showLocked) {
                    rawList.filter { !it.isLocked }
                } else {
                    rawList
                }
            } catch (e: Exception) {
                e.printStackTrace()
                notes = emptyList()
            }
        }
    }

    override fun onDestroy() {
        notes = emptyList()
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= notes.size) return null
        val note = notes[position]

        val views = RemoteViews(context.packageName, R.layout.widget_note_item)

        // Başlık
        val titleText = if (note.title.isNotBlank()) note.title else "Başlıksız Not"
        views.setTextViewText(R.id.widget_item_title, titleText)

        // İçerik özeti
        if (note.isLocked) {
            views.setTextViewText(R.id.widget_item_content, "🔒 Bu not kilitli (Görüntülemek için dokunun)")
            views.setViewVisibility(R.id.widget_item_lock, View.VISIBLE)
        } else {
            val contentText = if (note.content.isNotBlank()) {
                note.content.replace("\n", " ").take(100)
            } else {
                "İçerik yok"
            }
            views.setTextViewText(R.id.widget_item_content, contentText)
            views.setViewVisibility(R.id.widget_item_lock, View.GONE)
        }

        // Sabitleme rozeti
        views.setViewVisibility(
            R.id.widget_item_pin,
            if (note.isPinned) View.VISIBLE else View.GONE
        )

        // Arka plan rengi (renk ayarlanmışsa hafif tonla renklendir)
        if (note.color != 0) {
            views.setInt(R.id.widget_item_container, "setBackgroundColor", note.color)
        } else {
            views.setInt(R.id.widget_item_container, "setBackgroundResource", R.drawable.widget_item_background)
        }

        // Tıklama intent'i (fill-in intent)
        val fillInIntent = Intent().apply {
            putExtra("noteId", note.id)
            putExtra("isFromWidget", true)
        }
        views.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return if (position in notes.indices) notes[position].id else position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}
