package com.example.noteapp.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.noteapp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        private const val GITHUB_VERSION_JSON_URL = "https://raw.githubusercontent.com/Kadiraydin321/NoteApp/hermes/version.json"
        private const val GITHUB_APK_RAW_URL = "https://raw.githubusercontent.com/Kadiraydin321/NoteApp/hermes/NoteApp-release.apk"
        private const val GITHUB_COMMITS_API = "https://api.github.com/repos/Kadiraydin321/NoteApp/commits/apk"
        private const val APK_FILE_NAME = "NoteApp-update.apk"
        private const val APK_TEMP_NAME = "NoteApp-update.apk.tmp"
    }

    // Singleton uygulama kapsamlı coroutine scope: Ekranlar arası geçişte indirme ASLA kesilmez
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    // En son tespit edilen güncelleme bilgisi (indirme/yükleme sırasında da korunur)
    var lastAvailableUpdate: UpdateStatus.UpdateAvailable? = null
        private set

    // Bu oturumda hangi sürüm için popup gösterildiğini takip eder (tekrar tekrar popup açılmasını önler)
    var hasShownDialogForVersion: Int? = null
        private set

    fun markDialogShown(versionCode: Int) {
        hasShownDialogForVersion = versionCode
    }

    /**
     * Daha önce indirilmiş geçerli bir APK olup olmadığını kontrol eder.
     */
    fun getExistingDownloadedApk(targetVersionCode: Int): File? {
        val updateDir = File(context.cacheDir, "updates")
        val apkFile = File(updateDir, APK_FILE_NAME)
        if (apkFile.exists() && apkFile.length() > 500_000L) {
            try {
                val pkgInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                if (pkgInfo != null) {
                    val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkgInfo.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        pkgInfo.versionCode
                    }
                    if (archiveVersionCode == targetVersionCode) {
                        return apkFile
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * GitHub'daki sürümü yerel kurulu sürümle karşılaştırır.
     * Halihazırda indirme devam ediyorsa veya paket zaten hazırsa bu durumu bozmaz.
     */
    suspend fun checkForUpdates(force: Boolean = false): UpdateStatus = withContext(Dispatchers.IO) {
        // 1. Zaten indirme devam ediyorsa durumu koru
        if (_updateStatus.value is UpdateStatus.Downloading) {
            return@withContext _updateStatus.value
        }

        // 2. Halihazırda indirilmiş ve hazır bir APK varsa durumu koru
        val currentStatus = _updateStatus.value
        if (currentStatus is UpdateStatus.ReadyToInstall && currentStatus.apkFile.exists()) {
            return@withContext currentStatus
        }

        _updateStatus.value = UpdateStatus.Checking

        val result = performCheck()

        if (result is UpdateStatus.UpdateAvailable) {
            lastAvailableUpdate = result
            // Eğer bu sürüme ait APK zaten önceden indirilmişse doğrudan ReadyToInstall yap
            val cachedApk = getExistingDownloadedApk(result.remoteVersionCode)
            if (cachedApk != null) {
                val readyStatus = UpdateStatus.ReadyToInstall(cachedApk)
                _updateStatus.value = readyStatus
                return@withContext readyStatus
            }
        }

        _updateStatus.value = result
        return@withContext result
    }

    private suspend fun performCheck(): UpdateStatus = withContext(Dispatchers.IO) {
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
     * Singleton scope üzerinde APK'yı arka planda indirir ve bitince yükleme ekranını açar.
     * Kullanıcı Ayarlar'dan çıksa bile indirme kesilmez.
     */
    fun downloadAndInstallUpdate() {
        if (downloadJob?.isActive == true || _updateStatus.value is UpdateStatus.Downloading) {
            return
        }

        // Eğer zaten indirilmiş hazır APK varsa tekrar indirme, doğrudan kur!
        val available = lastAvailableUpdate
        if (available != null) {
            val cachedApk = getExistingDownloadedApk(available.remoteVersionCode)
            if (cachedApk != null) {
                _updateStatus.value = UpdateStatus.ReadyToInstall(cachedApk)
                installApk(cachedApk)
                return
            }
        }

        downloadJob = scope.launch {
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val tempFile = File(updateDir, APK_TEMP_NAME)
            val apkFile = File(updateDir, APK_FILE_NAME)

            if (tempFile.exists()) tempFile.delete()

            _updateStatus.value = UpdateStatus.Downloading(0, 0L, -1L)

            try {
                val url = URL("$GITHUB_APK_RAW_URL?t=${System.currentTimeMillis()}")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "NoteApp-Android")
                    setRequestProperty("Cache-Control", "no-cache")
                }

                val totalLength = connection.contentLengthLong

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
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
                                    _updateStatus.value = UpdateStatus.Downloading(progress, totalRead, totalLength)
                                }
                            } else {
                                _updateStatus.value = UpdateStatus.Downloading(-1, totalRead, -1L)
                            }
                        }
                        output.flush()
                    }
                }

                if (apkFile.exists()) apkFile.delete()
                tempFile.renameTo(apkFile)

                val readyStatus = UpdateStatus.ReadyToInstall(apkFile)
                _updateStatus.value = readyStatus

                // İndirme tamamlandı; yükleme niyetini başlat
                withContext(Dispatchers.Main) {
                    installApk(apkFile)
                }
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                _updateStatus.value = UpdateStatus.Error("Güncelleme indirilemedi: ${e.localizedMessage ?: "Bağlantı koptu"}")
            }
        }
    }

    /**
     * Hazır APK dosyasını yüklemeyi tetikler.
     */
    fun installReadyApk() {
        val currentStatus = _updateStatus.value
        val apkFile = if (currentStatus is UpdateStatus.ReadyToInstall) {
            currentStatus.apkFile
        } else {
            lastAvailableUpdate?.let { getExistingDownloadedApk(it.remoteVersionCode) }
        }

        if (apkFile != null && apkFile.exists()) {
            installApk(apkFile)
        } else {
            downloadAndInstallUpdate()
        }
    }

    fun dismissUpdateStatus() {
        if (_updateStatus.value !is UpdateStatus.Downloading) {
            _updateStatus.value = UpdateStatus.Idle
        }
    }

    /**
     * İndirilen APK dosyasını Android Paket Yükleyicisi ile kurma niyetini (Intent) başlatır.
     */
    fun installApk(apkFile: File) {
        try {
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
        } catch (_: Exception) {}
    }
}
