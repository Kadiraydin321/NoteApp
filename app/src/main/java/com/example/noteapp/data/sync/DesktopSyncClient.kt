package com.example.noteapp.data.sync

import com.example.noteapp.data.backup.BackupManager
import com.example.noteapp.data.backup.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.inject.Inject
import javax.inject.Singleton

data class SyncServerStatus(
    val appName: String,
    val notesCount: Int,
    val requiresPin: Boolean
)

data class SyncPairingSession(
    val sessionToken: String,
    val expiresAt: Long,
    val tlsCertSha256: String? = null
)

sealed class SyncResult {
    data class Success(val message: String, val notesCount: Int = 0) : SyncResult()
    data class Error(val message: String, val requiresRePair: Boolean = false) : SyncResult()
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
            val scheme = uri.scheme?.lowercase() ?: return ""
            val host = uri.host ?: return ""
            if (scheme !in setOf("http", "https") ||
                uri.rawUserInfo != null ||
                uri.rawQuery != null ||
                uri.rawFragment != null ||
                (!uri.rawPath.isNullOrEmpty() && uri.rawPath != "/")
            ) return ""
            val port = if (uri.port != -1) uri.port else defaultPort
            if (!SyncHostValidator.isValidPort(port)) return ""
            val urlHost = if (host.contains(':')) "[$host]" else host
            val ep = if (endpoint.startsWith("/")) endpoint else "/$endpoint"
            "$scheme://${urlHost}:$port$ep"
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Masaüstü sunucusunun erişilebilir olup olmadığını ve durumunu kontrol eder.
     */
    suspend fun checkConnection(
        hostOrUrl: String,
        tlsCertSha256: String? = null
    ): Result<SyncServerStatus> = withContext(Dispatchers.IO) {
        try {
            val urlStr = sanitizeUrl(hostOrUrl, endpoint = "/api/status")
            if (urlStr.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Lütfen geçerli bir IP veya sunucu adresi girin."))
            }

            val conn = openConnection(urlStr, tlsCertSha256).apply {
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

    suspend fun pairSession(
        hostOrUrl: String,
        pairingToken: String,
        tlsCertSha256: String? = null
    ): Result<SyncPairingSession> =
        withContext(Dispatchers.IO) {
            if (!SyncHostValidator.isValidPairingToken(pairingToken)) {
                return@withContext Result.failure(IllegalArgumentException("QR eşleşme kodu geçersiz. Masaüstünden yeni bir QR kod üretin."))
            }
            val urlStr = sanitizeUrl(hostOrUrl, endpoint = "/api/v2/pair")
            if (urlStr.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Masaüstü adresi geçersiz. QR kodunu yeniden okutun."))
            }

            try {
                val conn = openConnection(urlStr, tlsCertSha256).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Pairing-Token", pairingToken)
                }
                val responseCode = conn.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val message = if (responseCode == 401 || responseCode == 410) {
                        "QR eşleşme kodu kullanılmış veya süresi dolmuş. Masaüstünde yeni bir kod üretip tekrar okutun."
                    } else {
                        "Masaüstü eşleştirme isteğini kabul etmedi (HTTP $responseCode). Yeni QR kodunu deneyin."
                    }
                    return@withContext Result.failure(IllegalStateException(message))
                }

                val json = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                val version = json.optInt("version", -1)
                val sessionToken = json.optString("session_token", "")
                val expiresAt = json.optLong("expires_at", 0L)
                val now = System.currentTimeMillis()
                val maxSessionExpiry = now + SESSION_MAX_TTL_MILLIS + CLOCK_SKEW_MILLIS
                if (version != 2 ||
                    !SyncHostValidator.isValidPairingToken(sessionToken) ||
                    expiresAt <= now ||
                    expiresAt > maxSessionExpiry
                ) {
                    return@withContext Result.failure(IllegalStateException("Masaüstünden geçersiz eşleşme yanıtı alındı. Yeni QR kodunu deneyin."))
                }
                Result.success(SyncPairingSession(sessionToken, expiresAt, tlsCertSha256))
            } catch (_: Exception) {
                Result.failure(IllegalStateException("Masaüstüne güvenli eşleşme yapılamadı. Bilgisayarda Wi-Fi senkronizasyonunun açık olduğunu kontrol edin."))
            }
        }

    /**
     * Telefondaki tüm notları, kategorileri ve medyaları Masaüstü uygulamasına yükler.
     */
    suspend fun sendNotesToDesktop(
        hostOrUrl: String,
        pin: String,
        clearExisting: Boolean = false,
        vaultPassword: String? = null,
        sessionToken: String? = null,
        tlsCertSha256: String? = null,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val urlStr = sanitizeUrl(hostOrUrl, endpoint = "/api/sync/upload")
            if (urlStr.isBlank()) {
                return@withContext SyncResult.Error("Masaüstü adresi geçersiz. QR kodunu okutun veya gelişmiş ayarlarda yerel adresi girin.")
            }
            if (sessionToken.isNullOrBlank() && !pin.matches(Regex("[0-9]{4}"))) {
                return@withContext SyncResult.Error("Lütfen masaüstünde görüntülenen 4 haneli PIN kodunu girin.")
            }

            // 1. Telefondaki notları bellek içi ZIP arşivine paketle
            val (zipBytes, notesCount, attachmentsCount) = backupManager.buildZipArchiveBytes()
            if (zipBytes.isEmpty()) {
                return@withContext SyncResult.Error("Yedeklenecek veri bulunamadı.")
            }
            if (zipBytes.size.toLong() > MAX_ZIP_BYTES) {
                return@withContext SyncResult.Error("ZIP aktarımı 50 MiB sınırını aşıyor.")
            }

            val url = URL(urlStr)
            val conn = openConnection(url.toString(), tlsCertSha256).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 60000
                setRequestProperty("Content-Type", "application/zip")
                if (!sessionToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $sessionToken")
                } else {
                    setRequestProperty("X-Sync-PIN", pin)
                }
                setRequestProperty("X-Clear-Existing", clearExisting.toString())
                if (!vaultPassword.isNullOrBlank()) {
                    setRequestProperty("X-Vault-Password", vaultPassword)
                }
                setFixedLengthStreamingMode(zipBytes.size)
            }

            conn.outputStream.use { os ->
                val chunkSize = 64 * 1024
                var sent = 0
                while (sent < zipBytes.size) {
                    val end = (sent + chunkSize).coerceAtMost(zipBytes.size)
                    os.write(zipBytes, sent, end - sent)
                    sent = end
                    onProgress(sent.toLong(), zipBytes.size.toLong())
                }
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
            } else if (responseCode == 401 || responseCode == 403) {
                if (!sessionToken.isNullOrBlank()) {
                    SyncResult.Error("Masaüstü oturumu artık geçerli değil. Uygulamayı yeniden başlattıysanız yeni QR kodunu okutun.", requiresRePair = true)
                } else {
                    SyncResult.Error("Eşleşme PIN kodu hatalı! Lütfen masaüstü ekranındaki 4 haneli PIN'i kontrol edin.")
                }
            } else {
                val errText = runCatching {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull() ?: ""
                SyncResult.Error("Masaüstü aktarım hatası (HTTP $responseCode): $errText")
            }
        } catch (e: Exception) {
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
        vaultPassword: String? = null,
        sessionToken: String? = null,
        tlsCertSha256: String? = null,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val urlStr = sanitizeUrl(hostOrUrl, endpoint = "/api/sync/download")
            if (urlStr.isBlank()) {
                return@withContext SyncResult.Error("Masaüstü adresi geçersiz. QR kodunu okutun veya gelişmiş ayarlarda yerel adresi girin.")
            }
            if (sessionToken.isNullOrBlank() && !pin.matches(Regex("[0-9]{4}"))) {
                return@withContext SyncResult.Error("Lütfen masaüstünde görüntülenen 4 haneli PIN kodunu girin.")
            }

            val url = URL(urlStr)
            val conn = openConnection(url.toString(), tlsCertSha256).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 60000
                if (!sessionToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $sessionToken")
                } else {
                    setRequestProperty("X-Sync-PIN", pin)
                }
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val contentLength = conn.contentLengthLong
                if (contentLength > MAX_ZIP_BYTES) {
                    return@withContext SyncResult.Error("Alınan ZIP 50 MiB sınırını aşıyor.")
                }
                val zipBytes = conn.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        received += read
                        if (received > MAX_ZIP_BYTES) {
                            return@withContext SyncResult.Error("Alınan ZIP 50 MiB sınırını aşıyor.")
                        }
                        output.write(buffer, 0, read)
                        onProgress(received, contentLength)
                    }
                    output.toByteArray()
                }
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
            } else if (responseCode == 401 || responseCode == 403) {
                if (!sessionToken.isNullOrBlank()) {
                    SyncResult.Error("Masaüstü oturumu artık geçerli değil. Uygulamayı yeniden başlattıysanız yeni QR kodunu okutun.", requiresRePair = true)
                } else {
                    SyncResult.Error("Eşleşme PIN kodu hatalı! Lütfen masaüstü ekranındaki 4 haneli PIN'i kontrol edin.")
                }
            } else {
                val errText = runCatching {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull() ?: ""
                SyncResult.Error("Masaüstünden indirme hatası (HTTP $responseCode): $errText")
            }
        } catch (e: Exception) {
            SyncResult.Error("Bağlantı hatası: ${e.localizedMessage ?: "Sunucuya ulaşılamadı"}")
        }
    }

    private fun openConnection(url: String, tlsCertSha256: String?): HttpURLConnection {
        val connection = URL(url).openConnection() as? HttpURLConnection
            ?: throw IllegalArgumentException("Desteklenmeyen eşitleme bağlantısı.")
        if (connection is HttpsURLConnection) {
            val fingerprint = tlsCertSha256
                ?.takeIf(SyncHostValidator::isValidCertificateFingerprint)
                ?: throw SecurityException("HTTPS bağlantısı için QR sertifika parmak izi gereklidir.")
            val expected = fingerprint.hexToBytes()
            val trustManager = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    throw CertificateException("İstemci sertifikası doğrulaması desteklenmiyor.")
                }

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    val leaf = chain.firstOrNull()
                        ?: throw CertificateException("Sunucu sertifikası yok.")
                    val actual = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
                    if (!MessageDigest.isEqual(expected, actual)) {
                        throw CertificateException("Sunucu sertifika parmak izi eşleşmiyor.")
                    }
                }
            }
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), null)
            }
            connection.sslSocketFactory = sslContext.socketFactory
            // The QR pin is the endpoint identity; the generated certificate is self-signed.
            connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        } else if (tlsCertSha256 != null) {
            throw SecurityException("Sertifika sabitlemeli bağlantı HTTPS olmalıdır.")
        }
        return connection
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val SESSION_MAX_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
        const val CLOCK_SKEW_MILLIS = 60_000L
        const val MAX_ZIP_BYTES = 50L * 1024 * 1024
    }
}
