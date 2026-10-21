package com.example.noteapp.domain.util

import java.util.Locale

/**
 * Metin okunabilirlik derecesi.
 */
enum class ReadabilityGrade(val title: String, val description: String) {
    VERY_EASY("Çok Kolay", "Her yaştan okuyucu için son derece akıcı ve sade bir metin."),
    EASY("Kolay", "Günlük konuşma dilinde, rahatça ve hızlıca okunabilir."),
    MODERATE("Orta", "Standart gazete ve makale düzeyinde, dengeli bir anlatım."),
    CHALLENGING("İleri Düzey", "Daha uzun cümleler ve zengin kelime dağarcığı içerir."),
    TECHNICAL("Teknik / Akademik", "Yoğun bilgi, teknik terimler veya karmaşık cümle yapıları içerir.")
}

/**
 * Anahtar kelime ve metin içindeki frekansı.
 */
data class KeywordFrequency(
    val word: String,
    val count: Int
)

/**
 * Not metnine ait kapsamlı analitik ve okunabilirlik metrikleri.
 */
data class NoteMetrics(
    val wordCount: Int,
    val charCountWithSpaces: Int,
    val charCountWithoutSpaces: Int,
    val paragraphCount: Int,
    val sentenceCount: Int,
    val syllableCount: Int,
    val uniqueWordCount: Int,
    val lexicalRichnessPercent: Int,
    val silentReadingTimeMinutes: Float,
    val readingTimeFormatted: String,
    val speakingTimeMinutes: Float,
    val speakingTimeFormatted: String,
    val readabilityScore: Float,
    val readabilityGrade: ReadabilityGrade,
    val topKeywords: List<KeywordFrequency>
)

/**
 * Not İçi Metin Analitik ve Okunabilirlik Motoru.
 * Markdown temizleme, hece sayımı, kelime sıklığı, Flesch uyarlamalı okunabilirlik skoru
 * ve okuma/konuşma sürelerini hesaplar.
 */
object NoteMetricsEngine {

    private const val WORDS_PER_MINUTE_READING = 200f
    private const val WORDS_PER_MINUTE_SPEAKING = 130f

    // Türkçe ve İngilizce yaygın stop-words (bağlaçlar, zamirler, edatlar)
    private val STOP_WORDS = setOf(
        "ve", "veya", "ile", "bir", "bu", "şu", "o", "da", "de", "için", "gibi",
        "kadar", "daha", "en", "çok", "az", "ama", "fakat", "ancak", "lakin", "çünkü",
        "ise", "ki", "mi", "mı", "mu", "mü", "ne", "nasıl", "neden", "niçin", "kim",
        "ben", "sen", "biz", "siz", "onlar", "bunu", "şunu", "onu", "buna", "şuna", "ona",
        "burada", "şurada", "orada", "her", "tüm", "hep", "hiç", "bazı", "biri",
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "with",
        "is", "are", "was", "were", "be", "been", "it", "its", "of", "as", "by", "that", "this"
    )

    // Türkçe ve Latince sesli harfler
    private val VOWELS = setOf('a', 'e', 'ı', 'i', 'o', 'ö', 'u', 'ü', 'A', 'E', 'I', 'İ', 'O', 'Ö', 'U', 'Ü')

