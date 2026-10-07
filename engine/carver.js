// engine/carver.js
// Yüksek Başarımlı Ham Sektör & Dahili Bilgisayar Sürücüsü/Klasörü Kurtarma Motoru

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { EventEmitter } from 'node:events';
import { BUILTIN_SIGNATURES, parseCustomSignature } from './signatures.js';

export class CarverEngine extends EventEmitter {
  constructor() {
    super();
    this.isRunning = false;
    this.isPaused = false;
    this.shouldStop = false;
    this.currentFd = null;
    this.activeSource = null;
    this.stats = {
      bytesScanned: 0,
      totalBytes: 0,
      filesFound: 0,
      startTime: 0,
      speedMBps: 0,
      etaSeconds: 0,
      percentage: 0
    };
    this.discoveredFiles = [];
  }

  // Taramayı başlat (Hem Ham Disk/İmaj hem de Dahili Sürücü/Klasör destekler)
  async startScan(options) {
    if (this.isRunning) {
      throw new Error('Tarama zaten çalışıyor.');
    }

    const {
      sourcePath,
      selectedCategories = ['images', 'documents', 'media', 'archives'],
      selectedSignatureIds = [],
      customSignatures = [],
      chunkSize = 2 * 1024 * 1024,
      sectorAlign = 512,
      maxScanFiles = 50000 // Klasör taraması için maksimum dosya sayısı
    } = options;

    if (!fs.existsSync(sourcePath)) {
      throw new Error(`Belirtilen sürücü, klasör veya disk dosyası bulunamadı: ${sourcePath}`);
    }

    // İmzaları filtrele ve hazırla
    let activeSignatures = BUILTIN_SIGNATURES.filter(sig => {
      if (selectedCategories.includes(sig.category)) return true;
      if (selectedSignatureIds.includes(sig.id)) return true;
      return false;
    });

    for (const custom of customSignatures) {
      try {
        activeSignatures.push(parseCustomSignature(custom));
      } catch (err) {
        console.warn('Özel imza ayrıştırılamadı:', err.message);
      }
    }

    if (activeSignatures.length === 0) {
      throw new Error('En az bir dosya türü veya imza seçilmelidir.');
    }

    this.isRunning = true;
    this.isPaused = false;
    this.shouldStop = false;
    this.activeSource = sourcePath;
    this.discoveredFiles = [];

    const isDirectory = fs.statSync(sourcePath).isDirectory();

    if (isDirectory) {
      // DAHİLİ SÜRÜCÜ / KLASÖR / RECYCLE BIN TARAMASI
      await this.runDirectoryScanLoop(sourcePath, activeSignatures, maxScanFiles);
    } else {
      // HAM DİSK / İMAJ DOSYASI TARAMASI (SLIDING WINDOW)
      await this.runBlockScan(sourcePath, activeSignatures, chunkSize, sectorAlign);
    }
  }

