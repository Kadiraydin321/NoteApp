package com.example.noteapp.data.sync

import android.content.Context
import android.util.Base64
import com.example.noteapp.BuildConfig
import com.example.noteapp.data.backup.BackupManager
import com.example.noteapp.data.backup.RestoreResult
import com.example.noteapp.domain.repository.NoteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class IncomingSyncTransfer(
    val id: String,
    val notesCount: Int,
    val sizeBytes: Long,
    val requestedClearExisting: Boolean
)

data class SyncUploadProgress(
    val receivedBytes: Long = 0L,
    val totalBytes: Long = 0L
) {
    val percent: Int
        get() = if (totalBytes <= 0L) 0 else ((receivedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
}

data class SyncHostTransferResult(
    val success: Boolean,
    val message: String
)

@Singleton
class AndroidSyncServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val noteRepository: NoteRepository,
    private val tlsIdentity: SyncTlsIdentity
) {
    private val server = AtomicReference<SecureNanoServer?>()
    private val sessions = ConcurrentHashMap<String, Long>()
    private val approval = AtomicReference<PendingUpload?>()
    @Volatile
    private var pairingTokenDigest: ByteArray? = null
    @Volatile
    private var pairingExpiresAt: Long = 0L
    private val _incomingTransfer = MutableStateFlow<IncomingSyncTransfer?>(null)
    val incomingTransfer: StateFlow<IncomingSyncTransfer?> = _incomingTransfer.asStateFlow()
    private val _uploadProgress = MutableStateFlow(SyncUploadProgress())
    val uploadProgress: StateFlow<SyncUploadProgress> = _uploadProgress.asStateFlow()
    private val _transferResult = MutableStateFlow<SyncHostTransferResult?>(null)
    val transferResult: StateFlow<SyncHostTransferResult?> = _transferResult.asStateFlow()

    @Synchronized
    fun start(host: String, port: Int = DEFAULT_PORT): SyncQrPayload {
        require(SyncHostValidator.isAllowedHost(host)) { "Yalnızca yerel ağ adresinde eşitleme sunucusu başlatılabilir." }
        require(SyncHostValidator.isValidPort(port)) { "Eşitleme portu geçersiz." }
        stop()

        val tls = tlsIdentity.getOrCreate()
        val pairingToken = randomToken()
        val expiresAt = System.currentTimeMillis() + PAIRING_TTL_MILLIS
        val nextServer = SecureNanoServer(host, port, tls.serverSocketFactory) { session ->
            handleRequest(session)
        }
        nextServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        pairingTokenDigest = sha256(pairingToken)
        pairingExpiresAt = expiresAt
        server.set(nextServer)
        return SyncQrPayload(
            ip = host,
            port = nextServer.listeningPort,
            version = 3,
            pairingToken = pairingToken,
            expiresAt = expiresAt,
            scheme = "https",
            tlsCertSha256 = tls.certificateFingerprint
        )
    }

    @Synchronized
    fun stop() {
        server.getAndSet(null)?.stop()
        pairingTokenDigest = null
        pairingExpiresAt = 0L
        sessions.clear()
        approval.getAndSet(null)?.let { pending ->
            pending.decision.complete(UploadDecision(accepted = false, clearExisting = false))
            pending.file.delete()
        }
        _incomingTransfer.value = null
        _uploadProgress.value = SyncUploadProgress()
        _transferResult.value = null
    }

    fun approveIncoming(id: String, clearExistingConfirmed: Boolean) {
        val pending = approval.get() ?: return
        if (pending.publicRequest.id != id) return
        pending.decision.complete(UploadDecision(accepted = true, clearExisting = clearExistingConfirmed))
    }

    fun rejectIncoming(id: String) {
        val pending = approval.get() ?: return
        if (pending.publicRequest.id == id) {
            pending.decision.complete(UploadDecision(accepted = false, clearExisting = false))
        }
    }

    private fun handleRequest(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return try {
            when {
                session.method == NanoHTTPD.Method.GET && session.uri == "/api/status" -> statusResponse()
                session.method == NanoHTTPD.Method.POST && session.uri == "/api/v2/pair" -> pair(session)
                session.uri == "/api/sync/upload" && session.method == NanoHTTPD.Method.POST -> upload(session)
                session.uri == "/api/sync/download" && session.method == NanoHTTPD.Method.GET -> download(session)
                else -> jsonResponse(NanoHTTPD.Response.Status.NOT_FOUND, errorJson("not_found", "Endpoint bulunamadı."))
            }
        } catch (_: Exception) {
            jsonResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                errorJson("internal_error", "Eşitleme isteği tamamlanamadı.")
            )
        }
    }

    private fun statusResponse(): NanoHTTPD.Response {
        val notesCount = runBlocking(Dispatchers.IO) { noteRepository.getAllNotes().size }
        val body = JSONObject().apply {
            put("status", "ok")
            put("appName", "Not Defterim Android")
            put("version", BuildConfig.VERSION_NAME)
            put("notesCount", notesCount)
            put("requiresPairing", true)
            put("port", server.get()?.listeningPort ?: DEFAULT_PORT)
        }
        return jsonResponse(NanoHTTPD.Response.Status.OK, body.toString())
    }

    @Synchronized
    private fun pair(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val suppliedToken = header(session, "x-pairing-token").orEmpty()
        val expectedDigest = pairingTokenDigest
        if (session.headers["content-length"]?.toLongOrNull() !in listOf(null, 0L) ||
            header(session, "transfer-encoding") != null
        ) {
            return jsonResponse(NanoHTTPD.Response.Status.BAD_REQUEST, errorJson("invalid_body", "Pairing isteği gövdesiz olmalıdır."))
        }
        if (expectedDigest == null ||
            System.currentTimeMillis() >= pairingExpiresAt ||
            !MessageDigest.isEqual(expectedDigest, sha256(suppliedToken))
        ) {
            return jsonResponse(NanoHTTPD.Response.Status.UNAUTHORIZED, errorJson("pairing_expired", "QR eşleşme kodu geçersiz veya süresi dolmuş."))
        }

        pairingTokenDigest = null
        val token = randomToken()
        val expiresAt = System.currentTimeMillis() + SESSION_TTL_MILLIS
        sessions[sha256(token).toHex()] = expiresAt
        val response = JSONObject().apply {
            put("version", 2)
            put("session_token", token)
            put("expires_at", expiresAt)
        }
        return jsonResponse(NanoHTTPD.Response.Status.OK, response.toString())
    }

    private fun download(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (!isAuthorized(session)) {
            return jsonResponse(NanoHTTPD.Response.Status.UNAUTHORIZED, errorJson("unauthorized", "Eşitleme oturumu geçersiz veya süresi dolmuş."))
        }
        val (zipBytes, notesCount, _) = runBlocking(Dispatchers.IO) { backupManager.buildZipArchiveBytes() }
        if (zipBytes.isEmpty() || zipBytes.size > MAX_ZIP_BYTES) {
            return jsonResponse(NanoHTTPD.Response.Status.PAYLOAD_TOO_LARGE, errorJson("archive_too_large", "Yedek paketi 50 MiB sınırını aşıyor."))
        }
        val response = NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/zip",
            zipBytes.inputStream(),
            zipBytes.size.toLong()
        )
        response.addHeader("X-Notes-Count", notesCount.toString())
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun upload(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (!isAuthorized(session)) {
            return jsonResponse(NanoHTTPD.Response.Status.UNAUTHORIZED, errorJson("unauthorized", "Eşitleme oturumu geçersiz veya süresi dolmuş."))
        }
        val contentLength = session.headers["content-length"]?.toLongOrNull()
            ?: return jsonResponse(NanoHTTPD.Response.Status.LENGTH_REQUIRED, errorJson("length_required", "İçerik uzunluğu belirtilmelidir."))
        if (contentLength !in 1..MAX_ZIP_BYTES) {
            return jsonResponse(NanoHTTPD.Response.Status.PAYLOAD_TOO_LARGE, errorJson("archive_too_large", "ZIP aktarımı 50 MiB sınırını aşamaz."))
        }
        if (!header(session, "content-type").orEmpty().startsWith("application/zip", ignoreCase = true)) {
            return jsonResponse(NanoHTTPD.Response.Status.BAD_REQUEST, errorJson("invalid_content_type", "Yalnızca ZIP yedekleri kabul edilir."))
        }
        val requestedClearExisting = when (header(session, "x-clear-existing")?.lowercase()) {
            null, "false" -> false
            "true" -> true
            else -> return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                errorJson("invalid_clear_existing", "X-Clear-Existing yalnızca true veya false olabilir.")
            )
        }
        val file = File.createTempFile("sync-upload-", ".zip", context.cacheDir)
        try {
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var received = 0L
                while (received < contentLength) {
                    val read = session.inputStream.read(buffer, 0, minOf(buffer.size.toLong(), contentLength - received).toInt())
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    received += read
                    _uploadProgress.value = SyncUploadProgress(received, contentLength)
                }
                if (received != contentLength) {
                    return jsonResponse(NanoHTTPD.Response.Status.BAD_REQUEST, errorJson("invalid_length", "Aktarılan ZIP boyutu içerik uzunluğuyla eşleşmiyor."))
                }
            }

            val notesCount = inspectZip(file)
                ?: return jsonResponse(NanoHTTPD.Response.Status.BAD_REQUEST, errorJson("invalid_archive", "Geçerli Not Defterim ZIP yedeği değil."))
            val publicRequest = IncomingSyncTransfer(
                id = randomToken(),
                notesCount = notesCount,
                sizeBytes = contentLength,
                requestedClearExisting = requestedClearExisting
            )
            val pending = PendingUpload(publicRequest, file, CompletableDeferred())
            if (!approval.compareAndSet(null, pending)) {
                return jsonResponse(NanoHTTPD.Response.Status.CONFLICT, errorJson("transfer_pending", "Başka bir aktarım onayı bekliyor."))
            }
            _incomingTransfer.value = publicRequest
            val decision = runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(UPLOAD_APPROVAL_TTL_MILLIS) { pending.decision.await() }
            }
            _incomingTransfer.value = null
            if (decision == null ||
                !decision.accepted ||
                (requestedClearExisting && !decision.clearExisting)
            ) {
                return jsonResponse(NanoHTTPD.Response.Status.FORBIDDEN, errorJson("transfer_not_approved", "Aktarım kullanıcı tarafından onaylanmadı."))
            }

            val restoreResult = runBlocking(Dispatchers.IO) {
                FileInputStream(file).use { input ->
                    backupManager.restoreBackup(
                        inputStream = input,
                        clearExisting = decision.clearExisting
                    )
                }
            }
            return when (restoreResult) {
                is RestoreResult.Success -> {
                    _transferResult.value = SyncHostTransferResult(
                        success = true,
                        message = "Aktarım başarılı: ${restoreResult.notesCount} not ve ${restoreResult.attachmentsCount} dosya içe aktarıldı."
                    )
                    jsonResponse(
                        NanoHTTPD.Response.Status.OK,
                        JSONObject().apply {
                            put("success", true)
                            put("notesCount", restoreResult.notesCount)
                        }.toString()
                    )
                }
                is RestoreResult.NeedsPassword -> {
                    val message = "Şifreli yedek için kasa parolası gerekir."
                    _transferResult.value = SyncHostTransferResult(false, message)
                    jsonResponse(NanoHTTPD.Response.Status.BAD_REQUEST, errorJson("password_required", message))
                }
                is RestoreResult.Error -> {
                    _transferResult.value = SyncHostTransferResult(false, restoreResult.message)
                    jsonResponse(NanoHTTPD.Response.Status.BAD_REQUEST, errorJson("restore_failed", restoreResult.message))
                }
            }
        } finally {
            val current = approval.get()
            if (current?.file == file && approval.compareAndSet(current, null)) {
                _incomingTransfer.value = null
            }
            file.delete()
            _uploadProgress.value = SyncUploadProgress()
        }
    }

    private fun isAuthorized(session: NanoHTTPD.IHTTPSession): Boolean {
        val token = header(session, "authorization")
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.takeIf(SyncHostValidator::isValidPairingToken)
            ?: return false
        val digest = sha256(token).toHex()
        val expiry = sessions[digest] ?: return false
        if (System.currentTimeMillis() >= expiry) {
            sessions.remove(digest)
            return false
        }
        return true
    }

    private fun inspectZip(file: File): Int? {
        return try {
            ZipInputStream(FileInputStream(file)).use { zip ->
                var expandedBytes = 0L
                var entryCount = 0
                var hasNotes = false
                var notesCount: Int? = null
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) return null
                    if (entry.name == "notes.json") hasNotes = true
                    val metadata = entry.name == "backup_info.json"
                    if (metadata && notesCount != null) return null
                    val perEntryLimit = when (entry.name) {
                        "backup_info.json" -> MAX_METADATA_BYTES.toLong()
                        "notes.json", "categories.json", "note_history.json", "templates.json" -> MAX_JSON_ENTRY_BYTES
                        else -> MAX_SINGLE_ENTRY_BYTES
                    }
                    var entryBytes = 0L
                    val metadataBytes = if (metadata) ByteArrayOutputStream() else null
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        expandedBytes += read
                        if (entryBytes > perEntryLimit || expandedBytes > MAX_EXPANDED_BYTES) return null
                        metadataBytes?.write(buffer, 0, read)
                    }
                    if (metadataBytes != null) {
                        val parsedCount = JSONObject(
                            String(metadataBytes.toByteArray(), Charsets.UTF_8)
                        ).optInt("notesCount", -1)
                        if (parsedCount < 0) return null
                        notesCount = parsedCount
                    }
                    zip.closeEntry()
                }
                notesCount?.takeIf { hasNotes }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun header(session: NanoHTTPD.IHTTPSession, name: String): String? =
        session.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun jsonResponse(status: NanoHTTPD.Response.IStatus, body: String): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", body).apply {
            addHeader("Cache-Control", "no-store")
        }

    private fun errorJson(code: String, message: String): String =
        JSONObject().put("error", JSONObject().put("code", code).put("message", message)).toString()

    private fun randomToken(): String {
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }

    private data class PendingUpload(
        val publicRequest: IncomingSyncTransfer,
        val file: File,
        val decision: CompletableDeferred<UploadDecision>
    )

    private data class UploadDecision(
        val accepted: Boolean,
        val clearExisting: Boolean
    )

    private class SecureNanoServer(
        hostname: String,
        port: Int,
        socketFactory: javax.net.ssl.SSLServerSocketFactory,
        private val requestHandler: (NanoHTTPD.IHTTPSession) -> NanoHTTPD.Response
    ) : NanoHTTPD(hostname, port) {
        init {
            val enabledProtocols = socketFactory.createServerSocket().use { socket ->
                (socket as? javax.net.ssl.SSLServerSocket)?.supportedProtocols
                    ?.filter { it == "TLSv1.3" || it == "TLSv1.2" }
                    ?.toTypedArray()
                    .orEmpty()
            }
            require(enabledProtocols.isNotEmpty()) { "Bu cihaz TLS 1.2 veya üzerini desteklemiyor." }
            makeSecure(socketFactory, enabledProtocols)
        }

        override fun serve(session: IHTTPSession): Response = requestHandler(session)

    }

    private companion object {
        const val DEFAULT_PORT = 8765
        const val MAX_ZIP_BYTES = 50L * 1024 * 1024
        const val MAX_METADATA_BYTES = 64 * 1024
        const val MAX_JSON_ENTRY_BYTES = 16L * 1024 * 1024
        const val MAX_SINGLE_ENTRY_BYTES = 64L * 1024 * 1024
        const val MAX_EXPANDED_BYTES = 200L * 1024 * 1024
        const val MAX_ZIP_ENTRIES = 10_000
        const val PAIRING_TTL_MILLIS = 2 * 60 * 1000L
        const val SESSION_TTL_MILLIS = 15 * 60 * 1000L
        const val UPLOAD_APPROVAL_TTL_MILLIS = 2 * 60 * 1000L

    }
}
