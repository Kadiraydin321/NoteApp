package com.example.noteapp.domain.util

/**
 * Çift köşeli parantezli WikiLink referansı modeli.
 * [[Hedef Not]], [[Hedef Not#Alt Başlık]], [[Hedef Not|Görünen Metin]] veya [[Hedef Not#Alt Başlık|Görünen Metin]]
 * formatlarını destekler.
 */
data class WikiLink(
    val raw: String,
    val targetTitle: String,
    val subSection: String? = null,
    val displayText: String,
    val range: IntRange
)

/**
 * Markdown motoru ve iki yönlü graf haritası için WikiLink ayrıştırma motoru (WikiLink Parser).
 * Obsidian, Roam Research ve Logseq uyumlu çift köşeli parantezli wikilink referanslarını ayrıştırır.
 */
object WikiLinkParser {

    /**
     * Regex deseni:
     * [[target(#section)?(|alias)?]]
     * 1. grup: Hedef Not Başlığı (hedef notun adı)
     * 2. grup (isteğe bağlı): Alt başlık / bölüm referansı (#heading)
     * 3. grup (isteğe bağlı): Görünen alternatif metin alias (|display)
     */
    val WIKILINK_REGEX = Regex("\\[\\[([^#\\|\\]\\n]+)(?:#([^\\|\\]\\n]+))?(?:\\|([^\\]\\n]+))?\\]\\]")

    /**
     * Metindeki tüm geçerli `[[...]]` wikilink referanslarını ayrıştırır.
     */
    fun extractWikiLinks(content: String): List<WikiLink> {
        if (content.isBlank() || !content.contains("[[")) return emptyList()

        val results = mutableListOf<WikiLink>()
        for (match in WIKILINK_REGEX.findAll(content)) {
            val raw = match.value
            val target = match.groupValues[1].trim()
            if (target.isBlank()) continue

            val section = match.groupValues[2].trim().ifBlank { null }
            val alias = match.groupValues[3].trim().ifBlank { null }

            val displayText = when {
                alias != null -> alias
                section != null -> "$target > $section"
                else -> target
            }

            results.add(
                WikiLink(
                    raw = raw,
                    targetTitle = target,
                    subSection = section,
                    displayText = displayText,
                    range = match.range
                )
            )
        }
        return results
    }

    /**
     * Metindeki wikilink referanslarının işaret ettiği tekil hedef not başlıklarını döndürür.
     */
    fun extractTargetTitles(content: String): List<String> {
        return extractWikiLinks(content)
            .map { it.targetTitle }
            .distinct()
    }

    /**
     * Metindeki wikilink'leri ve çevresindeki bağlam cümlelerini ayıklar (Backlink önizlemeleri için).
     */
    fun extractWikilinksWithSnippets(content: String, snippetRadius: Int = 50): List<WikilinkReference> {
        if (content.isBlank() || !content.contains("[[")) return emptyList()

        val links = extractWikiLinks(content)
        val results = mutableListOf<WikilinkReference>()

        for (link in links) {
            val startPos = link.range.first
            val endPos = link.range.last + 1

            val snippetStart = (startPos - snippetRadius).coerceAtLeast(0)
            val snippetEnd = (endPos + snippetRadius).coerceAtMost(content.length)
            var rawSnippet = content.substring(snippetStart, snippetEnd).trim()

            rawSnippet = rawSnippet.replace('\n', ' ')
            val prefix = if (snippetStart > 0) "…" else ""
            val suffix = if (snippetEnd < content.length) "…" else ""
            val context = "$prefix$rawSnippet$suffix"

            results.add(WikilinkReference(link.targetTitle, context))
        }
        return results
    }
}