  // --- 1. DAHİLİ BİLGİSAYAR SÜRÜCÜSÜ & KLASÖR TARAMASI ---
  async runDirectoryScanLoop(dirPath, signatures, maxScanFiles) {
    this.stats = {
      bytesScanned: 0,
      totalBytes: 50 * 1024 * 1024 * 1024, // Tahmini 50 GB
      filesFound: 0,
      startTime: Date.now(),
      speedMBps: 0,
      etaSeconds: 0,
      percentage: 0
    };

    this.emit('started', { ...this.stats, mode: 'directory', totalSignatures: signatures.length });

    let bytesScanned = 0;
    let filesInspected = 0;
    let lastReportTime = Date.now();
    let bytesInWindow = 0;

    // Windows $Recycle.Bin metaveri sözlüğü ($I -> $R eşleştirmesi)
    const recycleBinMetadata = new Map();

    const scanQueue = [dirPath];

    while (scanQueue.length > 0 && this.isRunning && !this.shouldStop) {
      if (this.isPaused) {
        await new Promise(r => setTimeout(r, 200));
        continue;
      }

      const currentDir = scanQueue.shift();
      let entries = [];

      try {
        entries = fs.readdirSync(currentDir, { withFileTypes: true });
      } catch (_) {
        continue; // İzin verilmeyen sistem klasörlerini atla
      }

      for (const entry of entries) {
        if (!this.isRunning || this.shouldStop) break;

        const fullPath = path.join(currentDir, entry.name);

        if (entry.isDirectory()) {
          // Çok derin veya döngüsel sistem klasörlerini engelle ($RECYCLE.BIN alt klasörlerine izin ver)
          if (!['node_modules', '.git', 'AppData/Local/Temp'].includes(entry.name) && scanQueue.length < 5000) {
            scanQueue.push(fullPath);
          }
        } else if (entry.isFile()) {
          filesInspected++;

          try {
            const stat = fs.statSync(fullPath);
            const fileSize = stat.size;
            bytesScanned += fileSize;
            bytesInWindow += fileSize;

            // A) Windows Recycle Bin ($I) Dosyası İncelemesi
            if (entry.name.startsWith('$I')) {
              try {
                const iBuf = fs.readFileSync(fullPath);
                if (iBuf.length >= 28) {
                  const origSize = Number(iBuf.readBigUInt64LE(8));
                  const pathLen = iBuf.readUInt32LE(24);
                  const origPath = iBuf.subarray(28, 28 + pathLen * 2).toString('utf16le').replace(/\0/g, '').trim();
                  const origExt = path.extname(origPath).replace(/^\./, '').toLowerCase();

                  // Eşleşen $R dosyasını ara
                  const rName = '$R' + entry.name.slice(2);
                  const rPath = path.join(currentDir, rName);
                  const hasRFile = fs.existsSync(rPath);

                  const hexOffset = '0x' + (this.discoveredFiles.length * 512).toString(16).toUpperCase().padStart(8, '0');
                  const origBaseName = path.basename(origPath) || `silinen_${entry.name}`;

                  const recItem = {
                    id: `rec_${this.discoveredFiles.length + 1}_${entry.name}`,
                    name: origBaseName,
                    type: origExt || 'bin',
                    category: this.getCategoryForExt(origExt),
                    displayName: `Silinen Dosya (${origExt.toUpperCase()})`,
                    mime: this.getMimeForExt(origExt),
                    filePath: hasRFile ? rPath : fullPath,
                    originalPath: origPath,
                    isRecycleBin: true,
                    hasData: hasRFile,
                    offset: 0,
                    hexOffset,
                    size: hasRFile ? fs.statSync(rPath).size : origSize || fileSize,
                    confidence: hasRFile ? 100 : 85,
                    previewable: ['jpg', 'png', 'gif', 'webp', 'bmp', 'txt', 'pdf'].includes(origExt),
                    foundAt: Date.now()
                  };

                  this.discoveredFiles.push(recItem);
                  this.stats.filesFound = this.discoveredFiles.length;
                  this.emit('file-found', recItem);
                  continue;
                }
              } catch (_) {}
            }

            // B) Normal veya Gizli / Geçici Dosya Magic Byte Analizi
            if (fileSize > 16 && fileSize <= 500 * 1024 * 1024) {
              const checkLen = Math.min(fileSize, 4096);
              const fd = fs.openSync(fullPath, 'r');
              const headBuf = Buffer.alloc(checkLen);
              fs.readSync(fd, headBuf, 0, checkLen, 0);
              fs.closeSync(fd);

              // Başlık imzalarıyla karşılaştır
              for (const sig of signatures) {
                const offset = sig.headerOffset || 0;
                if (checkLen < offset + sig.header.length) continue;

                let match = true;
                for (let b = 0; b < sig.header.length; b++) {
                  if (headBuf[offset + b] !== sig.header[b]) {
                    match = false;
                    break;
                  }
                }

                if (match) {
                  if (typeof sig.validate === 'function') {
                    if (!sig.validate(headBuf, 0)) continue;
                  }

                  let ext = sig.extension;
                  let displayName = sig.name;
                  let mime = sig.mime;

                  // DOCX / XLSX / PPTX tespiti
                  if (typeof sig.postProcess === 'function') {
                    const refined = sig.postProcess(headBuf, 0, fileSize);
                    if (refined) {
                      ext = refined.extension;
                      displayName = refined.name;
                      mime = refined.mime;
                    }
                  }

                  const isTempOrOrphan = entry.name.endsWith('.tmp') ||
                                         entry.name.endsWith('.bak') ||
                                         entry.name.startsWith('~') ||
                                         entry.name.startsWith('$') ||
                                         entry.name.endsWith('.old');

                  const recoveredName = isTempOrOrphan 
                    ? `kurtarilan_${entry.name.replace(/\.[^.]+$/, '')}.${ext}`
                    : entry.name;

                  const hexOffset = '0x' + (this.discoveredFiles.length * 512).toString(16).toUpperCase().padStart(8, '0');

                  const foundItem = {
                    id: `file_${this.discoveredFiles.length + 1}_${Date.now()}`,
                    name: recoveredName,
                    type: ext,
                    category: sig.category,
                    displayName,
                    mime,
                    filePath: fullPath,
                    offset: 0,
                    hexOffset,
                    size: fileSize,
                    confidence: 95,
                    previewable: ['jpg', 'png', 'gif', 'webp', 'bmp', 'txt', 'pdf'].includes(ext),
                    foundAt: Date.now()
                  };

                  this.discoveredFiles.push(foundItem);
                  this.stats.filesFound = this.discoveredFiles.length;
                  this.emit('file-found', foundItem);
                  break;
                }
              }
            }
          } catch (_) {}

          // İlerleme Raporu
          const now = Date.now();
          const elapsedSec = (now - lastReportTime) / 1000;
          if (elapsedSec >= 0.25) {
            const speed = bytesInWindow / (elapsedSec || 1);
            this.stats.speedMBps = parseFloat((speed / (1024 * 1024)).toFixed(2));
            bytesInWindow = 0;
            lastReportTime = now;

            this.stats.bytesScanned = bytesScanned;
            this.stats.percentage = Math.min(100, parseFloat(((filesInspected / 5000) * 100).toFixed(1)));
            this.emit('progress', { ...this.stats });
          }

          if (filesInspected >= maxScanFiles) break;
        }
      }

      await new Promise(r => setImmediate(r));
    }

    this.stats.percentage = 100;
    this.isRunning = false;
    this.emit('completed', { ...this.stats, files: this.discoveredFiles });
  }

