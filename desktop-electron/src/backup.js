const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const JSZip = require('jszip');

const VAULT_MAGIC = Buffer.from('NOTEVLT1', 'ascii');
const SALT_LEN = 16;
const IV_LEN = 12;
const HASH_LEN = 32;
const TAG_LEN = 16;
const PBKDF2_ITERATIONS = 65536;
const KEY_LEN = 32;

class BackupManager {
  constructor(dbManager, attachmentsDir) {
    this.dbManager = dbManager;
    this.attachmentsDir = attachmentsDir;
  }

  async buildZipArchiveBuffer() {
    const zip = new JSZip();
    const notes = this.dbManager.getAllNotes();
    const categories = this.dbManager.getAllCategories();
    let attachmentsCount = 0;

    // 1. backup_info.json
    const infoJson = {
      version: 2,
      appName: 'Not Defterim Masaüstü (Electron)',
      timestamp: Date.now(),
      exportDate: new Date().toLocaleString('tr-TR'),
      notesCount: notes.length,
      categoriesCount: categories.length
    };
    zip.file('backup_info.json', JSON.stringify(infoJson, null, 2));

    // 2. categories.json
    zip.file('categories.json', JSON.stringify(categories, null, 2));

    // 3. Attachments & notes.json
    const includedFiles = new Set();
    const processedNotes = [];

    for (const note of notes) {
      const allAtts = [...(note.attachments || [])];
      if (note.backgroundImage && !allAtts.includes(note.backgroundImage)) {
        allAtts.push(note.backgroundImage);
      }

      for (const attPath of allAtts) {
        if (attPath && fs.existsSync(attPath)) {
          const fileName = path.basename(attPath);
          const entryName = `attachments/${fileName}`;
          if (!includedFiles.has(entryName)) {
            includedFiles.add(entryName);
            try {
              const fileContent = fs.readFileSync(attPath);
              zip.file(entryName, fileContent);
              attachmentsCount++;
            } catch (err) {
              console.warn('Failed reading attachment:', attPath, err);
            }
          }
        }
      }

      const relativeAttachments = (note.attachments || [])
        .filter(att => att && fs.existsSync(att))
        .map(att => `attachments/${path.basename(att)}`);

      const relativeBg = (note.backgroundImage && fs.existsSync(note.backgroundImage))
        ? `attachments/${path.basename(note.backgroundImage)}`
        : null;

      processedNotes.push({
        id: note.id,
        title: note.title,
        content: note.content,
        timestamp: note.timestamp,
        createdAt: note.createdAt || note.timestamp,
        updatedAt: note.updatedAt || note.timestamp,
        color: note.color || 0,
        categoryId: note.categoryId,
        isPinned: note.isPinned,
        isArchived: note.isArchived,
        isDeleted: note.isDeleted,
        deletedAt: note.deletedAt,
        isLocked: note.isLocked,
        reminderTime: note.reminderTime,
        attachments: relativeAttachments,
        backgroundImage: relativeBg
      });
    }

    zip.file('notes.json', JSON.stringify(processedNotes, null, 2));

    const zipBuffer = await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
    return {
      zipBuffer,
      notesCount: notes.length,
      attachmentsCount
    };
  }

  isVaultFile(filePath) {
    if (!fs.existsSync(filePath)) return false;
    try {
      const fd = fs.openSync(filePath, 'r');
      const header = Buffer.alloc(VAULT_MAGIC.length);
      fs.readSync(fd, header, 0, VAULT_MAGIC.length, 0);
      fs.closeSync(fd);
      return header.equals(VAULT_MAGIC);
    } catch {
      return false;
    }
  }

  async exportBackup(targetPath, vaultPassword = null) {
    try {
      const { zipBuffer, notesCount, attachmentsCount } = await this.buildZipArchiveBuffer();

      if (vaultPassword && vaultPassword.length > 0) {
        const salt = crypto.randomBytes(SALT_LEN);
        const iv = crypto.randomBytes(IV_LEN);

        const passwordHash = crypto.createHash('sha256')
          .update(salt)
          .update(Buffer.from(vaultPassword, 'utf8'))
          .digest();

        const key = crypto.pbkdf2Sync(
          Buffer.from(vaultPassword, 'utf8'),
          salt,
          PBKDF2_ITERATIONS,
          KEY_LEN,
          'sha256'
        );

        const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
        const encrypted = Buffer.concat([
          cipher.update(zipBuffer),
          cipher.final()
        ]);
        const authTag = cipher.getAuthTag();

        const encryptedWithTag = Buffer.concat([encrypted, authTag]);
        const fullPayload = Buffer.concat([
          VAULT_MAGIC,
          salt,
          iv,
          passwordHash,
          encryptedWithTag
        ]);

        fs.writeFileSync(targetPath, fullPayload);
        return {
          success: true,
          message: `${notesCount} not ve ${attachmentsCount} dosya Kasa Kilidi (AES-256-GCM) ile şifrelenerek yedeklendi.`,
          notesCount,
          attachmentsCount
        };
      } else {
        fs.writeFileSync(targetPath, zipBuffer);
        return {
          success: true,
          message: `${notesCount} not ve ${attachmentsCount} dosya başarıyla yedeklendi.`,
          notesCount,
          attachmentsCount
        };
      }
    } catch (e) {
      console.error('Export backup error:', e);
      return { success: false, message: 'Yedekleme sırasında hata oluştu: ' + e.message };
    }
  }

