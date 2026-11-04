const { app, BrowserWindow, ipcMain, dialog, shell, Notification } = require('electron');
const path = require('path');
const fs = require('fs');
const QRCode = require('qrcode');

const DatabaseManager = require('./src/db');
const CryptoManager = require('./src/crypto');
const BackupManager = require('./src/backup');
const SyncServer = require('./src/sync-server');

let mainWindow = null;
let dbManager = null;
let cryptoManager = null;
let backupManager = null;
let syncServer = null;

function getAppDirectory() {
  const appData = process.env.APPDATA;
  const baseDir = appData && appData.trim().length > 0
    ? path.join(appData, 'NotDefterim')
    : path.join(process.env.HOME || app.getPath('home'), '.notdefterim');

  if (!fs.existsSync(baseDir)) {
    fs.mkdirSync(baseDir, { recursive: true });
  }
  return baseDir;
}

async function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 960,
    minHeight: 640,
    backgroundColor: '#0b1120',
    title: 'Not Defterim (Electron)',
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'src', 'index.html'));

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });

  mainWindow.on('closed', () => {
    if (syncServer) {
      syncServer.stop();
    }
    mainWindow = null;
  });
}

app.whenReady().then(async () => {
  const appDir = getAppDirectory();
  const attachmentsDir = path.join(appDir, 'attachments');

  dbManager = new DatabaseManager(appDir);
  await dbManager.init();

  cryptoManager = new CryptoManager(appDir);
  backupManager = new BackupManager(dbManager, attachmentsDir);

  syncServer = new SyncServer(dbManager, backupManager, 8765, (state) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('sync:stateChanged', state);
    }
  });

  // Setup IPC Handlers
  setupIpcHandlers(appDir, attachmentsDir);

  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

