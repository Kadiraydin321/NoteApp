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

private val WIDGET_HEADING_REGEX = Regex("#{1,6}\\s+.*")
private val WIDGET_HEADING_PREFIX_REGEX = Regex("^#{1,6}\\s+")
private val WIDGET_CALLOUT_PREFIX_REGEX = Regex("^>\\s+\\[![A-Za-z]+]\\s*", RegexOption.IGNORE_CASE)
private val WIDGET_WIKI_LINK_REGEX = Regex("\\[\\[([^\\]]+)]]")
private val WIDGET_MARKDOWN_LINK_REGEX = Regex("\\[([^]]+)]\\([^)]+\\)")
private val WIDGET_BOLD_REGEX = Regex("(\\*\\*|__)(.+?)\\1")
private val WIDGET_ITALIC_REGEX = Regex("(\\*|_)([^*_]+)\\1")
private val WIDGET_STRIKE_REGEX = Regex("~~(.+?)~~")
private val WIDGET_HIGHLIGHT_REGEX = Regex("==(.+?)==")
private val WIDGET_INLINE_CODE_REGEX = Regex("`([^`]+)`")

private fun widgetMarkdownExcerpt(raw: String, maxLength: Int = 120): String {
    var insideCodeBlock = false
    val plainText = raw.lineSequence().mapNotNull { sourceLine ->
        val line = sourceLine.trimStart()
        if (line.startsWith("```")) {
            insideCodeBlock = !insideCodeBlock
            return@mapNotNull null
        }
        if (line.isBlank()) return@mapNotNull null

        var display = when {
            WIDGET_HEADING_REGEX.matches(line) -> line.replaceFirst(WIDGET_HEADING_PREFIX_REGEX, "")
            line.startsWith("- [x] ", ignoreCase = true) -> "☑ " + line.drop(6)
            line.startsWith("- [ ] ") -> "☐ " + line.removePrefix("- [ ] ")
            line.startsWith("> [!", ignoreCase = true) -> line.replaceFirst(WIDGET_CALLOUT_PREFIX_REGEX, "")
            line.startsWith("> ") -> "“${line.removePrefix("> ")}”"
            line.startsWith("- ") -> "• " + line.removePrefix("- ")
            else -> line
        }
        display = display
            .replace(WIDGET_WIKI_LINK_REGEX, "$1")
            .replace(WIDGET_MARKDOWN_LINK_REGEX, "$1")
            .replace(WIDGET_BOLD_REGEX, "$2")
            .replace(WIDGET_ITALIC_REGEX, "$2")
            .replace(WIDGET_STRIKE_REGEX, "$1")
            .replace(WIDGET_HIGHLIGHT_REGEX, "$1")
            .replace(WIDGET_INLINE_CODE_REGEX, "$1")

        if (insideCodeBlock) "⌘ $display" else display
    }.joinToString("\n")

    return plainText.take(maxLength).trim().ifBlank { "İçerik yok" }
}

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

        val isDark = AppSettingsManager.isWidgetDarkTheme(context)

        // Başlık
        val titleText = if (note.title.isNotBlank()) note.title else "Başlıksız Not"
        views.setTextViewText(R.id.widget_item_title, titleText)

        // İçerik özeti (Onay kutusu işaretlerini güzelleştir: [x] -> ☑, [ ] -> ☐)
        if (note.isLocked) {
            views.setTextViewText(R.id.widget_item_content, "Kilitli not · Görüntülemek için dokunun")
            views.setViewVisibility(R.id.widget_item_lock, View.VISIBLE)
        } else if (AppSettingsManager.getWidgetShowContent(context)) {
            views.setTextViewText(R.id.widget_item_content, widgetMarkdownExcerpt(note.content))
            views.setViewVisibility(R.id.widget_item_content, View.VISIBLE)
            views.setViewVisibility(R.id.widget_item_lock, View.GONE)
        } else {
            views.setTextViewText(R.id.widget_item_content, "")
            views.setViewVisibility(R.id.widget_item_content, View.GONE)
            views.setViewVisibility(R.id.widget_item_lock, View.GONE)
        }

        // Sabitleme rozeti
        views.setViewVisibility(
            R.id.widget_item_pin,
            if (note.isPinned) View.VISIBLE else View.GONE
        )

        // Yuvarlak Köşeli Arka Plan Renklendirmesi (Radius 20dp asla bozulmaz)
        val cardColor = if (isDark) 0xFF24252B.toInt() else 0xFFFAFAFC.toInt()
        val accentColor = if (note.color != 0) note.color else if (isDark) 0xFFD0BCFF.toInt() else 0xFF6750A4.toInt()
        views.setInt(R.id.widget_item_bg_image, "setColorFilter", cardColor)
        views.setInt(R.id.widget_item_accent, "setColorFilter", accentColor)

        // Metin rengini arka plan parlaklığına (luminance) göre zıt yap
        val r = android.graphics.Color.red(cardColor) / 255.0
        val g = android.graphics.Color.green(cardColor) / 255.0
        val b = android.graphics.Color.blue(cardColor) / 255.0
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b

        val titleColor = if (luminance < 0.45) 0xFFF8F9FA.toInt() else 0xFF1D1B20.toInt()
        val contentColor = if (luminance < 0.45) 0xFFD0D4D9.toInt() else 0xFF49454F.toInt()

        views.setTextColor(R.id.widget_item_title, titleColor)
        views.setTextColor(R.id.widget_item_content, contentColor)

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
