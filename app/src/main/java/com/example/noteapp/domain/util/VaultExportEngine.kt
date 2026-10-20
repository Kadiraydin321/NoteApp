package com.example.noteapp.domain.util

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class VaultNote(
    val title: String,
    val content: String,
    val color: Int = 0,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isLocked: Boolean = false,
    val categoryName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class VaultPayload(
    val version: Int = 1,
    val exportTimestamp: Long = System.currentTimeMillis(),
    val notes: List<VaultNote>
)

class WrongPasswordException(message: String = "Girdiğiniz şifre hatalı.") : Exception(message)
class InvalidVaultFileException(message: String = "Geçersiz veya bozuk .notevault dosyası.") : Exception(message)

/**
 * Notları PBKDF2 türetimli AES-256-GCM ile taşınabilir .notevault kasasına şifreleyen
 * ve çözen askeri düzeyde güvenlik motoru.
 */
object VaultExportEngine {

    val MAGIC_HEADER = byteArrayOf('N'.code.toByte(), 'O'.code.toByte(), 'T'.code.toByte(), 'E'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte())
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val HASH_LEN = 32
    private const val HEADER_LEN = 8 + SALT_LEN + IV_LEN + HASH_LEN // 68 bytes
    private const val PBKDF2_ITERATIONS = 12000
    private const val KEY_LEN = 256

    /**
     * Verilen byte dizisinin geçerli bir .notevault başlığına sahip olup olmadığını doğrular.
     */
    fun isVaultFile(bytes: ByteArray): Boolean {
        if (bytes.size < HEADER_LEN) return false
        val fileMagic = bytes.copyOfRange(0, 8)
        return Arrays.equals(MAGIC_HEADER, fileMagic)
    }

    /**
     * Not listesini belirtilen parola ile şifreleyip .notevault ikili verisine dönüştürür.
     */
    fun createVault(password: String, notes: List<VaultNote>): ByteArray {
        require(password.isNotBlank()) { "Şifre boş olamaz." }

        // 1. JSON Yükünü Hazırla
        val json = JSONObject().apply {
            put("version", 1)
            put("exportTimestamp", System.currentTimeMillis())
            val notesArray = JSONArray()
            notes.forEach { note ->
                val noteObj = JSONObject().apply {
                    put("title", note.title)
                    put("content", note.content)
                    put("color", note.color)
                    put("isPinned", note.isPinned)
                    put("isArchived", note.isArchived)
                    put("isLocked", note.isLocked)
                    put("categoryName", note.categoryName ?: "")
                    put("createdAt", note.createdAt)
                    put("updatedAt", note.updatedAt)
                }
                notesArray.put(noteObj)
            }
            put("notes", notesArray)
        }

        val plainBytes = json.toString().toByteArray(StandardCharsets.UTF_8)

        // 2. Rastgele Salt ve IV Üret
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }

        // 3. Parola Doğrulama Hash'i: SHA-256(salt + password)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(password.toByteArray(StandardCharsets.UTF_8))
        val passwordHash = md.digest()

        // 4. PBKDF2-HMAC-SHA256 ile AES-256 Anahtarı Türet
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LEN)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        // 5. AES-GCM Şifreleme (128-bit kimlik doğrulama etiketi ile)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val ciphertext = cipher.doFinal(plainBytes)

        // 6. Başlık ve Şifreli Veriyi Birleştir
        val totalSize = HEADER_LEN + ciphertext.size
        val result = ByteArray(totalSize)
        var offset = 0

        System.arraycopy(MAGIC_HEADER, 0, result, offset, 8)
        offset += 8
        System.arraycopy(salt, 0, result, offset, SALT_LEN)
        offset += SALT_LEN
        System.arraycopy(iv, 0, result, offset, IV_LEN)
        offset += IV_LEN
        System.arraycopy(passwordHash, 0, result, offset, HASH_LEN)
        offset += HASH_LEN
        System.arraycopy(ciphertext, 0, result, offset, ciphertext.size)

        return result
    }

    /**
     * Şifreli .notevault ikili verisini verilen parola ile çözer ve doğrular.
     */
    fun readVault(password: String, vaultBytes: ByteArray): VaultPayload {
        if (!isVaultFile(vaultBytes)) {
            throw InvalidVaultFileException("Dosya geçerli bir .notevault formatında değil.")
        }

        var offset = 8
        val salt = vaultBytes.copyOfRange(offset, offset + SALT_LEN)
        offset += SALT_LEN
        val iv = vaultBytes.copyOfRange(offset, offset + IV_LEN)
        offset += IV_LEN
        val expectedHash = vaultBytes.copyOfRange(offset, offset + HASH_LEN)
        offset += HASH_LEN
        val ciphertext = vaultBytes.copyOfRange(offset, vaultBytes.size)

        // 1. Parola Doğrulama Hash'i Kontrolü
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(password.toByteArray(StandardCharsets.UTF_8))
        val computedHash = md.digest()

        if (!Arrays.equals(expectedHash, computedHash)) {
            throw WrongPasswordException("Girdiğiniz şifre hatalı. Not kasası açılamadı.")
        }

        // 2. AES-256 Anahtarını Türet
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LEN)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        // 3. AES-GCM Çözme
        val plainBytes = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw WrongPasswordException("Şifre çözme hatası veya bozuk dosya.")
        }

        // 4. JSON Ayrıştırma
        return try {
            val jsonStr = String(plainBytes, StandardCharsets.UTF_8)
            val json = JSONObject(jsonStr)
            val version = json.optInt("version", 1)
            val exportTimestamp = json.optLong("exportTimestamp", System.currentTimeMillis())
            val notesArray = json.getJSONArray("notes")

            val notes = mutableListOf<VaultNote>()
            for (i in 0 until notesArray.length()) {
                val obj = notesArray.getJSONObject(i)
                notes.add(
                    VaultNote(
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        color = obj.optInt("color", 0),
                        isPinned = obj.optBoolean("isPinned", false),
                        isArchived = obj.optBoolean("isArchived", false),
                        isLocked = obj.optBoolean("isLocked", false),
                        categoryName = obj.optString("categoryName", "").ifBlank { null },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            VaultPayload(
                version = version,
                exportTimestamp = exportTimestamp,
                notes = notes
            )
        } catch (e: Exception) {
            throw InvalidVaultFileException("Kasa içeriği bozuk veya okunamıyor.")
        }
    }

    /**
     * .notevault verisini Cihazın İndirilenler/NotDefterim klasörüne veya önbelleğe kaydeder.
     */
    fun saveVaultToDownloads(context: Context, vaultBytes: ByteArray, suggestedFileName: String): File {
        val sanitized = suggestedFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = if (sanitized.endsWith(".notevault")) sanitized else "$sanitized.notevault"

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadsDir, "NotDefterim").apply { mkdirs() }
        val targetFile = File(targetDir, fileName)

        FileOutputStream(targetFile).use { it.write(vaultBytes) }
        return targetFile
    }

    /**
     * Paylaşım için önbellek klasöründe geçici bir .notevault dosyası oluşturur.
     */
    fun createShareableVaultFile(context: Context, vaultBytes: ByteArray, suggestedFileName: String): File {
        val sanitized = suggestedFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = if (sanitized.endsWith(".notevault")) sanitized else "$sanitized.notevault"

        val cacheDir = File(context.cacheDir, "vault_exports").apply { mkdirs() }
        val cacheFile = File(cacheDir, fileName)

        FileOutputStream(cacheFile).use { it.write(vaultBytes) }
        return cacheFile
    }
}
