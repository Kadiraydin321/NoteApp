package com.example.noteapp.desktop.data.backup

import com.example.noteapp.desktop.data.local.DatabaseHelper
import com.example.noteapp.desktop.data.local.DesktopNoteDao
import com.example.noteapp.desktop.domain.model.Category
import com.example.noteapp.desktop.domain.model.Note
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

sealed class BackupResult {
    data class Success(
        val notesCount: Int,
        val attachmentsCount: Int,
        val outputFile: File,
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

class DesktopBackupManager(private val dao: DesktopNoteDao) {

    companion object {
        private val VAULT_MAGIC = "NOTEVLT1".toByteArray(Charsets.US_ASCII)
        private const val SALT_LEN = 16
        private const val IV_LEN = 12
        private const val HASH_LEN = 32
        private const val PBKDF2_ITERATIONS = 65536
        private const val KEY_LEN = 256
    }

    /**
     * Tüm notları, kategorileri ve ekleri bellek içi ZIP arşivi baytları olarak hazırlar.
     */
    fun buildZipArchiveBytes(): Triple<ByteArray, Int, Int> {
        val notes = dao.getAllNotes()
        val categories = dao.getAllCategories()
        var attachmentsCount = 0

        val baos = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(baos)).use { zipOut ->
            // 1. Bilgi Dosyası (backup_info.json)
            val infoJson = JSONObject().apply {
                put("version", 2)
                put("appName", "Not Defterim Masaüstü")
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

            // 3. Ekler ve Notlar (notes.json)
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
                    put("deletedAt", note.deletedAt ?: JSONObject.NULL)
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
     * Dışa aktarma (ZIP veya Şifreli .notevault) oluşturur.
     */
    fun exportBackup(targetFile: File, vaultPassword: String? = null): BackupResult {
        return try {
            val (plainZipBytes, notesCount, attachmentsCount) = buildZipArchiveBytes()

            targetFile.outputStream().use { outputStream ->
                if (!vaultPassword.isNullOrBlank()) {
                    val random = SecureRandom()
                    val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
                    val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }

                    val md = MessageDigest.getInstance("SHA-256")
                    md.update(salt)
                    md.update(vaultPassword.toByteArray(Charsets.UTF_8))
                    val passwordHash = md.digest()

                    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    val spec = PBEKeySpec(vaultPassword.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LEN)
                    val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val gcmSpec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
                    val encryptedZip = cipher.doFinal(plainZipBytes)

                    outputStream.write(VAULT_MAGIC)
                    outputStream.write(salt)
                    outputStream.write(iv)
                    outputStream.write(passwordHash)
                    outputStream.write(encryptedZip)
                    outputStream.flush()

                    BackupResult.Success(
                        notesCount = notesCount,
                        attachmentsCount = attachmentsCount,
                        outputFile = targetFile,
                        message = "$notesCount not ve $attachmentsCount dosya Kasa Kilidi (AES-256-GCM) ile şifrelenerek yedeklendi."
                    )
                } else {
                    outputStream.write(plainZipBytes)
                    outputStream.flush()

                    BackupResult.Success(
                        notesCount = notesCount,
                        attachmentsCount = attachmentsCount,
                        outputFile = targetFile,
                        message = "$notesCount not ve $attachmentsCount dosya başarıyla yedeklendi."
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            BackupResult.Error("Yedek oluşturulurken hata: ${e.localizedMessage}")
        }
    }

    /**
     * Dosyanın Kasa Kilidi (.notevault) olup olmadığını denetler.
     */
    fun isVaultFile(file: File): Boolean {
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(VAULT_MAGIC.size)
                val read = stream.read(header)
                read == VAULT_MAGIC.size && header.contentEquals(VAULT_MAGIC)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * ZIP veya .notevault yedeğini okuyup veritabanına ve dosya sistemine aktarır.
     */
    fun importBackup(
        backupFile: File,
        clearExisting: Boolean = false,
        vaultPassword: String? = null
    ): RestoreResult {
        return try {
            val allBytes = backupFile.readBytes()
            val isVault = allBytes.size >= (VAULT_MAGIC.size + SALT_LEN + IV_LEN + HASH_LEN) &&
                    allBytes.copyOfRange(0, VAULT_MAGIC.size).contentEquals(VAULT_MAGIC)

            val zipBytes: ByteArray = if (isVault) {
                if (vaultPassword.isNullOrBlank()) {
                    return RestoreResult.NeedsPassword
                }

                var offset = VAULT_MAGIC.size
                val salt = allBytes.copyOfRange(offset, offset + SALT_LEN)
                offset += SALT_LEN
                val iv = allBytes.copyOfRange(offset, offset + IV_LEN)
                offset += IV_LEN
                val storedHash = allBytes.copyOfRange(offset, offset + HASH_LEN)
                offset += HASH_LEN
                val ciphertext = allBytes.copyOfRange(offset, allBytes.size)

                // 1. Hash Kontrolü
                val md = MessageDigest.getInstance("SHA-256")
                md.update(salt)
                md.update(vaultPassword.toByteArray(Charsets.UTF_8))
                val computedHash = md.digest()

                if (!computedHash.contentEquals(storedHash)) {
                    return RestoreResult.Error("Hatalı Kasa Şifresi! Girilen şifre bu yedek dosyası ile eşleşmiyor.")
                }

                // 2. AES-256-GCM Çözme
                try {
                    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    val spec = PBEKeySpec(vaultPassword.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LEN)
                    val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val gcmSpec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
                    cipher.doFinal(ciphertext)
                } catch (e: Exception) {
                    return RestoreResult.Error("Kasa şifresi çözülemedi: ${e.localizedMessage}")
                }
            } else {
                allBytes
            }

            var notesJsonStr: String? = null
            var categoriesJsonStr: String? = null
            val extractedAttachmentMap = mutableMapOf<String, String>()
            var attachmentsCount = 0

            val targetAttachDir = DatabaseHelper.attachmentsDir

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
                                val destFile = File(targetAttachDir, fileName)
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
                return RestoreResult.Error("Geçersiz yedek dosyası: notes.json bulunamadı.")
            }

            if (clearExisting) {
                dao.deleteAllNotes()
                dao.deleteAllCategories()
            }

            // Kategorileri içe aktar
            var categoriesCount = 0
            val oldToNewCategoryId = mutableMapOf<Long, Long>()

            if (!categoriesJsonStr.isNullOrBlank()) {
                val catArray = JSONArray(categoriesJsonStr)
                for (i in 0 until catArray.length()) {
                    val obj = catArray.getJSONObject(i)
                    val oldId = obj.optLong("id", 0L)
                    val name = obj.getString("name")
                    val color = obj.optInt("color", 0)

                    // Mevcut kategorilerde aynı isim var mı kontrol et
                    val existing = dao.getAllCategories().find { it.name.equals(name, ignoreCase = true) }
                    val newId = if (existing != null) {
                        existing.id
                    } else {
                        dao.insertCategory(Category(name = name, color = color))
                    }
                    if (oldId != 0L) {
                        oldToNewCategoryId[oldId] = newId
                    }
                    categoriesCount++
                }
            }

            // Notları içe aktar
            val notesArray = JSONArray(notesJsonStr)
            var insertedNotesCount = 0

            for (i in 0 until notesArray.length()) {
                val obj = notesArray.getJSONObject(i)

                val attachmentsArray = obj.optJSONArray("attachments")
                val resolvedAttachments = mutableListOf<String>()
                if (attachmentsArray != null) {
                    for (j in 0 until attachmentsArray.length()) {
                        val relPath = attachmentsArray.getString(j)
                        val absPath = extractedAttachmentMap[relPath] ?: File(targetAttachDir, File(relPath).name).absolutePath
                        resolvedAttachments.add(absPath)
                    }
                }

                val bgRelPath = if (obj.isNull("backgroundImage")) null else obj.optString("backgroundImage")
                val resolvedBgPath = if (bgRelPath != null) {
                    extractedAttachmentMap[bgRelPath] ?: File(targetAttachDir, File(bgRelPath).name).absolutePath
                } else null

                val origCatId = if (obj.isNull("categoryId")) null else obj.optLong("categoryId")
                val mappedCatId = if (origCatId != null) oldToNewCategoryId[origCatId] ?: origCatId else null

                val ts = obj.optLong("timestamp", System.currentTimeMillis())
                val cAt = obj.optLong("createdAt", ts)
                val uAt = obj.optLong("updatedAt", ts)

                val note = Note(
                    id = 0L, // Yeni ID atanacak
                    title = obj.optString("title", ""),
                    content = obj.optString("content", ""),
                    timestamp = ts,
                    createdAt = cAt,
                    updatedAt = uAt,
                    color = obj.optInt("color", 0),
                    categoryId = mappedCatId,
                    isPinned = obj.optBoolean("isPinned", false),
                    isArchived = obj.optBoolean("isArchived", false),
                    isDeleted = obj.optBoolean("isDeleted", false),
                    deletedAt = if (obj.isNull("deletedAt")) null else obj.optLong("deletedAt"),
                    isLocked = obj.optBoolean("isLocked", false),
                    reminderTime = if (obj.isNull("reminderTime")) null else obj.optLong("reminderTime"),
                    attachments = resolvedAttachments,
                    backgroundImage = resolvedBgPath
                )
                dao.insertNote(note)
                insertedNotesCount++
            }

            RestoreResult.Success(
                notesCount = insertedNotesCount,
                categoriesCount = categoriesCount,
                attachmentsCount = attachmentsCount
            )
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult.Error("Geri yükleme başarısız: ${e.localizedMessage}")
        }
    }
}
