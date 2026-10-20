package com.example.noteapp

import com.example.noteapp.domain.util.InvalidVaultFileException
import com.example.noteapp.domain.util.VaultExportEngine
import com.example.noteapp.domain.util.VaultNote
import com.example.noteapp.domain.util.WrongPasswordException
import org.junit.Assert.*
import org.junit.Test

class VaultExportEngineTest {

    @Test
    fun createVault_and_readVault_roundTripSuccessfully() {
        val notes = listOf(
            VaultNote(
                title = "Çok Gizli Proje",
                content = "# Başlık\nBu not AES-256 ile korunmaktadır.",
                color = 2,
                isPinned = true,
                isArchived = false,
                isLocked = true,
                categoryName = "Kişisel"
            ),
            VaultNote(
                title = "Alışveriş Listesi",
                content = "- [x] Süt\n- [ ] Ekmek",
                color = 0,
                isPinned = false,
                isArchived = false,
                isLocked = false,
                categoryName = "Günlük"
            )
        )

        val password = "SuperSecretPassword123!"
        val vaultBytes = VaultExportEngine.createVault(password, notes)

        assertTrue("Kasa dosyası geçerli başlığa sahip olmalıdır", VaultExportEngine.isVaultFile(vaultBytes))

        val decryptedPayload = VaultExportEngine.readVault(password, vaultBytes)
        assertEquals(1, decryptedPayload.version)
        assertEquals(2, decryptedPayload.notes.size)

        val note1 = decryptedPayload.notes[0]
        assertEquals("Çok Gizli Proje", note1.title)
        assertEquals("# Başlık\nBu not AES-256 ile korunmaktadır.", note1.content)
        assertEquals(2, note1.color)
        assertTrue(note1.isPinned)
        assertTrue(note1.isLocked)
        assertEquals("Kişisel", note1.categoryName)

        val note2 = decryptedPayload.notes[1]
        assertEquals("Alışveriş Listesi", note2.title)
        assertEquals("- [x] Süt\n- [ ] Ekmek", note2.content)
        assertFalse(note2.isLocked)
        assertEquals("Günlük", note2.categoryName)
    }

    @Test
    fun readVault_rejectsWrongPassword() {
        val notes = listOf(
            VaultNote(title = "Gizli", content = "İçerik")
        )
        val vaultBytes = VaultExportEngine.createVault("DogruSifre123", notes)

        try {
            VaultExportEngine.readVault("YanlisSifre456", vaultBytes)
            fail("Yanlış şifre ile WrongPasswordException fırlatılmalıydı.")
        } catch (e: WrongPasswordException) {
            assertTrue(e.message?.contains("şifre hatalı", ignoreCase = true) == true)
        }
    }

    @Test
    fun isVaultFile_identifiesInvalidHeaders() {
        assertFalse(VaultExportEngine.isVaultFile(byteArrayOf(1, 2, 3)))
        assertFalse(VaultExportEngine.isVaultFile("PK34ZIP".toByteArray()))
        assertFalse(VaultExportEngine.isVaultFile(ByteArray(100)))

        val validHeaderBytes = ByteArray(100)
        System.arraycopy(VaultExportEngine.MAGIC_HEADER, 0, validHeaderBytes, 0, 8)
        assertTrue(VaultExportEngine.isVaultFile(validHeaderBytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun createVault_rejectsBlankPassword() {
        VaultExportEngine.createVault("   ", listOf(VaultNote(title = "A", content = "B")))
    }

    @Test(expected = InvalidVaultFileException::class)
    fun readVault_rejectsCorruptedHeader() {
        val corruptedBytes = ByteArray(100) { 0 }
        VaultExportEngine.readVault("AnyPassword", corruptedBytes)
    }
}
