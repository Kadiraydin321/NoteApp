package com.example.noteapp.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
}

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NoteRepository
) {

    /**
     * Tüm notları, kategorileri ve medya dosyalarını belirtilen OutputStream'e ZIP olarak paketler.
     */
    suspend fun createBackup(outputStream: OutputStream): BackupResult = withContext(Dispatchers.IO) {
        try {
            val notes = repository.getAllNotes()
            val categories = repository.getAllCategoriesList()
            var attachmentsCount = 0

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                // 1. Bilgi Dosyası (backup_info.json)
                val infoJson = JSONObject().apply {
                    put("version", 1)
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

                // 3. Ekler (Görseller, Çizimler, Ses Kayıtları) ve Notlar (notes.json)
                val includedFiles = mutableSetOf<String>()
                val notesArray = JSONArray()

                for (note in notes) {
                    val relativeAttachments = JSONArray()
                    for (attPath in note.attachments) {
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
                            relativeAttachments.put(entryName)
                        }
                    }

                    val noteObj = JSONObject().apply {
                        put("id", note.id)
                        put("title", note.title)
                        put("content", note.content)
                        put("timestamp", note.timestamp)
                        put("color", note.color)
                        put("categoryId", note.categoryId ?: JSONObject.NULL)
                        put("isPinned", note.isPinned)
                        put("isArchived", note.isArchived)
                        put("isDeleted", note.isDeleted)
                        put("isLocked", note.isLocked)
                        put("reminderTime", note.reminderTime ?: JSONObject.NULL)
                        put("attachments", relativeAttachments)
                    }
                    notesArray.put(noteObj)
                }

                zipOut.putNextEntry(ZipEntry("notes.json"))
                zipOut.write(notesArray.toString(2).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            BackupResult.Success(
                notesCount = notes.size,
                attachmentsCount = attachmentsCount,
                message = "${notes.size} not ve $attachmentsCount medya dosyası başarıyla yedeklendi."
            )
        } catch (e: Exception) {
            e.printStackTrace()
            BackupResult.Error("Yedekleme oluşturulurken hata meydana geldi: ${e.localizedMessage}")
        }
    }

    /**
     * Paylaşım için önbellekte geçici bir .zip yedek dosyası oluşturur.
     */
    suspend fun createShareableBackupFile(): BackupResult = withContext(Dispatchers.IO) {
        try {
            val backupDir = File(context.cacheDir, "backup").apply { mkdirs() }
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFile = File(backupDir, "NoteApp_Yedek_$dateStr.zip")

            FileOutputStream(zipFile).use { fos ->
                val result = createBackup(fos)
                if (result is BackupResult.Success) {
                    return@withContext result.copy(zipFile = zipFile)
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
     * Oluşturulan yedek dosyasını Android Paylaş Menüsü ile paylaşmak için Intent döndürür.
     */
    fun createShareIntent(zipFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Modern Note App Yedeği")
            putExtra(Intent.EXTRA_TEXT, "Modern Note App yedek dosyası ekte yer almaktadır.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * ZIP yedek akışından verileri ayrıştırır ve veritabanına geri yükler.
     */
    suspend fun restoreBackup(inputStream: InputStream, clearExisting: Boolean = false): RestoreResult = withContext(Dispatchers.IO) {
        try {
            var notesJsonStr: String? = null
            var categoriesJsonStr: String? = null
            val extractedAttachmentMap = mutableMapOf<String, String>() // "attachments/file.png" -> absolutePath
            var attachmentsCount = 0

            // 1. ZIP İçeriğini Ayrıştır ve Ekleri Dosya Sistemine Çıkar
            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
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

                notesToInsert.add(
                    Note(
                        id = if (clearExisting) obj.optLong("id", 0L) else 0L,
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        color = obj.optInt("color", 0),
                        categoryId = if (obj.isNull("categoryId")) null else obj.optLong("categoryId"),
                        isPinned = obj.optBoolean("isPinned", false),
                        isArchived = obj.optBoolean("isArchived", false),
                        isDeleted = obj.optBoolean("isDeleted", false),
                        isLocked = obj.optBoolean("isLocked", false),
                        reminderTime = if (obj.isNull("reminderTime")) null else obj.optLong("reminderTime"),
                        attachments = resolvedAttachments
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
