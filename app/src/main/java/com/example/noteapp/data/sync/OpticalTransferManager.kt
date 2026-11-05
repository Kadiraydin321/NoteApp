package com.example.noteapp.data.sync

import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optik akış çerçeve formatı.
 *
 * v1 (eski, şifresiz): {"s":1,"t":10,"d":"<düz JSON dilimi>","c":crc32}
 * v2 (şifreli):        {"v":2,"i":"<aktarım id>","s":1,"t":10,"d":"<base64url dilimi>","c":crc32}
 *
 * v2 yükü: base64url(MAGIC(2) | VERSION(1) | salt(16) | iv(12) | AES-256-GCM(gzip(JSON))).
 * Anahtar, parolanın PBKDF2WithHmacSHA256 ile türetilmesiyle elde edilir; başlık GCM AAD'sine bağlanır.
 * "c" yalnızca aktarım hatalarını yakalayan bir bütünlük (CRC32) kontrolüdür, güvenlik sağlamaz.
 */
object OpticalFormat {
    const val VERSION_LEGACY = 1
    const val VERSION_ENCRYPTED = 2
    const val MAX_CHUNKS = 3000
    const val MAX_CHUNK_DATA_CHARS = 4000
    const val MIN_PASSWORD_LENGTH = 6
    const val PBKDF2_ITERATIONS = 120_000
    const val KEY_BITS = 256
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    const val GCM_TAG_BITS = 128
    const val MAX_DECOMPRESSED_BYTES = 32L * 1024 * 1024
    val MAGIC = byteArrayOf('N'.code.toByte(), 'Q'.code.toByte())
}

data class OpticalChunk(
    val sequence: Int,    // 1-indexed (s)
    val total: Int,       // total chunks (t)
    val data: String,     // chunk payload (d)
    val checksum: Long,   // IEEE 802.3 CRC32 bütünlük değeri (c)
    val version: Int = OpticalFormat.VERSION_LEGACY, // format sürümü (v)
    val transferId: String = ""                      // aktarım kimliği (i) - yalnızca v2
)

data class OpticalStreamProgress(
    val totalChunks: Int = 0,
    val receivedCount: Int = 0,
    val progressPercent: Int = 0,
    val missingChunks: List<Int> = emptyList(),
    val isComplete: Boolean = false,
    val isChecksumValid: Boolean = false,
    val lastReceivedSequence: Int = 0,
    val errorMessage: String? = null,
    val isEncrypted: Boolean = false
)

/** Gönderici tarafı: şifrelenmiş ve kare kare bölünmüş aktarım paketi. */
data class OpticalTransferPackage(
    val envelope: String,
    val transferId: String,
    val checksum: Long,
    val noteCount: Int,
    val categoryCount: Int,
    val skippedLockedCount: Int,
    val payloadBytes: Int
) {
    fun frames(chunkChars: Int): List<String> {
        val size = chunkChars.coerceIn(MIN_CHUNK_CHARS, MAX_CHUNK_CHARS)
        val total = (envelope.length + size - 1) / size
        return (0 until total).map { index ->
            val start = index * size
            val slice = envelope.substring(start, minOf(start + size, envelope.length))
            JSONObject().apply {
                put("v", OpticalFormat.VERSION_ENCRYPTED)
                put("i", transferId)
                put("s", index + 1)
                put("t", total)
                put("d", slice)
                put("c", checksum)
            }.toString()
        }
    }

    companion object {
        const val MIN_CHUNK_CHARS = 120
        const val MAX_CHUNK_CHARS = 1200
    }
}