  // --- 2. HAM BLOK CİHAZ & İMAJ TARAMASI (SLIDING WINDOW) ---
  async runBlockScan(sourcePath, signatures, chunkSize, sectorAlign) {
    let totalBytes = 0;
    try {
      const stat = fs.statSync(sourcePath);
      totalBytes = stat.size || this.detectBlockDeviceSize(sourcePath);
    } catch (_) {
      totalBytes = 1024 * 1024 * 1024;
    }

    this.stats = {
      bytesScanned: 0,
      totalBytes: totalBytes || 1,
      filesFound: 0,
      startTime: Date.now(),
      speedMBps: 0,
      etaSeconds: 0,
      percentage: 0
    };

    this.emit('started', { ...this.stats, mode: 'raw_block', totalSignatures: signatures.length });

    try {
      this.currentFd = fs.openSync(sourcePath, 'r');
      await this.runScanLoop(signatures, chunkSize, sectorAlign);
    } catch (err) {
      if (!this.shouldStop) {
        this.emit('error', err);
      }
    } finally {
      if (this.currentFd !== null) {
        try { fs.closeSync(this.currentFd); } catch (_) {}
        this.currentFd = null;
      }
      this.isRunning = false;
      this.emit('completed', { ...this.stats, files: this.discoveredFiles });
    }
  }

