package com.example.noteapp

import com.example.noteapp.data.security.NoteCryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class NoteCryptoManagerTest {

    private lateinit var primaryKey: SecretKey
    private lateinit var secondaryKey: SecretKey
    private lateinit var cryptoManager: NoteCryptoManager

    @Before
    fun setup() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        primaryKey = keyGen.generateKey()
        secondaryKey = keyGen.generateKey()
        cryptoManager = NoteCryptoManager(keyProvider = { primaryKey })
    }

    @Test
    fun `encrypt and decrypt roundtrip restores original plain text`() {
        val plainText = "Gizli Not İçeriği: Çok Gizli 12345!"
        val encrypted = cryptoManager.encrypt(plainText)

        assertTrue(cryptoManager.isEncrypted(encrypted))
        assertTrue(encrypted.startsWith("ENC_GCM_V1:"))
        assertNotEquals(plainText, encrypted)

        val decrypted = cryptoManager.decrypt(encrypted)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun `encrypt is idempotent and never re-encrypts already encrypted text`() {
        val plainText = "Bu metin sadece bir kez şifrelenmeli."
        val firstEncrypted = cryptoManager.encrypt(plainText)

        // İkinci kez şifrelenmeye çalışıldığında aynı metin dönmeli
        val secondEncrypted = cryptoManager.encrypt(firstEncrypted)
        assertEquals(firstEncrypted, secondEncrypted)

        // Üçüncü kez denenirse yine aynı kalmalı
        val thirdEncrypted = cryptoManager.encrypt(secondEncrypted)
        assertEquals(firstEncrypted, thirdEncrypted)
    }

    @Test
    fun `encrypt returns empty string when given empty input`() {
        val result = cryptoManager.encrypt("")
        assertEquals("", result)
    }

    @Test
    fun `decrypt returns original text when text is not encrypted`() {
        val plain = "Bu henüz kilitlenmemiş normal bir not."
        val result = cryptoManager.decrypt(plain)
        assertEquals(plain, result)

        val empty = cryptoManager.decrypt("")
        assertEquals("", empty)
    }

    @Test
    fun `decrypt recursively resolves multi-layered encrypted content`() {
        val plainText = "Çok katmanlı şifrelenmiş miras içerik"

        // Manuel olarak 3 katman tekil AES şifreleme simüle edelim
        fun singleEncryptRaw(text: String, key: SecretKey): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            val buf = ByteBuffer.allocate(iv.size + cipherBytes.size)
            buf.put(iv)
            buf.put(cipherBytes)
            return "ENC_GCM_V1:" + Base64.getEncoder().encodeToString(buf.array())
        }

        val layer1 = singleEncryptRaw(plainText, primaryKey)
        val layer2 = singleEncryptRaw(layer1, primaryKey)
        val layer3 = singleEncryptRaw(layer2, primaryKey)

        assertTrue(layer3.startsWith("ENC_GCM_V1:"))

        // Tek bir decrypt çağrısı tüm 3 katmanı özyinelemeli çözüp orijinal düz metne ulaşmalı
        val fullyDecrypted = cryptoManager.decrypt(layer3)
        assertEquals(plainText, fullyDecrypted)
    }

    @Test
    fun `decrypt with invalid key fails authentication and returns fallback message`() {
        val plainText = "Anahtar A ile şifrelendi"
        val encrypted = cryptoManager.encrypt(plainText)

        // Farklı bir anahtarla çalışan ikinci yönetici
        val otherManager = NoteCryptoManager(keyProvider = { secondaryKey })

        val result = otherManager.decrypt(encrypted)
        assertEquals("[Korumalı İçerik Çözülemedi]", result)
    }

    @Test
    fun `decrypt returns fallback message when ciphertext is tampered`() {
        val plainText = "Bütünlük kontrolü testi"
        val encrypted = cryptoManager.encrypt(plainText)
        val rawBase64 = encrypted.removePrefix("ENC_GCM_V1:")
        val rawBytes = Base64.getDecoder().decode(rawBase64)

        // Son baytı (GCM auth tag) manipüle edelim
        rawBytes[rawBytes.size - 1] = (rawBytes[rawBytes.size - 1].toInt() xor 0xFF).toByte()
        val tamperedCipher = "ENC_GCM_V1:" + Base64.getEncoder().encodeToString(rawBytes)

        val result = cryptoManager.decrypt(tamperedCipher)
        assertEquals("[Korumalı İçerik Çözülemedi]", result)
    }

    @Test
    fun `decrypt handles malformed base64 and truncated payload gracefully`() {
        // Geçersiz base64
        val malformed = "ENC_GCM_V1:***NOT_BASE64***"
        assertEquals("[Korumalı İçerik Çözülemedi]", cryptoManager.decrypt(malformed))

        // 12 bayttan kısa veri (IV bile eksik)
        val truncated = "ENC_GCM_V1:" + Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        assertEquals("[Korumalı İçerik Çözülemedi]", cryptoManager.decrypt(truncated))
    }

    @Test
    fun `isEncrypted accurately checks prefix`() {
        assertTrue(cryptoManager.isEncrypted("ENC_GCM_V1:abc123xyz"))
        assertFalse(cryptoManager.isEncrypted("ENC_CBC_V1:abc123xyz"))
        assertFalse(cryptoManager.isEncrypted("Düz metin"))
        assertFalse(cryptoManager.isEncrypted(""))
    }

    @Test
    fun `random IV ensures identical plaintext produces distinct ciphertexts`() {
        val plain = "Aynı metin"
        val enc1 = cryptoManager.encrypt(plain)
        val enc2 = cryptoManager.encrypt(plain)

        // IV rastgele olduğu için iki şifreli metin birbirinden farklı olmalı
        assertNotEquals(enc1, enc2)

        // Ama ikisi de aynı düz metne çözülebilmeli
        assertEquals(plain, cryptoManager.decrypt(enc1))
        assertEquals(plain, cryptoManager.decrypt(enc2))
    }

    @Test
    fun `encrypt and decrypt preserves unicode, emojis and long texts`() {
        val richText = """
            # Başlık: Türkçe Karakterler (ç, ğ, ı, ö, ş, ü, İ, Ğ)
            🔒 Güvenlik Notu: Şifreler ve Biyometrik Veriler 🚀
            ---
            * Uzun metin testi: ${"Lorem ipsum dolor sit amet. ".repeat(100)}
        """.trimIndent()

        val encrypted = cryptoManager.encrypt(richText)
        val decrypted = cryptoManager.decrypt(encrypted)

        assertEquals(richText, decrypted)
    }
}
