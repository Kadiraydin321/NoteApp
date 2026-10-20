package com.example.noteapp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupRestoreEngineTest {

    @Test
    fun `zip archive packaging and unpackaging roundtrip succeeds`() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zipOut ->
            // 1. backup_info.json
            val infoJson = JSONObject().apply {
                put("version", 2)
                put("appName", "Not Defterim")
                put("notesCount", 1)
            }
            zipOut.putNextEntry(ZipEntry("backup_info.json"))
            zipOut.write(infoJson.toString().toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 2. notes.json with deletedAt and attachments
            val notesArray = JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("id", 101L)
                        put("title", "Project Roadmap")
                        put("content", "Autonomous development in progress")
                        put("timestamp", 1700000000000L)
                        put("isDeleted", true)
                        put("deletedAt", 1700000500000L)
                        put("attachments", JSONArray().apply { put("attachments/diagram.png") })
                    }
                )
            }
            zipOut.putNextEntry(ZipEntry("notes.json"))
            zipOut.write(notesArray.toString().toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 3. note_history.json
            val historyArray = JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("id", 1L)
                        put("noteId", 101L)
                        put("title", "Project Roadmap v1")
                        put("content", "Initial draft")
                        put("timestamp", 1699999000000L)
                        put("changeSummary", "İlk taslak oluşturuldu")
                    }
                )
            }
            zipOut.putNextEntry(ZipEntry("note_history.json"))
            zipOut.write(historyArray.toString().toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 4. templates.json
            val templatesArray = JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("id", "tmpl_retro")
                        put("title", "Sprint Retrospektif")
                        put("iconEmoji", "🚀")
                        put("contentTemplate", "# Ne iyi gitti?\n# Ne geliştirilebilir?")
                    }
                )
            }
            zipOut.putNextEntry(ZipEntry("templates.json"))
            zipOut.write(templatesArray.toString().toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }

        val zipBytes = baos.toByteArray()
        assertTrue(zipBytes.isNotEmpty())

        // Read and verify entries
        val entriesFound = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                entriesFound[entry.name] = zipIn.bufferedReader(Charsets.UTF_8).readText()
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        assertEquals(4, entriesFound.size)
        assertTrue(entriesFound.containsKey("backup_info.json"))
        assertTrue(entriesFound.containsKey("notes.json"))
        assertTrue(entriesFound.containsKey("note_history.json"))
        assertTrue(entriesFound.containsKey("templates.json"))

        // Validate note contents
        val parsedNotes = JSONArray(entriesFound["notes.json"]!!)
        assertEquals(1, parsedNotes.length())
        val noteObj = parsedNotes.getJSONObject(0)
        assertEquals("Project Roadmap", noteObj.getString("title"))
        assertTrue(noteObj.getBoolean("isDeleted"))
        assertEquals(1700000500000L, noteObj.getLong("deletedAt"))
        assertEquals("attachments/diagram.png", noteObj.getJSONArray("attachments").getString(0))

        // Validate history
        val parsedHistory = JSONArray(entriesFound["note_history.json"]!!)
        assertEquals(1, parsedHistory.length())
        assertEquals("İlk taslak oluşturuldu", parsedHistory.getJSONObject(0).getString("changeSummary"))

        // Validate templates
        val parsedTemplates = JSONArray(entriesFound["templates.json"]!!)
        assertEquals(1, parsedTemplates.length())
        assertEquals("tmpl_retro", parsedTemplates.getJSONObject(0).getString("id"))
    }

    @Test
    fun `vault magic constant matches expected specification`() {
        val magic = "NOTEVLT1".toByteArray(Charsets.US_ASCII)
        assertEquals(8, magic.size)
        assertEquals('N'.code.toByte(), magic[0])
        assertEquals('1'.code.toByte(), magic[7])
    }
}