  detectBlockDeviceSize(devicePath) {
    try {
      const devName = path.basename(devicePath);
      const sizePath = `/sys/class/block/${devName}/size`;
      if (fs.existsSync(sizePath)) {
        const sectors = parseInt(fs.readFileSync(sizePath, 'utf8').trim(), 10);
        return sectors * 512;
      }
    } catch (_) {}
    return 0;
  }

  async runScanLoop(signatures, chunkSize, sectorAlign) {
    const OVERLAP_SIZE = 64 * 1024;
    let fileOffset = 0;
    const readBuffer = Buffer.allocUnsafe(chunkSize + OVERLAP_SIZE);
    let overlapBytes = 0;
    let lastReportTime = Date.now();
    let bytesInWindow = 0;

    while (this.isRunning && !this.shouldStop) {
      if (this.isPaused) {
        await new Promise(resolve => setTimeout(resolve, 200));
        continue;
      }

      const bytesToRead = chunkSize;
      const targetOffset = overlapBytes;
      let bytesRead = 0;

      try {
        bytesRead = fs.readSync(this.currentFd, readBuffer, targetOffset, bytesToRead, fileOffset);
      } catch (err) {
        if (err.code === 'EOF' || bytesRead === 0) break;
        throw err;
      }

      if (bytesRead === 0) break;

      const currentBuffer = readBuffer.subarray(0, overlapBytes + bytesRead);
      const scanLimit = currentBuffer.length;
      const step = sectorAlign > 0 ? sectorAlign : 1;

      for (let relOffset = 0; relOffset <= scanLimit - 16; relOffset += step) {
        for (const sig of signatures) {
          const checkOffset = relOffset + (sig.headerOffset || 0);
          if (checkOffset + sig.header.length > scanLimit) continue;

          let match = true;
          for (let b = 0; b < sig.header.length; b++) {
            if (currentBuffer[checkOffset + b] !== sig.header[b]) {
              match = false;
              break;
            }
          }

          if (match) {
            if (typeof sig.validate === 'function') {
              if (!sig.validate(currentBuffer, relOffset)) continue;
            }

            let fileSize = null;
            let confidence = 85;

            if (typeof sig.extractSize === 'function') {
              fileSize = sig.extractSize(currentBuffer, relOffset);
            }

            if (!fileSize || fileSize < sig.minSize) {
              if (sig.footer) {
                fileSize = this.findFooter(currentBuffer, relOffset, sig.footer, sig.maxSize);
                if (fileSize) confidence = 100;
              }
            } else {
              confidence = 95;
            }

            if (!fileSize || fileSize <= 0) {
              fileSize = Math.min(scanLimit - relOffset, 1024 * 1024);
              confidence = 70;
            }

            fileSize = Math.min(fileSize, sig.maxSize);
            const absoluteOffset = (fileOffset - overlapBytes) + relOffset;

            const alreadyFound = this.discoveredFiles.some(f => Math.abs(f.offset - absoluteOffset) < 32);
            if (!alreadyFound && absoluteOffset >= 0) {
              let ext = sig.extension;
              let mime = sig.mime;
              let displayName = sig.name;

              if (typeof sig.postProcess === 'function') {
                const refined = sig.postProcess(currentBuffer, relOffset, fileSize);
                if (refined) {
                  ext = refined.extension;
                  mime = refined.mime;
                  displayName = refined.name;
                }
              }

              const hexOffset = '0x' + absoluteOffset.toString(16).toUpperCase().padStart(8, '0');
              const foundItem = {
                id: `carved_${this.discoveredFiles.length + 1}_${hexOffset}`,
                name: `kurtarilan_${String(this.discoveredFiles.length + 1).padStart(4, '0')}.${ext}`,
                type: ext,
                category: sig.category,
                displayName,
                mime,
                offset: absoluteOffset,
                hexOffset,
                size: fileSize,
                confidence,
                previewable: ['jpg', 'png', 'gif', 'webp', 'bmp', 'txt', 'pdf'].includes(ext),
                foundAt: Date.now()
              };

              this.discoveredFiles.push(foundItem);
              this.stats.filesFound = this.discoveredFiles.length;
              this.emit('file-found', foundItem);
            }
          }
        }
      }

      fileOffset += bytesRead;
      this.stats.bytesScanned = fileOffset;
      bytesInWindow += bytesRead;

      const now = Date.now();
      const elapsedWindowSec = (now - lastReportTime) / 1000;

      if (elapsedWindowSec >= 0.25) {
        const speedBytesPerSec = bytesInWindow / (elapsedWindowSec || 1);
        this.stats.speedMBps = parseFloat((speedBytesPerSec / (1024 * 1024)).toFixed(2));
        bytesInWindow = 0;
        lastReportTime = now;

        if (this.stats.totalBytes > 0) {
          const pct = Math.min(100, (fileOffset / this.stats.totalBytes) * 100);
          this.stats.percentage = parseFloat(pct.toFixed(2));
          
          if (speedBytesPerSec > 0 && fileOffset < this.stats.totalBytes) {
            const remainingBytes = this.stats.totalBytes - fileOffset;
            this.stats.etaSeconds = Math.round(remainingBytes / speedBytesPerSec);
          } else {
            this.stats.etaSeconds = 0;
          }
        }

        this.emit('progress', { ...this.stats });
      }

      if (bytesRead >= OVERLAP_SIZE) {
        currentBuffer.copy(readBuffer, 0, currentBuffer.length - OVERLAP_SIZE, currentBuffer.length);
        overlapBytes = OVERLAP_SIZE;
      } else {
        overlapBytes = 0;
      }

      await new Promise(resolve => setImmediate(resolve));
    }
  }

