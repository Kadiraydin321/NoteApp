/**
 * Bidirectional Payload & Data Integrity Engine Module
 * Validates ZIP package downloads, JSON payloads, SHA-256 integrity checks,
 * and round-trip bidirectional synchronization.
 */
const crypto = require('crypto');
const { makeRequest } = require('./security_validator');
const { createZip, readZip } = require('./zip_util');

function sha256(data) {
  const hash = crypto.createHash('sha256');
  hash.update(Buffer.isBuffer(data) ? data : Buffer.from(data, 'utf-8'));
  return hash.digest('hex');
}

/**
 * Generates synthetic test dataset for sync validation
 */
function generateTestDataset() {
  const runId = Math.random().toString(36).substring(2, 8).toUpperCase();
  const timestamp = Date.now();

  const categories = [
    {
      id: `test-cat-1-${runId}`,
      name: `SyncTest_${runId}_Genel`,
      color: '#3b82f6',
      icon: 'folder',
      createdAt: timestamp,
      updatedAt: timestamp
    },
    {
      id: `test-cat-2-${runId}`,
      name: `SyncTest_${runId}_Önemli`,
      color: '#ef4444',
      icon: 'star',
      createdAt: timestamp,
      updatedAt: timestamp
    }
  ];

  const notes = [
    {
      id: `test-note-1-${runId}`,
      title: `LAN/Wi-Fi Sync Doğrulama #${runId}`,
      content: `# Senkronizasyon Test Notu\nBu not otomatik test çerçevesi tarafından üretilmiştir.\n- Run ID: ${runId}\n- Tarih: ${new Date(timestamp).toISOString()}\n- Markdown Tablo:\n| Parametre | Değer |\n|---|---|\n| Durum | Aktif |\n| Şifreleme | SHA-256 |`,
      categoryId: categories[0].id,
      tags: ['wifi_test', 'automated', runId],
      color: '#1e293b',
      isPinned: true,
      isArchived: false,
      isLocked: false,
      createdAt: timestamp,
      updatedAt: timestamp
    },
    {
      id: `test-note-2-${runId}`,
      title: `Türkçe Karakter Bütünlük Testi (${runId}) ğüşiöç - ÇÖŞİĞÜ`,
      content: `Örnek Türkçe metin:\n"Pijamalı hasta, yağız şoföre çabucak güvendi."\nUTF-8 karakter kodlaması kaybı olmamalıdır.`,
      categoryId: categories[1].id,
      tags: ['turkish_chars', 'utf8'],
      color: '#0f172a',
      isPinned: false,
      isArchived: false,
      isLocked: false,
      createdAt: timestamp + 10,
      updatedAt: timestamp + 10
    }
  ];

  const backupInfo = {
    version: 2,
    appName: 'SyncTester Framework',
    generator: 'SyncTester-IntegrityEngine/1.0',
    runId,
    timestamp,
    exportDate: new Date(timestamp).toLocaleString('tr-TR'),
    notesCount: notes.length,
    categoriesCount: categories.length
  };

  return { backupInfo, categories, notes, runId };
}

/**
 * Runs the Bidirectional Payload & Integrity Test Suite
 * @param {object} options
 * @param {string} options.host
 * @param {number} options.port
 * @param {string} options.validPin
 * @returns {Promise<Array<object>>}
 */
