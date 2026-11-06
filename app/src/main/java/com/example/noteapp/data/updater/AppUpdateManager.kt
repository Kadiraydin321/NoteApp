package com.example.noteapp.data.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
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
        val releaseDate: String = "",
        val downloadUrl: String = ""
    ) : UpdateStatus()
    data class UpToDate(val currentVersionName: String) : UpdateStatus()
    data class Downloading(val progressPercent: Int, val downloadedBytes: Long, val totalBytes: Long) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

data class CandidateRelease(
    val remoteVersionCode: Int,
    val remoteVersionName: String,
    val changeLog: String,
    val releaseDate: String = "",
    val downloadUrl: String = "",
    val sourceName: String = ""
)

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Çoklu Kaynak Desteği (Multi-Source Fallback)
        // 1. Kaynak: apk branch version.json
        private const val SOURCE_1_APK_BRANCH_JSON = "https://raw.githubusercontent.com/Kadiraydin321/NoteApp/apk/version.json"
        // 2. Kaynak: main branch version.json (Yedek)
        private const val SOURCE_2_MAIN_BRANCH_JSON = "https://raw.githubusercontent.com/Kadiraydin321/NoteApp/main/version.json"
        // 3. Kaynak: GitHub Releases Latest API
        private const val SOURCE_3_GITHUB_RELEASES_API = "https://api.github.com/repos/Kadiraydin321/NoteApp/releases/latest"
        // 4. Kaynak: Commits API (Son çare)
        private const val SOURCE_4_COMMITS_API = "https://api.github.com/repos/Kadiraydin321/NoteApp/commits/apk"

        // İndirme URL Adayları (Fail-safe download fallback)
        private const val DEFAULT_APK_RAW_URL = "https://raw.githubusercontent.com/Kadiraydin321/NoteApp/apk/NoteApp-release.apk"
        private const val FALLBACK_MAIN_APK_URL = "https://raw.githubusercontent.com/Kadiraydin321/NoteApp/main/NoteApp-release.apk"
        private const val FALLBACK_RELEASE_APK_URL = "https://github.com/Kadiraydin321/NoteApp/releases/latest/download/NoteApp-release.apk"

        private const val APK_FILE_NAME = "NoteApp-update.apk"
        private const val APK_TEMP_NAME = "NoteApp-update.apk.tmp"

        private const val UPDATE_CHANNEL_ID = "note_app_update_channel"
        private const val UPDATE_NOTIFICATION_ID = 99881
    }

    // Singleton uygulama kapsamlı coroutine scope: Ekranlar arası geçişte indirme ASLA kesilmez
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    // En son tespit edilen güncelleme bilgisi (indirme/yükleme sırasında da korunur)
    var lastAvailableUpdate: UpdateStatus.UpdateAvailable? = null
        private set

    // Bu oturumda ve sonraki oturumlarda hangi sürüm için popup gösterildiğini SharedPreferences ile kalıcı takip eder
    private val prefs = context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)

    var hasShownDialogForVersion: Int?
        get() = prefs.getInt("shown_dialog_version", -1).takeIf { it != -1 }
        private set(value) {
            if (value != null) {
                prefs.edit().putInt("shown_dialog_version", value).apply()
            } else {
                prefs.edit().remove("shown_dialog_version").apply()
            }
        }

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
                    if (archiveVersionCode == targetVersionCode && targetVersionCode > BuildConfig.VERSION_CODE) {
                        return apkFile
                    } else if (archiveVersionCode <= BuildConfig.VERSION_CODE) {
                        apkFile.delete()
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

        // 2. Halihazırda indirilmiş ve hazır bir APK varsa sadece henüz kurulmamışsa koru
        val currentStatus = _updateStatus.value
        if (currentStatus is UpdateStatus.ReadyToInstall && currentStatus.apkFile.exists()) {
            val pkgInfo = context.packageManager.getPackageArchiveInfo(currentStatus.apkFile.absolutePath, 0)
            val archiveVersionCode = if (pkgInfo != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode.toInt() else @Suppress("DEPRECATION") pkgInfo.versionCode
            } else 0
            if (archiveVersionCode > BuildConfig.VERSION_CODE) {
                return@withContext currentStatus
            } else {
                currentStatus.apkFile.delete()
                _updateStatus.value = UpdateStatus.Idle
            }
        }

        _updateStatus.value = UpdateStatus.Checking

        val result = performMultiSourceCheck()

        if (result is UpdateStatus.UpdateAvailable) {
            lastAvailableUpdate = result

            // Yeni sürüm bulundu, kullanıcıya doğrudan sistem bildirimi göster
            showUpdateNotification(result)

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

    /**
     * Çoklu Kaynak Kontrolü:
     * 1. apk/version.json
     * 2. main/version.json
     * 3. releases/latest
     * 4. commits/apk
     * Tüm kaynaklar taranır, en güncel sürüm adayı seçilir.
     */
    private suspend fun performMultiSourceCheck(): UpdateStatus = withContext(Dispatchers.IO) {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val currentVersionName = BuildConfig.VERSION_NAME

        val candidates = mutableListOf<CandidateRelease>()
        var hasSuccessfulSource = false

        // 1. Kaynak: apk dalındaki version.json
        fetchVersionJson(SOURCE_1_APK_BRANCH_JSON, "apk/version.json")?.let {
            candidates.add(it)
            hasSuccessfulSource = true
        }

        // 2. Kaynak: main dalındaki version.json (Yedek kaynak)
        fetchVersionJson(SOURCE_2_MAIN_BRANCH_JSON, "main/version.json")?.let {
            candidates.add(it)
            hasSuccessfulSource = true
        }

        // 3. Kaynak: GitHub Releases Latest API
        fetchLatestRelease(SOURCE_3_GITHUB_RELEASES_API)?.let {
            candidates.add(it)
            hasSuccessfulSource = true
        }

        // 4. Kaynak: Eğer yukarıdaki hiçbir kaynakta aday bulunamadıysa Commits API'ye bak
        if (candidates.isEmpty()) {
            fetchLatestCommit(SOURCE_4_COMMITS_API)?.let {
                candidates.add(it)
                hasSuccessfulSource = true
            }
        }

        if (!hasSuccessfulSource && candidates.isEmpty()) {
            return@withContext UpdateStatus.Error("İnternet bağlantısı kurulamadığı için güncelleme kontrol edilemedi.")
        }

        // Mevcut sürümden daha büyük olan adayları filtrele
        val availableUpdates = candidates.filter { it.remoteVersionCode > currentVersionCode }

        if (availableUpdates.isNotEmpty()) {
            // En yüksek sürüm koduna sahip olanı seç
            val best = availableUpdates.maxByOrNull { it.remoteVersionCode }!!
            return@withContext UpdateStatus.UpdateAvailable(
                remoteVersionName = best.remoteVersionName.ifBlank { "v${best.remoteVersionCode}" },
                remoteVersionCode = best.remoteVersionCode,
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                message = best.changeLog.ifBlank { "Yeni geliştirmeler, hata düzeltmeleri ve optimizasyonlar." },
                releaseDate = best.releaseDate,
                downloadUrl = best.downloadUrl
            )
        }

        UpdateStatus.UpToDate(currentVersionName)
    }

    private fun fetchVersionJson(urlStr: String, sourceName: String): CandidateRelease? {
        return try {
            val url = URL("$urlStr?t=${System.currentTimeMillis()}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "NoteApp-Android")
                setRequestProperty("Cache-Control", "no-cache")
            }

            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val remoteVersionCode = json.optInt("versionCode", 0)
                val remoteVersionName = json.optString("versionName", "")
                val releaseDate = json.optString("releaseDate", "")
                val changeLog = json.optString("changeLog", "Yeni geliştirmeler ve optimizasyonlar")
                val downloadUrl = json.optString("downloadUrl", "")

                if (remoteVersionCode > 0) {
                    CandidateRelease(
                        remoteVersionCode = remoteVersionCode,
                        remoteVersionName = remoteVersionName.ifBlank { "v$remoteVersionCode" },
                        changeLog = changeLog,
                        releaseDate = releaseDate,
                        downloadUrl = downloadUrl,
                        sourceName = sourceName
                    )
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchLatestRelease(urlStr: String): CandidateRelease? {
        return try {
            val url = URL("$urlStr?t=${System.currentTimeMillis()}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "NoteApp-Android")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("Cache-Control", "no-cache")
            }

            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val tagName = json.optString("tag_name", "").trim()
                val body = json.optString("body", "GitHub Release Güncellemesi")
                val publishedAt = json.optString("published_at", "")

                // Tag'den versiyon kodu ve ismi çıkar
                val cleanTag = tagName.removePrefix("v").removePrefix("V")
                val tagDigitsOnly = cleanTag.filter { it.isDigit() }
                val parsedCode = when {
                    tagDigitsOnly.isNotEmpty() && cleanTag.all { it.isDigit() } -> tagDigitsOnly.toIntOrNull() ?: 0
                    cleanTag.contains(".") -> {
                        // "1.3.1" -> 1*1000 + 3*10 + 1 veya benzeri, ya da BuildConfig.VERSION_CODE + 1
                        val parts = cleanTag.split(".")
                        if (parts.size >= 2) {
                            val major = parts[0].toIntOrNull() ?: 1
                            val minor = parts[1].toIntOrNull() ?: 0
                            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
                            (major * 100) + (minor * 10) + patch
                        } else BuildConfig.VERSION_CODE + 1
                    }
                    else -> BuildConfig.VERSION_CODE + 1
                }

                // Assetler arasından APK dosyasını bul
                var apkDownloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                if (parsedCode > 0 || tagName.isNotEmpty()) {
                    CandidateRelease(
                        remoteVersionCode = parsedCode,
                        remoteVersionName = if (tagName.startsWith("v", ignoreCase = true)) tagName else "v$cleanTag",
                        changeLog = body,
                        releaseDate = publishedAt,
                        downloadUrl = apkDownloadUrl,
                        sourceName = "GitHub Releases"
                    )
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchLatestCommit(urlStr: String): CandidateRelease? {
        return try {
            val url = URL("$urlStr?t=${System.currentTimeMillis()}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "NoteApp-Android")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("Cache-Control", "no-cache")
            }

            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val commitObj = json.getJSONObject("commit")
                val commitMsg = commitObj.getString("message").lines().firstOrNull() ?: "Yeni sürüm yayında"
                val dateStr = commitObj.getJSONObject("committer").optString("date", "")
                val commitMillis = parseIso8601ToMillis(dateStr)

                val hasNewerCommit = commitMillis > 0L && commitMillis > (BuildConfig.BUILD_TIME + 60_000L)
                if (hasNewerCommit) {
                    CandidateRelease(
                        remoteVersionCode = BuildConfig.VERSION_CODE + 1,
                        remoteVersionName = "Yeni Sürüm (Son Commit)",
                        changeLog = commitMsg,
                        releaseDate = dateStr,
                        downloadUrl = DEFAULT_APK_RAW_URL,
                        sourceName = "GitHub Commits"
                    )
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
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
     * Fail-safe indirme: Birincil URL başarısız olursa yedek URL'leri dener.
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

            // Aday İndirme URL'leri (Sırasıyla denenir)
            val candidateUrls = mutableListOf<String>()
            if (!available?.downloadUrl.isNullOrBlank()) {
                candidateUrls.add(available!!.downloadUrl)
            }
            candidateUrls.add(DEFAULT_APK_RAW_URL)
            candidateUrls.add(FALLBACK_MAIN_APK_URL)
            candidateUrls.add(FALLBACK_RELEASE_APK_URL)

            var downloadSuccess = false
            var lastErrorMessage: String? = null

            for (targetUrl in candidateUrls) {
                if (downloadSuccess) break
                try {
                    val url = URL("$targetUrl?t=${System.currentTimeMillis()}")
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 12000
                        readTimeout = 30000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "NoteApp-Android")
                        setRequestProperty("Cache-Control", "no-cache")
                    }

                    if (connection.responseCode !in 200..299) {
                        continue
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

                    if (tempFile.exists() && tempFile.length() > 500_000L) {
                        if (apkFile.exists()) apkFile.delete()
                        tempFile.renameTo(apkFile)
                        downloadSuccess = true
                        break
                    } else {
                        if (tempFile.exists()) tempFile.delete()
                    }
                } catch (e: Exception) {
                    lastErrorMessage = e.localizedMessage
                    if (tempFile.exists()) tempFile.delete()
                }
            }

            if (downloadSuccess && apkFile.exists()) {
                val pkgInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                val archiveVersionCode = if (pkgInfo != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode.toInt() else @Suppress("DEPRECATION") pkgInfo.versionCode
                } else 0

                if (archiveVersionCode <= BuildConfig.VERSION_CODE) {
                    apkFile.delete()
                    _updateStatus.value = UpdateStatus.Error("İndirilen APK sunucuda henüz yeni sürüme güncellenmemiş. Lütfen daha sonra tekrar deneyin.")
                    return@launch
                }
                val readyStatus = UpdateStatus.ReadyToInstall(apkFile)
                _updateStatus.value = readyStatus

                // İndirme tamamlandı; yükleme niyetini başlat
                withContext(Dispatchers.Main) {
                    installApk(apkFile)
                }
            } else {
                _updateStatus.value = UpdateStatus.Error("Güncelleme indirilemedi: ${lastErrorMessage ?: "Tüm indirme kaynakları yanıt vermedi"}")
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

    /**
     * Yeni bir sürüm tespit edildiğinde kullanıcıya sistem bildirimi gösterir.
     */
    private fun showUpdateNotification(updateInfo: UpdateStatus.UpdateAvailable) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "Uygulama Güncellemeleri",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Yeni NoteApp sürümleri çıktığında bildirim gönderir"
                    enableLights(true)
                    lightColor = android.graphics.Color.GREEN
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.createNotificationChannel(channel)
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("focus_section", "update")
                putExtra("auto_download_update", true)
            }

            val pendingIntent = if (launchIntent != null) {
                PendingIntent.getActivity(
                    context,
                    UPDATE_NOTIFICATION_ID,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )
            } else null

            val builder = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("🎉 Yeni Sürüm Hazır: ${updateInfo.remoteVersionName}")
                .setContentText("Not Defterim güncellendi. Yüklemek için dokunun.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("${updateInfo.remoteVersionName} hazır!\n${updateInfo.message}"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent)
                builder.addAction(
                    android.R.drawable.stat_sys_download,
                    "Şimdi Kur",
                    pendingIntent
                )
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(UPDATE_NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {}
    }
}
