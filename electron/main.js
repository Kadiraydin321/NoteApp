// electron/main.js
import { app, BrowserWindow, ipcMain, dialog } from 'electron';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { CarverEngine } from '../engine/carver.js';
import { DiskDetector } from '../engine/disk-detector.js';
import { SampleDiskGenerator } from '../engine/sample-generator.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

let mainWindow = null;
let activeEngine = null;

// Windows görev çubuğu ve bildirimler için uygulama kimliği
if (process.platform === 'win32') {
  app.setAppUserModelId('com.kadir.byterescue');
}

function createWindow() {
  const iconPath = path.join(__dirname, '../build/icon.png');

  mainWindow = new BrowserWindow({
    width: 1320,
    height: 880,
    minWidth: 1024,
    minHeight: 720,
    title: 'ByteRescue // Veri Kurtarıcı',
    backgroundColor: '#070a13',
    icon: fs.existsSync(iconPath) ? iconPath : undefined,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true
    }
  });

  const distPath = path.join(__dirname, '../dist/index.html');
  if (fs.existsSync(distPath)) {
    mainWindow.loadFile(distPath);
  }

  mainWindow.on('closed', () => {
    if (activeEngine && activeEngine.isRunning) {
      activeEngine.stopScan();
    }
    mainWindow = null;
  });
}

// IPC Handlers
ipcMain.handle('list-drives', async () => {
  return DiskDetector.listDrives();
});

ipcMain.handle('select-file', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: 'Taranacak Disk İmajını Seçin',
    properties: ['openFile'],
    filters: [
      { name: 'Disk İmajları', extensions: ['img', 'raw', 'dd', 'iso', 'bin', 'vhd'] },
      { name: 'Tüm Dosyalar', extensions: ['*'] }
    ]
  });

  if (result.canceled || result.filePaths.length === 0) return null;
  const filePath = result.filePaths[0];
  const stat = fs.statSync(filePath);
  return {
    path: filePath,
    name: path.basename(filePath),
    size: stat.size,
    sizeHuman: DiskDetector.formatSize(stat.size)
  };
});

ipcMain.handle('select-scan-folder', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: 'Bilgisayarınızda Taranacak Klasörü Seçin (Masaüstü, İndirilenler vb.)',
    properties: ['openDirectory']
  });

  if (result.canceled || result.filePaths.length === 0) return null;
  const folderPath = result.filePaths[0];
  return {
    path: folderPath,
    name: path.basename(folderPath) || folderPath,
    sizeHuman: 'Klasör'
  };
});

ipcMain.handle('select-folder', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: 'Kurtarılan Dosyaların Kaydedileceği Klasörü Seçin',
    properties: ['openDirectory', 'createDirectory']
  });

  if (result.canceled || result.filePaths.length === 0) return null;
  return result.filePaths[0];
});

ipcMain.handle('check-safety', async (_event, source, dest) => {
  return DiskDetector.checkSafeDestination(source, dest);
});

ipcMain.handle('start-scan', async (_event, options) => {
  if (activeEngine && activeEngine.isRunning) {
    throw new Error('Zaten aktif bir tarama yürütülüyor.');
  }

  activeEngine = new CarverEngine();

  activeEngine.on('progress', (data) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('scan-progress', data);
    }
  });

  activeEngine.on('file-found', (item) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('file-found', item);
    }
  });

  activeEngine.on('completed', (data) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('scan-completed', data);
      mainWindow.webContents.send('scan-status-change', { status: 'completed' });
    }
  });

  activeEngine.on('error', (err) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('scan-error', err.message);
      mainWindow.webContents.send('scan-status-change', { status: 'error' });
    }
  });

  activeEngine.on('paused', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('scan-status-change', { status: 'paused' });
    }
  });

  activeEngine.on('resumed', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('scan-status-change', { status: 'running' });
    }
  });

  activeEngine.on('stopped', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('scan-status-change', { status: 'stopped' });
    }
  });

  activeEngine.startScan(options).catch(err => {
    console.error('Tarama hatası:', err);
  });

  return { success: true };
});

ipcMain.handle('pause-scan', async () => {
  if (activeEngine) activeEngine.pauseScan();
  return { success: true };
});

ipcMain.handle('resume-scan', async () => {
  if (activeEngine) activeEngine.resumeScan();
  return { success: true };
});

ipcMain.handle('stop-scan', async () => {
  if (activeEngine) activeEngine.stopScan();
  return { success: true };
});

ipcMain.handle('get-hex-dump', async (_event, sourcePath, offset, length, filePath) => {
  return CarverEngine.generateHexDump(sourcePath, offset, length || 256, filePath);
});

ipcMain.handle('get-file-preview', async (_event, sourcePath, offset, size, mime, filePath) => {
  try {
    const previewSize = Math.min(size, 8 * 1024 * 1024);
    const chunk = CarverEngine.readChunk(sourcePath, offset, previewSize, filePath);

    if (mime.startsWith('image/')) {
      return {
        type: 'image',
        dataUri: `data:${mime};base64,${chunk.toString('base64')}`
      };
    } else if (mime === 'text/plain' || mime.includes('text')) {
      return {
        type: 'text',
        content: chunk.toString('utf8', 0, Math.min(chunk.length, 32768))
      };
    } else if (mime === 'application/pdf') {
      const pdfText = chunk.toString('binary', 0, Math.min(chunk.length, 16384));
      return {
        type: 'pdf',
        snippet: pdfText.replace(/[^\x20-\x7E\r\n]/g, '·')
      };
    }
    return { type: 'binary', size };
  } catch (err) {
    return { type: 'error', message: err.message };
  }
});

ipcMain.handle('recover-files', async (_event, sourcePath, files, destDir) => {
  return CarverEngine.recoverFiles(sourcePath, files, destDir);
});

ipcMain.handle('create-test-image', async () => {
  const result = SampleDiskGenerator.createTestDisk();
  return result;
});

app.whenReady().then(() => {
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
