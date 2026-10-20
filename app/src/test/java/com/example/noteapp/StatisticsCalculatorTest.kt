package com.example.noteapp

import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.util.StatisticsCalculator
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

class StatisticsCalculatorTest {

    @Test
    fun testWordCountAccuracy() {
        assertEquals(0, StatisticsCalculator.countWords(""))
        assertEquals(0, StatisticsCalculator.countWords("   \n\t  "))
        assertEquals(1, StatisticsCalculator.countWords("Merhaba"))
        assertEquals(4, StatisticsCalculator.countWords("Bu bir test notudur."))
        assertEquals(5, StatisticsCalculator.countWords("Çoklu   boşluklar   ve\nyeni\nsatırlar."))
    }

    @Test
    fun testStatisticsCalculation() {
        val now = System.currentTimeMillis()
        val categories = listOf(
            Category(id = 1L, name = "İş", color = 0xFF123456.toInt()),
            Category(id = 2L, name = "Kişisel", color = 0xFF654321.toInt())
        )

        val notes = listOf(
            Note(
                id = 1L,
                title = "Sprint Planı",
                content = "Bugün #kotlin ve #android çalışılacak. Hedefler belirlendi.",
                categoryId = 1L,
                attachments = listOf("voice_note.m4a", "drawing_123.png"),
                timestamp = now,
                updatedAt = now
            ),
            Note(
                id = 2L,
                title = "Alışveriş",
                content = "Market ihtiyaçları: Süt, ekmek, yumurta. #market #ev",
                categoryId = 2L,
                attachments = listOf("receipt.jpg"),
                timestamp = now,
                updatedAt = now
            ),
            Note(
                id = 3L,
                title = "Arşivlenmiş Not",
                content = "Eski bilgiler",
                isArchived = true,
                timestamp = now
            ),
            Note(
                id = 4L,
                title = "Çöp Not",
                content = "Silinen metin",
                isDeleted = true,
                timestamp = now
            )
        )

        val stats = StatisticsCalculator.calculate(notes, categories)

        // Temel Not Sayıları
        assertEquals(2, stats.totalActiveNotes)
        assertEquals(1, stats.totalArchivedNotes)
        assertEquals(1, stats.totalTrashNotes)

        // Kelime Sayıları (Aktif notlar: Note 1 title 2 + content 7 = 9; Note 2 title 1 + content 5 = 6; Toplam = 15)
        assertTrue(stats.totalWords >= 14)
        assertTrue(stats.totalCharacters > 0)
        assertTrue(stats.averageWordsPerNote > 0)
        assertEquals(1, stats.estimatedReadingMinutes)

        // Medya Envanteri
        assertEquals(1, stats.mediaInventory.totalAudios)
        assertEquals(1, stats.mediaInventory.totalDrawings)
        assertEquals(1, stats.mediaInventory.totalImages)

        // Etiketler
        val tagNames = stats.topTags.map { it.tag }
        assertTrue(tagNames.contains("kotlin"))
        assertTrue(tagNames.contains("android"))
        assertTrue(tagNames.contains("market"))

        // Kategori Dağılımı
        assertEquals(2, stats.categoryDistribution.size)
        val catNames = stats.categoryDistribution.map { it.categoryName }
        assertTrue(catNames.contains("İş"))
        assertTrue(catNames.contains("Kişisel"))

        // Streak Kontrolü (Bugün not yazıldığı için aktif streak >= 1 olmalı)
        assertTrue(stats.streakInfo.currentStreak >= 1)
        assertTrue(stats.streakInfo.isStreakActiveToday)

        // Bugün Yazılan Kelimeler
        assertTrue(stats.todayWords > 0)
    }

    @Test
    fun testStreakCalculationConsecutiveDays() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()

        // 3 gün üst üste not
        val notes = mutableListOf<Note>()
        for (i in 0..2) {
            val noteCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            notes.add(
                Note(
                    id = (i + 1).toLong(),
                    title = "Günlük Not $i",
                    content = "Gün $i içeriği",
                    timestamp = noteCal.timeInMillis,
                    updatedAt = noteCal.timeInMillis
                )
            )
        }

        val stats = StatisticsCalculator.calculate(notes, emptyList())
        assertEquals(3, stats.streakInfo.currentStreak)
        assertEquals(3, stats.streakInfo.bestStreak)
        assertTrue(stats.streakInfo.isStreakActiveToday)
    }
}
