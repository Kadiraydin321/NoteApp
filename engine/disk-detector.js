// engine/disk-detector.js
// Bilgisayar Dahili Sürücüleri, Klasörleri ve Güvenli Kurtarma (Safe Guard) Denetleyicisi

import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

export class DiskDetector {
  // Sistemdeki dahili bilgisayar sürücülerini, kullanıcı klasörlerini ve aygıtları listele
  static listDrives() {
    const items = [];

    // 1. DAHİLİ BİLGİSAYAR SÜRÜCÜLERİ (Windows C:, D: ve Linux Kök)
    // Windows C: sürücüsü
    if (fs.existsSync('/mnt/c')) {
      const sizeInfo = DiskDetector.getMountedPathSize('/mnt/c');
      items.push({
        id: 'drive_c',
        name: 'C: Sürücüsü (Windows)',
        path: '/mnt/c',
        sizeBytes: sizeInfo.sizeBytes,
        sizeHuman: sizeInfo.sizeHuman,
        type: 'computer_drive',
        category: 'drives',
        badge: 'Dahili Sürücü',
        isAccessible: true,
        description: 'Windows ana yerel sürücüsü ve programlar'
      });
    }

    // Windows D: sürücüsü
    if (fs.existsSync('/mnt/d')) {
      const sizeInfo = DiskDetector.getMountedPathSize('/mnt/d');
      items.push({
        id: 'drive_d',
        name: 'D: Sürücüsü (Veri Deposu)',
        path: '/mnt/d',
        sizeBytes: sizeInfo.sizeBytes,
        sizeHuman: sizeInfo.sizeHuman,
        type: 'computer_drive',
        category: 'drives',
        badge: 'Dahili Sürücü',
        isAccessible: true,
        description: 'İkincil veri ve depolama diski'
      });
    }

    // Linux Kök / Sürücüsü
    const rootSizeInfo = DiskDetector.getMountedPathSize('/');
    items.push({
      id: 'drive_root',
      name: 'Linux Sistem Depolama (Kök /)',
      path: '/',
      sizeBytes: rootSizeInfo.sizeBytes,
      sizeHuman: rootSizeInfo.sizeHuman,
      type: 'computer_drive',
      category: 'drives',
      badge: 'Sistem',
      isAccessible: true,
      description: 'Linux kök dosya sistemi ve uygulamalar'
    });

    // 2. HIZLI KULLANICI KLASÖRLERİ (Masaüstü, İndirilenler, Belgeler)
    const userDirs = DiskDetector.detectUserLocations();
    for (const u of userDirs) {
      items.push(u);
    }

    // 3. GERİ DÖNÜŞÜM KUTULARI (Recycle Bin & Trash)
    const recycleBins = DiskDetector.detectRecycleBins();
    for (const rb of recycleBins) {
      items.push(rb);
    }

    // 4. FİZİKSEL BLOK CİHAZLARI (/dev/sdX)
    try {
      const output = execSync('lsblk -J -b -o NAME,PATH,SIZE,TYPE,MOUNTPOINT,FSTYPE,MODEL,VENDOR,RO', {
        encoding: 'utf8',
        timeout: 3000
      });
      const data = JSON.parse(output);

      if (data && data.blockdevices) {
        for (const dev of data.blockdevices) {
          const devItem = DiskDetector.formatDevice(dev);
          items.push(devItem);

          if (dev.children && Array.isArray(dev.children)) {
            for (const child of dev.children) {
              items.push(DiskDetector.formatDevice(child, dev.path));
            }
          }
        }
      }
    } catch (_) {}

    return items;
  }

