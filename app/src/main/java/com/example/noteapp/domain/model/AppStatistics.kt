package com.example.noteapp.domain.model

data class DailyStreakInfo(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalActiveDays: Int = 0,
    val isStreakActiveToday: Boolean = false
)

data class TagFrequency(
    val tag: String,
    val count: Int,
    val percentage: Float
)

data class CategoryStat(
    val categoryName: String,
    val noteCount: Int,
    val color: Int
)

data class MediaInventory(
    val totalAudios: Int = 0,
    val totalImages: Int = 0,
    val totalDrawings: Int = 0
)

data class AppStatistics(
    val totalActiveNotes: Int = 0,
    val totalArchivedNotes: Int = 0,
    val totalTrashNotes: Int = 0,
    val totalPinnedNotes: Int = 0,
    val totalLockedNotes: Int = 0,
    val totalWords: Long = 0,
    val totalCharacters: Long = 0,
    val averageWordsPerNote: Int = 0,
    val estimatedReadingMinutes: Int = 0,
    val streakInfo: DailyStreakInfo = DailyStreakInfo(),
    val topTags: List<TagFrequency> = emptyList(),
    val categoryDistribution: List<CategoryStat> = emptyList(),
    val mediaInventory: MediaInventory = MediaInventory(),
    val weeklyActivity: List<Int> = List(7) { 0 }, // Son 7 gün (Bugünden geriye doğru)
    val dayNames: List<String> = emptyList(), // Gün isimleri (Pzt, Sal, Çar vb.)
    val todayWords: Int = 0
)
