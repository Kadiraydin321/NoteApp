package com.example.noteapp.data.sync

import org.json.JSONObject
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

data class SyncQrPayload(
    val ip: String,
    val port: Int = 8765,
    val pin: String? = null,
    val version: Int = 1,
    val pairingToken: String? = null,
    val expiresAt: Long? = null,
    val scheme: String = "http",
    val tlsCertSha256: String? = null
) {
    fun toQrJson(): String? {
        if (version != 3 ||
            scheme != "https" ||
            !SyncHostValidator.isAllowedHost(ip) ||
            !SyncHostValidator.isValidPort(port) ||
            tlsCertSha256?.let(SyncHostValidator::isValidCertificateFingerprint) != true ||
            pairingToken?.let(SyncHostValidator::isValidPairingToken) != true ||
            expiresAt == null ||
            expiresAt <= System.currentTimeMillis()
        ) return null
        return JSONObject().apply {
            put("type", "noteapp_sync")
            put("version", 3)
            put("scheme", scheme)
            put("host", ip)
            put("port", port)
            put("tls_cert_sha256", tlsCertSha256)
            put("pairing_token", pairingToken)
            put("expires_at", expiresAt)
        }.toString()
    }

    val fullUrl: String
        get() {
            val urlHost = if (ip.contains(':')) "[$ip]" else ip
            return "$scheme://$urlHost:$port"
        }

    val isSessionPairing: Boolean
        get() = version == 2 || version == 3
}

object SyncHostValidator {
    private val localHostname = Regex(
        "^(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+local\\.?$",
        RegexOption.IGNORE_CASE
    )
    private val pairingTokenPattern = Regex("^[A-Za-z0-9_-]{32}$")
    private val certificateFingerprintPattern = Regex("^[0-9a-f]{64}$")

    fun isAllowedHost(host: String): Boolean {
        if (host.isBlank() || host != host.trim() || host.any { it.isWhitespace() || it == '/' || it == '@' }) {
            return false
        }

        if (localHostname.matches(host)) return true

        val octets = host.split('.')
        if (octets.size == 4) {
            val values = octets.map { it.toIntOrNull() ?: return false }
            if (values.any { it !in 0..255 } || octets.zip(values).any { (part, value) -> part != value.toString() }) {
                return false
            }
            return values[0] == 10 ||
                (values[0] == 172 && values[1] in 16..31) ||
                (values[0] == 192 && values[1] == 168) ||
                values[0] == 127 ||
                (values[0] == 169 && values[1] == 254)
        }

        if (!host.contains(':') || host.contains('%')) return false
        return runCatching {
            val address = InetAddress.getByName(host)
            address is Inet6Address &&
                (address.isLoopbackAddress ||
                    address.isLinkLocalAddress ||
                    ((address.address[0].toInt() and 0xfe) == 0xfc))
        }.getOrDefault(false)
    }

    fun isValidPort(port: Int): Boolean = port in 1..65535

    fun isValidPairingToken(token: String): Boolean = pairingTokenPattern.matches(token)

    fun isValidCertificateFingerprint(fingerprint: String): Boolean =
        certificateFingerprintPattern.matches(fingerprint)
}

@Singleton
class QrSyncManager @Inject constructor() {

