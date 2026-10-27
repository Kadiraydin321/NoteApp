package com.example.noteapp.domain.util

import com.example.noteapp.domain.model.Note
import java.util.LinkedList
import java.util.Locale
import java.util.Queue

/**
 * Bir metin içinden ayıklanan wikilink referansı ve cümlenin bağlam özeti.
 */
data class WikilinkReference(
    val targetTitle: String,
    val contextSnippet: String
)

/**
 * Giden bağlantıların ayrıştırılmış sonucu.
 */
data class OutboundLinksResult(
    val existingNotes: List<Note>,
    val phantomTitles: List<String>
)

/**
 * Bağlantı grafındaki bir not düğümü.
 */
data class NoteNode(
    val noteId: Long,
    val title: String,
    val outboundTitles: List<String>,
    val inboundNoteIds: Set<Long>
)

/**
 * Tüm not defterine ait graf istatistikleri.
 */
data class NoteGraphStats(
    val totalNotes: Int,
    val totalLinks: Int,
    val orphanNotes: List<Note>,
    val hubNotes: List<Pair<Note, Int>>
)

/**
 * İki Yönlü Not Bağlantı Haritası ve Graf Motoru (Bidirectional Wikilink Graph Engine).
 * Notlar arasındaki çift yönlü wikilink bağlantılarını, geri referansları (backlinks),
 * 2. derece ilişkili notları ve en kısa bağlantı yollarını (BFS) hesaplar.
 */
object NoteLinkGraphEngine {

    private val WIKILINK_REGEX = WikiLinkParser.WIKILINK_REGEX

    /**
     * Metindeki tüm `[[Not Başlığı]]` referanslarını ve her birinin geçtiği cümle bağlamını ayıklar.
     */
    fun extractWikilinksWithSnippets(content: String): List<WikilinkReference> {
        return WikiLinkParser.extractWikilinksWithSnippets(content)
    }

    /**
     * Metindeki wikilink hedef başlıklarını tekil olarak döndürür.
     */
    fun extractOutboundTitles(content: String): List<String> {
        return WikiLinkParser.extractTargetTitles(content)
    }

    /**
     * Belirli bir notun giden bağlantılarını, mevcut veritabanı notları ve henüz oluşturulmamış (hayalet)
     * başlıklar olarak ayrıştırır.
     */
    fun resolveOutboundLinks(sourceNote: Note, allNotes: List<Note>): OutboundLinksResult {
        val outboundTitles = extractOutboundTitles(sourceNote.content)
        val noteTitleMap = allNotes.associateBy { it.title.trim().lowercase(Locale.getDefault()) }

        val existing = mutableListOf<Note>()
        val phantom = mutableListOf<String>()

        for (title in outboundTitles) {
            val normalized = title.trim().lowercase(Locale.getDefault())
            val matchedNote = noteTitleMap[normalized]
            if (matchedNote != null && matchedNote.id != sourceNote.id) {
                existing.add(matchedNote)
            } else if (matchedNote == null) {
                phantom.add(title)
            }
        }

        return OutboundLinksResult(
            existingNotes = existing.distinctBy { it.id },
            phantomTitles = phantom.distinct()
        )
    }

    /**
     * Belirtilen nota referans veren (bu notun başlığını içeren) diğer tüm notları
     * ve referansın geçtiği bağlam cümlesini bulur.
     */
    fun findBacklinks(targetNote: Note, allNotes: List<Note>): List<Pair<Note, String>> {
        val targetTitle = targetNote.title.trim()
        if (targetTitle.isBlank()) return emptyList()

        val results = mutableListOf<Pair<Note, String>>()
        val searchTokenLower = "[[${targetTitle.lowercase(Locale.getDefault())}]]"

        for (otherNote in allNotes) {
            if (otherNote.id == targetNote.id) continue

            val refs = extractWikilinksWithSnippets(otherNote.content)
            for (ref in refs) {
                if (ref.targetTitle.equals(targetTitle, ignoreCase = true)) {
                    results.add(Pair(otherNote, ref.contextSnippet))
                    break // Not başına bir snippet yeterli
                }
            }
        }
        return results
    }

    /**
     * Tüm not havuzu üzerinden iki yönlü bağlantı grafını inşa eder.
     */
    fun buildGraph(allNotes: List<Note>): Map<Long, NoteNode> {
        val titleToIdMap = allNotes.associate { it.title.trim().lowercase(Locale.getDefault()) to it.id }
        val outboundMap = mutableMapOf<Long, List<String>>()
        val inboundMap = mutableMapOf<Long, MutableSet<Long>>()

        for (note in allNotes) {
            inboundMap[note.id] = mutableSetOf()
            val links = extractOutboundTitles(note.content)
            outboundMap[note.id] = links
        }

        for (sourceNote in allNotes) {
            val targets = outboundMap[sourceNote.id] ?: emptyList()
            for (targetTitle in targets) {
                val targetId = titleToIdMap[targetTitle.lowercase(Locale.getDefault())]
                if (targetId != null && targetId != sourceNote.id) {
                    inboundMap[targetId]?.add(sourceNote.id)
                }
            }
        }

        return allNotes.associate { note ->
            note.id to NoteNode(
                noteId = note.id,
                title = note.title,
                outboundTitles = outboundMap[note.id] ?: emptyList(),
                inboundNoteIds = inboundMap[note.id] ?: emptySet()
            )
        }
    }