    /**
     * Verilen başlık ve içerikten kapsamlı analiz metriklerini üretir.
     */
    fun calculateMetrics(title: String, content: String): NoteMetrics {
        val fullRawText = if (title.isBlank()) content else "$title\n\n$content"
        val cleanText = stripMarkdownAndSpecialChars(fullRawText).trim()

        if (cleanText.isEmpty()) {
            return NoteMetrics(
                wordCount = 0,
                charCountWithSpaces = 0,
                charCountWithoutSpaces = 0,
                paragraphCount = 0,
                sentenceCount = 0,
                syllableCount = 0,
                uniqueWordCount = 0,
                lexicalRichnessPercent = 0,
                silentReadingTimeMinutes = 0f,
                readingTimeFormatted = "< 1 dk",
                speakingTimeMinutes = 0f,
                speakingTimeFormatted = "< 1 dk",
                readabilityScore = 100f,
                readabilityGrade = ReadabilityGrade.VERY_EASY,
                topKeywords = emptyList()
            )
        }

        val charCountWithSpaces = cleanText.length
        val charCountWithoutSpaces = cleanText.count { !it.isWhitespace() }

        // Paragraflar
        val paragraphs = cleanText.split(Regex("\n+")).filter { it.isNotBlank() }
        val paragraphCount = paragraphs.size.coerceAtLeast(1)

        // Cümleler
        val sentences = cleanText.split(Regex("[.!?…]+"))
            .map { it.trim() }
            .filter { it.length > 1 }
        val sentenceCount = sentences.size.coerceAtLeast(1)

        // Kelimeler
        val words = cleanText.split(Regex("[\\s,;:\"]+"))
            .map { cleanWord(it) }
            .filter { it.isNotBlank() }
        val wordCount = words.size

        // Hece sayısı (Sesli harf sayısı Türkçe için doğrudan hece sayısını verir)
        val syllableCount = words.sumOf { countSyllables(it) }.coerceAtLeast(wordCount)

        // Tekil kelimeler & kelime zenginliği
        val lowerWords = words.map { it.lowercase(Locale.getDefault()) }
        val uniqueWords = lowerWords.toSet()
        val uniqueWordCount = uniqueWords.size
        val lexicalRichnessPercent = if (wordCount > 0) {
            ((uniqueWordCount.toFloat() / wordCount.toFloat()) * 100f).toInt().coerceIn(1, 100)
        } else 0

        // Okuma ve konuşma süreleri
        val silentReadingMinutes = wordCount / WORDS_PER_MINUTE_READING
        val speakingMinutes = wordCount / WORDS_PER_MINUTE_SPEAKING

        val readingTimeFormatted = formatMinutes(silentReadingMinutes)
        val speakingTimeFormatted = formatMinutes(speakingMinutes)

        // Okunabilirlik skoru (Flesch Readability uyarlaması)
        val readabilityScore = calculateReadabilityScore(wordCount, sentenceCount, syllableCount)
        val readabilityGrade = classifyReadability(readabilityScore)

        // En sık kullanılan anahtar kelimeler
        val topKeywords = extractTopKeywords(lowerWords, limit = 6)

        return NoteMetrics(
            wordCount = wordCount,
            charCountWithSpaces = charCountWithSpaces,
            charCountWithoutSpaces = charCountWithoutSpaces,
            paragraphCount = paragraphCount,
            sentenceCount = sentenceCount,
            syllableCount = syllableCount,
            uniqueWordCount = uniqueWordCount,
            lexicalRichnessPercent = lexicalRichnessPercent,
            silentReadingTimeMinutes = silentReadingMinutes,
            readingTimeFormatted = readingTimeFormatted,
            speakingTimeMinutes = speakingMinutes,
            speakingTimeFormatted = speakingTimeFormatted,
            readabilityScore = readabilityScore,
            readabilityGrade = readabilityGrade,
            topKeywords = topKeywords
        )
    }

    /**
     * Kelimedeki hece sayısını hesaplar.
     */
    fun countSyllables(word: String): Int {
        var count = 0
        for (char in word) {
            if (char in VOWELS) {
                count++
            }
        }
        return count.coerceAtLeast(1)
    }