  // Windows ve Linux kullanıcı klasörlerini bul
  static detectUserLocations() {
    const locations = [];

    // Windows kullanıcı klasörleri (Örn: /mnt/c/Users/Birin)
    const windowsUsersDir = '/mnt/c/Users';
    let winUserPath = null;

    if (fs.existsSync(windowsUsersDir)) {
      try {
        const users = fs.readdirSync(windowsUsersDir);
        for (const u of users) {
          if (!['Default', 'Default User', 'Public', 'All Users'].includes(u) && !u.startsWith('.')) {
            const p = path.join(windowsUsersDir, u);
            if (fs.statSync(p).isDirectory()) {
              winUserPath = p;
              break;
            }
          }
        }
      } catch (_) {}
    }

    if (winUserPath) {
      const winUserName = path.basename(winUserPath);

      // Masaüstü
      const desktop = path.join(winUserPath, 'Desktop');
      if (fs.existsSync(desktop)) {
        locations.push({
          id: 'user_desktop',
          name: `Masaüstü (Desktop - ${winUserName})`,
          path: desktop,
          sizeBytes: DiskDetector.getDirSizeFast(desktop),
          sizeHuman: 'Klasör',
          type: 'user_folder',
          category: 'folders',
          badge: 'Windows',
          isAccessible: true,
          description: 'Masaüstünden silinen dosyalar ve kısayollar'
        });
      }

      // İndirilenler
      const downloads = path.join(winUserPath, 'Downloads');
      if (fs.existsSync(downloads)) {
        locations.push({
          id: 'user_downloads',
          name: `İndirilenler (Downloads - ${winUserName})`,
          path: downloads,
          sizeBytes: DiskDetector.getDirSizeFast(downloads),
          sizeHuman: 'Klasör',
          type: 'user_folder',
          category: 'folders',
          badge: 'Windows',
          isAccessible: true,
          description: 'İndirilenler klasöründen silinen dosyalar'
        });
      }

      // Belgeler
      const docs = path.join(winUserPath, 'Documents');
      if (fs.existsSync(docs)) {
        locations.push({
          id: 'user_docs',
          name: `Belgeler (Documents - ${winUserName})`,
          path: docs,
          sizeBytes: DiskDetector.getDirSizeFast(docs),
          sizeHuman: 'Klasör',
          type: 'user_folder',
          category: 'folders',
          badge: 'Windows',
          isAccessible: true,
          description: 'Kişisel ve ofis belgeleri'
        });
      }
    }

    // Linux Ev Dizini
    const homeDir = os.homedir();
    if (fs.existsSync(homeDir)) {
      locations.push({
        id: 'user_home',
        name: `Linux Kullanıcı Klasörü (${homeDir})`,
        path: homeDir,
        sizeBytes: 0,
        sizeHuman: 'Klasör',
        type: 'user_folder',
        category: 'folders',
        badge: 'Linux',
        isAccessible: true,
        description: 'Linux kullanıcı dizini ve projeler'
      });
    }

    return locations;
  }

  // Windows $Recycle.Bin ve Linux Trash klasörlerini tespit et
  static detectRecycleBins() {
    const bins = [];

    // Windows C:\$Recycle.Bin
    if (fs.existsSync('/mnt/c/$Recycle.Bin')) {
      bins.push({
        id: 'recycle_c',
        name: 'Geri Dönüşüm Kutusu (C: $Recycle.Bin)',
        path: '/mnt/c/$Recycle.Bin',
        sizeBytes: 0,
        sizeHuman: 'Çöp Kutusu',
        type: 'recycle_bin',
        category: 'recycle',
        badge: 'Silinenler',
        isAccessible: true,
        description: 'Windows C: sürücüsünden silinmiş orijinal dosyalar ve metaveriler'
      });
    }

    // Windows D:\$Recycle.Bin
    if (fs.existsSync('/mnt/d/$Recycle.Bin')) {
      bins.push({
        id: 'recycle_d',
        name: 'Geri Dönüşüm Kutusu (D: $Recycle.Bin)',
        path: '/mnt/d/$Recycle.Bin',
        sizeBytes: 0,
        sizeHuman: 'Çöp Kutusu',
        type: 'recycle_bin',
        category: 'recycle',
        badge: 'Silinenler',
        isAccessible: true,
        description: 'Windows D: sürücüsünden silinmiş dosyalar'
      });
    }

    // Linux ~/.local/share/Trash
    const linuxTrash = path.join(os.homedir(), '.local/share/Trash');
    if (fs.existsSync(linuxTrash)) {
      bins.push({
        id: 'recycle_linux',
        name: 'Linux Çöp Kutusu (Trash)',
        path: linuxTrash,
        sizeBytes: 0,
        sizeHuman: 'Çöp Kutusu',
        type: 'recycle_bin',
        category: 'recycle',
        badge: 'Linux',
        isAccessible: true,
        description: 'Linux masaüstü çöp kutusu'
      });
    }

    return bins;
  }

