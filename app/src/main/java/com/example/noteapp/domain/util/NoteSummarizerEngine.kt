package com.example.noteapp.domain.util

import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Not özetleme sonucu modeli.
 */
data class NoteSummaryResult(
    val executiveSummary: String,
    val keyTakeaways: List<String>,
    val actionItems: List<String>,
    val originalWordCount: Int,
    val summaryWordCount: Int,
    val readTimeSavedMinutes: Int,
    val compressionPercentage: Int
)

/**
 * Cümle puanlama modeli.
 */
private data class ScoredSentence(
    val text: String,
    val score: Double,
    val originalIndex: Int
)

/**
 * Akıllı Not Özetleyici & Anahtar Çıkarımlar Motoru.
 * Not metnini istatistiksel ve anlamsal yöntemlerle (TF-IDF benzeri frekans analizi,
 * pozisyonel ağırlıklandırma, eylem belirteçleri) cihaz üzerinde güvenli, anlık ve çevrimdışı
 * olarak özetler, anahtar çıkarımları ve yapılacak eylem maddelerini ayıklar.
 */
object NoteSummarizerEngine {

    // Türkçe ve İngilizce yaygın dolgu kelimeleri (Stopwords)
    private val STOPWORDS = setOf(
        "ve", "veya", "ile", "bir", "bu", "şu", "o", "için", "de", "da", "ki", "mi", "mu", "mü",
        "daha", "en", "çok", "gibi", "kadar", "ise", "ancak", "fakat", "lakin", "ama", "çünkü",
        "diye", "hem", "göre", "tarafından", "her", "bazı", "tüm", "bütün", "nasıl", "neden", "niçin",
        "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", "in", "on", "at", "to", "for",
        "with", "by", "from", "of", "about", "into", "through", "after", "before", "it", "this", "that"
    )

    // Eylem belirteçleri (Action item indicators)
    private val ACTION_KEYWORDS = listOf(
        "yapılmalı", "edilmeli", "tamamlanmalı", "hazırlanmalı", "gözden geçir",
        "incelenecek", "gönderilecek", "aranacak", "toplantı", "deadline", "son tarih",
        "teslim", "kontrol et", "yayınla", "düzelt", "öde", "alınacak", "yap", "et",
        "todo", "must", "should", "need to", "action", "deadline", "follow up"
    )

    // Ana çıkarım belirteçleri (Takeaway indicators)
    private val TAKEAWAY_KEYWORDS = listOf(
        "önemli", "sonuç olarak", "özetle", "kısacası", "temel nokta", "dikkat",
        "kritik", "başlıca", "kayda değer", "nihayetinde", "özellikle", "püf noktası",
        "important", "in conclusion", "summary", "key point", "crucial", "notably"
    )

