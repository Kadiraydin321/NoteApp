package com.example.noteapp.data.sync

import com.example.noteapp.data.backup.BackupManager
import com.example.noteapp.data.backup.RestoreResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class SyncServerStatus(
    val appName: String,
    val notesCount: Int,
    val requiresPin: Boolean
)

sealed class SyncResult {
    data class Success(val message: String, val notesCount: Int = 0) : SyncResult()
    data class Error(val message: String) : SyncResult()
    data class NeedsPassword(val message: String = "Kasa şifresi gerekiyor.") : SyncResult()
}

@Singleton
class DesktopSyncClient @Inject constructor(
    private val backupManager: BackupManager
) {

    fun sanitizeUrl(hostOrUrl: String, defaultPort: Int = 8765, endpoint: String): String {
        var clean = hostOrUrl.trim()
        if (clean.isBlank()) return ""
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        return try {
            val uri = URI(clean)
            val host = uri.host ?: clean.removePrefix("http://").substringBefore(":").substringBefore("/")
            val port = if (uri.port != -1) uri.port else defaultPort
            val ep = if (endpoint.startsWith("/")) endpoint else "/$endpoint"
            "http://$host:$port$ep"
        } catch (_: Exception) {
            val ep = if (endpoint.startsWith("/")) endpoint else "/$endpoint"
            if (clean.contains(":")) "$clean$ep" else "$clean:$defaultPort$ep"
        }
    }

    /**
     * Masaüstü sunucusunun erişilebilir olup olmadığını ve durumunu kontrol eder.
     */
    suspend fun checkConnection(hostOrUrl: String): Result<SyncServerStatus> = withContext(Dispatchers.IO) {
        try {
            val urlStr = sanitizeUrl(hostOrUrl, endpoint = "/api/status")
            if (urlStr.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Lütfen geçerli bir IP veya sunucu adresi girin."))
            }

            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(body)
                val status = SyncServerStatus(
                    appName = json.optString("appName", "Not Defterim Masaüstü"),
                    notesCount = json.optInt("notesCount", 0),
                    requiresPin = json.optBoolean("requiresPin", true)
                )
                Result.success(status)
            } else {
                Result.failure(Exception("Masaüstü sunucusundan geçersiz yanıt alındı (HTTP $responseCode)"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Masaüstü uygulamasına bağlanılamadı. Bilgisayarda 'Wi-Fi Senkronizasyonu' açık mı? (${e.localizedMessage ?: e.javaClass.simpleName})"))
        }
    }

    /**
     * Yerel ağdaki (LAN) 'Not Defterim Masaüstü' sunucusunu otomatik arar.
     * Subnet'teki cihazları ve emülatör IP'lerini paralel sorgular.
     */
    suspend fun autoDiscoverServer(preferredHost: String? = null): Result<Pair<String, SyncServerStatus>> = withContext(Dispatchers.IO) {
        // 1. Kullanıcının daha önce bağlandığı kayıtlı bir IP varsa önce onu dene
        if (!preferredHost.isNullOrBlank()) {
            val res = checkConnection(preferredHost)
            if (res.isSuccess) {
                return@withContext Result.success(preferredHost to res.getOrThrow())
            }
        }

        // 2. Emülatör veya yerel IP öncelikli adaylar
        val priorityCandidates = listOf("10.0.2.2", "127.0.0.1")
        for (cand in priorityCandidates) {
            val res = checkConnection(cand)
            if (res.isSuccess) {
                return@withContext Result.success(cand to res.getOrThrow())
            }
        }

        // 3. Cihazın bağlı olduğu yerel IPv4 subnet'lerini tespit et
        val subnets = mutableSetOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddr = addr.hostAddress ?: ""
                        val parts = hostAddr.split(".")
                        if (parts.size == 4) {
                            subnets.add("${parts[0]}.${parts[1]}.${parts[2]}")
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (subnets.isEmpty()) {
            subnets.add("192.168.1")
            subnets.add("192.168.0")
        }

        // 4. Subnet'teki IP'leri paralel olarak tara (hızlı 400ms zaman aşımı ile)
        val foundDeferred = CompletableDeferred<Pair<String, SyncServerStatus>>()
        val scanScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        try {
            for (subnet in subnets) {
                for (i in 1..254) {
                    val candidateIp = "$subnet.$i"
                    scanScope.launch {
                        if (foundDeferred.isCompleted) return@launch
                        try {
                            val url = URL("http://$candidateIp:8765/api/status")
                            val conn = (url.openConnection() as HttpURLConnection).apply {
                                requestMethod = "GET"
                                connectTimeout = 450
                                readTimeout = 450
                                setRequestProperty("Accept", "application/json")
                            }
                            if (conn.responseCode == 200) {
                                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                                val json = JSONObject(body)
                                if (json.optString("status") == "ok") {
                                    val status = SyncServerStatus(
                                        appName = json.optString("appName", "Not Defterim Masaüstü"),
                                        notesCount = json.optInt("notesCount", 0),
                                        requiresPin = json.optBoolean("requiresPin", true)
                                    )
                                    foundDeferred.complete(candidateIp to status)
                                }
                            }
                        } catch (_: Exception) {
                            // Hedef kapalı veya erişilemez, sessizce geç
                        }
                    }
                }
            }

            val result = withTimeoutOrNull(3500) {
                foundDeferred.await()
            }

            scanScope.cancel()

            if (result != null) {
                Result.success(result)
            } else {
                Result.failure(Exception("Yerel ağda 'Not Defterim Masaüstü' sunucusu bulunamadı. Lütfen bilgisayarda uygulamanın ve senkronizasyonun açık olduğunu kontrol edin."))
            }
        } catch (e: Exception) {
            scanScope.cancel()
            Result.failure(e)
        }
    }

    /**
     * Telefondaki tüm notları, kategorileri ve medyaları Masaüstü uygulamasına yükler.
     */
    suspend fun sendNotesToDesktop(
        hostOrUrl: String,
        pin: String,
        clearExisting: Boolean = false,
        vaultPassword: String? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val urlStr = sanitizeUrl(hostOrUrl, endpoint = "/api/sync/upload")
            if (urlStr.isBlank()) {
                return@withContext SyncResult.Error("Lütfen masaüstü IP adresini girin.")
            }
            if (pin.trim().length != 4) {
                return@withContext SyncResult.Error("Lütfen masaüstünde görüntülenen 4 haneli PIN kodunu girin.")
            }

            // 1. Telefondaki notları bellek içi ZIP arşivine paketle
            val (zipBytes, notesCount, attachmentsCount) = backupManager.buildZipArchiveBytes()
            if (zipBytes.isEmpty()) {
                return@withContext SyncResult.Error("Yedeklenecek veri bulunamadı.")
            }

            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 60000
                setRequestProperty("Content-Type", "application/zip")
                setRequestProperty("X-Sync-PIN", pin.trim())
                setRequestProperty("X-Clear-Existing", clearExisting.toString())
                if (!vaultPassword.isNullOrBlank()) {
                    setRequestProperty("X-Vault-Password", vaultPassword)
                }
                setFixedLengthStreamingMode(zipBytes.size)
            }

            conn.outputStream.use { os ->
                os.write(zipBytes)
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val respText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(respText)
                if (json.optBoolean("success", false)) {
                    val count = json.optInt("notesCount", notesCount)
                    SyncResult.Success(
                        message = "✓ $count not ve $attachmentsCount medya dosyası başarıyla Masaüstüne aktarıldı!",
                        notesCount = count
                    )
                } else {
                    SyncResult.Error(json.optString("error", "Masaüstü aktarımı tamamlayamadı."))
                }
            } else if (responseCode == 401) {
                SyncResult.Error("Eşleşme PIN kodu hatalı! Lütfen masaüstü ekranındaki 4 haneli PIN'i kontrol edin.")
            } else {
                val errText = runCatching {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull() ?: ""
                SyncResult.Error("Masaüstü aktarım hatası (HTTP $responseCode): $errText")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SyncResult.Error("Bağlantı hatası: ${e.localizedMessage ?: "Sunucuya ulaşılamadı"}")
        }
    }

    /**
     * Masaüstü uygulamasındaki notları telefona indirir ve Room veritabanına işler.
     */
    suspend fun pullNotesFromDesktop(
        hostOrUrl: String,
        pin: String,
        clearExisting: Boolean = false,
        vaultPassword: String? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val urlStr = sanitizeUrl(hostOrUrl, endpoint = "/api/sync/download")
            if (urlStr.isBlank()) {
                return@withContext SyncResult.Error("Lütfen masaüstü IP adresini girin.")
            }
            if (pin.trim().length != 4) {
                return@withContext SyncResult.Error("Lütfen masaüstünde görüntülenen 4 haneli PIN kodunu girin.")
            }

            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 60000
                setRequestProperty("X-Sync-PIN", pin.trim())
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val zipBytes = conn.inputStream.use { it.readBytes() }
                if (zipBytes.isEmpty()) {
                    return@withContext SyncResult.Error("Masaüstünden boş veri alındı.")
                }

                // Gelen ZIP arşivini Room veritabanına aktar
                val restoreResult = backupManager.restoreBackup(
                    inputStream = ByteArrayInputStream(zipBytes),
                    clearExisting = clearExisting,
                    vaultPassword = vaultPassword
                )

                when (restoreResult) {
                    is RestoreResult.Success -> {
                        SyncResult.Success(
                            message = "✓ Masaüstünden ${restoreResult.notesCount} not ve ${restoreResult.attachmentsCount} dosya başarıyla telefona aktarıldı!",
                            notesCount = restoreResult.notesCount
                        )
                    }
                    is RestoreResult.NeedsPassword -> {
                        SyncResult.NeedsPassword("Masaüstü yedeği Kasa Kilidi ile korunuyor. Lütfen kasa şifrenizi girin.")
                    }
                    is RestoreResult.Error -> {
                        SyncResult.Error("İçe aktarma başarısız: ${restoreResult.message}")
                    }
                }
            } else if (responseCode == 401) {
                SyncResult.Error("Eşleşme PIN kodu hatalı! Lütfen masaüstü ekranındaki 4 haneli PIN'i kontrol edin.")
            } else {
                val errText = runCatching {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull() ?: ""
                SyncResult.Error("Masaüstünden indirme hatası (HTTP $responseCode): $errText")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SyncResult.Error("Bağlantı hatası: ${e.localizedMessage ?: "Sunucuya ulaşılamadı"}")
        }
    }
}
