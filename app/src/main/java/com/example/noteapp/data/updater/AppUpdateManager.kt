package com.example.noteapp.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.noteapp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class UpdateAvailable(
        val remoteVersionName: String,
        val remoteVersionCode: Int,
        val currentVersionName: String,
        val currentVersionCode: Int,
        val message: String,
        val releaseDate: String = ""
    ) : UpdateStatus()
    data class UpToDate(val currentVersionName: String) : UpdateStatus()
    data class Downloading(val progressPercent: Int, val downloadedBytes: Long, val totalBytes: Long) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val GITHUB_VERSION_JSON_URL = "https://raw.githubusercontent.com/Kadiraydin321/NoteApp/apk/version.json"
        private const val GITHUB_APK_RAW_URL = "https://raw.githubusercontent.com/Kadiraydin321/NoteApp/apk/NoteApp-release.apk"
        private const val GITHUB_COMMITS_API = "https://api.github.com/repos/Kadiraydin321/NoteApp/commits/apk"
        private const val APK_FILE_NAME = "NoteApp-update.apk"
    }

    /**
     * GitHub'daki sürümü yerel kurulu sürümle karşılaştırır.
     * Karar mekanizması:
     * 1) version.json içindeki versionCode > yerel versionCode ise YENİ SÜRÜM VAR.
     * 2) versionCode eşit ama remote buildTime > yerel buildTime + 1 dk ise YENİ SÜRÜM VAR.
     * 3) Aksi halde UYGULAMA GÜNCELDİR (UpToDate) - gereksiz indirme yapılmaz.
     */
    suspend fun checkForUpdates(): UpdateStatus = withContext(Dispatchers.IO) {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val currentVersionName = BuildConfig.VERSION_NAME
        val currentBuildTime = BuildConfig.BUILD_TIME

        // 1. Öncelik: version.json dosyasını raw CDN'den sorgula (Hızlı ve rate limit yok)
        try {
            val url = URL("$GITHUB_VERSION_JSON_URL?t=${System.currentTimeMillis()}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "NoteApp-Android")
                setRequestProperty("Cache-Control", "no-cache")
            }

            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val remoteVersionCode = json.optInt("versionCode", 0)
                val remoteVersionName = json.optString("versionName", "")
                val remoteBuildTime = json.optLong("buildTime", 0L)
                val releaseDate = json.optString("releaseDate", "")
                val changeLog = json.optString("changeLog", "Yeni geliştirmeler ve optimizasyonlar")

                val hasNewerVersion = remoteVersionCode > currentVersionCode ||
                        (remoteVersionCode == currentVersionCode && remoteBuildTime > currentBuildTime + 60_000L)

                return@withContext if (hasNewerVersion) {
                    UpdateStatus.UpdateAvailable(
                        remoteVersionName = remoteVersionName.ifBlank { "v$remoteVersionCode" },
                        remoteVersionCode = remoteVersionCode,
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode,
                        message = changeLog,
                        releaseDate = releaseDate
                    )
                } else {
                    UpdateStatus.UpToDate(currentVersionName)
                }
            }
        } catch (e: java.net.UnknownHostException) {
            return@withContext UpdateStatus.Error("İnternet bağlantısı kurulamadığı için güncelleme kontrol edilemedi.")
        } catch (_: Exception) {
            // version.json henüz oluşturulmamış veya ulaşılamıyorsa Commits API'ye bak
        }

        // 2. Yedek Yöntem: GitHub Commits API üzerinden son commit tarihine bak
        try {
            val url = URL("$GITHUB_COMMITS_API?t=${System.currentTimeMillis()}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "NoteApp-Android")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("Cache-Control", "no-cache")
            }

            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val commitObj = json.getJSONObject("commit")
                val commitMsg = commitObj.getString("message").lines().firstOrNull() ?: "Güncelleme mevcut"
                val dateStr = commitObj.getJSONObject("committer").optString("date", "")
                val commitMillis = parseIso8601ToMillis(dateStr)

                // Eğer commit tarihi mevcut derleme tarihinden en az 1 dakika daha yeniyse güncelleme var demektir
                val hasNewerCommit = commitMillis > 0L && commitMillis > (currentBuildTime + 60_000L)

                return@withContext if (hasNewerCommit) {
                    UpdateStatus.UpdateAvailable(
                        remoteVersionName = "Yeni Sürüm",
                        remoteVersionCode = currentVersionCode + 1,
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode,
                        message = commitMsg,
                        releaseDate = dateStr
                    )
                } else {
                    UpdateStatus.UpToDate(currentVersionName)
                }
            }
        } catch (e: Exception) {
            return@withContext UpdateStatus.Error("İnternet bağlantısı kurulamadığı için güncelleme kontrol edilemedi.")
        }

        UpdateStatus.UpToDate(currentVersionName)
    }

    private fun parseIso8601ToMillis(isoString: String): Long {
        if (isoString.isBlank()) return 0L
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(isoString)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * GitHub raw URL üzerinden NoteApp-release.apk'yı indirir.
     */
    suspend fun downloadLatestApk(
        onProgress: (Int, Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val url = URL("$GITHUB_APK_RAW_URL?t=${System.currentTimeMillis()}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NoteApp-Android")
            setRequestProperty("Cache-Control", "no-cache")
        }

        val totalLength = connection.contentLengthLong
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updateDir, APK_FILE_NAME)
        if (apkFile.exists()) {
            apkFile.delete()
        }

        connection.inputStream.use { input ->
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var totalRead = 0L
                var lastEmittedProgress = -1

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalLength > 0) {
                        val progress = ((totalRead * 100) / totalLength).toInt()
                        if (progress != lastEmittedProgress || totalRead == totalLength) {
                            lastEmittedProgress = progress
                            onProgress(progress, totalRead, totalLength)
                        }
                    } else {
                        onProgress(-1, totalRead, -1)
                    }
                }
                output.flush()
            }
        }
        apkFile
    }

    /**
     * İndirilen APK dosyasını Android Paket Yükleyicisi ile kurma niyetini (Intent) başlatır.
     */
    fun installApk(apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(installIntent)
    }
}