    /**
     * İkinci derece ilişkili notları (ortak bir not üzerinden dolaylı bağlananlar) tespit eder.
     */
    fun findSecondDegreeRelatedNotes(targetNoteId: Long, allNotes: List<Note>): List<Note> {
        val graph = buildGraph(allNotes)
        val targetNode = graph[targetNoteId] ?: return emptyList()
        val allNotesMap = allNotes.associateBy { it.id }
        val titleToIdMap = allNotes.associate { it.title.trim().lowercase(Locale.getDefault()) to it.id }

        val directConnectedIds = mutableSetOf<Long>()
        directConnectedIds.add(targetNoteId)
        directConnectedIds.addAll(targetNode.inboundNoteIds)
        for (outTitle in targetNode.outboundTitles) {
            titleToIdMap[outTitle.lowercase(Locale.getDefault())]?.let { directConnectedIds.add(it) }
        }

        val secondDegreeIds = mutableSetOf<Long>()
        for (directId in directConnectedIds) {
            if (directId == targetNoteId) continue
            val directNode = graph[directId] ?: continue
            for (secondInbound in directNode.inboundNoteIds) {
                if (!directConnectedIds.contains(secondInbound)) {
                    secondDegreeIds.add(secondInbound)
                }
            }
            for (secondOutTitle in directNode.outboundTitles) {
                val sId = titleToIdMap[secondOutTitle.lowercase(Locale.getDefault())]
                if (sId != null && !directConnectedIds.contains(sId)) {
                    secondDegreeIds.add(sId)
                }
            }
        }

        return secondDegreeIds.mapNotNull { allNotesMap[it] }.take(6)
    }

    /**
     * İki not başlığı arasındaki en kısa bağlantı yolunu Genişlik Öncelikli Arama (BFS) ile bulur.
     */
    fun findShortestPath(startTitle: String, targetTitle: String, allNotes: List<Note>): List<String>? {
        if (startTitle.isBlank() || targetTitle.isBlank()) return null
        if (startTitle.equals(targetTitle, ignoreCase = true)) return listOf(startTitle)

        val noteByTitle = allNotes.associateBy { it.title.trim().lowercase(Locale.getDefault()) }
        val queue: Queue<List<String>> = LinkedList()
        val visited = mutableSetOf<String>()

        val startNorm = startTitle.trim().lowercase(Locale.getDefault())
        val targetNorm = targetTitle.trim().lowercase(Locale.getDefault())

        queue.add(listOf(startTitle))
        visited.add(startNorm)

        while (queue.isNotEmpty()) {
            val currentPath = queue.poll() ?: break
            val currentLast = currentPath.last().trim().lowercase(Locale.getDefault())

            if (currentLast == targetNorm) {
                return currentPath
            }

            val note = noteByTitle[currentLast] ?: continue
            val outbound = extractOutboundTitles(note.content)

            for (nextTitle in outbound) {
                val nextNorm = nextTitle.trim().lowercase(Locale.getDefault())
                if (!visited.contains(nextNorm)) {
                    visited.add(nextNorm)
                    val newPath = ArrayList(currentPath)
                    newPath.add(nextTitle)
                    if (nextNorm == targetNorm) {
                        return newPath
                    }
                    queue.add(newPath)
                }
            }
        }
        return null
    }

    /**
     * Genel not defterine ait bağlantı haritası istatistiklerini hesaplar.
     */
    fun computeGraphStats(allNotes: List<Note>): NoteGraphStats {
        val graph = buildGraph(allNotes)
        val allNotesMap = allNotes.associateBy { it.id }

        var totalLinks = 0
        val orphanList = mutableListOf<Note>()
        val hubList = mutableListOf<Pair<Note, Int>>()

        for ((id, node) in graph) {
            val note = allNotesMap[id] ?: continue
            val degree = node.outboundTitles.size + node.inboundNoteIds.size
            totalLinks += node.outboundTitles.size

            if (degree == 0) {
                orphanList.add(note)
            } else {
                hubList.add(Pair(note, degree))
            }
        }

        return NoteGraphStats(
            totalNotes = allNotes.size,
            totalLinks = totalLinks,
            orphanNotes = orphanList,
            hubNotes = hubList.sortedByDescending { it.second }.take(5)
        )
    }
}
