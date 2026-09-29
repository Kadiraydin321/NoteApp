package com.example.noteapp.media

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Görsel düzenlemeleri için geçici yedekleme ve geri alma yöneticisi.
 * Yapılan düzenlemeleri (eski görsel -> yeni görsel) belirli bir süre (örneğin 24 saat)
 * hafızada ve diskte saklar; kullanıcı yanlış bir işlem yaptığında not içerisindeki
 * görseli anında önceki veya orijinal haline geri döndürebilir.
 * Belli bir süre sonra eski geçici dosyaları otomatik olarak temizler.
 */
object ImageBackupManager {

    private const val PREFS_NAME = "image_edit_backup_prefs"
    private const val KEY_BACKUP_MAP = "backup_history_json"
    private const val BACKUP_DIR_NAME = "image_backups"

    // 24 saat sonra geçici yedek dosyalarını süresi dolmuş kabul et
    private const val EXPIRATION_TIME_MILLIS = 24 * 60 * 60 * 1000L

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getBackupDir(context: Context): File {
        val dir = File(context.filesDir, BACKUP_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Bir düzenleme yapıldığında, orijinal görseli güvenli yedek alanına kopyalar
     * ve 'yeniGörselYolu -> yedekGörselYolu' ilişkisini zaman damgasıyla kaydeder.
     */
    fun recordEditBackup(context: Context, originalPath: String, newEditedPath: String) {
        try {
            val originalFile = File(originalPath)
            if (!originalFile.exists()) return

            val backupDir = getBackupDir(context)
            val extension = originalFile.extension.ifBlank { "jpg" }
            val backupFile = File(backupDir, "BACKUP_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")

            // Orijinal dosyanın kopyasını yedek klasörüne al
            FileInputStream(originalFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output)
                }
            }

            // SharedPreferences haritasına kaydet
            val prefs = getPrefs(context)
            val currentJsonStr = prefs.getString(KEY_BACKUP_MAP, "{}") ?: "{}"
            val json = JSONObject(currentJsonStr)

            val itemObj = JSONObject().apply {
                put("backupPath", backupFile.absolutePath)
                put("originalSourcePath", originalPath)
                put("timestamp", System.currentTimeMillis())
            }

            json.put(newEditedPath, itemObj)
            prefs.edit().putString(KEY_BACKUP_MAP, json.toString()).apply()

            // Süresi dolmuş eski yedekleri arka planda temizle
            cleanupExpiredBackups(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Bu görsel için geri alınabilecek önceki bir versiyon var mı?
     */
    fun hasPreviousVersion(context: Context, currentPath: String): Boolean {
        return try {
            val prefs = getPrefs(context)
            val jsonStr = prefs.getString(KEY_BACKUP_MAP, "{}") ?: "{}"
            val json = JSONObject(jsonStr)
            if (!json.has(currentPath)) return false

            val item = json.getJSONObject(currentPath)
            val backupPath = item.optString("backupPath")
            backupPath.isNotBlank() && File(backupPath).exists()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Bu görselin önceki versiyonunun dosya yolunu döner.
     */
    fun getPreviousVersionPath(context: Context, currentPath: String): String? {
        return try {
            val prefs = getPrefs(context)
            val jsonStr = prefs.getString(KEY_BACKUP_MAP, "{}") ?: "{}"
            val json = JSONObject(jsonStr)
            if (!json.has(currentPath)) return null

            val item = json.getJSONObject(currentPath)
            val backupPath = item.optString("backupPath")
            if (File(backupPath).exists()) backupPath else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Görseli önceki versiyonuna geri döndürür.
     * Döndürülen önceki dosya yolunu verir ve haritadaki kaydı günceller.
     */
    fun revertToPreviousVersion(context: Context, currentPath: String): String? {
        return try {
            val prefs = getPrefs(context)
            val jsonStr = prefs.getString(KEY_BACKUP_MAP, "{}") ?: "{}"
            val json = JSONObject(jsonStr)
            if (!json.has(currentPath)) return null

            val item = json.getJSONObject(currentPath)
            val backupPath = item.optString("backupPath")
            val originalSourcePath = item.optString("originalSourcePath")

            val targetFile = if (File(originalSourcePath).exists()) {
                originalSourcePath
            } else if (File(backupPath).exists()) {
                backupPath
            } else {
                null
            }

            // Düzenlenen dosyayı temizle
            FileStorageHelper.deleteFile(currentPath)

            // Kaydı haritadan kaldır
            json.remove(currentPath)
            prefs.edit().putString(KEY_BACKUP_MAP, json.toString()).apply()

            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 24 saatten daha eski olan geçici yedek dosyalarını diskten ve kayıtlardan temizler.
     */
    fun cleanupExpiredBackups(context: Context) {
        try {
            val prefs = getPrefs(context)
            val jsonStr = prefs.getString(KEY_BACKUP_MAP, "{}") ?: "{}"
            val json = JSONObject(jsonStr)
            val now = System.currentTimeMillis()

            val keysToRemove = mutableListOf<String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = json.getJSONObject(key)
                val timestamp = item.optLong("timestamp", 0L)
                if (now - timestamp > EXPIRATION_TIME_MILLIS) {
                    val backupPath = item.optString("backupPath")
                    FileStorageHelper.deleteFile(backupPath)
                    keysToRemove.add(key)
                }
            }

            if (keysToRemove.isNotEmpty()) {
                keysToRemove.forEach { json.remove(it) }
                prefs.edit().putString(KEY_BACKUP_MAP, json.toString()).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
