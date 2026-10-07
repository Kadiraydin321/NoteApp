// tests/test-carver.js
// Carver Engine Test Suite

import path from 'node:path';
import fs from 'node:fs';
import { SampleDiskGenerator } from '../engine/sample-generator.js';
import { CarverEngine } from '../engine/carver.js';
import { DiskDetector } from '../engine/disk-detector.js';

async function runTests() {
  console.log('--- BYTERESCUE MOTOR TESTLERİ BAŞLIYOR ---\n');

  const testImgPath = '/tmp/byterescue_test_disk.img';
  const recoverDir = '/tmp/byterescue_recovered_output';

  if (fs.existsSync(recoverDir)) {
    fs.rmSync(recoverDir, { recursive: true, force: true });
  }

  // 1. Sentetik Disk İmajı Oluştur
  console.log('1. Sentetik test disk imajı oluşturuluyor...');
  const diskInfo = SampleDiskGenerator.createTestDisk(testImgPath);
  console.log(`   Oluşturuldu: ${diskInfo.path} (${(diskInfo.sizeBytes / 1024 / 1024).toFixed(1)} MB)`);
  console.log(`   Enjekte edilen dosyalar: ${diskInfo.injectedFiles.length} adet\n`);

  // 2. Disk Detector & Safe Guard Testi
  console.log('2. Disk Detector & Safe Guard denetimi test ediliyor...');
  const drives = DiskDetector.listDrives();
  console.log(`   Sistemde ${drives.length} adet sürücü/bölüm tespit edildi.`);
  
  const safeCheck = DiskDetector.checkSafeDestination(testImgPath, recoverDir);
  console.log(`   Hedef güvenlik kontrolü: ${safeCheck.level} - ${safeCheck.message}\n`);

  // 3. CarverEngine Taraması
  console.log('3. Ham Sektör Taraması (Carver) başlatılıyor...');
  const engine = new CarverEngine();

  const foundList = [];
  engine.on('started', (data) => {
    console.log(`   Tarama başladı. Toplam boyut: ${(data.totalBytes / 1024 / 1024).toFixed(1)} MB, İmzalar: ${data.totalSignatures}`);
  });

  engine.on('progress', (data) => {
    process.stdout.write(`\r   [İlerleme: %${data.percentage} | Hız: ${data.speedMBps} MB/s | Bulunan: ${data.filesFound}]`);
  });

  engine.on('file-found', (file) => {
    foundList.push(file);
    console.log(`\n   🎯 Bulundu: ${file.name} | Tür: ${file.type.toUpperCase()} | Ofset: ${file.hexOffset} | Boyut: ${file.size} B | Güven: %${file.confidence}`);
  });

  await engine.startScan({
    sourcePath: testImgPath,
    chunkSize: 1024 * 1024, // 1 MB parçalar
    sectorAlign: 512
  });

  console.log('\n\n4. Tarama tamamlandı. Bulunan toplam dosya:', foundList.length);

  if (foundList.length < 4) {
    throw new Error(`Beklenen en az 4 dosya bulunamadı! Bulunan: ${foundList.length}`);
  }

  // 5. Hex Döküm Testi
  console.log('\n5. Hex Görüntüleyici testi:');
  const firstFile = foundList[0];
  const hexDump = CarverEngine.generateHexDump(testImgPath, firstFile.offset, 64);
  console.log(`   Ofset: ${hexDump.offset} (İlk 32 bayt):`);
  for (const row of hexDump.rows.slice(0, 2)) {
    console.log(`   ${row.offset}  ${row.hexFirst}  ${row.hexSecond}  |${row.ascii}|`);
  }

  // 6. Dosya Kurtarma Testi
  console.log('\n6. Dosyaları diske kurtarma testi...');
  const recoveryResult = await CarverEngine.recoverFiles(testImgPath, foundList, recoverDir);
  console.log(`   Kurtarılan: ${recoveryResult.recoveredCount} / ${foundList.length}`);
  console.log(`   Rapor dosyası: ${recoveryResult.reportPath}`);

  for (const item of recoveryResult.items) {
    console.log(`   ✅ [${item.name}] Boyut: ${item.size} B | SHA-256: ${item.sha256.substring(0, 16)}...`);
  }

  console.log('\n🎉 TÜM MOTOR TESTLERİ BAŞARIYLA GEÇTİ!');
}

runTests().catch(err => {
  console.error('\n❌ Test Başarısız:', err);
  process.exit(1);
});