    fun parseSyncPayload(rawText: String, nowMillis: Long = System.currentTimeMillis()): SyncQrPayload? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty() || trimmed.length > 4096) return null
        if (trimmed.startsWith("noteapp://", ignoreCase = true)) return parseLegacyUri(trimmed)
        if (!trimmed.startsWith("{")) return parseSimpleHash(trimmed)
        if (!trimmed.endsWith("}")) return null

        return runCatching {
            val json = JSONObject(trimmed)
            if (json.optString("type") != "noteapp_sync") return null

            val version = if (json.has("version")) json.intValue("version") ?: return null else 1
            when (version) {
                1 -> {
                    val host = json.optString("ip", "")
                    val port = if (json.has("port")) json.intValue("port") ?: return null else 8765
                    val pin = json.optString("pin", "")
                    if (!SyncHostValidator.isAllowedHost(host) ||
                        !SyncHostValidator.isValidPort(port) ||
                        !pin.matches(Regex("[0-9]{4}"))
                    ) return null
                    SyncQrPayload(ip = host, port = port, pin = pin, version = 1)
                }
                2 -> {
                    val host = json.optString("host", "")
                    val port = json.intValue("port") ?: return null
                    val token = json.optString("pairing_token", "")
                    val expiresAt = json.longValue("expires_at") ?: return null
                    if (!SyncHostValidator.isAllowedHost(host) ||
                        !SyncHostValidator.isValidPort(port) ||
                        !SyncHostValidator.isValidPairingToken(token) ||
                        expiresAt <= nowMillis
                    ) return null
                    SyncQrPayload(
                        ip = host,
                        port = port,
                        version = 2,
                        pairingToken = token,
                        expiresAt = expiresAt
                    )
                }
                3 -> {
                    val scheme = json.optString("scheme", "")
                    val host = json.optString("host", "")
                    val port = json.intValue("port") ?: return null
                    val fingerprint = json.optString("tls_cert_sha256", "")
                    val token = json.optString("pairing_token", "")
                    val expiresAt = json.longValue("expires_at") ?: return null
                    if (scheme != "https" ||
                        !SyncHostValidator.isAllowedHost(host) ||
                        !SyncHostValidator.isValidPort(port) ||
                        !SyncHostValidator.isValidCertificateFingerprint(fingerprint) ||
                        !SyncHostValidator.isValidPairingToken(token) ||
                        expiresAt <= nowMillis
                    ) return null
                    SyncQrPayload(
                        ip = host,
                        port = port,
                        version = 3,
                        pairingToken = token,
                        expiresAt = expiresAt,
                        scheme = scheme,
                        tlsCertSha256 = fingerprint
                    )
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun parseLegacyUri(value: String): SyncQrPayload? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("noteapp", ignoreCase = true) ||
            !uri.rawAuthority.equals("sync", ignoreCase = true) ||
            !uri.rawPath.isNullOrEmpty() ||
            uri.rawFragment != null
        ) return null

        val query = uri.rawQuery ?: return null
        val parameters = mutableMapOf<String, String>()
        for (component in query.split('&')) {
            val separator = component.indexOf('=')
            if (separator <= 0) return null
            val key = decodeQueryComponent(component.substring(0, separator)) ?: return null
            val parameterValue = decodeQueryComponent(component.substring(separator + 1)) ?: return null
            if (key !in setOf("ip", "port", "pin") || parameters.put(key, parameterValue) != null) {
                return null
            }
        }

        return legacyPayload(
            host = parameters["ip"] ?: return null,
            portValue = parameters["port"],
            pin = parameters["pin"] ?: return null
        )
    }

    private fun parseSimpleHash(value: String): SyncQrPayload? {
        val separator = value.lastIndexOf('#')
        if (separator <= 0 || separator == value.lastIndex) return null
        val pin = value.substring(separator + 1)
        if (!pin.matches(Regex("[0-9]{4}"))) return null

        val endpoint = runCatching { URI("http://${value.substring(0, separator)}") }.getOrNull()
            ?: return null
        if (endpoint.rawUserInfo != null ||
            !endpoint.rawPath.isNullOrEmpty() ||
            endpoint.rawQuery != null ||
            endpoint.rawFragment != null
        ) return null
        return legacyPayload(
            host = endpoint.host ?: return null,
            portValue = endpoint.port.takeIf { it >= 0 }?.toString(),
            pin = pin
        )
    }

    private fun legacyPayload(host: String, portValue: String?, pin: String): SyncQrPayload? {
        val port = portValue?.toIntOrNull() ?: if (portValue == null) 8765 else return null
        if (!SyncHostValidator.isAllowedHost(host) ||
            !SyncHostValidator.isValidPort(port) ||
            !pin.matches(Regex("[0-9]{4}"))
        ) return null
        return SyncQrPayload(ip = host, port = port, pin = pin, version = 1)
    }

    private fun decodeQueryComponent(value: String): String? =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrNull()
}

private fun JSONObject.intValue(name: String): Int? {
    val value = opt(name) as? Number ?: return null
    val asDouble = value.toDouble()
    if (!asDouble.isFinite() || asDouble % 1.0 != 0.0 ||
        asDouble < Int.MIN_VALUE || asDouble > Int.MAX_VALUE
    ) return null
    return asDouble.toInt()
}

private fun JSONObject.longValue(name: String): Long? {
    val value = opt(name) as? Number ?: return null
    val asDouble = value.toDouble()
    if (!asDouble.isFinite() || asDouble % 1.0 != 0.0 ||
        asDouble < 0.0 || asDouble > Long.MAX_VALUE.toDouble()
    ) return null
    return value.toLong()
}
