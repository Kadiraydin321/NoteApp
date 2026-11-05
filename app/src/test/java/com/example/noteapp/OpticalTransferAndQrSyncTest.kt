package com.example.noteapp

import com.example.noteapp.data.sync.OpticalTransferManager
import com.example.noteapp.data.sync.QrSyncManager
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.model.NoteHistory
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.domain.util.NoteDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.zip.CRC32

class FakeNoteRepository : NoteRepository {
    val notes = mutableListOf<Note>()
    val categories = mutableListOf<Category>()

    override fun getActiveNotes(): Flow<List<Note>> = emptyFlow()
    override suspend fun getActiveNotesList(): List<Note> = notes
    override suspend fun getPinnedNotesList(): List<Note> = emptyList()
    override fun getArchivedNotes(): Flow<List<Note>> = emptyFlow()
    override fun getTrashNotes(): Flow<List<Note>> = emptyFlow()
    override fun getNotesByCategory(categoryId: Long): Flow<List<Note>> = emptyFlow()
    override suspend fun getNoteById(id: Long): Note? = notes.find { it.id == id }
    override suspend fun getNoteByTitle(title: String): Note? = notes.find { it.title == title }
    override suspend fun getNotesLinkingTo(noteTitle: String, excludeId: Long): List<Note> = emptyList()
    override suspend fun insertNote(note: Note): Long {
        notes.add(note)
        return notes.size.toLong()
    }
    override suspend fun updateNote(note: Note) {}
    override suspend fun deleteNote(note: Note) { notes.remove(note) }
    override suspend fun emptyTrash() {}
    override suspend fun getAllNotes(): List<Note> = notes
    override suspend fun insertNotes(notes: List<Note>): List<Long> {
        this.notes.addAll(notes)
        return notes.map { it.id }
    }
    override suspend fun deleteAllNotes() { notes.clear() }
    override fun getAllCategories(): Flow<List<Category>> = emptyFlow()
    override suspend fun getAllCategoriesList(): List<Category> = categories
    override suspend fun insertCategory(category: Category): Long {
        categories.add(category)
        return categories.size.toLong()
    }
    override suspend fun insertCategories(categories: List<Category>) { this.categories.addAll(categories) }
    override suspend fun deleteAllCategories() { categories.clear() }
    override suspend fun deleteCategory(category: Category) { categories.remove(category) }
    override suspend fun moveNotesToTrash(noteIds: List<Long>, deletedAt: Long) {}
    override suspend fun cleanUpOldTrashNotes(threshold: Long) {}
    override suspend fun updateNotesCategory(noteIds: List<Long>, categoryId: Long?) {}
    override suspend fun deleteNotesPermanently(noteIds: List<Long>) {}
    override fun getHistoryForNote(noteId: Long): Flow<List<NoteHistory>> = emptyFlow()
    override suspend fun getHistoryListForNote(noteId: Long): List<NoteHistory> = emptyList()
    override suspend fun getHistoryById(historyId: Long): NoteHistory? = null
    override suspend fun insertNoteHistory(history: NoteHistory): Long = 1
    override suspend fun pruneOldHistory(noteId: Long, keepCount: Int) {}
    override suspend fun deleteHistoryForNote(noteId: Long) {}
    override suspend fun getAllNoteHistory(): List<NoteHistory> = emptyList()
    override suspend fun insertNoteHistories(histories: List<NoteHistory>) {}
    override fun getNotesByFolder(folderId: Long): Flow<List<Note>> = emptyFlow()
    override suspend fun getNotesByFolderList(folderId: Long): List<Note> = emptyList()
    override suspend fun updateNotesFolder(noteIds: List<Long>, folderId: Long?) {}
    override suspend fun getDraft(noteId: Long?): NoteDraft? = null
    override suspend fun saveDraft(noteId: Long?, title: String, content: String, timestamp: Long) {}
    override suspend fun deleteDraft(noteId: Long?) {}
    override suspend fun getAllDrafts(): List<NoteDraft> = emptyList()
    override suspend fun deleteAllDrafts() {}
}

class OpticalTransferAndQrSyncTest {

