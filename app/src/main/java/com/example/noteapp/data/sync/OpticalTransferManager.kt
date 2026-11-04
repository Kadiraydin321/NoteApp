package com.example.noteapp.data.sync

import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.zip.CRC32
import javax.inject.Inject
import javax.inject.Singleton

data class OpticalChunk(
    val sequence: Int,    // 1-indexed (s)
    val total: Int,       // total chunks (t)
    val data: String,     // chunk payload (d)
    val checksum: Long    // IEEE 802.3 CRC32 checksum (c)
)

data class OpticalStreamProgress(
    val totalChunks: Int = 0,
    val receivedCount: Int = 0,
    val progressPercent: Int = 0,
    val missingChunks: List<Int> = emptyList(),
    val isComplete: Boolean = false,
    val isChecksumValid: Boolean = false,
    val lastReceivedSequence: Int = 0,
    val errorMessage: String? = null
)

data class OpticalImportResult(
    val notesCount: Int,
    val categoriesCount: Int,
    val message: String
)

@Singleton
class OpticalTransferManager @Inject constructor(
    private val noteRepository: NoteRepository
) {
    private val receivedChunks = mutableMapOf<Int, String>()
    private var totalChunksExpected: Int = 0
    private var expectedChecksum: Long = 0L

    /**
     * Önceki aktarım oturumunu sıfırlar.
     */
    @Synchronized
    fun reset() {
        receivedChunks.clear()
        totalChunksExpected = 0
        expectedChecksum = 0L
    }

    /**
     * QR koddan okunan ham JSON dizesini parse eder.
     * Format: {"s": 1, "t": 10, "d": "...", "c": 391823}
     */
    fun parseChunk(rawText: String): OpticalChunk? {
        val trimmed = rawText.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null

        return try {
            val json = JSONObject(trimmed)
            if (json.has("s") && json.has("t") && json.has("d")) {
                val s = json.getInt("s")
                val t = json.getInt("t")
                val d = json.getString("d")
                val c = json.optLong("c", 0L)
                OpticalChunk(sequence = s, total = t, data = d, checksum = c)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Gelen bir QR parçasını işler ve anlık ilerleme durumunu döndürür.
     */
    @Synchronized
    fun processChunk(rawText: String): OpticalStreamProgress {
        val chunk = parseChunk(rawText) ?: return getProgress(errorMessage = null)

        // Eğer yeni veya farklı bir aktarım paketi geldiyse veya toplam parça değiştiyse sıfırla
        if (totalChunksExpected != 0 && chunk.total != totalChunksExpected) {
            receivedChunks.clear()
        }

        totalChunksExpected = chunk.total
        expectedChecksum = chunk.checksum
        receivedChunks[chunk.sequence] = chunk.data

        val missing = (1..totalChunksExpected).filter { it !in receivedChunks.keys }
        val receivedCount = receivedChunks.size
        val percent = if (totalChunksExpected > 0) {
            ((receivedCount.toFloat() / totalChunksExpected) * 100).toInt().coerceIn(0, 100)
        } else 0
        val isComplete = missing.isEmpty() && receivedCount == totalChunksExpected

        return OpticalStreamProgress(
            totalChunks = totalChunksExpected,
            receivedCount = receivedCount,
            progressPercent = percent,
            missingChunks = missing,
            isComplete = isComplete,
            isChecksumValid = false,
            lastReceivedSequence = chunk.sequence,
            errorMessage = null
        )
    }

    @Synchronized
    fun getProgress(errorMessage: String? = null): OpticalStreamProgress {
        val missing = if (totalChunksExpected > 0) {
            (1..totalChunksExpected).filter { it !in receivedChunks.keys }
        } else emptyList()
        val receivedCount = receivedChunks.size
        val percent = if (totalChunksExpected > 0) {
            ((receivedCount.toFloat() / totalChunksExpected) * 100).toInt().coerceIn(0, 100)
        } else 0
        val isComplete = missing.isEmpty() && totalChunksExpected > 0 && receivedCount == totalChunksExpected

        return OpticalStreamProgress(
            totalChunks = totalChunksExpected,
            receivedCount = receivedCount,
            progressPercent = percent,
            missingChunks = missing,
            isComplete = isComplete,
            isChecksumValid = false,
            lastReceivedSequence = 0,
            errorMessage = errorMessage
        )
    }

    /**
     * Alınan tüm parçaları sırayla birleştirir ve CRC32 checksum doğrulaması yapar.
     */
    @Synchronized
    fun assembleAndVerify(): Result<String> {
        if (totalChunksExpected <= 0 || receivedChunks.size < totalChunksExpected) {
            return Result.failure(IllegalStateException("Tüm kareler henüz tamamlanmadı (${receivedChunks.size}/$totalChunksExpected)"))
        }

        val sb = StringBuilder()
        for (i in 1..totalChunksExpected) {
            val part = receivedChunks[i]
                ?: return Result.failure(IllegalStateException("Eksik kare: $i"))
            sb.append(part)
        }

        val assembled = sb.toString()

        // IEEE 802.3 CRC32 doğrulaması
        if (expectedChecksum != 0L) {
            val crc = CRC32()
            crc.update(assembled.toByteArray(Charsets.UTF_8))
            val calculated = crc.value

            if (calculated != expectedChecksum) {
                return Result.failure(
                    SecurityException("Veri bütünlüğü doğrulaması başarısız! (Beklenen CRC: $expectedChecksum, Hesaplanan CRC: $calculated)")
                )
            }
        }

        return Result.success(assembled)
    }

    /**
     * Doğrulanmış tam JSON verisini Room veritabanına aktarır.
     */
    suspend fun importAssembledNotes(jsonString: String, clearExisting: Boolean = false): Result<OpticalImportResult> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val notesArray = root.optJSONArray("notes")
            val categoriesArray = root.optJSONArray("categories")

            if (notesArray == null) {
                return@withContext Result.failure(IllegalArgumentException("Geçersiz yedek formatı: 'notes' dizisi bulunamadı."))
            }

            if (clearExisting) {
                noteRepository.deleteAllNotes()
            }

            // 1. Kategorileri içe aktar veya mevcutlarla eşleştir
            val categoryNameToId = mutableMapOf<String, Long>()
            val existingCategories = noteRepository.getAllCategoriesList()
            existingCategories.forEach { categoryNameToId[it.name.trim().lowercase()] = it.id }

            var importedCategoriesCount = 0
            if (categoriesArray != null) {
                for (i in 0 until categoriesArray.length()) {
                    val catObj = categoriesArray.getJSONObject(i)
                    val catName = catObj.optString("name", "").trim()
                    val catColor = catObj.optInt("color", 0)

                    if (catName.isNotEmpty()) {
                        val key = catName.lowercase()
                        if (!categoryNameToId.containsKey(key)) {
                            val newId = noteRepository.insertCategory(Category(name = catName, color = catColor))
                            categoryNameToId[key] = newId
                            importedCategoriesCount++
                        }
                    }
                }
            }

            // 2. Notları içe aktar
            val notesToInsert = mutableListOf<Note>()
            val now = System.currentTimeMillis()

            for (i in 0 until notesArray.length()) {
                val noteObj = notesArray.getJSONObject(i)
                val title = noteObj.optString("title", "")
                val content = noteObj.optString("content", "")
                val isPinned = noteObj.optBoolean("isPinned", false)
                val isArchived = noteObj.optBoolean("isArchived", false)
                val color = noteObj.optInt("color", 0)
                val categoryName = noteObj.optString("categoryName", "").trim()
                val reminderTime = if (noteObj.has("reminderTime") && !noteObj.isNull("reminderTime")) {
                    noteObj.optLong("reminderTime")
                } else null
                val createdAt = noteObj.optLong("createdAt", now)
                val updatedAt = noteObj.optLong("updatedAt", now)

                val assignedCatId = if (categoryName.isNotEmpty()) {
                    categoryNameToId[categoryName.lowercase()]
                } else null

                val note = Note(
                    title = title,
                    content = content,
                    timestamp = updatedAt,
                    color = color,
                    categoryId = assignedCatId,
                    isPinned = isPinned,
                    isArchived = isArchived,
                    isDeleted = false,
                    deletedAt = null,
                    isLocked = false,
                    reminderTime = reminderTime,
                    attachments = emptyList(),
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
                notesToInsert.add(note)
            }

            if (notesToInsert.isNotEmpty()) {
                noteRepository.insertNotes(notesToInsert)
            }

            Result.success(
                OpticalImportResult(
                    notesCount = notesToInsert.size,
                    categoriesCount = importedCategoriesCount,
                    message = "✓ Optik akıştan ${notesToInsert.size} not ve $importedCategoriesCount kategori başarıyla içeri aktarıldı!"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Optik veri içe aktarma hatası: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }
}