    /**
     * Not metnini analiz ederek özet, ana çıkarımlar ve eylem maddeleri üretir.
     */
    fun summarize(
        content: String,
        targetSummaryRatio: Float = 0.35f,
        maxTakeaways: Int = 4,
        maxActionItems: Int = 5
    ): NoteSummaryResult {
        val cleanContent = cleanMarkdownFormatting(content)
        val allWords = cleanContent.split(Regex("\\s+")).filter { it.isNotBlank() }
        val originalWordCount = allWords.size

        if (originalWordCount < 10) {
            return NoteSummaryResult(
                executiveSummary = cleanContent,
                keyTakeaways = if (cleanContent.isNotBlank()) listOf(cleanContent) else emptyList(),
                actionItems = emptyList(),
                originalWordCount = originalWordCount,
                summaryWordCount = originalWordCount,
                readTimeSavedMinutes = 0,
                compressionPercentage = 0
            )
        }

        // 1. Cümleleri ayıkla
        val sentences = extractSentences(cleanContent)
        if (sentences.isEmpty()) {
            return NoteSummaryResult(
                executiveSummary = cleanContent,
                keyTakeaways = emptyList(),
                actionItems = emptyList(),
                originalWordCount = originalWordCount,
                summaryWordCount = originalWordCount,
                readTimeSavedMinutes = 0,
                compressionPercentage = 0
            )
        }

        // 2. Kelime frekans haritasını çıkar
        val wordFrequencies = calculateWordFrequencies(allWords)

        // 3. Cümleleri puanla
        val scoredSentences = sentences.mapIndexed { index, sentence ->
            val score = scoreSentence(sentence, index, sentences.size, wordFrequencies)
            ScoredSentence(sentence, score, index)
        }

        // 4. Yönetici Özeti (En yüksek puanlı cümleler, orijinal sırasıyla)
        val summarySentenceCount = max(1, (sentences.size * targetSummaryRatio).roundToInt())
        val topSummarySentences = scoredSentences
            .sortedByDescending { it.score }
            .take(summarySentenceCount)
            .sortedBy { it.originalIndex }
            .map { it.text }

        val executiveSummary = topSummarySentences.joinToString(" ")
        val summaryWordCount = executiveSummary.split(Regex("\\s+")).count { it.isNotBlank() }

        // 5. Ana Çıkarımlar (Key Takeaways)
        val keyTakeaways = extractKeyTakeaways(sentences, scoredSentences, maxTakeaways)

        // 6. Eylem Maddeleri (Action Items)
        val actionItems = extractActionItems(sentences, maxActionItems)

        // 7. Tasarruf edilen okuma süresi ve sıkıştırma oranı
        // Dakikada ortalama 200 kelime okuma hızı
        val wordsSaved = max(0, originalWordCount - summaryWordCount)
        val minutesSaved = (wordsSaved / 200f).roundToInt()
        val compressionPercent = if (originalWordCount > 0) {
            (((originalWordCount - summaryWordCount).toFloat() / originalWordCount.toFloat()) * 100).toInt().coerceIn(0, 95)
        } else 0

        return NoteSummaryResult(
            executiveSummary = executiveSummary,
            keyTakeaways = keyTakeaways,
            actionItems = actionItems,
            originalWordCount = originalWordCount,
            summaryWordCount = summaryWordCount,
            readTimeSavedMinutes = minutesSaved,
            compressionPercentage = compressionPercent
        )
    }

