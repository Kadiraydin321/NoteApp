package com.example.noteapp.domain.util

data class FindMatch(
    val index: Int,
    val length: Int
)

data class FindReplaceResult(
    val newContent: String,
    val count: Int,
    val newSelectionIndex: Int
)

object FindReplaceEngine {

    /**
     * Metin içinde sorgulanan kelimenin tüm başlangıç ve uzunluk indislerini bulur.
     */
    fun findAllMatches(
        content: String,
        query: String,
        matchCase: Boolean = false
    ): List<FindMatch> {
        if (query.isEmpty() || content.isEmpty()) return emptyList()

        val matches = mutableListOf<FindMatch>()
        val searchContent = if (matchCase) content else content.lowercase()
        val searchQuery = if (matchCase) query else query.lowercase()

        var startIndex = 0
        while (startIndex < searchContent.length) {
            val foundIndex = searchContent.indexOf(searchQuery, startIndex)
            if (foundIndex == -1) break

            matches.add(FindMatch(index = foundIndex, length = query.length))
            startIndex = foundIndex + query.length.coerceAtLeast(1)
        }

        return matches
    }

    /**
     * Belirtilen eşleşmeyi yeni metinle ikame eder.
     */
    fun replaceCurrent(
        content: String,
        match: FindMatch,
        replacement: String
    ): FindReplaceResult {
        if (match.index < 0 || match.index + match.length > content.length) {
            return FindReplaceResult(newContent = content, count = 0, newSelectionIndex = 0)
        }

        val prefix = content.substring(0, match.index)
        val suffix = content.substring(match.index + match.length)
        val newContent = prefix + replacement + suffix
        val newSelection = prefix.length + replacement.length

        return FindReplaceResult(
            newContent = newContent,
            count = 1,
            newSelectionIndex = newSelection
        )
    }

    /**
     * Metin içindeki tüm eşleşmeleri topluca yeni metinle ikame eder.
     */
    fun replaceAll(
        content: String,
        query: String,
        replacement: String,
        matchCase: Boolean = false
    ): FindReplaceResult {
        if (query.isEmpty() || content.isEmpty()) {
            return FindReplaceResult(newContent = content, count = 0, newSelectionIndex = 0)
        }

        val matches = findAllMatches(content, query, matchCase)
        if (matches.isEmpty()) {
            return FindReplaceResult(newContent = content, count = 0, newSelectionIndex = 0)
        }

        val regexOption = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val regex = Regex(Regex.escape(query), regexOption)
        val newContent = regex.replace(content, replacement)

        return FindReplaceResult(
            newContent = newContent,
            count = matches.size,
            newSelectionIndex = 0
        )
    }
}
