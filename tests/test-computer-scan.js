// tests/test-computer-scan.js
import { DiskDetector } from '../engine/disk-detector.js';
import { CarverEngine } from '../engine/carver.js';

async function testComputerScan() {
  console.log('--- BİLGİSAYAR SÜRÜCÜSÜ & ÇÖP KUTUSU TARAMA TESTİ ---\n');

  // 1. Sürücü ve Klasörleri Listele
  const items = DiskDetector.listDrives();
  console.log(`Tespit edilen bilgisayar hedefleri: ${items.length} adet:`);
  for (const it of items) {
    console.log(`  [${it.category.toUpperCase()}] ${it.name} -> ${it.path} (${it.sizeHuman})`);
  }

  // 2. Windows Geri Dönüşüm Kutusu Taraması
  const recycleItem = items.find(i => i.id === 'recycle_c');
  if (recycleItem) {
    console.log(`\n2. Geri Dönüşüm Kutusu taranıyor: ${recycleItem.path}`);
    const engine = new CarverEngine();

    const foundFiles = [];
    engine.on('file-found', (f) => {
      foundFiles.push(f);
      console.log(`  🎯 Bulundu: ${f.name} | Tür: ${f.type} | Orijinal Yol: ${f.originalPath || '-'}`);
    });

    await engine.startScan({
      sourcePath: recycleItem.path,
      selectedCategories: ['images', 'documents', 'media', 'archives']
    });

    console.log(`\nGeri Dönüşüm Kutusundan bulunan dosya sayısı: ${foundFiles.length}`);
  }

  console.log('\n✅ BİLGİSAYAR TARAMA MOTORU TESTİ BAŞARILI!');
}

testComputerScan().catch(console.error);