    /**
     * Markdown etiketlerini ve özel biçimlendirme simgelerini metinden temizler.
     */
    fun stripMarkdownAndSpecialChars(text: String): String {
        return text
            // Başlık işaretleri (# H1, ## H2 vb.)
            .replace(Regex("(?m)^#{1,6}\\s*"), "")
            // Blok alıntı (> quote)
            .replace(Regex("(?m)^>\\s*"), "")
            // Kod blokları (```code```)
            .replace(Regex("```[\\s\\S]*?```"), " ")
            // Satır içi kod (`code`)
            .replace(Regex("`[^`]+`"), " ")
            // Kalın ve italik (**bold**, *italic*, __bold__, _italic_)
            .replace(Regex("[*_]{1,3}"), "")
            // Üstü çizili (~~strikethrough~~)
            .replace(Regex("~~"), "")
            // Bağlantılar [text](url) -> text
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            // Görseller ![alt](url) -> boşluk
            .replace(Regex("!\\[[^\\]]*\\]\\([^)]+\\)"), " ")
            // Tablo ayracı (|---|---|)
            .replace(Regex("(?m)^\\|?\\s*[-:]+[-| :]*\\|?$"), " ")
            // Tablo hücre çizgileri (|)
            .replace("|", " ")
            // Liste işaretleri (- [ ], - [x], *, -)
            .replace(Regex("(?m)^\\s*[-*+]\\s*(\\[[ xX]\\])?\\s*"), "")
            // Numaralı listeler (1. )
            .replace(Regex("(?m)^\\s*\\d+\\.\\s*"), "")
    }

    /**
     * Kelimeyi noktalama işaretlerinden arındırır.
     */
    private fun cleanWord(raw: String): String {
        return raw.trim { !it.isLetterOrDigit() }
    }

    /**
     * Dakikayı "X dk Y sn" veya "< 1 dk" biçimine dönüştürür.
     */
    fun formatMinutes(fractionalMinutes: Float): String {
        val totalSeconds = (fractionalMinutes * 60f).toInt()
        if (totalSeconds < 40) return "< 1 dk"
        val minutes = totalSeconds / 60
        val remainingSeconds = totalSeconds % 60
        return when {
            minutes == 0 -> "$remainingSeconds sn"
            remainingSeconds == 0 -> "$minutes dk"
            else -> "$minutes dk $remainingSeconds sn"
        }
    }

    /**
     * Flesch Reading Ease uyarlaması.
     * Skor 0 ile 100 arasında normalize edilir.
     */
    private fun calculateReadabilityScore(wordCount: Int, sentenceCount: Int, syllableCount: Int): Float {
        if (wordCount == 0 || sentenceCount == 0) return 100f
        val wordsPerSentence = wordCount.toFloat() / sentenceCount.toFloat()
        val syllablesPerWord = syllableCount.toFloat() / wordCount.toFloat()

        // Flesch Reading Ease: 206.835 - (1.015 * ASL) - (84.6 * ASW)
        val rawScore = 206.835f - (1.015f * wordsPerSentence) - (84.6f * syllablesPerWord)
        return rawScore.coerceIn(0f, 100f)
    }

    /**
     * Skoru seviye enumuna çevirir.
     */
    private fun classifyReadability(score: Float): ReadabilityGrade {
        return when {
            score >= 80f -> ReadabilityGrade.VERY_EASY
            score >= 65f -> ReadabilityGrade.EASY
            score >= 50f -> ReadabilityGrade.MODERATE
            score >= 35f -> ReadabilityGrade.CHALLENGING
            else -> ReadabilityGrade.TECHNICAL
        }
    }

    /**
     * Metindeki en sık geçen anahtar kelimeleri stop-word'leri eleyerek frekansına göre sıralar.
     */
    fun extractTopKeywords(lowerWords: List<String>, limit: Int = 6): List<KeywordFrequency> {
        val frequencies = mutableMapOf<String, Int>()
        for (w in lowerWords) {
            if (w.length >= 3 && !STOP_WORDS.contains(w) && w.all { it.isLetter() }) {
                frequencies[w] = (frequencies[w] ?: 0) + 1
            }
        }

        return frequencies.entries
            .filter { it.value >= 1 }
            .sortedByDescending { it.value }
            .take(limit)
            .map { KeywordFrequency(it.key, it.value) }
    }
}
