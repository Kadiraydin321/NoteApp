package com.example.noteapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Arrays
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class UploadedVaultInspectionTest {

    private val vaultFile = File("C:/Users/Birin/.gemini/antigravity/brain/b91a0902-06ca-4b38-86bb-b4bd528047b8/.user_uploaded/media_1793806061875_e56aa220.notevault")

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    private fun sha256(salt: ByteArray, candidate: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(candidate.toByteArray(StandardCharsets.UTF_8))
        return md.digest()
    }

    @Test
    fun testInspectUploadedVaultHeader() {
        println("==================================================")
        println("1. YÜKLENEN DOSYA BAŞLIK ANALİZİ")
        println("Dosya Yolu: ${vaultFile.absolutePath}")
        assertTrue("Yüklenen .notevault dosyası bulunamadı!", vaultFile.exists())
        println("Dosya Boyutu: ${vaultFile.length()} bayt")

        val bytes = vaultFile.readBytes()
        assertTrue("Dosya boyutu minimum başlık uzunluğundan (68 bayt) küçük!", bytes.size >= 68)

        // 1. Magic (8B)
        val magicBytes = bytes.copyOfRange(0, 8)
        val magicString = String(magicBytes, StandardCharsets.US_ASCII)
        println("Magic String: '$magicString'")
        println("Magic Hex: ${bytesToHex(magicBytes)}")
        assertEquals("NOTEVLT1", magicString)

        // 2. Salt (16B)
        val salt = bytes.copyOfRange(8, 24)
        println("Salt Boyutu: ${salt.size} bayt")
        println("Salt Hex: ${bytesToHex(salt)}")
        assertEquals(16, salt.size)

        // 3. IV (12B)
        val iv = bytes.copyOfRange(24, 36)
        println("IV Boyutu: ${iv.size} bayt")
        println("IV Hex: ${bytesToHex(iv)}")
        assertEquals(12, iv.size)

        // 4. StoredHash (32B)
        val storedHash = bytes.copyOfRange(36, 68)
        println("StoredHash Boyutu: ${storedHash.size} bayt")
        println("StoredHash Hex: ${bytesToHex(storedHash)}")
        assertEquals(32, storedHash.size)

        // 5. Ciphertext (kalan)
        val ciphertext = bytes.copyOfRange(68, bytes.size)
        println("Ciphertext Boyutu: ${ciphertext.size} bayt")
        println("==================================================")
    }

    @Test
    fun testFindPasswordAndDecrypt() {
        println("==================================================")
        println("2. ŞİFRE / PIN EŞLEŞTİRME VE DEŞİFRE TESTİ")
        assertTrue("Yüklenen .notevault dosyası bulunamadı!", vaultFile.exists())

        val bytes = vaultFile.readBytes()
        val salt = bytes.copyOfRange(8, 24)
        val iv = bytes.copyOfRange(24, 36)
        val storedHash = bytes.copyOfRange(36, 68)
        val ciphertext = bytes.copyOfRange(68, bytes.size)

        var matchedPassword: String? = null

        // 1. Özel / Yaygın Kelimeler
        val candidateWords = listOf(
            "", "null", "NULL", "1234", "0000", "1111", "123456", "12345678", "000000",
            "password", "admin", "kasa", "notdefterim", "not", "notlar", "master", "pin", "vault",
            "secret", "123321", "654321", "987654", "2580", "159753", "qwert", "qwerty",
            "NotDefterim", "Kasa", "Sifre", "şifre", "gizli", "Android", "123"
        )

        for (cand in candidateWords) {
            val hash = sha256(salt, cand)
            if (Arrays.equals(hash, storedHash)) {
                matchedPassword = cand
                println(">>> ADAY KELİMELERDEN EŞLEŞEN ŞİFRE BULUNDU: '$matchedPassword'")
                break
            }
        }

        // 2. 4 Haneli PIN Taraması (0000 - 9999)
        if (matchedPassword == null) {
            println("4 haneli PIN'ler taranıyor (0000..9999)...")
            for (i in 0..9999) {
                val pinFormatted = "%04d".format(i)
                if (Arrays.equals(sha256(salt, pinFormatted), storedHash)) {
                    matchedPassword = pinFormatted
                    println(">>> 4 HANELİ PIN BULUNDU: '$matchedPassword'")
                    break
                }
                val pinRaw = i.toString()
                if (pinRaw != pinFormatted && Arrays.equals(sha256(salt, pinRaw), storedHash)) {
                    matchedPassword = pinRaw
                    println(">>> 1-4 BASAMAKLI PIN BULUNDU: '$matchedPassword'")
                    break
                }
            }
        }

        // 3. 5 Haneli PIN Taraması (00000 - 99999)
        if (matchedPassword == null) {
            println("5 haneli PIN'ler taranıyor (00000..99999)...")
            for (i in 0..99999) {
                val pinFormatted = "%05d".format(i)
                if (Arrays.equals(sha256(salt, pinFormatted), storedHash)) {
                    matchedPassword = pinFormatted
                    println(">>> 5 HANELİ PIN BULUNDU: '$matchedPassword'")
                    break
                }
            }
        }

        // 4. 6 Haneli PIN Taraması (000000 - 999999)
        if (matchedPassword == null) {
            println("6 haneli PIN'ler taranıyor (000000..999999)...")
            for (i in 0..999999) {
                val pinFormatted = "%06d".format(i)
                if (Arrays.equals(sha256(salt, pinFormatted), storedHash)) {
                    matchedPassword = pinFormatted
                    println(">>> 6 HANELİ PIN BULUNDU: '$matchedPassword'")
                    break
                }
                if (i % 200000 == 0 && i > 0) {
                    println("... $i / 1.000.000 tarandı ...")
                }
            }
        }

        println("Eşleşen Parola Sonucu: ${matchedPassword ?: "BULUNAMADI (Sözlük/Daha Uzun Şifre Gerekli)"}")
        assertNotNull("Kasa şifresi veya PIN eşleşmesi tespit edilmelidir!", matchedPassword)

        // Deşifre Etme Girişimi:
        // Iterasyon adayları: 65536 (BackupManager), 12000 (VaultExportEngine), 10000, 1000
        val iterationCandidates = listOf(65536, 12000, 10000, 1000)
        var decryptedBytes: ByteArray? = null
        var usedIterations = 0

        for (iterations in iterationCandidates) {
            try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val spec = PBEKeySpec(matchedPassword!!.toCharArray(), salt, iterations, 256)
                val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
                decryptedBytes = cipher.doFinal(ciphertext)
                usedIterations = iterations
                println(">>> BAŞARI: AES-GCM Deşifreleme $iterations iterasyon ile tamamlandı!")
                break
            } catch (e: Exception) {
                // Denemeye devam et
            }
        }

        assertNotNull("Şifre doğru ancak PBKDF2/AES-GCM deşifre edilemedi!", decryptedBytes)
        println("Deşifre edilen veri boyutu: ${decryptedBytes!!.size} bayt")

        // Veri formatını incele (ZIP mi JSON mı?)
        val isZip = decryptedBytes.size >= 4 &&
                decryptedBytes[0] == 0x50.toByte() &&
                decryptedBytes[1] == 0x4B.toByte()

        if (isZip) {
            println("Veri Formatı: ZIP Arşivi (BackupManager formatı)")
            val zis = ZipInputStream(ByteArrayInputStream(decryptedBytes))
            var entry = zis.nextEntry
            var entryCount = 0
            while (entry != null) {
                entryCount++
                println("  - ZIP Girdisi #$entryCount: ${entry.name} (${entry.size} bayt)")
                if (entry.name == "backup_info.json" || entry.name == "categories.json" || entry.name == "notes.json") {
                    val content = zis.readBytes().toString(StandardCharsets.UTF_8)
                    val preview = if (content.length > 200) content.substring(0, 200) + "..." else content
                    println("    Önizleme: $preview")
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            println("Toplam Arşiv Girdisi: $entryCount")
            assertTrue("ZIP arşivi boş olmamalıdır!", entryCount > 0)
        } else {
            val text = String(decryptedBytes, StandardCharsets.UTF_8)
            println("Veri Formatı: Düz Metin / JSON (VaultExportEngine formatı)")
            val preview = if (text.length > 300) text.substring(0, 300) + "..." else text
            println("İçerik Önizleme: $preview")
            assertTrue("JSON içeriği 'notes' veya 'version' barındırmalıdır", text.contains("version") || text.contains("notes"))
        }
        println("==================================================")
    }
}