/** Alıcı tarafı: içe aktarma öncesi kullanıcıya gösterilecek özet. */
data class OpticalImportSummary(
    val notesCount: Int,
    val categoriesCount: Int,
    val payloadBytes: Int
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
    private var streamVersion: Int = OpticalFormat.VERSION_LEGACY
    private var streamTransferId: String = ""

    /** Alınan akış şifreli (v2) ise true. */
    @get:Synchronized
    val isEncryptedStream: Boolean
        get() = streamVersion >= OpticalFormat.VERSION_ENCRYPTED

    /**
     * Önceki aktarım oturumunu sıfırlar.
     */
    @Synchronized
    fun reset() {
        receivedChunks.clear()
        totalChunksExpected = 0
        expectedChecksum = 0L
        streamVersion = OpticalFormat.VERSION_LEGACY
        streamTransferId = ""
    }

    /**
     * QR koddan okunan ham JSON dizesini parse eder; geçersiz veya desteklenmeyen kareler için null döner.
     */
    fun parseChunk(rawText: String): OpticalChunk? {
        val trimmed = rawText.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null

        return try {
            val json = JSONObject(trimmed)
            if (!(json.has("s") && json.has("t") && json.has("d"))) return null
            val version = json.optInt("v", OpticalFormat.VERSION_LEGACY)
            if (version != OpticalFormat.VERSION_LEGACY && version != OpticalFormat.VERSION_ENCRYPTED) return null

            val s = json.getInt("s")
            val t = json.getInt("t")
            val d = json.getString("d")
            val c = json.optLong("c", 0L)
            val id = json.optString("i", "")
            if (t !in 1..OpticalFormat.MAX_CHUNKS || s !in 1..t) return null
            if (d.length > OpticalFormat.MAX_CHUNK_DATA_CHARS) return null
            if (version == OpticalFormat.VERSION_ENCRYPTED && (id.isBlank() || id.length > 32 || c == 0L)) return null

            OpticalChunk(sequence = s, total = t, data = d, checksum = c, version = version, transferId = id)
        } catch (_: Exception) {
            null
        }
    }

    /** Çerçeve JSON'u okunabiliyor ama bu uygulama sürümünün desteklemediği bir format sürümü taşıyorsa true. */
    fun isUnsupportedVersion(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        return try {
            val json = JSONObject(trimmed)
            json.has("s") && json.has("t") && json.has("d") &&
                json.optInt("v", OpticalFormat.VERSION_LEGACY) > OpticalFormat.VERSION_ENCRYPTED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Gelen bir QR parçasını işler ve anlık ilerleme durumunu döndürür.
     */
    @Synchronized
    fun processChunk(rawText: String): OpticalStreamProgress {
        val chunk = parseChunk(rawText)
            ?: return getProgress(
                errorMessage = if (isUnsupportedVersion(rawText)) {
                    "Bu akış daha yeni bir uygulama sürümüyle üretilmiş. Uygulamayı güncelleyin."
                } else null
            )

        // Farklı bir aktarıma (kimlik, sürüm, toplam kare veya bütünlük değeri farklı) geçildiyse sıfırla
        val isDifferentStream = totalChunksExpected != 0 && (
            chunk.total != totalChunksExpected ||
                chunk.version != streamVersion ||
                chunk.transferId != streamTransferId ||
                (chunk.checksum != expectedChecksum)
            )
        if (isDifferentStream) {
            receivedChunks.clear()
        }

        totalChunksExpected = chunk.total
        expectedChecksum = chunk.checksum
        streamVersion = chunk.version
        streamTransferId = chunk.transferId
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
            errorMessage = null,
            isEncrypted = isEncryptedStream
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
            errorMessage = errorMessage,
            isEncrypted = isEncryptedStream
        )
    }

    /**
     * Alınan tüm parçaları sırayla birleştirir ve CRC32 bütünlük kontrolünü yapar.
     * Sonuç v2 akışında şifreli zarf (base64url), v1 akışında düz JSON metnidir; hiçbiri otomatik içe aktarılmaz.
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

        // IEEE 802.3 CRC32: yalnızca aktarım/okuma hatalarını yakalayan bütünlük kontrolü (kimlik doğrulama değildir)
        if (expectedChecksum != 0L) {
            val calculated = crc32Of(assembled)

            if (calculated != expectedChecksum) {
                return Result.failure(
                    SecurityException("Veri bütünlüğü kontrolü başarısız! (Beklenen CRC: $expectedChecksum, Hesaplanan CRC: $calculated)")
                )
            }
        }

        return Result.success(assembled)
    }

    // ------------------------------------------------------------------
    // GÖNDERİCİ: notları şifreleyip animasyonlu QR karelerine hazırlar
    // ------------------------------------------------------------------

    /**
     * Cihazdaki (silinmemiş, kilitsiz) notları parola ile AES-256-GCM şifreleyerek aktarım paketine dönüştürür.
     * Kilitli notlar içerikleri kasa anahtarıyla saklandığı için aktarıma dahil edilmez.
     */
    suspend fun prepareTransfer(password: CharArray): Result<OpticalTransferPackage> = withContext(Dispatchers.Default) {
        try {
            if (password.size < OpticalFormat.MIN_PASSWORD_LENGTH) {
                return@withContext Result.failure(
                    IllegalArgumentException("Parola en az ${OpticalFormat.MIN_PASSWORD_LENGTH} karakter olmalıdır.")
                )
            }
            val allNotes = noteRepository.getAllNotes().filter { !it.isDeleted }
            val sendable = allNotes.filter { !it.isLocked }
            val skippedLocked = allNotes.size - sendable.size
            if (sendable.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Aktarılacak not bulunamadı."))
            }
            val categories = noteRepository.getAllCategoriesList()
            val categoryNames = categories.associate { it.id to it.name }

            val json = JSONObject().apply {
                put("version", 1)
                put("type", "noteapp_backup")
                put("exportedAt", System.currentTimeMillis())
                put("notes", JSONArray().apply {
                    sendable.forEach { note ->
                        put(JSONObject().apply {
                            put("title", note.title)
                            put("content", note.content)
                            put("isPinned", note.isPinned)
                            put("isArchived", note.isArchived)
                            put("color", note.color)
                            put("categoryName", note.categoryId?.let { categoryNames[it] } ?: "")
                            if (note.reminderTime != null) put("reminderTime", note.reminderTime)
                            put("createdAt", note.createdAt)
                            put("updatedAt", note.updatedAt)
                        })
                    }
                })
                put("categories", JSONArray().apply {
                    categories.forEach { category ->
                        put(JSONObject().apply {
                            put("name", category.name)
                            put("color", category.color)
                        })
                    }
                })
            }.toString()

            val payloadBytes = json.toByteArray(Charsets.UTF_8).size
            val envelope = encryptPayload(json, password)
            Result.success(
                OpticalTransferPackage(
                    envelope = envelope,
                    transferId = randomTransferId(),
                    checksum = crc32Of(envelope),
                    noteCount = sendable.size,
                    categoryCount = categories.size,
                    skippedLockedCount = skippedLocked,
                    payloadBytes = payloadBytes
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("Aktarım paketi hazırlanamadı: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }

    /** JSON → gzip → AES-256-GCM → base64url zarf. */
    fun encryptPayload(json: String, password: CharArray): String {
        val random = SecureRandom()
        val salt = ByteArray(OpticalFormat.SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(OpticalFormat.IV_BYTES).also(random::nextBytes)
        val header = OpticalFormat.MAGIC + byteArrayOf(OpticalFormat.VERSION_ENCRYPTED.toByte())

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(OpticalFormat.GCM_TAG_BITS, iv))
        cipher.updateAAD(header)
        val ciphertext = cipher.doFinal(gzip(json.toByteArray(Charsets.UTF_8)))

        val envelope = header + salt + iv + ciphertext
        return Base64.getUrlEncoder().withoutPadding().encodeToString(envelope)
    }

    // ------------------------------------------------------------------
    // ALICI: zarfı çözer, özet çıkarır, kullanıcı onayından sonra içe aktarır
    // ------------------------------------------------------------------

    /** v2 zarfını parola ile çözer. Yanlış parola veya bozuk veri için başarısız sonuç döner. */
    suspend fun decryptEnvelope(envelope: String, password: CharArray): Result<String> = withContext(Dispatchers.Default) {
        try {
            val bytes = Base64.getUrlDecoder().decode(envelope)
            val headerSize = OpticalFormat.MAGIC.size + 1
            val minSize = headerSize + OpticalFormat.SALT_BYTES + OpticalFormat.IV_BYTES + OpticalFormat.GCM_TAG_BITS / 8
            if (bytes.size < minSize ||
                bytes[0] != OpticalFormat.MAGIC[0] || bytes[1] != OpticalFormat.MAGIC[1] ||
                bytes[2].toInt() != OpticalFormat.VERSION_ENCRYPTED
            ) {
                return@withContext Result.failure(IllegalArgumentException("Geçersiz veya desteklenmeyen aktarım paketi."))
            }
            val header = bytes.copyOfRange(0, headerSize)
            val salt = bytes.copyOfRange(headerSize, headerSize + OpticalFormat.SALT_BYTES)
            val ivStart = headerSize + OpticalFormat.SALT_BYTES
            val iv = bytes.copyOfRange(ivStart, ivStart + OpticalFormat.IV_BYTES)
            val ciphertext = bytes.copyOfRange(ivStart + OpticalFormat.IV_BYTES, bytes.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(OpticalFormat.GCM_TAG_BITS, iv))
            cipher.updateAAD(header)
            val plain = cipher.doFinal(ciphertext)
            Result.success(String(gunzip(plain), Charsets.UTF_8))
        } catch (_: GeneralSecurityException) {
            Result.failure(SecurityException("Parola yanlış veya veri bozuk."))
        } catch (e: Exception) {
            Result.failure(Exception("Aktarım paketi çözülemedi: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }

    /** İçe aktarmadan önce kullanıcıya gösterilecek not/kategori sayısı ve boyutu. */
    fun summarize(jsonString: String): Result<OpticalImportSummary> = try {
        val root = JSONObject(jsonString)
        val notes = root.optJSONArray("notes")
            ?: throw IllegalArgumentException("Geçersiz yedek formatı: 'notes' dizisi bulunamadı.")
        Result.success(
            OpticalImportSummary(
                notesCount = notes.length(),
                categoriesCount = root.optJSONArray("categories")?.length() ?: 0,
                payloadBytes = jsonString.toByteArray(Charsets.UTF_8).size
            )
        )
    } catch (e: Exception) {
        Result.failure(Exception("Aktarım verisi okunamadı: ${e.localizedMessage ?: e.javaClass.simpleName}"))
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, OpticalFormat.PBKDF2_ITERATIONS, OpticalFormat.KEY_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(data)).use { input ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > OpticalFormat.MAX_DECOMPRESSED_BYTES) {
                    throw IllegalStateException("Aktarım verisi izin verilen boyutu aşıyor.")
                }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun crc32Of(text: String): Long = CRC32().also { it.update(text.toByteArray(Charsets.UTF_8)) }.value

    private fun randomTransferId(): String {
        val bytes = ByteArray(6).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
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