function setupIpcHandlers(appDir, attachmentsDir) {
  // DB Handlers
  ipcMain.handle('db:getActiveNotes', () => dbManager.getActiveNotes());
  ipcMain.handle('db:getArchivedNotes', () => dbManager.getArchivedNotes());
  ipcMain.handle('db:getTrashNotes', () => dbManager.getTrashNotes());
  ipcMain.handle('db:getReminderNotes', () => dbManager.getReminderNotes());
  ipcMain.handle('db:getNoteById', (_, id) => dbManager.getNoteById(id));
  ipcMain.handle('db:getNoteByTitle', (_, title) => dbManager.getNoteByTitle(title));
  ipcMain.handle('db:getNotesLinkingTo', (_, title, excludeId) => dbManager.getNotesLinkingTo(title, excludeId));
  ipcMain.handle('db:insertNote', (_, note) => dbManager.insertNote(note));
  ipcMain.handle('db:updateNote', (_, note) => dbManager.updateNote(note));
  ipcMain.handle('db:moveToTrash', (_, id) => dbManager.moveToTrash(id));
  ipcMain.handle('db:moveNotesToTrash', (_, ids) => dbManager.moveNotesToTrash(ids));
  ipcMain.handle('db:restoreFromTrash', (_, id) => dbManager.restoreFromTrash(id));
  ipcMain.handle('db:deletePermanently', (_, id) => dbManager.deletePermanently(id));
  ipcMain.handle('db:deleteNotesPermanently', (_, ids) => dbManager.deleteNotesPermanently(ids));
  ipcMain.handle('db:emptyTrash', () => dbManager.emptyTrash());
  ipcMain.handle('db:getAllCategories', () => dbManager.getAllCategories());
  ipcMain.handle('db:insertCategory', (_, cat) => dbManager.insertCategory(cat));
  ipcMain.handle('db:deleteCategory', (_, id) => dbManager.deleteCategory(id));

  // Crypto Handlers
  ipcMain.handle('crypto:hasMasterPassword', () => cryptoManager.hasMasterPassword());
  ipcMain.handle('crypto:setupMasterPassword', (_, pwd) => cryptoManager.setupMasterPassword(pwd));
  ipcMain.handle('crypto:unlockVault', (_, pwd) => cryptoManager.unlockVault(pwd));
  ipcMain.handle('crypto:lockVault', () => cryptoManager.lockVault());
  ipcMain.handle('crypto:isVaultUnlocked', () => cryptoManager.isVaultUnlocked);
  ipcMain.handle('crypto:encryptContent', (_, content) => cryptoManager.encryptContent(content));
  ipcMain.handle('crypto:decryptContent', (_, content) => cryptoManager.decryptContent(content));

  // Backup Handlers
  ipcMain.handle('backup:exportBackup', async (_, vaultPassword) => {
    const defaultExt = vaultPassword ? 'notevault' : 'zip';
    const filters = vaultPassword
      ? [{ name: 'Not Defterim Şifreli Kasa (*.notevault)', extensions: ['notevault'] }]
      : [{ name: 'Not Defterim ZIP Yedeği (*.zip)', extensions: ['zip'] }];

    const result = await dialog.showSaveDialog(mainWindow, {
      title: 'Yedek Dosyasını Kaydet',
      defaultPath: `NotDefterim_Yedek_${Date.now()}.${defaultExt}`,
      filters
    });

    if (result.canceled || !result.filePath) {
      return { success: false, message: 'İptal edildi' };
    }

    return await backupManager.exportBackup(result.filePath, vaultPassword);
  });

  ipcMain.handle('backup:importBackup', async (_, vaultPassword) => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Yedek Dosyası Seçin',
      filters: [
        { name: 'Not Defterim Yedekleri (*.zip, *.notevault)', extensions: ['zip', 'notevault'] },
        { name: 'Tüm Dosyalar', extensions: ['*'] }
      ],
      properties: ['openFile']
    });

    if (result.canceled || !result.filePaths || result.filePaths.length === 0) {
      return { success: false, message: 'İptal edildi' };
    }

    const filePath = result.filePaths[0];
    return await backupManager.importBackup(filePath, false, vaultPassword);
  });

  // Sync Handlers
  ipcMain.handle('sync:getState', () => ({
    isRunning: syncServer.isRunning(),
    localIp: syncServer.getLocalIpAddress(),
    port: syncServer.port,
    pairingPin: syncServer.pairingPin,
    lastSyncMessage: syncServer.lastMessage
  }));
  ipcMain.handle('sync:start', () => {
    syncServer.start();
    return true;
  });
  ipcMain.handle('sync:stop', () => {
    syncServer.stop();
    return true;
  });
  ipcMain.handle('sync:refreshPin', () => {
    syncServer.refreshPin();
    return true;
  });

  // CRC32 implementation for IEEE 802.3 standard (compatible with java.util.zip.CRC32)
  const crc32Table = (() => {
    const table = new Uint32Array(256);
    for (let i = 0; i < 256; i++) {
      let c = i;
      for (let j = 0; j < 8; j++) {
        c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
      }
      table[i] = c >>> 0;
    }
    return table;
  })();

  function computeCrc32(str) {
    const bytes = Buffer.from(str, 'utf8');
    let crc = 0xFFFFFFFF;
    for (let i = 0; i < bytes.length; i++) {
      crc = (crc >>> 8) ^ crc32Table[(crc ^ bytes[i]) & 0xFF];
    }
    return ((crc ^ 0xFFFFFFFF) >>> 0);
  }

  // QR Code Generator API
  ipcMain.handle('qr:generate', async (_, text, options = {}) => {
    try {
      const opts = {
        errorCorrectionLevel: options.errorCorrectionLevel || 'M',
        type: 'image/png',
        quality: 0.92,
        margin: options.margin !== undefined ? options.margin : 2,
        width: options.width || 300,
        color: {
          dark: options.dark || '#000000',
          light: options.light || '#ffffff'
        }
      };
      const dataUrl = await QRCode.toDataURL(text, opts);
      return { success: true, dataUrl };
    } catch (err) {
      console.error('QR Generator Error:', err);
      return { success: false, error: err.message };
    }
  });

  // Optical Air-Gapped Stream Preparation
  ipcMain.handle('optical:prepareChunks', async (_, options = {}) => {
    try {
      const chunkSize = options.chunkSize || 240; // Optimal for QR readability at 10-15 FPS
      const notes = dbManager.getActiveNotes();
      const categories = dbManager.getAllCategories();

      const payload = {
        version: 1,
        type: 'noteapp_backup',
        exportedAt: Date.now(),
        notes: notes.map(n => ({
          title: n.title || '',
          content: n.content || '',
          isPinned: Boolean(n.isPinned),
          isArchived: Boolean(n.isArchived),
          color: n.color || 0,
          categoryName: n.categoryName || null,
          categoryColor: n.categoryColor || 0,
          reminderTime: n.reminderTime || null,
          createdAt: n.createdAt || Date.now(),
          updatedAt: n.updatedAt || Date.now()
        })),
        categories: categories.map(c => ({
          name: c.name,
          color: c.color
        }))
      };

      const fullJson = JSON.stringify(payload);
      const totalLen = fullJson.length;
      const totalChunks = Math.max(1, Math.ceil(totalLen / chunkSize));
      const crc32Val = computeCrc32(fullJson);

      const chunks = [];
      for (let i = 0; i < totalChunks; i++) {
        const start = i * chunkSize;
        const end = Math.min(start + chunkSize, totalLen);
        const chunkSlice = fullJson.slice(start, end);
        chunks.push({
          s: i + 1,       // 1-indexed sequence number
          t: totalChunks, // total number of chunks
          d: chunkSlice,  // data slice
          c: crc32Val     // total payload crc32 checksum
        });
      }

      return {
        success: true,
        notesCount: notes.length,
        categoriesCount: categories.length,
        totalBytes: totalLen,
        totalChunks,
        checksum: crc32Val,
        chunks
      };
    } catch (err) {
      console.error('Optical Chunks Prepare Error:', err);
      return { success: false, error: err.message };
    }
  });

  // System Handlers
  ipcMain.handle('system:saveDrawing', async (_, dataUrl) => {
    try {
      const base64Data = dataUrl.replace(/^data:image\/png;base64,/, '');
      const buffer = Buffer.from(base64Data, 'base64');
      const filename = `cizim_${Date.now()}.png`;
      const filePath = path.join(attachmentsDir, filename);
      fs.writeFileSync(filePath, buffer);
      return { success: true, filePath };
    } catch (err) {
      return { success: false, error: err.message };
    }
  });

  ipcMain.handle('system:pickAttachment', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Görsel veya Dosya Eki Seç',
      properties: ['openFile']
    });
    if (result.canceled || !result.filePaths || result.filePaths.length === 0) {
      return null;
    }
    const source = result.filePaths[0];
    const filename = `${Date.now()}_${path.basename(source)}`;
    const dest = path.join(attachmentsDir, filename);
    fs.copyFileSync(source, dest);
    return dest;
  });

  ipcMain.handle('system:pickBackgroundImage', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Arka Plan Görseli Seç',
      filters: [{ name: 'Görseller', extensions: ['png', 'jpg', 'jpeg', 'webp'] }],
      properties: ['openFile']
    });
    if (result.canceled || !result.filePaths || result.filePaths.length === 0) {
      return null;
    }
    const source = result.filePaths[0];
    const filename = `bg_${Date.now()}_${path.basename(source)}`;
    const dest = path.join(attachmentsDir, filename);
    fs.copyFileSync(source, dest);
    return dest;
  });

  ipcMain.handle('system:openAttachment', async (_, filePath) => {
    if (fs.existsSync(filePath)) {
      shell.openPath(filePath);
    }
  });

  ipcMain.handle('system:exportMarkdown', async (_, title, content) => {
    const safeTitle = (title || 'Not').replace(/[^a-zA-Z0-9çğıöşüÇĞİÖŞÜ_ -]/g, '_');
    const result = await dialog.showSaveDialog(mainWindow, {
      title: 'Notu Markdown Olarak Kaydet',
      defaultPath: `${safeTitle}.md`,
      filters: [{ name: 'Markdown (*.md)', extensions: ['md'] }]
    });

    if (!result.canceled && result.filePath) {
      fs.writeFileSync(result.filePath, `# ${title}\n\n${content}`, 'utf8');
      return { success: true, path: result.filePath };
    }
    return { success: false };
  });

  ipcMain.handle('system:setScreenshotProtection', (_, enabled) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.setContentProtection(Boolean(enabled));
      return true;
    }
    return false;
  });

  ipcMain.handle('system:showNotification', (_, title, body) => {
    if (Notification.isSupported()) {
      const notif = new Notification({
        title: title || 'Not Defterim',
        body: body || ''
      });
      notif.show();
    }
    return true;
  });
}
