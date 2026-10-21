package com.example.noteapp.domain.util

import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import java.util.Calendar
import java.util.Locale

/**
 * Not zaman çizelgesi dilimleri.
 */
enum class TimelineBucket(val title: String, val iconEmoji: String, val order: Int) {
    PINNED("Sabitlenenler", "📌", 0),
    TODAY("Bugün", "⚡", 1),
    YESTERDAY("Dün", "🗓️", 2),
    THIS_WEEK("Bu Hafta", "📅", 3),
    THIS_MONTH("Bu Ay", "🗓️", 4),
    OLDER("Daha Eski", "🗃️", 5)
}

/**
 * Notlar listeleme gruplama modu.
 */
enum class NoteGroupingMode(val title: String, val iconEmoji: String) {
    FLAT("Düz Liste", "📄"),
    TIMELINE("Zaman Çizelgesi", "⏳"),
    CATEGORY("Kategoriye Göre", "🏷️")
}

/**
 * Bölümlere ayrılmış not grubu modeli.
 */
data class NoteSection<T>(
    val sectionKey: String,
    val title: String,
    val iconEmoji: String = "",
    val color: Int? = null,
    val items: List<T>
)

/**
 * Not Zaman Çizelgesi & Bölümlü Gruplama Motoru.
 * Notları oluşturulma/düzenlenme tarihlerine göre insan odaklı zaman dilimlerine
 * ("📌 Sabitlenenler", "⚡ Bugün", "🗓️ Dün", "📅 Bu Hafta", "🗓️ Bu Ay", "🗃️ Daha Eski")
 * veya kategori bazında şık bölümlere ayırır.
 */
object NoteTimelineGrouper {

    /**
     * Verilen referans zamana göre bir notun ait olduğu zaman dilimini belirler.
     */
    fun getTimelineBucketForTimestamp(
        noteTimestampMs: Long,
        referenceTimestampMs: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault()
    ): TimelineBucket {
        val refCal = Calendar.getInstance(locale).apply { timeInMillis = referenceTimestampMs }

        // Bugünün başlangıcı (00:00:00.000)
        val todayStart = Calendar.getInstance(locale).apply {
            timeInMillis = referenceTimestampMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Dünün başlangıcı (00:00:00.000)
        val yesterdayStart = Calendar.getInstance(locale).apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis

        // Bu haftanın başlangıcı (Pazartesi 00:00:00.000)
        val thisWeekStart = Calendar.getInstance(locale).apply {
            timeInMillis = todayStart
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            if (timeInMillis > todayStart) {
                // Eğer haftanın ilk günü bugünden ileride ise önceki haftaya çek
                add(Calendar.WEEK_OF_YEAR, -1)
            }
        }.timeInMillis

        // Bu ayın başlangıcı (1. gün 00:00:00.000)
        val thisMonthStart = Calendar.getInstance(locale).apply {
            timeInMillis = todayStart
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

        return when {
            noteTimestampMs >= todayStart -> TimelineBucket.TODAY
            noteTimestampMs >= yesterdayStart -> TimelineBucket.YESTERDAY
            noteTimestampMs >= thisWeekStart -> TimelineBucket.THIS_WEEK
            noteTimestampMs >= thisMonthStart -> TimelineBucket.THIS_MONTH
            else -> TimelineBucket.OLDER
        }
    }

    /**
     * Notları zaman çizelgesine göre bölümlendirir.
     * Sabitlenmiş notlar (isPinned = true) her zaman en başta "Sabitlenenler" bölümünde toplanır.
     */
    fun groupNotesByTimeline(
        notes: List<Note>,
        referenceTimestampMs: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault()
    ): List<NoteSection<Note>> {
        if (notes.isEmpty()) return emptyList()

        val sections = mutableListOf<NoteSection<Note>>()

        // 1. Sabitlenmiş Notlar
        val (pinnedNotes, unpinnedNotes) = notes.partition { it.isPinned }
        if (pinnedNotes.isNotEmpty()) {
            sections.add(
                NoteSection(
                    sectionKey = TimelineBucket.PINNED.name,
                    title = TimelineBucket.PINNED.title,
                    iconEmoji = TimelineBucket.PINNED.iconEmoji,
                    items = pinnedNotes
                )
            )
        }

        // 2. Zaman Dilimlerine Göre Grupla
        val bucketMap = mutableMapOf<TimelineBucket, MutableList<Note>>()
        TimelineBucket.values().filter { it != TimelineBucket.PINNED }.forEach {
            bucketMap[it] = mutableListOf()
        }

        unpinnedNotes.forEach { note ->
            val effectiveTime = getEffectiveTimestamp(note)
            val bucket = getTimelineBucketForTimestamp(effectiveTime, referenceTimestampMs, locale)
            bucketMap[bucket]?.add(note)
        }

        // Boş olmayan zaman dilimlerini sıralı şekilde ekle
        TimelineBucket.values().filter { it != TimelineBucket.PINNED }.forEach { bucket ->
            val list = bucketMap[bucket]
            if (!list.isNullOrEmpty()) {
                sections.add(
                    NoteSection(
                        sectionKey = bucket.name,
                        title = bucket.title,
                        iconEmoji = bucket.iconEmoji,
                        items = list
                    )
                )
            }
        }

        return sections
    }

    /**
     * Notları kategorilerine göre bölümlendirir.
     */
    fun groupNotesByCategory(
        notes: List<Note>,
        categories: List<Category>
    ): List<NoteSection<Note>> {
        if (notes.isEmpty()) return emptyList()

        val categoryLookup = categories.associateBy { it.id }
        val groupedByCatId = notes.groupBy { it.categoryId }

        val sections = mutableListOf<NoteSection<Note>>()

        // Önce tanımlı kategorilerdeki notlar
        categories.forEach { cat ->
            val catNotes = groupedByCatId[cat.id]
            if (!catNotes.isNullOrEmpty()) {
                sections.add(
                    NoteSection(
                        sectionKey = "cat_${cat.id}",
                        title = cat.name,
                        iconEmoji = "🏷️",
                        color = cat.color,
                        items = catNotes
                    )
                )
            }
        }

        // Kategorisiz veya silinmiş kategoriye ait notlar
        val uncategorizedNotes = notes.filter { note ->
            note.categoryId == null || !categoryLookup.containsKey(note.categoryId)
        }
        if (uncategorizedNotes.isNotEmpty()) {
            sections.add(
                NoteSection(
                    sectionKey = "uncategorized",
                    title = "Kategorisiz",
                    iconEmoji = "📁",
                    color = null,
                    items = uncategorizedNotes
                )
            )
        }

        return sections
    }

    /**
     * Bir notun en güncel geçerli zaman damgasını döndürür.
     */
    fun getEffectiveTimestamp(note: Note): Long {
        return maxOf(note.updatedAt, note.timestamp, note.createdAt)
    }
}
