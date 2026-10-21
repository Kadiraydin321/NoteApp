package com.example.noteapp

import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.util.NoteLinkGraphEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLinkGraphEngineTest {

    @Test
    fun extractWikilinksWithSnippets_extractsTargetsAndContext() {
        val content = "Bu proje [[Mimari Kararlar]] dokümanını referans alır ve [[Veritabanı]] ile iletişim kurar."

        val refs = NoteLinkGraphEngine.extractWikilinksWithSnippets(content)

        assertEquals(2, refs.size)
        assertEquals("Mimari Kararlar", refs[0].targetTitle)
        assertTrue(refs[0].contextSnippet.contains("Mimari Kararlar"))
        assertEquals("Veritabanı", refs[1].targetTitle)
        assertTrue(refs[1].contextSnippet.contains("Veritabanı"))
    }

    @Test
    fun resolveOutboundLinks_differentiatesExistingAndPhantomNotes() {
        val note1 = Note(id = 1L, title = "Ana Plan", content = "Detaylar için [[Yol Haritası]] ve [[Gelecek Vizyonu]] inceleyin.")
        val note2 = Note(id = 2L, title = "Yol Haritası", content = "İçerik...")

        val allNotes = listOf(note1, note2)

        val result = NoteLinkGraphEngine.resolveOutboundLinks(note1, allNotes)

        assertEquals(1, result.existingNotes.size)
        assertEquals(2L, result.existingNotes[0].id)
        assertEquals(1, result.phantomTitles.size)
        assertEquals("Gelecek Vizyonu", result.phantomTitles[0])
    }

    @Test
    fun findBacklinks_findsReferringNotesWithSnippets() {
        val targetNote = Note(id = 10L, title = "Android Mimarisi", content = "Genel bakış...")
        val noteA = Note(id = 11L, title = "Geliştirme Rehberi", content = "Tüm ekip [[Android Mimarisi]] kurallarına uymalıdır.")
        val noteB = Note(id = 12L, title = "Sprint Planı", content = "Burada link yok.")
        val noteC = Note(id = 13L, title = "Kod İnceleme", content = "Detay: [[android mimarisi]] dokümanı.")

        val backlinks = NoteLinkGraphEngine.findBacklinks(targetNote, listOf(targetNote, noteA, noteB, noteC))

        assertEquals(2, backlinks.size)
        val noteIds = backlinks.map { it.first.id }.toSet()
        assertTrue(noteIds.contains(11L))
        assertTrue(noteIds.contains(13L))
    }

    @Test
    fun buildGraph_createsBidirectionalAdjacencyMap() {
        val n1 = Note(id = 1L, title = "Not 1", content = "Bağlantı [[Not 2]]")
        val n2 = Note(id = 2L, title = "Not 2", content = "Bağlantı [[Not 3]]")
        val n3 = Note(id = 3L, title = "Not 3", content = "Son not.")

        val graph = NoteLinkGraphEngine.buildGraph(listOf(n1, n2, n3))

        assertEquals(3, graph.size)
        assertEquals(listOf("Not 2"), graph[1L]?.outboundTitles)
        assertEquals(setOf(1L), graph[2L]?.inboundNoteIds)
        assertEquals(listOf("Not 3"), graph[2L]?.outboundTitles)
        assertEquals(setOf(2L), graph[3L]?.inboundNoteIds)
    }

    @Test
    fun findSecondDegreeRelatedNotes_discoversIndirectConnections() {
        val n1 = Note(id = 1L, title = "A", content = "[[B]]")
        val n2 = Note(id = 2L, title = "B", content = "[[C]]")
        val n3 = Note(id = 3L, title = "C", content = "[[D]]")
        val n4 = Note(id = 4L, title = "D", content = "Son")

        val all = listOf(n1, n2, n3, n4)
        val secondDegree = NoteLinkGraphEngine.findSecondDegreeRelatedNotes(1L, all)

        // A -> B doğrudan bağlı. C ise B üzerinden 2. derece dolaylı bağlı.
        assertTrue(secondDegree.any { it.title == "C" })
    }

    @Test
    fun findShortestPath_findsBfsPathBetweenNotes() {
        val n1 = Note(id = 1L, title = "Başlangıç", content = "Geçiş: [[Ara Adım 1]]")
        val n2 = Note(id = 2L, title = "Ara Adım 1", content = "Geçiş: [[Hedef]]")
        val n3 = Note(id = 3L, title = "Hedef", content = "Vardık.")
        val n4 = Note(id = 4L, title = "İzole", content = "Yol yok.")

        val path = NoteLinkGraphEngine.findShortestPath("Başlangıç", "Hedef", listOf(n1, n2, n3, n4))

        assertNotNull(path)
        assertEquals(listOf("Başlangıç", "Ara Adım 1", "Hedef"), path)

        val noPath = NoteLinkGraphEngine.findShortestPath("Başlangıç", "İzole", listOf(n1, n2, n3, n4))
        assertNull(noPath)
    }

    @Test
    fun computeGraphStats_identifiesOrphansAndHubs() {
        val hub = Note(id = 1L, title = "Merkez Hub", content = "[[A]] [[B]] [[C]]")
        val a = Note(id = 2L, title = "A", content = "İçerik")
        val b = Note(id = 3L, title = "B", content = "İçerik")
        val c = Note(id = 4L, title = "C", content = "İçerik")
        val orphan = Note(id = 5L, title = "Yalnız Not", content = "Hiçbir link yok.")

        val stats = NoteLinkGraphEngine.computeGraphStats(listOf(hub, a, b, c, orphan))

        assertEquals(5, stats.totalNotes)
        assertEquals(3, stats.totalLinks)
        assertEquals(1, stats.orphanNotes.size)
        assertEquals(5L, stats.orphanNotes[0].id)
        assertEquals(1L, stats.hubNotes.first().first.id)
    }
}
