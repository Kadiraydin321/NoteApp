package com.example.noteapp.desktop.data.sync

import com.example.noteapp.desktop.data.backup.DesktopBackupManager
import com.example.noteapp.desktop.data.local.DesktopNoteDao
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors

data class SyncServerState(
    val isRunning: Boolean,
    val localIp: String,
    val port: Int,
    val pairingPin: String,
    val lastSyncMessage: String? = null
)

class DesktopSyncServer(
    private val dao: DesktopNoteDao,
    private val backupManager: DesktopBackupManager,
    private val port: Int = 8765,
    private val onStateChanged: (SyncServerState) -> Unit = {}
) {
    private var server: HttpServer? = null
    var pairingPin: String = generatePin()
        private set

    var lastMessage: String? = null
        private set

    companion object {
        fun generatePin(): String {
            return (1000..9999).random().toString()
        }

        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback || !iface.isUp) continue
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val host = addr.hostAddress
                            if (!host.startsWith("127.")) {
                                return host
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "127.0.0.1"
        }
    }

    fun isRunning(): Boolean = server != null

    fun start() {
        if (server != null) return
        try {
            pairingPin = generatePin()
            val localIp = getLocalIpAddress()
            val addr = InetSocketAddress(port)
            server = HttpServer.create(addr, 0).apply {
                executor = Executors.newFixedThreadPool(4)

                // 1. Status API
                createContext("/api/status", StatusHandler())

                // 2. Upload / Import Backup API
                createContext("/api/sync/upload", UploadHandler())

                // 3. Download / Export Backup API
                createContext("/api/sync/download", DownloadHandler())

                // 4. Web Sync Arayüzü (Telefonda tarayıcı ile doğrudan açılabilir)
                createContext("/", WebInterfaceHandler())

                start()
            }
            lastMessage = "Wi-Fi Sunucusu aktif: http://$localIp:$port"
            notifyState()
        } catch (e: Exception) {
            e.printStackTrace()
            lastMessage = "Sunucu başlatılamadı: ${e.message}"
            notifyState()
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
        lastMessage = "Sunucu durduruldu"
        notifyState()
    }

    fun refreshPin() {
        pairingPin = generatePin()
        notifyState()
    }

    private fun notifyState() {
        onStateChanged(
            SyncServerState(
                isRunning = isRunning(),
                localIp = getLocalIpAddress(),
                port = port,
                pairingPin = pairingPin,
                lastSyncMessage = lastMessage
            )
        )
    }

    private inner class StatusHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val notes = dao.getAllNotes()
                val response = JSONObject().apply {
                    put("status", "ok")
                    put("appName", "Not Defterim Masaüstü")
                    put("notesCount", notes.size)
                    put("requiresPin", true)
                }.toString()

                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } catch (e: Exception) {
                exchange.sendResponseHeaders(500, -1)
            }
        }
    }

    private inner class UploadHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
                exchange.responseHeaders.set("Access-Control-Allow-Methods", "POST, OPTIONS")
                exchange.responseHeaders.set("Access-Control-Allow-Headers", "*")
                exchange.sendResponseHeaders(204, -1)
                return
            }

            if (!exchange.requestMethod.equals("POST", ignoreCase = true)) {
                exchange.sendResponseHeaders(405, -1)
                return
            }

            // PIN Doğrulama
            val pinHeader = exchange.requestHeaders.getFirst("X-Sync-PIN")
            if (pinHeader != pairingPin) {
                val err = "Geçersiz Eşleşme PIN kodu!".toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
                exchange.sendResponseHeaders(401, err.size.toLong())
                exchange.responseBody.use { it.write(err) }
                return
            }

            try {
                val tempBackupFile = File.createTempFile("sync_upload_", ".zip")
                exchange.requestBody.use { input ->
                    tempBackupFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val vaultPassword = exchange.requestHeaders.getFirst("X-Vault-Password")
                val clearExisting = exchange.requestHeaders.getFirst("X-Clear-Existing")?.toBoolean() ?: false

                val result = backupManager.importBackup(
                    backupFile = tempBackupFile,
                    clearExisting = clearExisting,
                    vaultPassword = vaultPassword
                )
                tempBackupFile.delete()

                val respJson = when (result) {
                    is com.example.noteapp.desktop.data.backup.RestoreResult.Success -> {
                        lastMessage = "Başarılı senkronizasyon: ${result.notesCount} not alındı."
                        JSONObject().apply {
                            put("success", true)
                            put("notesCount", result.notesCount)
                            put("categoriesCount", result.categoriesCount)
                            put("attachmentsCount", result.attachmentsCount)
                        }
                    }
                    is com.example.noteapp.desktop.data.backup.RestoreResult.Error -> {
                        lastMessage = "Senkronizasyon hatası: ${result.message}"
                        JSONObject().apply {
                            put("success", false)
                            put("error", result.message)
                        }
                    }
                    is com.example.noteapp.desktop.data.backup.RestoreResult.NeedsPassword -> {
                        JSONObject().apply {
                            put("success", false)
                            put("error", "Kasa şifresi gerekiyor.")
                            put("needsPassword", true)
                        }
                    }
                }

                val bytes = respJson.toString().toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                notifyState()
            } catch (e: Exception) {
                e.printStackTrace()
                exchange.sendResponseHeaders(500, -1)
            }
        }
    }

    private inner class DownloadHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val pinHeader = exchange.requestHeaders.getFirst("X-Sync-PIN")
                ?: exchange.requestURI.query?.split("&")?.find { it.startsWith("pin=") }?.removePrefix("pin=")

            if (pinHeader != pairingPin) {
                val err = "Geçersiz Eşleşme PIN kodu!".toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(401, err.size.toLong())
                exchange.responseBody.use { it.write(err) }
                return
            }

            try {
                val (zipBytes, notesCount, attachmentsCount) = backupManager.buildZipArchiveBytes()
                exchange.responseHeaders.set("Content-Type", "application/zip")
                exchange.responseHeaders.set("Content-Disposition", "attachment; filename=\"NoteApp_Sync.zip\"")
                exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
                exchange.sendResponseHeaders(200, zipBytes.size.toLong())
                exchange.responseBody.use { it.write(zipBytes) }
                lastMessage = "$notesCount not ve $attachmentsCount dosya telefona aktarıldı."
                notifyState()
            } catch (e: Exception) {
                e.printStackTrace()
                exchange.sendResponseHeaders(500, -1)
            }
        }
    }

    private inner class WebInterfaceHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            if (path != "/" && path != "/index.html") {
                exchange.sendResponseHeaders(404, -1)
                return
            }

            val html = """
                <!DOCTYPE html>
                <html lang="tr">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Not Defterim - Wi-Fi Eşitleme</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 24px; display: flex; flex-direction: column; align-items: center; }
                        .card { background: #1e293b; border-radius: 16px; padding: 24px; max-width: 480px; width: 100%; box-shadow: 0 10px 25px rgba(0,0,0,0.5); border: 1px solid #334155; }
                        h1 { font-size: 20px; margin-top: 0; color: #38bdf8; display: flex; align-items: center; gap: 8px; }
                        .pin-box { background: #0f172a; padding: 12px; border-radius: 8px; text-align: center; margin-bottom: 20px; border: 1px dashed #64748b; font-size: 14px; color: #94a3b8; }
                        .pin-code { font-size: 28px; font-weight: bold; letter-spacing: 4px; color: #f59e0b; margin-top: 4px; }
                        .section { margin-bottom: 20px; padding-bottom: 20px; border-bottom: 1px solid #334155; }
                        .btn { background: #0284c7; color: white; border: none; padding: 12px 18px; border-radius: 8px; font-size: 15px; font-weight: 600; cursor: pointer; width: 100%; display: block; text-align: center; text-decoration: none; box-sizing: border-box; }
                        .btn:hover { background: #0369a1; }
                        .btn-sec { background: #334155; margin-top: 8px; }
                        .btn-sec:hover { background: #475569; }
                        input[type="text"], input[type="file"], input[type="password"] { width: 100%; padding: 10px; margin-top: 6px; margin-bottom: 12px; border-radius: 6px; border: 1px solid #475569; background: #0f172a; color: white; box-sizing: border-box; }
                        label { font-size: 13px; color: #cbd5e1; }
                        #msg { margin-top: 12px; font-size: 14px; text-align: center; font-weight: 500; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h1>📱 ⇄ 💻 Not Defterim Wi-Fi Eşitleme</h1>
                        <div class="pin-box">
                            Eşleşme PIN Kodu
                            <div class="pin-code">$pairingPin</div>
                        </div>

                        <div class="section">
                            <h3>1. Bilgisayardan Telefona Notları İndir</h3>
                            <p style="font-size:13px; color:#94a3b8;">Bilgisayardaki tüm not ve kategorileri ZIP yedeği olarak indirir.</p>
                            <a class="btn" href="/api/sync/download?pin=$pairingPin">📥 Notları Telefona İndir</a>
                        </div>

                        <div class="section" style="border-bottom:none;">
                            <h3>2. Telefondaki Yedeği Bilgisayara Yükle</h3>
                            <form id="uploadForm">
                                <label>Yedek Dosyası (.zip veya .notevault):</label>
                                <input type="file" id="backupFile" accept=".zip,.notevault" required>
                                <label>Kasa Şifresi (Varsa):</label>
                                <input type="password" id="vaultPass" placeholder="Şifreli kasa ise şifreniz">
                                <label><input type="checkbox" id="clearOld"> Bilgisayardaki eski notları tamamen temizleyip üzerine yaz</label>
                                <div style="margin-top:12px;"></div>
                                <button type="submit" class="btn">📤 Bilgisayara Yükle & Eşitle</button>
                            </form>
                            <div id="msg"></div>
                        </div>
                    </div>

                    <script>
                        document.getElementById('uploadForm').onsubmit = async (e) => {
                            e.preventDefault();
                            const fileInput = document.getElementById('backupFile');
                            const vaultPass = document.getElementById('vaultPass').value;
                            const clearOld = document.getElementById('clearOld').checked;
                            const msg = document.getElementById('msg');

                            if (!fileInput.files[0]) return;
                            msg.style.color = '#38bdf8';
                            msg.textContent = 'Yükleniyor ve veritabanı eşitleniyor...';

                            try {
                                const headers = {
                                    'X-Sync-PIN': '$pairingPin',
                                    'X-Clear-Existing': clearOld ? 'true' : 'false'
                                };
                                if (vaultPass) headers['X-Vault-Password'] = vaultPass;

                                const res = await fetch('/api/sync/upload', {
                                    method: 'POST',
                                    headers: headers,
                                    body: fileInput.files[0]
                                });

                                const data = await res.json();
                                if (data.success) {
                                    msg.style.color = '#4ade80';
                                    msg.textContent = '✅ Başarılı! ' + data.notesCount + ' not bilgisayara aktarıldı.';
                                } else {
                                    msg.style.color = '#f87171';
                                    msg.textContent = '❌ Hata: ' + (data.error || 'Bilinmeyen hata');
                                }
                            } catch (err) {
                                msg.style.color = '#f87171';
                                msg.textContent = 'Bağlantı hatası: ' + err.message;
                            }
                        };
                    </script>
                </body>
                </html>
            """.trimIndent()

            val bytes = html.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