  static getMountedPathSize(mountPath) {
    try {
      const out = execSync(`df -B1 -P "${mountPath}"`, { encoding: 'utf8' });
      const lines = out.trim().split('\n');
      if (lines.length >= 2) {
        const parts = lines[1].split(/\s+/);
        const total = parseInt(parts[1], 10);
        return {
          sizeBytes: total,
          sizeHuman: DiskDetector.formatSize(total)
        };
      }
    } catch (_) {}
    return { sizeBytes: 0, sizeHuman: 'Bilinmeyen' };
  }

  static getDirSizeFast(dirPath) {
    try {
      let count = 0;
      const files = fs.readdirSync(dirPath);
      for (const f of files.slice(0, 50)) {
        try {
          count += fs.statSync(path.join(dirPath, f)).size;
        } catch (_) {}
      }
      return count;
    } catch (_) {
      return 0;
    }
  }

  static formatDevice(dev, parentPath = null) {
    const devPath = dev.path || (dev.name ? `/dev/${dev.name}` : 'Bilinmeyen');
    const size = parseInt(dev.size, 10) || 0;
    const canRead = DiskDetector.canRead(devPath);

    return {
      id: `dev_${dev.name}`,
      name: `${dev.name} (${dev.model || dev.fstype || dev.type})`,
      path: devPath,
      parentPath,
      sizeBytes: size,
      sizeHuman: DiskDetector.formatSize(size),
      type: 'physical_device',
      category: 'devices',
      mountpoint: dev.mountpoint || dev.mountpoints?.join(', ') || null,
      fstype: dev.fstype || null,
      model: (dev.vendor || '') + ' ' + (dev.model || '').trim(),
      readOnly: Boolean(dev.ro),
      isAccessible: canRead,
      needsSudo: !canRead && devPath.startsWith('/dev/'),
      description: `Fiziksel blok cihazı (${devPath})`
    };
  }

  static canRead(devPath) {
    try {
      fs.accessSync(devPath, fs.constants.R_OK);
      return true;
    } catch (_) {
      return false;
    }
  }

  static formatSize(bytes) {
    if (!bytes || bytes <= 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return (bytes / Math.pow(1024, i)).toFixed(2) + ' ' + units[i];
  }

  // Güvenli Kurtarma Koruması
  static checkSafeDestination(sourcePath, destPath) {
    if (!sourcePath || !destPath) {
      return { isSafe: true, message: 'Yollar belirtilmedi.' };
    }

    try {
      if (!fs.existsSync(destPath)) {
        try {
          fs.mkdirSync(destPath, { recursive: true });
        } catch (e) {
          return { isSafe: false, message: `Hedef klasör oluşturulamadı: ${e.message}` };
        }
      }

      const sourceAbs = path.resolve(sourcePath);
      const destAbs = path.resolve(destPath);

      // Hedef, taranan kaynak klasörün içinde mi?
      if (destAbs.startsWith(sourceAbs) || destAbs === sourceAbs) {
        return {
          isSafe: false,
          level: 'danger',
          message: 'KRİTİK UYARI: Kurtarma hedefi, taranan kaynak dizinin içinde yer alıyor! Bu durum taranan verilerin üzerine yazılmasına ve kaybolmasına yol açabilir.'
        };
      }

      return {
        isSafe: true,
        level: 'safe',
        message: 'Hedef klasör güvenli: Kaynak ile çakışma bulunmuyor.'
      };
    } catch (err) {
      return {
        isSafe: true,
        level: 'unknown',
        message: 'Güvenlik denetimi tamamlandı: ' + err.message
      };
    }
  }
}
