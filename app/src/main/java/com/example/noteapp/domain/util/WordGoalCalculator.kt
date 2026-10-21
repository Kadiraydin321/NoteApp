package com.example.noteapp.domain.util

data class WordGoalStatus(
    val currentWords: Int,
    val targetWords: Int,
    val progress: Float,
    val percent: Int,
    val remainingWords: Int,
    val isCompleted: Boolean
)

/**
 * Not bazlı kelime hedefi hesaplamalarını (hedef oranı, kalan kelime, tamamlanma durumu)
 * gerçekleştiren yardımcı sınıf.
 */
object WordGoalCalculator {

    fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }

    fun calculateStatus(currentWords: Int, targetWords: Int): WordGoalStatus {
        val target = targetWords.coerceAtLeast(1)
        val progress = (currentWords.toFloat() / target.toFloat()).coerceIn(0f, 1f)
        val percent = ((currentWords.toFloat() / target.toFloat()) * 100).toInt()
        val remaining = (target - currentWords).coerceAtLeast(0)

        return WordGoalStatus(
            currentWords = currentWords,
            targetWords = target,
            progress = progress,
            percent = percent,
            remainingWords = remaining,
            isCompleted = currentWords >= target
        )
    }
}
