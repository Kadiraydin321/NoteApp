package com.example.noteapp.domain.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import java.util.Locale

object TextHighlightHelper {

    /**
     * Metin içinde verilen arama sorgusunu (case-insensitive) bulur ve
     * bulunan tüm eşleşmelere vurgulu arka plan stili uygular.
     */
    fun highlightQuery(
        text: String,
        query: String,
        highlightColor: Color = Color(0xFFFFD54F).copy(alpha = 0.5f),
        highlightTextColor: Color = Color.Unspecified
    ): AnnotatedString {
        if (query.isBlank() || text.isBlank()) {
            return AnnotatedString(text)
        }

        val cleanQuery = query.trim().lowercase(Locale.getDefault())
        val lowerText = text.lowercase(Locale.getDefault())

        if (cleanQuery.isEmpty() || !lowerText.contains(cleanQuery)) {
            return AnnotatedString(text)
        }

        val builder = AnnotatedString.Builder()
        var searchFrom = 0

        while (searchFrom < text.length) {
            val matchIndex = lowerText.indexOf(cleanQuery, searchFrom)
            if (matchIndex < 0) {
                builder.append(text.substring(searchFrom))
                break
            }

            if (matchIndex > searchFrom) {
                builder.append(text.substring(searchFrom, matchIndex))
            }

            val matchEnd = minOf(matchIndex + cleanQuery.length, text.length)
            builder.withStyle(
                SpanStyle(
                    background = highlightColor,
                    color = highlightTextColor,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(text.substring(matchIndex, matchEnd))
            }

            searchFrom = matchEnd
        }

        return builder.toAnnotatedString()
    }
}
