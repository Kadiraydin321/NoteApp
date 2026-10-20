package com.example.noteapp.domain.util

import com.example.noteapp.domain.model.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object StatisticsCalculator {

    private val HASHTAG_REGEX = Regex("#([a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-]+)")

    fun calculate(
        allNotes: List<Note>,
        categories: List<Category>
    ): AppStatistics {
        val activeNotes = allNotes.filter { !it.isDeleted && !it.isArchived }
        val archivedNotes = allNotes.filter { it.isArchived && !it.isDeleted }
        val trashNotes = allNotes.filter { it.isDeleted }

        var totalWords = 0L
        var totalChars = 0L
        var audioCount = 0
        var imageCount = 0
        var drawingCount = 0
        val tagCounts = mutableMapOf<String, Int>()

        activeNotes.forEach { note ->
            val wordsInTitle = countWords(note.title)
            val wordsInContent = countWords(note.content)
            totalWords += (wordsInTitle + wordsInContent)
            totalChars += (note.title.length + note.content.length)

            // Etiketler
            HASHTAG_REGEX.findAll(note.content).forEach { match ->
                val tag = match.groupValues[1].lowercase(Locale("tr", "TR"))
                tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
            }

            // Medya
            note.attachments.forEach { path ->
                when {
                    path.endsWith(".m4a", ignoreCase = true) || path.endsWith(".mp3", ignoreCase = true) -> audioCount++
                    path.contains("drawing", ignoreCase = true) -> drawingCount++
                    else -> imageCount++
                }
            }
        }

        val avgWords = if (activeNotes.isNotEmpty()) (totalWords / activeNotes.size).toInt() else 0
        val readingMinutes = (totalWords / 200).toInt().coerceAtLeast(if (totalWords > 0) 1 else 0)

        // Etiket Sıralaması
        val topTags = tagCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { entry ->
                val percentage = if (activeNotes.isNotEmpty()) {
                    (entry.value.toFloat() / activeNotes.size) * 100f
                } else 0f
                TagFrequency(tag = entry.key, count = entry.value, percentage = percentage.coerceIn(0f, 100f))
            }

        // Kategori Dağılımı
        val categoryMap = categories.associateBy { it.id }
        val categoryNoteCounts = activeNotes.groupBy { it.categoryId }
        val categoryStats = categoryNoteCounts.map { (catId, notesList) ->
            val cat = if (catId != null) categoryMap[catId] else null
            CategoryStat(
                categoryName = cat?.name ?: "Kategorisiz",
                noteCount = notesList.size,
                color = cat?.color ?: 0
            )
        }.sortedByDescending { it.noteCount }

        // Günlük Yazma Serisi (Streak)
        val streakInfo = calculateStreak(activeNotes)

        // Haftalık Aktivite
        val (weeklyActivity, dayNames) = calculateWeeklyActivity(allNotes)

        // Bugün Yazılan Kelimeler
        val todaySdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = todaySdf.format(Date())
        val todayWords = allNotes.filter {
            val ts = if (it.updatedAt != 0L) it.updatedAt else it.timestamp
            todaySdf.format(Date(ts)) == todayStr
        }.sumOf { countWords(it.title) + countWords(it.content) }

        return AppStatistics(
            totalActiveNotes = activeNotes.size,
            totalArchivedNotes = archivedNotes.size,
            totalTrashNotes = trashNotes.size,
            totalPinnedNotes = activeNotes.count { it.isPinned },
            totalLockedNotes = activeNotes.count { it.isLocked },
            totalWords = totalWords,
            totalCharacters = totalChars,
            averageWordsPerNote = avgWords,
            estimatedReadingMinutes = readingMinutes,
            streakInfo = streakInfo,
            topTags = topTags,
            categoryDistribution = categoryStats,
            mediaInventory = MediaInventory(
                totalAudios = audioCount,
                totalImages = imageCount,
                totalDrawings = drawingCount
            ),
            weeklyActivity = weeklyActivity,
            dayNames = dayNames,
            todayWords = todayWords
        )
    }

    fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }

    private fun calculateStreak(notes: List<Note>): DailyStreakInfo {
        if (notes.isEmpty()) return DailyStreakInfo()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val activeDates = notes.map {
            val ts = if (it.updatedAt != 0L) it.updatedAt else it.timestamp
            sdf.format(Date(ts))
        }.toSet().sortedDescending()

        if (activeDates.isEmpty()) return DailyStreakInfo()

        val todayStr = sdf.format(Date())
        val yesterdayCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStr = sdf.format(yesterdayCalendar.time)

        val isActiveToday = activeDates.contains(todayStr)
        val startsStreak = isActiveToday || activeDates.contains(yesterdayStr)

        if (!startsStreak) {
            return DailyStreakInfo(
                currentStreak = 0,
                bestStreak = calculateBestStreak(activeDates),
                totalActiveDays = activeDates.size,
                isStreakActiveToday = false
            )
        }

        // Mevcut seriyi hesapla
        var currentStreak = 0
        val checkCalendar = Calendar.getInstance()
        if (!isActiveToday) {
            checkCalendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        while (true) {
            val dateStr = sdf.format(checkCalendar.time)
            if (activeDates.contains(dateStr)) {
                currentStreak++
                checkCalendar.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        val bestStreak = maxOf(currentStreak, calculateBestStreak(activeDates))

        return DailyStreakInfo(
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            totalActiveDays = activeDates.size,
            isStreakActiveToday = isActiveToday
        )
    }

    private fun calculateBestStreak(sortedDescendingDateStrings: List<String>): Int {
        if (sortedDescendingDateStrings.isEmpty()) return 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dates = sortedDescendingDateStrings.mapNotNull {
            runCatching { sdf.parse(it) }.getOrNull()
        }.sorted()

        var best = 1
        var current = 1

        for (i in 1 until dates.size) {
            val prev = dates[i - 1]
            val curr = dates[i]
            val diffDays = TimeUnit.MILLISECONDS.toDays(curr.time - prev.time)
            if (diffDays == 1L) {
                current++
                if (current > best) best = current
            } else if (diffDays > 1L) {
                current = 1
            }
        }
        return best
    }

    private fun calculateWeeklyActivity(notes: List<Note>): Pair<List<Int>, List<String>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEE", Locale("tr", "TR"))
        val activity = mutableListOf<Int>()
        val dayNames = mutableListOf<String>()

        val noteDateCounts = mutableMapOf<String, Int>()
        notes.forEach { note ->
            val ts = if (it_ts(note) != 0L) it_ts(note) else note.timestamp
            val dateStr = sdf.format(Date(ts))
            noteDateCounts[dateStr] = (noteDateCounts[dateStr] ?: 0) + 1
        }

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val dateStr = sdf.format(cal.time)
            activity.add(noteDateCounts[dateStr] ?: 0)
            dayNames.add(dayFormat.format(cal.time).replace(".", ""))
        }

        return Pair(activity, dayNames)
    }

    private fun it_ts(note: Note): Long {
        return if (note.updatedAt != 0L) note.updatedAt else note.timestamp
    }
}