    private lateinit var qrSyncManager: QrSyncManager
    private lateinit var fakeRepo: FakeNoteRepository
    private lateinit var opticalTransferManager: OpticalTransferManager

    @Before
    fun setUp() {
        qrSyncManager = QrSyncManager()
        fakeRepo = FakeNoteRepository()
        opticalTransferManager = OpticalTransferManager(fakeRepo)
    }

    // -------------------------------------------------------------
    // QR SYNC MANAGER TESTS
    // -------------------------------------------------------------

    @Test
    fun testParseJsonSyncPayload() {
        val json = """{"type":"noteapp_sync","ip":"192.168.1.45","port":8765,"pin":"7890"}"""
        val payload = qrSyncManager.parseSyncPayload(json)
        assertNotNull(payload)
        assertEquals("192.168.1.45", payload?.ip)
        assertEquals(8765, payload?.port)
        assertEquals("7890", payload?.pin)
        assertEquals("http://192.168.1.45:8765", payload?.fullUrl)
    }

    @Test
    fun testParseUriSyncPayload() {
        val uri = "noteapp://sync?ip=10.0.0.12&port=9000&pin=1234"
        val payload = qrSyncManager.parseSyncPayload(uri)
        assertNotNull(payload)
        assertEquals("10.0.0.12", payload?.ip)
        assertEquals(9000, payload?.port)
        assertEquals("1234", payload?.pin)
    }

    @Test
    fun testParseSimpleHashSyncPayload() {
        val text = "192.168.1.100:8765#4567"
        val payload = qrSyncManager.parseSyncPayload(text)
        assertNotNull(payload)
        assertEquals("192.168.1.100", payload?.ip)
        assertEquals(8765, payload?.port)
        assertEquals("4567", payload?.pin)
    }

    @Test
    fun testParseInvalidSyncPayload() {
        assertNull(qrSyncManager.parseSyncPayload(""))
        assertNull(qrSyncManager.parseSyncPayload("hello world"))
        assertNull(qrSyncManager.parseSyncPayload("""{"other":"data"}"""))
    }

    // -------------------------------------------------------------
    // OPTICAL AIR-GAPPED TRANSFER TESTS
    // -------------------------------------------------------------