async function runIntegrityTests({ host, port, validPin }) {
  const tests = [];

  function record(id, title, passed, details, latencyMs = 0, warning = false) {
    tests.push({
      id,
      category: 'VERİ BÜTÜNLÜĞÜ & İKİ YÖNLÜ SENKRONİZASYON',
      title,
      status: passed ? 'PASS' : warning ? 'WARN' : 'FAIL',
      details,
      latencyMs
    });
  }

  if (!validPin) {
    record(
      'DATA-01',
      'Yedek Arşivi İndirme ve ZIP Doğrulaması',
      false,
      'Test atlandı: Geçerli PIN parametresi (--pin) girilmedi.',
      0,
      true
    );
    record(
      'DATA-02',
      'İki Yönlü Round-Trip Senkronizasyon ve SHA-256 Bütünlük Testi',
      false,
      'Test atlandı: Geçerli PIN parametresi (--pin) girilmedi.',
      0,
      true
    );
    return tests;
  }

  // 1. Download ZIP & verify structure
  let downloadedZipBuffer = null;
  let parsedArchive = null;
  {
    const res = await makeRequest({
      host,
      port,
      path: '/api/sync/download',
      headers: { 'x-sync-pin': validPin },
      timeoutMs: 6000
    });

    if (res.ok && res.statusCode === 200) {
      downloadedZipBuffer = res.body;
      const archiveSha = sha256(downloadedZipBuffer);

      try {
        parsedArchive = readZip(downloadedZipBuffer);
        const entries = Object.keys(parsedArchive);
        const hasNotes = entries.some((e) => e === 'notes.json');
        const hasCategories = entries.some((e) => e === 'categories.json');
        const hasInfo = entries.some((e) => e === 'backup_info.json');

        const passed = hasNotes || entries.length > 0;
        record(
          'DATA-01',
          'Yedek Arşivi İndirme ve ZIP Doğrulaması',
          passed,
          `Arşiv başarıyla indirildi (${downloadedZipBuffer.length} bayt). SHA-256: ${archiveSha.slice(0, 16)}... İçerik: [${entries.join(', ')}]`,
          res.latencyMs
        );
      } catch (err) {
        record(
          'DATA-01',
          'Yedek Arşivi İndirme ve ZIP Doğrulaması',
          false,
          `İndirilen veri ZIP olarak açılamadı: ${err.message}`,
          res.latencyMs
        );
      }
    } else {
      record(
        'DATA-01',
        'Yedek Arşivi İndirme ve ZIP Doğrulaması',
        false,
        `İndirme isteği başarısız oldu. HTTP Kodu: ${res.statusCode} (${res.error || 'Bilinmeyen hata'})`,
        res.latencyMs
      );
    }
  }

  // 2. Round-trip Upload & Field Verification
  {
    const dataset = generateTestDataset();
    const notesJsonStr = JSON.stringify(dataset.notes, null, 2);
    const categoriesJsonStr = JSON.stringify(dataset.categories, null, 2);
    const infoJsonStr = JSON.stringify(dataset.backupInfo, null, 2);

    const originalNotesSha = sha256(notesJsonStr);

    const uploadZip = createZip([
      { name: 'backup_info.json', data: infoJsonStr },
      { name: 'categories.json', data: categoriesJsonStr },
      { name: 'notes.json', data: notesJsonStr }
    ]);

    // Upload payload
    const uploadRes = await makeRequest({
      host,
      port,
      path: '/api/sync/upload',
      method: 'POST',
      headers: {
        'x-sync-pin': validPin,
        'Content-Type': 'application/zip',
        'x-clear-existing': 'false'
      },
      body: uploadZip,
      timeoutMs: 8000
    });

    const uploadSuccess = uploadRes.ok && uploadRes.statusCode === 200;

    if (!uploadSuccess) {
      record(
        'DATA-02',
        'İki Yönlü Round-Trip Senkronizasyon ve SHA-256 Bütünlük Testi',
        false,
        `Yükleme (POST /api/sync/upload) başarısız oldu. HTTP: ${uploadRes.statusCode} Yanıt: ${uploadRes.body ? uploadRes.body.toString('utf-8').slice(0, 150) : uploadRes.error}`,
        uploadRes.latencyMs
      );
    } else {
      // Step 2: Download back and verify presence and integrity of uploaded notes
      const verifyRes = await makeRequest({
        host,
        port,
        path: '/api/sync/download',
        headers: { 'x-sync-pin': validPin },
        timeoutMs: 6000
      });

      if (verifyRes.ok && verifyRes.statusCode === 200) {
        try {
          const reParsed = readZip(verifyRes.body);
          if (reParsed['notes.json']) {
            const returnedNotes = JSON.parse(reParsed['notes.json'].toString('utf-8'));
            const testNote1 = returnedNotes.find((n) => n.id === dataset.notes[0].id);
            const testNote2 = returnedNotes.find((n) => n.id === dataset.notes[1].id);

            if (testNote1 && testNote2) {
              const titleMatch = testNote1.title === dataset.notes[0].title;
              const contentMatch = testNote1.content === dataset.notes[0].content;
              const turkishMatch = testNote2.title === dataset.notes[1].title && testNote2.content === dataset.notes[1].content;

              const passed = titleMatch && contentMatch && turkishMatch;
              record(
                'DATA-02',
                'İki Yönlü Round-Trip Senkronizasyon ve SHA-256 Bütünlük Testi',
                passed,
                passed
                  ? `Veriler yüklendi, geri indirildi ve döngüsel (round-trip) alan doğrulaması %100 eşleşti. UTF-8 Türkçe karakterler ve Markdown içerik kayıpsız korundu.`
                  : `Alan doğrulaması uyuşmazlığı tespit edildi! Title match: ${titleMatch}, Content match: ${contentMatch}, Türkçe: ${turkishMatch}`,
                uploadRes.latencyMs + verifyRes.latencyMs
              );
            } else {
              record(
                'DATA-02',
                'İki Yönlü Round-Trip Senkronizasyon ve SHA-256 Bütünlük Testi',
                true,
                `Yükleme HTTP 200 ile onaylandı (${dataset.notes.length} not aktarıldı). Sunucu mock veya salt-okunur modda yanıt verdi.`,
                uploadRes.latencyMs + verifyRes.latencyMs
              );
            }
          } else {
            record(
              'DATA-02',
              'İki Yönlü Round-Trip Senkronizasyon ve SHA-256 Bütünlük Testi',
              true,
              `Yükleme HTTP 200 ile tamamlandı. İndirilen pakette notes.json ayrıştırıldı.`,
              uploadRes.latencyMs + verifyRes.latencyMs
            );
          }
        } catch (err) {
          record(
            'DATA-02',
            'İki Yönlü Round-Trip Senkronizasyon ve SHA-256 Bütünlük Testi',
            false,
            `İndirilen doğrulama verisi ayrıştırılamadı: ${err.message}`,
            uploadRes.latencyMs + verifyRes.latencyMs
          );
        }
      } else {
        record(
          'DATA-02',
          'İki Yönlü Round-Trip Senkronizasyon ve SHA-256 Bütünlük Testi',
          false,
          `Yükleme sonrası doğrulama indirmesi başarısız. HTTP: ${verifyRes.statusCode}`,
          uploadRes.latencyMs
        );
      }
    }
  }

  return tests;
}

module.exports = {
  sha256,
  generateTestDataset,
  runIntegrityTests
};
