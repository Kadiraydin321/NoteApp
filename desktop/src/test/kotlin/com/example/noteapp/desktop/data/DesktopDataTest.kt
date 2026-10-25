package com.example.noteapp.desktop.data

import com.example.noteapp.desktop.data.backup.BackupResult
import com.example.noteapp.desktop.data.backup.DesktopBackupManager
import com.example.noteapp.desktop.data.backup.RestoreResult
import com.example.noteapp.desktop.data.local.DesktopNoteDao
import com.example.noteapp.desktop.data.security.DesktopCryptoManager
import com.example.noteapp.desktop.domain.model.Category
import com.example.noteapp.desktop.domain.model.Note
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class DesktopDataTest {

    private lateinit var dao: DesktopNoteDao
    private lateinit var backupManager: DesktopBackupManager

    @Before
    fun setUp() {
        dao = DesktopNoteDao()
        backupManager = DesktopBackupManager(dao)
        dao.deleteAllNotes()
        dao.deleteAllCategories()
    }

    @Test
    fun testNoteCrud() {
        val catId = dao.insertCategory(Category(name = "İş", color = 0xFFBBDEFB.toInt()))
        assertTrue(catId > 0)

        val noteId = dao.insertNote(
            Note(
                title = "Toplantı Notu",
                content = "Yarın saat 10:00 toplantı var.",
                categoryId = catId,
                isPinned = true
            )
        )
        assertTrue(noteId > 0)

        val activeList = dao.getActiveNotes()
        assertEquals(1, activeList.size)
        assertEquals("Toplantı Notu", activeList[0].title)
        assertTrue(activeList[0].isPinned)

        // Çöpe at
        dao.moveToTrash(noteId)
        assertEquals(0, dao.getActiveNotes().size)
        assertEquals(1, dao.getTrashNotes().size)

        // Geri yükle
        dao.restoreFromTrash(noteId)
        assertEquals(1, dao.getActiveNotes().size)
        assertEquals(0, dao.getTrashNotes().size)
    }

    @Test
    fun testCryptoManager() {
        val password = "SecretMasterPassword123!"
        val setupOk = DesktopCryptoManager.setupMasterPassword(password)
        assertTrue(setupOk)
        assertTrue(DesktopCryptoManager.isVaultUnlocked)

        val plainText = "Bu çok gizli bir hesap şifresidir."
        val cipherText = DesktopCryptoManager.encryptContent(plainText)
        assertNotEquals(plainText, cipherText)
        assertTrue(DesktopCryptoManager.isEncrypted(cipherText))

        val decrypted = DesktopCryptoManager.decryptContent(cipherText)
        assertEquals(plainText, decrypted)

        // Kasayı kilitle
        DesktopCryptoManager.lockVault()
        assertFalse(DesktopCryptoManager.isVaultUnlocked)

        // Yanlış şifre ile kilit açılamamalı
        val wrongAttempt = DesktopCryptoManager.unlockVault("YanlisSifre")
        assertFalse(wrongAttempt)

        // Doğru şifre ile açılmalı
        val rightAttempt = DesktopCryptoManager.unlockVault(password)
        assertTrue(rightAttempt)
    }

    @Test
    fun testBackupAndRestore() {
        dao.insertCategory(Category(name = "Kişisel", color = 0))
        dao.insertNote(
            Note(
                title = "Yedeklenecek Not 1",
                content = "# Markdown Başlık\n- [ ] Yapılacak iş",
                isPinned = false
            )
        )
        dao.insertNote(
            Note(
                title = "Yedeklenecek Not 2",
                content = "İkinci içerik metni",
                isPinned = true
            )
        )

        // 1. Düz ZIP Yedekleme
        val tempZip = File.createTempFile("test_backup_", ".zip")
        val zipResult = backupManager.exportBackup(tempZip)
        assertTrue(zipResult is BackupResult.Success)
        assertEquals(2, (zipResult as BackupResult.Success).notesCount)

        // Veritabanını temizle
        dao.deleteAllNotes()
        assertEquals(0, dao.getActiveNotes().size)

        // Geri yükle
        val restoreResult = backupManager.importBackup(tempZip, clearExisting = true)
        assertTrue(restoreResult is RestoreResult.Success)
        assertEquals(2, (restoreResult as RestoreResult.Success).notesCount)
        assertEquals(2, dao.getActiveNotes().size)

        tempZip.delete()
    }

    @Test
    fun testIconGeneration() {
        val iconFile = File("src/main/resources/icons/icon.png")
        com.example.noteapp.desktop.util.AppIconRenderer.saveIconToFile(iconFile, 256)
        assertTrue(iconFile.exists())
        assertTrue(iconFile.length() > 0)
    }
}
