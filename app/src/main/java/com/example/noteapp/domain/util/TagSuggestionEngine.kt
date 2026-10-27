package com.example.noteapp.domain.util

data class ActiveTagQuery(
    val query: String,
    val startIndex: Int,
    val endIndex: Int
)

data class ActiveWikilinkQuery(
    val query: String,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * Not içeriğinde dinamik #etiket tespiti, otomatik tamamlama ve toplu refaktör işlemlerini
 * gerçekleştiren akıllı etiket motoru.
 */
object TagSuggestionEngine {

    /**
     * İmlecin hemen solunda aktif bir '#etiket' girişi olup olmadığını tespit eder.
     * Örneğin: "Toplantı #pr|" -> ActiveTagQuery(query = "pr", startIndex = 9, endIndex = 12)
     * "Not #" -> ActiveTagQuery(query = "", startIndex = 4, endIndex = 5)
     */
    fun findActiveTagQuery(text: String, cursorPosition: Int): ActiveTagQuery? {
        if (cursorPosition <= 0 || cursorPosition > text.length) return null

        val sub = text.substring(0, cursorPosition)
        val lastHashIndex = sub.lastIndexOf('#')
        if (lastHashIndex == -1) return null

        // '#' öncesi ya metin başlangıcı ya da boşluk/satır başı olmalı
        if (lastHashIndex > 0) {
            val charBeforeHash = sub[lastHashIndex - 1]
            if (!charBeforeHash.isWhitespace()) {
                return null
            }
        }

        val tagPart = sub.substring(lastHashIndex + 1)
        // Eğer '#' sonrasında boşluk, noktalama vb. varsa etiket yazılmıyordur
        if (tagPart.any { it.isWhitespace() || it in ",;.!?/\\()[]{}\"" }) {
            return null
        }

        return ActiveTagQuery(
            query = tagPart,
            startIndex = lastHashIndex,
            endIndex = cursorPosition
        )
    }

    /**
     * Seçilen etiketi mevcut metne yerleştirir ve sonuna bir boşluk bırakır.
     * Eğer metinde zaten ardışık boşluk varsa çift boşluk oluşmasını önler.
     * Döndürülen çift: (yeni metin, yeni imleç konumu)
     */
    fun applyTagSuggestion(text: String, activeQuery: ActiveTagQuery, selectedTag: String): Pair<String, Int> {
        val cleanTag = selectedTag.removePrefix("#").trim()
        val safeStart = activeQuery.startIndex.coerceIn(0, text.length)
        val safeEnd = activeQuery.endIndex.coerceIn(safeStart, text.length)

        val hasSpaceAfter = text.getOrNull(safeEnd) == ' '
        val replacement = if (hasSpaceAfter) "#$cleanTag" else "#$cleanTag "

        val newText = text.substring(0, safeStart) + replacement + text.substring(safeEnd)
        val newCursor = safeStart + replacement.length + (if (hasSpaceAfter) 1 else 0)
        return newText to newCursor
    }

    /**
     * Bir metin içindeki belirli bir etiketi (#eskiAd) yeni adıyla (#yeniAd) değiştirir.
     * Regex negative lookahead sayesinde `#plan` değiştirilirken `#planlama` korunur.
     */
    fun replaceTagInText(content: String, oldTag: String, newTag: String): String {
        val cleanOld = oldTag.removePrefix("#").trim()
        val cleanNew = newTag.removePrefix("#").trim()
        if (cleanOld.isEmpty() || cleanNew.isEmpty() || cleanOld.equals(cleanNew, ignoreCase = true)) {
            return content
        }

        val pattern = Regex("#(?i)${Regex.escape(cleanOld)}(?![a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-])")
        return content.replace(pattern, "#$cleanNew")
    }

    /**
     * Bir metin içindeki belirli bir etiketi (#etiket) temizler.
     */
    fun removeTagFromText(content: String, tagToRemove: String): String {
        val cleanTag = tagToRemove.removePrefix("#").trim()
        if (cleanTag.isEmpty()) return content

        val pattern = Regex("#(?i)${Regex.escape(cleanTag)}(?![a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-])\\s*")
        return content.replace(pattern, "").trimEnd()
    }

    /**
     * İmlecin hemen solunda aktif bir '[[wikilink' girişi olup olmadığını tespit eder.
     * Örneğin: "Bkz [[Not" -> ActiveWikilinkQuery(query = "Not", startIndex = 4, endIndex = 9)
     * "Bağlantı [[" -> ActiveWikilinkQuery(query = "", startIndex = 9, endIndex = 11)
     */
    fun findActiveWikilinkQuery(text: String, cursorPosition: Int): ActiveWikilinkQuery? {
        if (cursorPosition < 2 || cursorPosition > text.length) return null

        val sub = text.substring(0, cursorPosition)
        val lastOpen = sub.lastIndexOf("[[")
        if (lastOpen == -1) return null

        val lastClose = sub.lastIndexOf("]]")
        if (lastClose != -1 && lastClose > lastOpen) return null

        if (sub.substring(lastOpen, cursorPosition).contains('\n')) return null

        val query = sub.substring(lastOpen + 2)
        return ActiveWikilinkQuery(
            query = query,
            startIndex = lastOpen,
            endIndex = cursorPosition
        )
    }

    /**
     * Seçilen not başlığını metne '[[Başlık]] ' olarak yerleştirir.
     */
    fun applyWikilinkSuggestion(
        text: String,
        activeQuery: ActiveWikilinkQuery,
        selectedTitle: String
    ): Pair<String, Int> {
        val cleanTitle = selectedTitle.removePrefix("[[").removeSuffix("]]").trim()
        val safeStart = activeQuery.startIndex.coerceIn(0, text.length)
        val safeEnd = activeQuery.endIndex.coerceIn(safeStart, text.length)

        val hasClosingAfter = text.substring(safeEnd).startsWith("]]")
        val replacement = if (hasClosingAfter) "[[$cleanTitle" else "[[$cleanTitle]] "

        val newText = text.substring(0, safeStart) + replacement + text.substring(safeEnd)
        val newCursor = safeStart + replacement.length + (if (hasClosingAfter) 2 else 0)
        return newText to newCursor
    }
}
