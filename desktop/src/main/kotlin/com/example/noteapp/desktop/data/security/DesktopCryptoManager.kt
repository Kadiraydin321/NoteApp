package com.example.noteapp.desktop.data.security

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

object DesktopCryptoManager {
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val PBKDF2_ITERATIONS = 65536
    private const val KEY_LEN = 256
    private const val PREFIX = "ENC_GCM_V1:"

    private val configFile get() = File(com.example.noteapp.desktop.data.local.DatabaseHelper.appDir, "vault_config.json")

    var isVaultUnlocked = false
        private set

    private var sessionKey: ByteArray? = null

    init {
        // Vault config dizini hazırla
        configFile.parentFile?.mkdirs()
    }

    fun hasMasterPassword(): Boolean {
        if (!configFile.exists()) return false
        return try {
            val json = JSONObject(configFile.readText())
            json.has("hash") && json.has("salt")
        } catch (_: Exception) {
            false
        }
    }

    fun setupMasterPassword(password: String): Boolean {
        if (password.length < 4) return false
        try {
            val random = SecureRandom()
            val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }

            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            md.update(password.toByteArray(Charsets.UTF_8))
            val hash = md.digest()

            val json = JSONObject().apply {
                put("salt", Base64.getEncoder().encodeToString(salt))
                put("hash", Base64.getEncoder().encodeToString(hash))
                put("createdAt", System.currentTimeMillis())
            }
            configFile.writeText(json.toString(2))

            // Hemen oturumu aç
            return unlockVault(password)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun unlockVault(password: String): Boolean {
        if (!hasMasterPassword()) return false
        try {
            val json = JSONObject(configFile.readText())
            val salt = Base64.getDecoder().decode(json.getString("salt"))
            val storedHash = Base64.getDecoder().decode(json.getString("hash"))

            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            md.update(password.toByteArray(Charsets.UTF_8))
            val computedHash = md.digest()

            if (MessageDigest.isEqual(storedHash, computedHash)) {
                // PBKDF2 ile AES-256 anahtarını türet
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LEN)
                sessionKey = factory.generateSecret(spec).encoded
                isVaultUnlocked = true
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun lockVault() {
        sessionKey = null
        isVaultUnlocked = false
    }

    fun encryptContent(plainText: String): String {
        if (plainText.isEmpty()) return plainText
        val keyBytes = sessionKey ?: return plainText
        return try {
            val random = SecureRandom()
            val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val byteBuffer = ByteBuffer.allocate(iv.size + cipherBytes.size)
            byteBuffer.put(iv)
            byteBuffer.put(cipherBytes)

            PREFIX + Base64.getEncoder().encodeToString(byteBuffer.array())
        } catch (e: Exception) {
            e.printStackTrace()
            plainText
        }
    }

    fun decryptContent(cipherText: String): String {
        if (!cipherText.startsWith(PREFIX)) return cipherText
        val keyBytes = sessionKey ?: return "[Korumalı Kasa İçeriği - Kilit Açınız]"
        return try {
            val base64Data = cipherText.removePrefix(PREFIX)
            val combined = Base64.getDecoder().decode(base64Data)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(128, combined, 0, IV_LEN)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(combined, IV_LEN, combined.size - IV_LEN)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            "[Şifreli İçerik Çözülemedi]"
        }
    }

    fun isEncrypted(text: String): Boolean = text.startsWith(PREFIX)
}