  findFooter(buffer, startOffset, footerBuf, maxSize) {
    const maxSearch = Math.min(buffer.length, startOffset + maxSize);
    for (let i = startOffset + 4; i <= maxSearch - footerBuf.length; i++) {
      let match = true;
      for (let f = 0; f < footerBuf.length; f++) {
        if (buffer[i + f] !== footerBuf[f]) {
          match = false;
          break;
        }
      }
      if (match) {
        return (i + footerBuf.length) - startOffset;
      }
    }
    return null;
  }

  pauseScan() {
    if (this.isRunning && !this.isPaused) {
      this.isPaused = true;
      this.emit('paused', { ...this.stats });
    }
  }

  resumeScan() {
    if (this.isRunning && this.isPaused) {
      this.isPaused = false;
      this.emit('resumed', { ...this.stats });
    }
  }

  stopScan() {
    if (this.isRunning) {
      this.shouldStop = true;
      this.isRunning = false;
      this.emit('stopped', { ...this.stats });
    }
  }

  getCategoryForExt(ext) {
    if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(ext)) return 'images';
    if (['pdf', 'docx', 'xlsx', 'pptx', 'txt'].includes(ext)) return 'documents';
    if (['mp4', 'mkv', 'mp3', 'wav', 'flac'].includes(ext)) return 'media';
    if (['zip', '7z', 'rar', 'tar', 'gz'].includes(ext)) return 'archives';
    return 'custom';
  }

  getMimeForExt(ext) {
    const map = {
      jpg: 'image/jpeg',
      jpeg: 'image/jpeg',
      png: 'image/png',
      gif: 'image/gif',
      webp: 'image/webp',
      bmp: 'image/bmp',
      pdf: 'application/pdf',
      zip: 'application/zip',
      mp4: 'video/mp4',
      mp3: 'audio/mpeg',
      txt: 'text/plain'
    };
    return map[ext] || 'application/octet-stream';
  }

  // Ham bayt parçası oku (Hem gerçek dosya yolundan hem de disk ofsetinden)
  static readChunk(sourcePath, offset, length, filePath = null) {
    const targetFile = filePath || sourcePath;
    const fd = fs.openSync(targetFile, 'r');
    try {
      const buffer = Buffer.alloc(length);
      const readOff = filePath ? 0 : offset;
      const bytesRead = fs.readSync(fd, buffer, 0, length, readOff);
      return buffer.subarray(0, bytesRead);
    } finally {
      fs.closeSync(fd);
    }
  }

  // Hex dökümü üret
  static generateHexDump(sourcePath, offset, length = 256, filePath = null) {
    const raw = CarverEngine.readChunk(sourcePath, offset, length, filePath);
    const rows = [];

    for (let r = 0; r < raw.length; r += 16) {
      const slice = raw.subarray(r, r + 16);
      const rowOffset = (offset + r).toString(16).toUpperCase().padStart(8, '0');
      
      const hexParts = [];
      const asciiParts = [];

      for (let i = 0; i < 16; i++) {
        if (i < slice.length) {
          const b = slice[i];
          hexParts.push(b.toString(16).toUpperCase().padStart(2, '0'));
          asciiParts.push((b >= 32 && b <= 126) ? String.fromCharCode(b) : '·');
        } else {
          hexParts.push('  ');
          asciiParts.push(' ');
        }
      }

      rows.push({
        offset: rowOffset,
        hexFirst: hexParts.slice(0, 8).join(' '),
        hexSecond: hexParts.slice(8, 16).join(' '),
        ascii: asciiParts.join('')
      });
    }

    return {
      offset: '0x' + offset.toString(16).toUpperCase(),
      totalBytes: raw.length,
      rows
    };
  }

  // Dosyaları güvenli hedefe kurtar
  static async recoverFiles(sourcePath, filesToRecover, destinationDir) {
    if (!fs.existsSync(destinationDir)) {
      fs.mkdirSync(destinationDir, { recursive: true });
    }

    const results = [];
    let recoveredCount = 0;
    let failedCount = 0;

    for (const item of filesToRecover) {
      try {
        const safeName = item.name.replace(/[/\\?%*:|"<>]/g, '_');
        const targetFilePath = path.join(destinationDir, safeName);

        if (item.filePath && fs.existsSync(item.filePath)) {
          // Dosyayı doğrudan kopyala
          fs.copyFileSync(item.filePath, targetFilePath);
          const data = fs.readFileSync(targetFilePath);
          const sha256 = crypto.createHash('sha256').update(data).digest('hex');

          results.push({
            id: item.id,
            name: safeName,
            path: targetFilePath,
            size: data.length,
            sha256,
            success: true
          });
          recoveredCount++;
        } else {
          // Ham disk ofsetinden oku ve yaz
          const fd = fs.openSync(sourcePath, 'r');
          const buffer = Buffer.alloc(item.size);
          const bytesRead = fs.readSync(fd, buffer, 0, item.size, item.offset);
          fs.closeSync(fd);

          const finalData = buffer.subarray(0, bytesRead);
          fs.writeFileSync(targetFilePath, finalData);
          const sha256 = crypto.createHash('sha256').update(finalData).digest('hex');

          results.push({
            id: item.id,
            name: safeName,
            path: targetFilePath,
            size: finalData.length,
            sha256,
            success: true
          });
          recoveredCount++;
        }
      } catch (err) {
        results.push({
          id: item.id,
          name: item.name,
          error: err.message,
          success: false
        });
        failedCount++;
      }
    }

    const reportPath = path.join(destinationDir, `kurtarma_raporu_${Date.now()}.json`);
    const report = {
      timestamp: new Date().toISOString(),
      sourcePath,
      destinationDir,
      totalRequested: filesToRecover.length,
      recoveredCount,
      failedCount,
      files: results
    };
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), 'utf8');

    return {
      recoveredCount,
      failedCount,
      reportPath,
      items: results
    };
  }
}
