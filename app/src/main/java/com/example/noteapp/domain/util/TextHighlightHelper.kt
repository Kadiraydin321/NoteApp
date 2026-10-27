package com.example.noteapp.domain.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import java.util.Locale

/**
 * Arama motoru için metin vurgulama yardımcısı (Text Highlight Engine).
 * Tekli arama sorgularının yanı sıra boşlukla ayrılmış çoklu tokenları ve
 * `#etiket` ifadelerini metin içinde bulup sarı/vurgulu (highlighted span) olarak stilize eder.
 */
object TextHighlightHelper {

    /**
     * Arama sorgusundaki tokenları (boşlukla ayrılmış kelimeler ve #etiket'ler) ayıklar.
     */
    fun extractTokens(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return query.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Metin içinde verilen arama sorgusunu (token bazlı, case-insensitive) bulur ve
     * bulunan tüm eşleşmelere sarı/vurgulu arka plan stili uygular.
     */
    fun highlightQuery(
        text: String,
        query: String,
        highlightColor: Color = Color(0xFFFFD54F).copy(alpha = 0.55f),
        highlightTextColor: Color = Color.Unspecified
    ): AnnotatedString {
        if (query.isBlank() || text.isBlank()) {
            return AnnotatedString(text)
        }

        val tokens = extractTokens(query)
        if (tokens.isEmpty()) {
            return AnnotatedString(text)
        }

        return highlightTokens(text, tokens, highlightColor, highlightTextColor)
    }

    /**
     * Verilen token listesindeki tüm kelime ve etiket eşleşmelerini metinde bulur ve vurgular.
     * Çakışan veya bitişik aralıkları güvenli bir şekilde birleştirir.
     */
    fun highlightTokens(
        text: String,
        tokens: List<String>,
        highlightColor: Color = Color(0xFFFFD54F).copy(alpha = 0.55f),
        highlightTextColor: Color = Color.Unspecified
    ): AnnotatedString {
        if (tokens.isEmpty() || text.isBlank()) {
            return AnnotatedString(text)
        }

        val lowerText = text.lowercase(Locale.getDefault())
        val matchedRanges = mutableListOf<IntRange>()

        for (token in tokens) {
            val cleanToken = token.trim().lowercase(Locale.getDefault())
            if (cleanToken.isEmpty()) continue

            // Eğer token '#etiket' ise hem '#etiket' hem de '#' olmaksızın 'etiket' aranır
            val searchTerms = if (cleanToken.startsWith("#") && cleanToken.length > 1) {
                listOf(cleanToken, cleanToken.removePrefix("#"))
            } else {
                listOf(cleanToken)
            }

            for (term in searchTerms) {
                var searchFrom = 0
                while (searchFrom < text.length) {
                    val matchIndex = lowerText.indexOf(term, searchFrom)
                    if (matchIndex < 0) break
                    val matchEnd = matchIndex + term.length
                    matchedRanges.add(matchIndex until matchEnd)
                    searchFrom = matchEnd
                }
            }
        }

        if (matchedRanges.isEmpty()) {
            return AnnotatedString(text)
        }

        val mergedRanges = mergeRanges(matchedRanges)

        val builder = AnnotatedString.Builder()
        var lastIndex = 0

        for (range in mergedRanges) {
            if (range.first > lastIndex) {
                builder.append(text.substring(lastIndex, range.first))
            }
            builder.withStyle(
                SpanStyle(
                    background = highlightColor,
                    color = highlightTextColor,
                    fontWeight = FontWeight.Bold
                )
            ) {
                builder.append(text.substring(range.first, range.last + 1))
            }
            lastIndex = range.last + 1
        }

        if (lastIndex < text.length) {
            builder.append(text.substring(lastIndex))
        }

        return builder.toAnnotatedString()
    }

    private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.size <= 1) return ranges
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        var current = sorted[0]

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.first <= current.last + 1) {
                current = current.first..maxOf(current.last, next.last)
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }
}
