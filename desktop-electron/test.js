const path = require('path');
const fs = require('fs');
const http = require('http');
const assert = require('assert');

const DatabaseManager = require('./src/db');
const CryptoManager = require('./src/crypto');
const BackupManager = require('./src/backup');
const SyncServer = require('./src/sync-server');

async function runTests() {
  console.log('============================================');
  console.log('🧪 Starting NoteApp Electron Automated Tests');
  console.log('============================================');

  const testDir = path.join(__dirname, 'test_tmp_' + Date.now());
  fs.mkdirSync(testDir, { recursive: true });

  try {
    // -------------------------------------------------------------
    // 1. DatabaseManager Tests
    // -------------------------------------------------------------
    console.log('\n[1/4] Testing DatabaseManager...');
    const dbManager = new DatabaseManager(testDir);
    await dbManager.init();

    // Categories
    const catId = dbManager.insertCategory({ name: 'İş & Projeler', color: -4465413 });
    assert(catId > 0, 'Category ID should be > 0');
    const categories = dbManager.getAllCategories();
    assert.strictEqual(categories.length, 1);
    assert.strictEqual(categories[0].name, 'İş & Projeler');
    console.log('  ✔ Categories CRUD passed.');

    // Notes
    const note1Id = dbManager.insertNote({
      title: 'Proje Planı',
      content: '# Proje Başlangıcı\n[[Kaynak Notu]] incelenecek. #proje #onemli',
      categoryId: catId,
      isPinned: true
    });
    assert(note1Id > 0, 'Note 1 ID should be > 0');

    const note2Id = dbManager.insertNote({
      title: 'Kaynak Notu',
      content: 'Projede kullanılacak kütüphaneler. #proje',
      reminderTime: Date.now() + 3600000
    });
    assert(note2Id > 0, 'Note 2 ID should be > 0');

    // Wikilinks backlink test
    const backlinks = dbManager.getNotesLinkingTo('Kaynak Notu', note2Id);
    assert.strictEqual(backlinks.length, 1, 'Should find 1 note linking to Kaynak Notu');
    assert.strictEqual(backlinks[0].id, note1Id, 'Backlink note ID should match');
    console.log('  ✔ Bidirectional Wikilinks backlink query passed.');

    // Active & Reminders
    const active = dbManager.getActiveNotes();
    assert.strictEqual(active.length, 2);
    const reminders = dbManager.getReminderNotes();
    assert.strictEqual(reminders.length, 1);
    console.log('  ✔ Note retrieval & Reminders query passed.');

    // Trash & Cleanup
    dbManager.moveToTrash(note2Id);
    const trash = dbManager.getTrashNotes();
    assert.strictEqual(trash.length, 1);
    dbManager.restoreFromTrash(note2Id);
    assert.strictEqual(dbManager.getActiveNotes().length, 2);
    console.log('  ✔ Trash move & restore passed.');

    // -------------------------------------------------------------
    // 2. CryptoManager Tests
    // -------------------------------------------------------------
    console.log('\n[2/4] Testing CryptoManager (AES-256-GCM + PBKDF2)...');
    const cryptoManager = new CryptoManager(testDir);
    assert.strictEqual(cryptoManager.hasMasterPassword(), false);

    const setupOk = cryptoManager.setupMasterPassword('GucluSifre123');
    assert(setupOk, 'Master password setup should succeed');
    assert(cryptoManager.hasMasterPassword(), 'Should report master password set');
    assert(cryptoManager.isVaultUnlocked, 'Vault should be unlocked after setup');

    const sampleSecret = 'Bu çok gizli bir hesap şifresidir: ABC-123456';
    const encrypted = cryptoManager.encryptContent(sampleSecret);
    assert(encrypted.startsWith('ENC_GCM_V1:'), 'Encrypted string must have prefix');
    assert(encrypted !== sampleSecret, 'Encrypted string must not match plaintext');

    const decrypted = cryptoManager.decryptContent(encrypted);
    assert.strictEqual(decrypted, sampleSecret, 'Decrypted text must match plaintext exactly');

    // Test lock & unlock
    cryptoManager.lockVault();
    assert.strictEqual(cryptoManager.isVaultUnlocked, false);
    assert(cryptoManager.decryptContent(encrypted).includes('Gizli Kasa'));

    const wrongUnlock = cryptoManager.unlockVault('YanlisSifre');
    assert.strictEqual(wrongUnlock, false, 'Wrong password must fail');

    const correctUnlock = cryptoManager.unlockVault('GucluSifre123');
    assert(correctUnlock, 'Correct password must succeed');
    assert.strictEqual(cryptoManager.decryptContent(encrypted), sampleSecret);
    console.log('  ✔ AES-256-GCM Encryption, Decryption and PBKDF2 Vault verified.');

    // -------------------------------------------------------------
    // 3. BackupManager Tests
    // -------------------------------------------------------------
    console.log('\n[3/4] Testing BackupManager (ZIP & .notevault Container)...');
    const backupManager = new BackupManager(dbManager, path.join(testDir, 'attachments'));

    // Export Plain ZIP
    const zipPath = path.join(testDir, 'backup.zip');
    const zipRes = await backupManager.exportBackup(zipPath, null);
    assert(zipRes.success, 'Export ZIP should succeed');
    assert(fs.existsSync(zipPath), 'ZIP file should exist on disk');

    // Export Encrypted .notevault
    const vaultPath = path.join(testDir, 'backup.notevault');
    const vaultRes = await backupManager.exportBackup(vaultPath, 'YedekSifresi99');
    assert(vaultRes.success, 'Export .notevault should succeed');
    assert(fs.existsSync(vaultPath), '.notevault file should exist on disk');
    assert(backupManager.isVaultFile(vaultPath), 'Should recognize NOTEVLT1 magic header');

    // Import Encrypted .notevault
    const testImportDir = path.join(testDir, 'import_test');
    fs.mkdirSync(testImportDir, { recursive: true });
    const importDbManager = new DatabaseManager(testImportDir);
    await importDbManager.init();
    const importBackupManager = new BackupManager(importDbManager, path.join(testImportDir, 'attachments'));

    // Try without password -> should prompt
    const noPwdRes = await importBackupManager.importBackup(vaultPath, false, null);
    assert(noPwdRes.needsPassword, 'Should request password for .notevault file');

    // Try with correct password
    const okPwdRes = await importBackupManager.importBackup(vaultPath, false, 'YedekSifresi99');
    assert(okPwdRes.success, 'Import with correct password should succeed');
    assert.strictEqual(importDbManager.getActiveNotes().length, 2, 'Imported DB should have 2 notes');
    console.log('  ✔ ZIP & AES-256-GCM .notevault container export & restore verified.');

    // -------------------------------------------------------------
    // 4. SyncServer Tests (Port 8765 HTTP Endpoints)
    // -------------------------------------------------------------
    console.log('\n[4/4] Testing Wi-Fi SyncServer (HTTP port 8765)...');
    const syncServer = new SyncServer(dbManager, backupManager, 8765);
    await syncServer.start();
    assert(syncServer.isRunning(), 'Server should be running');
    const pin = syncServer.pairingPin;
    assert(pin && pin.length >= 4, 'PIN should be set');

    // Helper HTTP request
    function httpGet(url, headers = {}) {
      return new Promise((resolve, reject) => {
        http.get(url, { headers }, (res) => {
          const chunks = [];
          res.on('data', c => chunks.push(c));
          res.on('end', () => resolve({ statusCode: res.statusCode, data: Buffer.concat(chunks) }));
        }).on('error', reject);
      });
    }

    // Test /api/status
    const statusRes = await httpGet('http://127.0.0.1:8765/api/status');
    assert.strictEqual(statusRes.statusCode, 200);
    const statusJson = JSON.parse(statusRes.data.toString('utf8'));
    assert.strictEqual(statusJson.status, 'ok');
    assert.strictEqual(statusJson.notesCount, 2);
    console.log('  ✔ GET /api/status verified.');

    // Test /api/sync/download with wrong pin
    const wrongPinRes = await httpGet('http://127.0.0.1:8765/api/sync/download?pin=0000');
    assert.strictEqual(wrongPinRes.statusCode, 401, 'Wrong PIN must return 401');

    // Test /api/sync/download with correct pin
    const downloadRes = await httpGet(`http://127.0.0.1:8765/api/sync/download?pin=${pin}`);
    assert.strictEqual(downloadRes.statusCode, 200, 'Correct PIN must return 200');
    assert(downloadRes.data.length > 100, 'Downloaded ZIP data must not be empty');
    console.log('  ✔ GET /api/sync/download with PIN authentication verified.');

    // Test /api/sync/upload
    const uploadReq = await new Promise((resolve, reject) => {
      const req = http.request('http://127.0.0.1:8765/api/sync/upload', {
        method: 'POST',
        headers: {
          'X-Sync-PIN': pin,
          'Content-Type': 'application/zip'
        }
      }, (res) => {
        const chunks = [];
        res.on('data', c => chunks.push(c));
        res.on('end', () => resolve({ statusCode: res.statusCode, data: Buffer.concat(chunks) }));
      });
      req.on('error', reject);
      req.write(downloadRes.data);
      req.end();
    });

    assert.strictEqual(uploadReq.statusCode, 200);
    const uploadJson = JSON.parse(uploadReq.data.toString('utf8'));
    assert(uploadJson.success, 'Upload response success should be true');
    console.log('  ✔ POST /api/sync/upload verified.');

    await syncServer.stop();
    assert.strictEqual(syncServer.isRunning(), false);
    console.log('  ✔ Wi-Fi SyncServer stopped cleanly.');

    console.log('\n============================================');
    console.log('🎉 ALL ELECTRON TESTS PASSED 100% CLEANLY!');
    console.log('============================================');
  } finally {
    // Cleanup temp dir
    try {
      fs.rmSync(testDir, { recursive: true, force: true });
    } catch {}
  }
}

runTests().catch(err => {
  console.error('\n❌ TEST FAILED:', err);
  process.exit(1);
});