  async importBackup(sourcePathOrBuffer, clearExisting = false, vaultPassword = null) {
    try {
      let fileBuffer = Buffer.isBuffer(sourcePathOrBuffer)
        ? sourcePathOrBuffer
        : fs.readFileSync(sourcePathOrBuffer);

      // Check if it's a .notevault file
      if (fileBuffer.length >= VAULT_MAGIC.length && fileBuffer.subarray(0, VAULT_MAGIC.length).equals(VAULT_MAGIC)) {
        if (!vaultPassword) {
          return { needsPassword: true, message: 'Kasa yedeğini açmak için şifre gereklidir.' };
        }

        let offset = VAULT_MAGIC.length;
        const salt = fileBuffer.subarray(offset, offset + SALT_LEN);
        offset += SALT_LEN;
        const iv = fileBuffer.subarray(offset, offset + IV_LEN);
        offset += IV_LEN;
        const storedHash = fileBuffer.subarray(offset, offset + HASH_LEN);
        offset += HASH_LEN;
        const encryptedDataWithTag = fileBuffer.subarray(offset);

        const computedHash = crypto.createHash('sha256')
          .update(salt)
          .update(Buffer.from(vaultPassword, 'utf8'))
          .digest();

        if (!crypto.timingSafeEqual(storedHash, computedHash)) {
          return { success: false, message: 'Kasa şifresi hatalı!' };
        }

        const key = crypto.pbkdf2Sync(
          Buffer.from(vaultPassword, 'utf8'),
          salt,
          PBKDF2_ITERATIONS,
          KEY_LEN,
          'sha256'
        );

        const ciphertext = encryptedDataWithTag.subarray(0, encryptedDataWithTag.length - TAG_LEN);
        const tag = encryptedDataWithTag.subarray(encryptedDataWithTag.length - TAG_LEN);

        const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
        decipher.setAuthTag(tag);
        fileBuffer = Buffer.concat([
          decipher.update(ciphertext),
          decipher.final()
        ]);
      }

      // Load ZIP with JSZip
      const zip = await JSZip.loadAsync(fileBuffer);

      // Restore attachments
      let restoredAttachmentsCount = 0;
      const attachmentEntries = Object.keys(zip.files).filter(f => f.startsWith('attachments/') && !zip.files[f].dir);

      for (const entryName of attachmentEntries) {
        const fileData = await zip.files[entryName].async('nodebuffer');
        const fileName = path.basename(entryName);
        const destPath = path.join(this.attachmentsDir, fileName);
        fs.writeFileSync(destPath, fileData);
        restoredAttachmentsCount++;
      }

      // Restore Categories
      const categoryIdMap = new Map();
      if (zip.file('categories.json')) {
        const catText = await zip.file('categories.json').async('string');
        const categories = JSON.parse(catText);
        const existingCats = this.dbManager.getAllCategories();

        for (const cat of categories) {
          const match = existingCats.find(c => c.name.toLowerCase() === cat.name.toLowerCase());
          if (match) {
            categoryIdMap.set(cat.id, match.id);
          } else {
            const newId = this.dbManager.insertCategory({
              name: cat.name,
              color: cat.color || 0
            });
            categoryIdMap.set(cat.id, newId);
          }
        }
      }

      // Restore Notes
      let restoredNotesCount = 0;
      if (zip.file('notes.json')) {
        const notesText = await zip.file('notes.json').async('string');
        const notes = JSON.parse(notesText);

        if (clearExisting) {
          this.dbManager.emptyTrash();
          this.dbManager.deleteNotesPermanently(this.dbManager.getAllNotes().map(n => n.id));
        }

        for (const n of notes) {
          const localAttachments = (n.attachments || []).map(att => {
            const fileName = path.basename(att);
            return path.join(this.attachmentsDir, fileName);
          });

          let localBg = null;
          if (n.backgroundImage) {
            localBg = path.join(this.attachmentsDir, path.basename(n.backgroundImage));
          }

          const targetCatId = n.categoryId ? (categoryIdMap.get(n.categoryId) || null) : null;

          this.dbManager.insertNote({
            title: n.title || '',
            content: n.content || '',
            timestamp: n.timestamp || Date.now(),
            createdAt: n.createdAt || n.timestamp || Date.now(),
            updatedAt: n.updatedAt || n.timestamp || Date.now(),
            color: n.color || 0,
            categoryId: targetCatId,
            isPinned: Boolean(n.isPinned),
            isArchived: Boolean(n.isArchived),
            isDeleted: Boolean(n.isDeleted),
            deletedAt: n.deletedAt || null,
            isLocked: Boolean(n.isLocked),
            reminderTime: n.reminderTime || null,
            attachments: localAttachments,
            backgroundImage: localBg
          });
          restoredNotesCount++;
        }
      }

      return {
        success: true,
        message: `${restoredNotesCount} not ve ${restoredAttachmentsCount} ek dosya başarıyla içe aktarıldı.`,
        notesCount: restoredNotesCount,
        attachmentsCount: restoredAttachmentsCount
      };
    } catch (e) {
      console.error('Import backup error:', e);
      return { success: false, message: 'Yedek yükleme başarısız: ' + e.message };
    }
  }
}

module.exports = BackupManager;