    @Test
    fun testProcessOpticalChunksAndAssemble() = runBlocking {
        // Ã–rnek bir not yedek JSON verisi oluÅŸturalÄ±m
        val originalPayload = JSONObject().apply {
            put("version", 1)
            put("type", "noteapp_backup")
            put("exportedAt", System.currentTimeMillis())
            put("notes", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("title", "Optik Gizli Not")
                    put("content", "Bu not tamamen optik kamera akÄ±ÅŸÄ±yla aktarÄ±ldÄ±!")
                    put("isPinned", true)
                    put("isArchived", false)
                    put("color", 1)
                    put("categoryName", "GÃ¼venlik")
                })
            })
            put("categories", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "GÃ¼venlik")
                    put("color", 0xFF00ACC1.toInt())
                })
            })
        }.toString()

        // CRC32 hesaplayalÄ±m
        val crc = CRC32()
        crc.update(originalPayload.toByteArray(Charsets.UTF_8))
        val expectedCrc = crc.value

        // Veriyi 3 parÃ§aya bÃ¶lelim
        val chunkSize = (originalPayload.length + 2) / 3
        val chunks = mutableListOf<String>()
        val total = 3
        for (i in 0 until total) {
            val start = i * chunkSize
            val end = (start + chunkSize).coerceAtMost(originalPayload.length)
            val slice = originalPayload.substring(start, end)
            val chunkJson = JSONObject().apply {
                put("s", i + 1)
                put("t", total)
                put("d", slice)
                put("c", expectedCrc)
            }.toString()
            chunks.add(chunkJson)
        }

        // ParÃ§alarÄ± KARIÅIK sÄ±rayla besleyelim (2, 3, sonra 1)
        var p2 = opticalTransferManager.processChunk(chunks[1]) // seq 2
        assertEquals(1, p2.receivedCount)
        assertEquals(3, p2.totalChunks)
        assertFalse(p2.isComplete)
        assertEquals(listOf(1, 3), p2.missingChunks)

        var p3 = opticalTransferManager.processChunk(chunks[2]) // seq 3
        assertEquals(2, p3.receivedCount)
        assertFalse(p3.isComplete)
        assertEquals(listOf(1), p3.missingChunks)

        var p1 = opticalTransferManager.processChunk(chunks[0]) // seq 1
        assertEquals(3, p1.receivedCount)
        assertTrue(p1.isComplete)
        assertTrue(p1.missingChunks.isEmpty())
        assertEquals(100, p1.progressPercent)

        // BirleÅŸtirme ve CRC32 doÄŸrulama
        val assembleResult = opticalTransferManager.assembleAndVerify()
        assertTrue(assembleResult.isSuccess)
        val assembledText = assembleResult.getOrThrow()
        assertEquals(originalPayload, assembledText)

        // VeritabanÄ±na aktarma
        val importResult = opticalTransferManager.importAssembledNotes(assembledText)
        assertTrue(importResult.isSuccess)
        val result = importResult.getOrThrow()
        assertEquals(1, result.notesCount)
        assertEquals(1, result.categoriesCount)

        // FakeRepo kontrolÃ¼
        assertEquals(1, fakeRepo.notes.size)
        assertEquals("Optik Gizli Not", fakeRepo.notes[0].title)
        assertTrue(fakeRepo.notes[0].isPinned)
        assertEquals(1, fakeRepo.categories.size)
        assertEquals("GÃ¼venlik", fakeRepo.categories[0].name)
    }

    @Test
    fun testOpticalTransferChecksumFailure() {
        val originalPayload = "Temsili gizli veri metni"
        val crc = CRC32()
        crc.update(originalPayload.toByteArray(Charsets.UTF_8))
        val realCrc = crc.value
        val corruptedCrc = realCrc + 9999L // HatalÄ± checksum

        val chunkJson = JSONObject().apply {
            put("s", 1)
            put("t", 1)
            put("d", originalPayload)
            put("c", corruptedCrc)
        }.toString()

        opticalTransferManager.processChunk(chunkJson)
        val result = opticalTransferManager.assembleAndVerify()

        // Checksum uyuÅŸmadÄ±ÄŸÄ±nda SecurityException fÄ±rlatÄ±lmalÄ±
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun testDesktopAppJsCrc32Compatibility() {
        // Masaüstü app.js computeCrc32 fonksiyonu ile hesaplanmış referans test vektörleri
        val testVectors = listOf(
            "123456789" to 3421780262L,
            "Not Defterim - Türkçe Karakter Testi: çğıöşü ÇĞİÖŞÜ" to 1249012273L,
            "{\"notes\":[{\"title\":\"Test Not\",\"content\":\"Optik Akış Testi\"}],\"categories\":[]}" to 2420340548L
        )

        val crc = CRC32()
        for ((input, expectedJsCrc) in testVectors) {
            crc.reset()
            crc.update(input.toByteArray(Charsets.UTF_8))
            val javaCrc = crc.value
            assertEquals("Dize için Java CRC32 ile Masaüstü computeCrc32 eşit olmalı: $input", expectedJsCrc, javaCrc)
        }

        // app.js startOpticalStream tarafından üretilen gerçek çerçeve simülasyonu
        val desktopChunkPayload = "{\"notes\":[{\"title\":\"Test Not\",\"content\":\"Optik Akış Testi\"}],\"categories\":[]}"
        val totalCrc = 2420340548L
        val frameJson = """{"s":1,"t":1,"d":"${desktopChunkPayload.replace("\"", "\\\"")}","c":$totalCrc}"""

        val parsed = opticalTransferManager.parseChunk(frameJson)
        assertNotNull(parsed)
        assertEquals(1, parsed?.sequence)
        assertEquals(1, parsed?.total)
        assertEquals(totalCrc, parsed?.checksum)
        assertEquals(desktopChunkPayload, parsed?.data)

        opticalTransferManager.reset()
        val progress = opticalTransferManager.processChunk(frameJson)
        assertTrue(progress.isComplete)
        val verifyResult = opticalTransferManager.assembleAndVerify()
        assertTrue(verifyResult.isSuccess)
        assertEquals(desktopChunkPayload, verifyResult.getOrNull())
    }
}
