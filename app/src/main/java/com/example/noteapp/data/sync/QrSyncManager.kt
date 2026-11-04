package com.example.noteapp.data.sync

import org.json.JSONObject
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class SyncQrPayload(
    val ip: String,
    val port: Int = 8765,
    val pin: String
) {
    val fullUrl: String
        get() = "http://$ip:$port"
}

@Singleton
class QrSyncManager @Inject constructor() {

    /**
     * QR koddan okunan ham metni ayrıştırır.
     * JSON ({"type":"noteapp_sync","ip":"...","port":8765,"pin":"..."})
     * URI (noteapp://sync?ip=...&pin=...)
     * veya basit metin (ip:port#pin) formatlarını destekler.
     */
    fun parseSyncPayload(rawText: String): SyncQrPayload? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null

        // 1. JSON Formatı
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return try {
                val json = JSONObject(trimmed)
                val type = json.optString("type", "")
                if (type == "noteapp_sync" || json.has("ip") || json.has("pin")) {
                    val ip = json.optString("ip", "").trim()
                    val port = json.optInt("port", 8765)
                    val pin = json.optString("pin", "").trim()
                    if (ip.isNotEmpty() && pin.isNotEmpty()) {
                        SyncQrPayload(ip = ip, port = port, pin = pin)
                    } else null
                } else null
            } catch (_: Exception) {
                null
            }
        }

        // 2. URI Formatı (noteapp://sync?ip=...&port=8765&pin=...)
        if (trimmed.startsWith("noteapp://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
            return try {
                val uri = URI(trimmed)
                val queryParams = uri.query?.split("&")?.associate {
                    val parts = it.split("=")
                    if (parts.size == 2) parts[0] to parts[1] else "" to ""
                } ?: emptyMap()

                val ip = queryParams["ip"] ?: uri.host ?: ""
                val port = queryParams["port"]?.toIntOrNull() ?: if (uri.port != -1) uri.port else 8765
                val pin = queryParams["pin"] ?: ""

                if (ip.isNotEmpty() && pin.isNotEmpty()) {
                    SyncQrPayload(ip = ip, port = port, pin = pin)
                } else null
            } catch (_: Exception) {
                null
            }
        }

        // 3. Basit format: 192.168.1.15:8765#1234 veya 192.168.1.15#1234
        if (trimmed.contains("#")) {
            val parts = trimmed.split("#")
            if (parts.size == 2) {
                val hostPart = parts[0].trim()
                val pin = parts[1].trim()
                val ip = hostPart.substringBefore(":")
                val port = hostPart.substringAfter(":", "8765").toIntOrNull() ?: 8765
                if (ip.isNotEmpty() && pin.isNotEmpty()) {
                    return SyncQrPayload(ip = ip, port = port, pin = pin)
                }
            }
        }

        return null
    }
}
