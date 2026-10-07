// electron/preload.js
import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('api', {
  // Sistem Sürücüleri & Güvenlik
  listDrives: () => ipcRenderer.invoke('list-drives'),
  selectFile: () => ipcRenderer.invoke('select-file'),
  selectScanFolder: () => ipcRenderer.invoke('select-scan-folder'),
  selectFolder: () => ipcRenderer.invoke('select-folder'),
  checkSafety: (source, dest) => ipcRenderer.invoke('check-safety', source, dest),

  // Tarama Kontrolleri
  startScan: (options) => ipcRenderer.invoke('start-scan', options),
  pauseScan: () => ipcRenderer.invoke('pause-scan'),
  resumeScan: () => ipcRenderer.invoke('resume-scan'),
  stopScan: () => ipcRenderer.invoke('stop-scan'),

  // İnceleme & Kurtarma
  getHexDump: (sourcePath, offset, length, filePath) => ipcRenderer.invoke('get-hex-dump', sourcePath, offset, length, filePath),
  getFilePreview: (sourcePath, offset, size, mime, filePath) => ipcRenderer.invoke('get-file-preview', sourcePath, offset, size, mime, filePath),
  recoverFiles: (sourcePath, files, destDir) => ipcRenderer.invoke('recover-files', sourcePath, files, destDir),

  // Test Verisi Oluşturma
  createTestImage: () => ipcRenderer.invoke('create-test-image'),

  // Olay Dinleyicileri (Events from Main process)
  onScanProgress: (callback) => {
    const sub = (_event, data) => callback(data);
    ipcRenderer.on('scan-progress', sub);
    return () => ipcRenderer.removeListener('scan-progress', sub);
  },
  onFileFound: (callback) => {
    const sub = (_event, data) => callback(data);
    ipcRenderer.on('file-found', sub);
    return () => ipcRenderer.removeListener('file-found', sub);
  },
  onScanCompleted: (callback) => {
    const sub = (_event, data) => callback(data);
    ipcRenderer.on('scan-completed', sub);
    return () => ipcRenderer.removeListener('scan-completed', sub);
  },
  onScanError: (callback) => {
    const sub = (_event, data) => callback(data);
    ipcRenderer.on('scan-error', sub);
    return () => ipcRenderer.removeListener('scan-error', sub);
  },
  onScanStatusChange: (callback) => {
    const sub = (_event, data) => callback(data);
    ipcRenderer.on('scan-status-change', sub);
    return () => ipcRenderer.removeListener('scan-status-change', sub);
  }
});
