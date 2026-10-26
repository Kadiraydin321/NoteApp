package com.example.noteapp.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kilitli notların içeriğini yerel veritabanında (SQLite/Room) askeri düzeyde korumak için
 * Android KeyStore destekli AES-256-GCM donanımsal şifreleme yöneticisi.
 *
 * Her şifrelemede rastgele 12-bayt IV (Initialization Vector) üretilir ve
 * 128-bit GCM kimlik doğrulama etiketi (authentication tag) ile bütünlük garanti edilir.
 */
@Singleton
class NoteCryptoManager @Inject constructor() {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "NoteAppMasterSecretKey_v1"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        private const val GCM_TAG_LENGTH = 128
        private const val PREFIX = "ENC_GCM_V1:"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(ALGORITHM, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(PADDING)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // Uygulama seviyesinde Master PIN/Biyometrik zaten doğrulanıyor
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    /**
     * Düz metni AES-256-GCM ile şifreler.
     * Zaten şifreli bir metin gelirse çift şifrelemeyi önlemek için olduğu gibi döndürür (idempotent).
     * Çıktı: "ENC_GCM_V1:<Base64(IV + Ciphertext)>"
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return plainText
        if (isEncrypted(plainText)) return plainText

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getOrCreateSecretKey()
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // IV (12 bayt) + CipherBytes birleştirilir
            val byteBuffer = ByteBuffer.allocate(iv.size + cipherBytes.size)
            byteBuffer.put(iv)
            byteBuffer.put(cipherBytes)
            val combined = byteBuffer.array()

            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw IllegalStateException("Kilitli not şifrelenemedi; düz metin olarak kaydedilmedi.", e)
        }
    }

    /**
     * Şifreli metni çözer. Eğer şifreli değilse orijinal metni döndürür.
     * Çok katmanlı şifrelenmiş içerikleri düz metne ulaşana kadar özyinelemeli (recursive) çözer.
     */
    fun decrypt(cipherText: String): String {
        if (!isEncrypted(cipherText)) return cipherText

        var current = cipherText
        var iterationCount = 0
        while (isEncrypted(current) && iterationCount < 10) {
            val decrypted = decryptSingle(current)
            if (decrypted == current || decrypted == "[Korumalı İçerik Çözülemedi]") {
                return decrypted
            }
            current = decrypted
            iterationCount++
        }
        return current
    }

    private fun decryptSingle(cipherText: String): String {
        if (!cipherText.startsWith(PREFIX)) return cipherText

        return try {
            val base64Data = cipherText.removePrefix(PREFIX)
            val combined = Base64.decode(base64Data, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getOrCreateSecretKey()

            // GCM standart IV boyutu 12 bayttır
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, combined, 0, 12)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(combined, 12, combined.size - 12)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            // Şifre çözülemezse hata mesajı döndür
            "[Korumalı İçerik Çözülemedi]"
        }
    }

    /**
     * Metnin şifreli olup olmadığını kontrol eder.
     */
    fun isEncrypted(text: String): Boolean = text.startsWith(PREFIX)
}