    /**
     * Cümle puanlama formülü:
     * Puan = (Kelime Frekans Puanı) + (Pozisyonel Ağırlık) + (Belirteç Ağırlığı)
     */
    private fun scoreSentence(
        sentence: String,
        index: Int,
        totalSentences: Int,
        frequencies: Map<String, Int>
    ): Double {
        val words = sentence.lowercase(Locale.ROOT)
            .replace(Regex("[^a-zA-Z0-9çğıöşüÇĞİÖŞÜ\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in STOPWORDS }

        if (words.isEmpty()) return 0.0

        var freqScore = 0.0
        words.forEach { word ->
            freqScore += frequencies[word] ?: 0
        }
        val normalizedFreqScore = freqScore / words.size.toDouble()

        // İlk cümleler ve son cümleler genellikle daha informatifdir
        var positionBonus = 0.0
        if (index == 0) positionBonus += 1.5
        else if (index == 1) positionBonus += 1.0
        else if (index == totalSentences - 1) positionBonus += 0.8

        // Ana çıkarım belirteçleri bonusu
        val lower = sentence.lowercase(Locale.ROOT)
        var indicatorBonus = 0.0
        if (TAKEAWAY_KEYWORDS.any { lower.contains(it) }) {
            indicatorBonus += 2.0
        }

        return normalizedFreqScore + positionBonus + indicatorBonus
    }

    /**
     * Metinden anlamlı anahtar çıkarımları ayıklar.
     */
    private fun extractKeyTakeaways(
        sentences: List<String>,
        scoredSentences: List<ScoredSentence>,
        maxCount: Int
    ): List<String> {
        val takeaways = mutableListOf<String>()

        // Öncelik: Belirteç içeren cümleler
        for (sentence in sentences) {
            val lower = sentence.lowercase(Locale.ROOT)
            if (TAKEAWAY_KEYWORDS.any { lower.contains(it) }) {
                if (sentence !in takeaways && sentence.length in 15..200) {
                    takeaways.add(sentence)
                    if (takeaways.size >= maxCount) break
                }
            }
        }

        // Yeterli değilse en yüksek puanlı cümlelerle tamamla
        if (takeaways.size < maxCount) {
            val sorted = scoredSentences.sortedByDescending { it.score }
            for (scored in sorted) {
                if (scored.text !in takeaways && scored.text.length in 15..200) {
                    takeaways.add(scored.text)
                    if (takeaways.size >= maxCount) break
                }
            }
        }

        return takeaways
    }

    /**
     * Eylem ve görev cümlelerini ayıklar.
     */
    private fun extractActionItems(sentences: List<String>, maxCount: Int): List<String> {
        val actions = mutableListOf<String>()

        for (sentence in sentences) {
            val lower = sentence.lowercase(Locale.ROOT)
            if (ACTION_KEYWORDS.any { lower.contains(it) }) {
                val clean = sentence.trim().removePrefix("-").removePrefix("•").trim()
                if (clean !in actions && clean.length in 10..180) {
                    actions.add(clean)
                    if (actions.size >= maxCount) break
                }
            }
        }

        return actions
    }

    /**
     * Cümle ayrıştırma: Nokta, ünlem ve soru işaretlerine göre mantıklı cümlelere böler.
     */
    fun extractSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        // Satır bazlı veya noktalama bazlı parçalama
        val rawSentences = text
            .replace("\r\n", "\n")
            .split(Regex("(?<=[.!?])\\s+|\n+"))

        return rawSentences
            .map { it.trim() }
            .filter { it.length >= 8 && it.any { c -> c.isLetter() } }
    }

    /**
     * Kelime frekans haritasını hesaplar.
     */
    private fun calculateWordFrequencies(words: List<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (w in words) {
            val clean = w.lowercase(Locale.ROOT).replace(Regex("[^a-zA-Z0-9çğıöşüÇĞİÖŞÜ]"), "")
            if (clean.length > 2 && clean !in STOPWORDS) {
                map[clean] = (map[clean] ?: 0) + 1
            }
        }
        return map
    }

    /**
     * Markdown etiket ve sembollerini temizleyerek ham metin elde eder.
     */
    fun cleanMarkdownFormatting(markdown: String): String {
        return markdown
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("(?<!\\\\)\\*\\*(.*?)(?<!\\\\)\\*\\*"), "$1")
            .replace(Regex("(?<!\\\\)__(.*?)(?<!\\\\)__"), "$1")
            .replace(Regex("~~(.*?)~~"), "$1")
            .replace(Regex("==(.*?)==\n?"), "$1")
            .replace(Regex("`{1,3}[^`]*`{1,3}"), "")
            .replace(Regex("\\[\\[([^\\]]+)\\]\\]"), "$1")
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            .replace(Regex("^>\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^[-*+]\\s+(\\[[ xX]\\])?\\s*", RegexOption.MULTILINE), "")
            .trim()
    }

    /**
     * Özeti şık bir Markdown Callout bloğuna dönüştürür.
     */
    fun formatAsCallout(result: NoteSummaryResult): String {
        val builder = StringBuilder()
        builder.append("> [!NOTE] 💡 Akıllı Not Özeti\n")
        builder.append("> ${result.executiveSummary}\n")

        if (result.keyTakeaways.isNotEmpty()) {
            builder.append(">\n")
            builder.append("> **Anahtar Noktalar:**\n")
            result.keyTakeaways.forEach { takeaway ->
                builder.append("> - $takeaway\n")
            }
        }

        if (result.actionItems.isNotEmpty()) {
            builder.append(">\n")
            builder.append("> **Eylem Maddeleri:**\n")
            result.actionItems.forEach { action ->
                builder.append("> - [ ] $action\n")
            }
        }

        return builder.toString().trim()
    }
}
