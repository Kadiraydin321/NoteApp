package com.example.noteapp.data.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.widget.NotesWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

sealed class BackupResult {
    data class Success(
        val notesCount: Int,
        val attachmentsCount: Int,
        val zipFile: File? = null,
        val message: String
    ) : BackupResult()

    data class Error(val message: String) : BackupResult()
}

sealed class RestoreResult {
    data class Success(
        val notesCount: Int,
        val categoriesCount: Int,
        val attachmentsCount: Int
    ) : RestoreResult()

    data class Error(val message: String) : RestoreResult()
    object NeedsPassword : RestoreResult()
}

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NoteRepository
) {

    companion object {
        private val VAULT_MAGIC = "NOTEVLT1".toByteArray(Charsets.US_ASCII)
        private const val SALT_LEN = 16
        private const val IV_LEN = 12
        private const val HASH_LEN = 32
        private const val PBKDF2_ITERATIONS = 65536
        private const val KEY_LEN = 256
    }

    /**
     * Tüm notları, kategorileri ve medya dosyalarını bellek içi ZIP arşivi olarak oluşturur.
     */
    private suspend fun buildZipArchiveBytes(): Triple<ByteArray, Int, Int> {
        val notes = repository.getAllNotes()
        val categories = repository.getAllCategoriesList()
        var attachmentsCount = 0

        val baos = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(baos)).use { zipOut ->
            // 1. Bilgi Dosyası (backup_info.json)
            val infoJson = JSONObject().apply {
                put("version", 2)
                put("appName", "Modern Note App")
                put("timestamp", System.currentTimeMillis())
                put("exportDate", SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()))
                put("notesCount", notes.size)
                put("categoriesCount", categories.size)
            }
            zipOut.putNextEntry(ZipEntry("backup_info.json"))
            zipOut.write(infoJson.toString(2).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 2. Kategoriler (categories.json)
            val categoriesArray = JSONArray()
            for (cat in categories) {
                val catObj = JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("color", cat.color)
                }
                categoriesArray.put(catObj)
            }
            zipOut.putNextEntry(ZipEntry("categories.json"))
            zipOut.write(categoriesArray.toString(2).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 3. Ekler (Görseller, Çizimler, Ses Kayıtları, Arka Plan Görselleri) ve Notlar (notes.json)
            val includedFiles = mutableSetOf<String>()
            val notesArray = JSONArray()

            for (note in notes) {
                val relativeAttachments = JSONArray()
                val allAttachmentPaths = note.attachments.toMutableList()
                if (!note.backgroundImage.isNullOrEmpty() && !allAttachmentPaths.contains(note.backgroundImage)) {
                    allAttachmentPaths.add(note.backgroundImage)
                }

                for (attPath in allAttachmentPaths) {
                    val attFile = File(attPath)
                    if (attFile.exists() && attFile.isFile) {
                        val entryName = "attachments/${attFile.name}"
                        if (includedFiles.add(entryName)) {
                            zipOut.putNextEntry(ZipEntry(entryName))
                            attFile.inputStream().use { input ->
                                input.copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                            attachmentsCount++
                        }
                    }
                }

                for (attPath in note.attachments) {
                    val attFile = File(attPath)
                    if (attFile.exists() && attFile.isFile) {
                        relativeAttachments.put("attachments/${attFile.name}")
                    }
                }

                val relativeBgImage = if (!note.backgroundImage.isNullOrEmpty()) {
                    "attachments/${File(note.backgroundImage).name}"
                } else null

                val noteObj = JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("content", note.content)
                    put("timestamp", note.timestamp)
                    put("createdAt", if (note.createdAt != 0L) note.createdAt else note.timestamp)
                    put("updatedAt", if (note.updatedAt != 0L) note.updatedAt else note.timestamp)
                    put("color", note.color)
                    put("categoryId", note.categoryId ?: JSONObject.NULL)
                    put("isPinned", note.isPinned)
                    put("isArchived", note.isArchived)
                    put("isDeleted", note.isDeleted)
                    put("isLocked", note.isLocked)
                    put("reminderTime", note.reminderTime ?: JSONObject.NULL)
                    put("attachments", relativeAttachments)
                    put("backgroundImage", relativeBgImage ?: JSONObject.NULL)
                }
                notesArray.put(noteObj)
            }

            zipOut.putNextEntry(ZipEntry("notes.json"))
            zipOut.write(notesArray.toString(2).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }

        return Triple(baos.toByteArray(), notes.size, attachmentsCount)
    }

    /**
     * Tüm notları, kategorileri ve medya dosyalarını belirtilen OutputStream'e
     * Kasa Kilidi (AES-256-GCM + PBKDF2-SHA256) ile şifrelenmiş veya düz ZIP olarak paketler.
     */
    suspend fun createBackup(outputStream: OutputStream, vaultPassword: String? = null): BackupResult = withContext(Dispatchers.IO) {
        try {
            val (plainZipBytes, notesCount, attachmentsCount) = buildZipArchiveBytes()

            if (!vaultPassword.isNullOrBlank()) {
                // Kasa Kilidi (AES-256-GCM + PBKDF2 + SHA-256 Hash Doğrulama) ile Şifreleme
                val random = SecureRandom()
                val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
                val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }

                // Kasa kilidi doğrulama hash'i: SHA-256(salt + password)
                val md = MessageDigest.getInstance("SHA-256")
                md.update(salt)
                md.update(vaultPassword.toByteArray(Charsets.UTF_8))
                val passwordHash = md.digest()

                // AES-256 Anahtarı Türetme (PBKDF2WithHmacSHA256)
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val spec = PBEKeySpec(vaultPassword.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LEN)
                val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

                // AES-GCM Şifreleme
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
                val encryptedZip = cipher.doFinal(plainZipBytes)

                // Başlık Yazma: Magic (8B) + Salt (16B) + IV (12B) + Hash (32B) + Ciphertext
                outputStream.write(VAULT_MAGIC)
                outputStream.write(salt)
                outputStream.write(iv)
                outputStream.write(passwordHash)
                outputStream.write(encryptedZip)
                outputStream.flush()

                BackupResult.Success(
                    notesCount = notesCount,
                    attachmentsCount = attachmentsCount,
                    message = "$notesCount not ve $attachmentsCount medya dosyası Kasa Kilidi (AES-256-GCM) ile şifrelenerek yedeklendi."
                )
            } else {
                // Düz ZIP
                outputStream.write(plainZipBytes)
                outputStream.flush()

                BackupResult.Success(
                    notesCount = notesCount,
                    attachmentsCount = attachmentsCount,
                    message = "$notesCount not ve $attachmentsCount medya dosyası başarıyla yedeklendi."
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            BackupResult.Error("Yedekleme oluşturulurken hata meydana geldi: ${e.localizedMessage}")
        }
    }

    /**
     * Paylaşım için önbellekte geçici bir şifreli/şifresiz yedek dosyası oluşturur.
     */
    suspend fun createShareableBackupFile(vaultPassword: String? = null): BackupResult = withContext(Dispatchers.IO) {
        try {
            val backupDir = File(context.cacheDir, "backup").apply { mkdirs() }
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val extension = if (!vaultPassword.isNullOrBlank()) "notevault" else "zip"
            val backupFile = File(backupDir, "NoteApp_Yedek_$dateStr.$extension")

            FileOutputStream(backupFile).use { fos ->
                val result = createBackup(fos, vaultPassword)
                if (result is BackupResult.Success) {
                    return@withContext result.copy(zipFile = backupFile)
                } else {
                    return@withContext result
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            BackupResult.Error("Paylaşılabilir yedek dosyası oluşturulamadı: ${e.localizedMessage}")
        }
    }

    /**
     * Dosyanın Kasa Kilidi ile şifrelenmiş olup olmadığını hızlıca tespit eder.
     */
    suspend fun checkIsVaultEncrypted(inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val header = ByteArray(VAULT_MAGIC.size)
            val read = inputStream.read(header)
            read == VAULT_MAGIC.size && header.contentEquals(VAULT_MAGIC)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Oluşturulan yedek dosyasını Android Paylaş Menüsü ile paylaşmak için Intent döndürür.
     */
    fun createShareIntent(zipFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        return Intent(Intent.ACTION_SEND).apply {
            type = if (zipFile.name.endsWith(".notevault")) "application/octet-stream" else "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Modern Note App Güvenli Kasa Yedeği")
            putExtra(Intent.EXTRA_TEXT, "Modern Note App şifreli yedek dosyası ekte yer almaktadır.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * ZIP veya Kasa Kilidi şifreli yedek akışından verileri ayrıştırır ve veritabanına geri yükler.
     */
    suspend fun restoreBackup(
        inputStream: InputStream,
        clearExisting: Boolean = false,
        vaultPassword: String? = null
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val allBytes = inputStream.readBytes()
            val isVault = allBytes.size >= (VAULT_MAGIC.size + SALT_LEN + IV_LEN + HASH_LEN) &&
                    allBytes.copyOfRange(0, VAULT_MAGIC.size).contentEquals(VAULT_MAGIC)

            val zipBytes: ByteArray = if (isVault) {
                if (vaultPassword.isNullOrBlank()) {
                    return@withContext RestoreResult.NeedsPassword
                }

                var offset = VAULT_MAGIC.size
                val salt = allBytes.copyOfRange(offset, offset + SALT_LEN)
                offset += SALT_LEN
                val iv = allBytes.copyOfRange(offset, offset + IV_LEN)
                offset += IV_LEN
                val storedHash = allBytes.copyOfRange(offset, offset + HASH_LEN)
                offset += HASH_LEN
                val ciphertext = allBytes.copyOfRange(offset, allBytes.size)

                // 1. Kasa Şifresi / Hash Doğrulama (SHA-256)
                val md = MessageDigest.getInstance("SHA-256")
                md.update(salt)
                md.update(vaultPassword.toByteArray(Charsets.UTF_8))
                val computedHash = md.digest()

                if (!computedHash.contentEquals(storedHash)) {
                    return@withContext RestoreResult.Error("Hatalı Kasa Kilidi! Girilen şifre yedek dosyası ile eşleşmiyor.")
                }

                // 2. AES-256-GCM ile Şifre Çözme
                try {
                    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    val spec = PBEKeySpec(vaultPassword.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LEN)
                    val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val gcmSpec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
                    cipher.doFinal(ciphertext)
                } catch (e: Exception) {
                    return@withContext RestoreResult.Error("Kasa şifresi çözülemedi veya dosya bütünlüğü bozulmuş: ${e.localizedMessage}")
                }
            } else {
                allBytes
            }

            // 1. ZIP İçeriğini Ayrıştır ve Ekleri Dosya Sistemine Çıkar
            var notesJsonStr: String? = null
            var categoriesJsonStr: String? = null
            val extractedAttachmentMap = mutableMapOf<String, String>()
            var attachmentsCount = 0

            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory) {
                        when {
                            name == "notes.json" -> {
                                notesJsonStr = zipIn.bufferedReader(Charsets.UTF_8).readText()
                            }
                            name == "categories.json" -> {
                                categoriesJsonStr = zipIn.bufferedReader(Charsets.UTF_8).readText()
                            }
                            name.startsWith("attachments/") -> {
                                val fileName = File(name).name
                                val destFile = File(context.filesDir, fileName)
                                FileOutputStream(destFile).use { out ->
                                    zipIn.copyTo(out)
                                }
                                extractedAttachmentMap[name] = destFile.absolutePath
                                attachmentsCount++
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            if (notesJsonStr == null) {
                return@withContext RestoreResult.Error("Geçersiz yedek dosyası: notes.json bulunamadı.")
            }

            // 2. Mevcut Verileri Temizle (Seçildiyse)
            if (clearExisting) {
                repository.deleteAllNotes()
                repository.deleteAllCategories()
            }

            // 3. Kategorileri İçe Aktar
            val categoriesToInsert = mutableListOf<Category>()
            if (!categoriesJsonStr.isNullOrBlank()) {
                val catArray = JSONArray(categoriesJsonStr)
                for (i in 0 until catArray.length()) {
                    val obj = catArray.getJSONObject(i)
                    categoriesToInsert.add(
                        Category(
                            id = if (clearExisting) obj.optLong("id", 0L) else 0L,
                            name = obj.getString("name"),
                            color = obj.optInt("color", 0)
                        )
                    )
                }
                if (categoriesToInsert.isNotEmpty()) {
                    repository.insertCategories(categoriesToInsert)
                }
            }

            // 4. Notları İçe Aktar
            val notesToInsert = mutableListOf<Note>()
            val notesArray = JSONArray(notesJsonStr)
            for (i in 0 until notesArray.length()) {
                val obj = notesArray.getJSONObject(i)

                val attachmentsArray = obj.optJSONArray("attachments")
                val resolvedAttachments = mutableListOf<String>()
                if (attachmentsArray != null) {
                    for (j in 0 until attachmentsArray.length()) {
                        val relPath = attachmentsArray.getString(j)
                        val absPath = extractedAttachmentMap[relPath] ?: File(context.filesDir, File(relPath).name).absolutePath
                        resolvedAttachments.add(absPath)
                    }
                }

                val bgRelPath = if (obj.isNull("backgroundImage")) null else obj.optString("backgroundImage")
                val resolvedBgPath = if (bgRelPath != null) {
                    extractedAttachmentMap[bgRelPath] ?: File(context.filesDir, File(bgRelPath).name).absolutePath
                } else null

                val ts = obj.optLong("timestamp", System.currentTimeMillis())
                val cAt = obj.optLong("createdAt", ts)
                val uAt = obj.optLong("updatedAt", ts)

                notesToInsert.add(
                    Note(
                        id = if (clearExisting) obj.optLong("id", 0L) else 0L,
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        timestamp = ts,
                        createdAt = cAt,
                        updatedAt = uAt,
                        color = obj.optInt("color", 0),
                        categoryId = if (obj.isNull("categoryId")) null else obj.optLong("categoryId"),
                        isPinned = obj.optBoolean("isPinned", false),
                        isArchived = obj.optBoolean("isArchived", false),
                        isDeleted = obj.optBoolean("isDeleted", false),
                        isLocked = obj.optBoolean("isLocked", false),
                        reminderTime = if (obj.isNull("reminderTime")) null else obj.optLong("reminderTime"),
                        attachments = resolvedAttachments,
                        backgroundImage = resolvedBgPath
                    )
                )
            }

            if (notesToInsert.isNotEmpty()) {
                repository.insertNotes(notesToInsert)
            }

            // Widget'ları Anında Güncelle
            NotesWidgetProvider.updateAllWidgets(context)

            RestoreResult.Success(
                notesCount = notesToInsert.size,
                categoriesCount = categoriesToInsert.size,
                attachmentsCount = attachmentsCount
            )
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult.Error("Geri yükleme sırasında hata oluştu: ${e.localizedMessage}")
        }
    }
}
