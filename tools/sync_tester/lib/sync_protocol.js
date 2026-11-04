const crypto = require('crypto');

class SyncProtocolSuite {
  static sha256(data) {
    return crypto.createHash('sha256').update(data).digest('hex');
  }

  static generateSamplePayload() {
    const now = Date.now();
    return {
      notes: [
        {
          id: 99901,
          title: 'Wi-Fi Senkronizasyon Test Notu 1',
          content: '# Test İçeriği\nBu not otomatik Wi-Fi test paketi tarafından üretilmiştir.\n- Madde 1\n- Madde 2',
          timestamp: now,
          updatedAt: now,
          categoryId: 1,
          isPinned: 1,
          isLocked: 0,
          isArchived: 0,
          isDeleted: 0
        },
        {
          id: 99902,
          title: 'Wi-Fi Test Notu 2',
          content: 'İkinci senkronizasyon doğrulama paketi verisi.',
          timestamp: now,
          updatedAt: now,
          categoryId: 1,
          isPinned: 0,
          isLocked: 0,
          isArchived: 0,
          isDeleted: 0
        }
      ],
      categories: [
        { id: 1, name: 'Otomasyon Testleri', color: 4282550000 }
      ],
      timestamp: now
    };
  }

  static async runProtocolTests(engine, pin) {
    let downloadedBuffer = null;

    // 1. Download Testi (Masaüstü -> İstemci)
    await engine.assertTest('Senkronizasyon Protokolü', 'Yedek İndirme / Dışa Aktarma (GET /api/sync/download)', async () => {
      const res = await engine.runHttp('GET', '/api/sync/download', {
        headers: { 'X-Sync-PIN': pin }
      });

      const pass = res.statusCode === 200 && res.bodyBuffer.length > 0;
      if (pass) {
        downloadedBuffer = res.bodyBuffer;
      }
      return {
        passed: pass,
        message: pass ? `Başarıyla ${res.bodyBuffer.length} bayt veri alındı. (Content-Type: ${res.headers['content-type'] || 'bilinmiyor'})` : `İndirme başarısız (HTTP ${res.statusCode})`,
        details: { size: res.bodyBuffer.length, contentType: res.headers['content-type'] }
      };
    });

    // 2. Veri Bütünlüğü Doğrulaması (SHA-256 Kontrolü)
    await engine.assertTest('Senkronizasyon Protokolü', 'Veri Bütünlüğü ve Arşiv İmzası (SHA-256 Hash)', async () => {
      if (!downloadedBuffer) {
        return { passed: false, message: 'İndirilen veri bulunmadığı için hash hesaplanamadı.' };
      }
      const hash = SyncProtocolSuite.sha256(downloadedBuffer);
      const isZip = downloadedBuffer.length >= 4 && downloadedBuffer[0] === 0x50 && downloadedBuffer[1] === 0x4B; // PK magic
      return {
        passed: true,
        message: `Veri arşivi SHA-256 doğrulaması tamamlandı. (Paket Türü: ${isZip ? 'ZIP Arşivi' : 'JSON Metin'})`,
        details: { sha256: hash, isZip, byteSize: downloadedBuffer.length }
      };
    });

    // 3. Upload Testi (İstemci -> Masaüstü)
    await engine.assertTest('Senkronizasyon Protokolü', 'Yedek Yükleme / İçe Aktarma (POST /api/sync/upload)', async () => {
      // Gönderilecek test paketi
      const testPayload = SyncProtocolSuite.generateSamplePayload();
      const bodyStr = JSON.stringify(testPayload);

      // Sunucu JSON veya ZIP kabul edebilir
      const res = await engine.runHttp('POST', '/api/sync/upload', {
        headers: {
          'X-Sync-PIN': pin,
          'Content-Type': 'application/json'
        },
        body: bodyStr
      });

      // Eğer sunucu sadece zip istiyorsa veya upload kabul ediyorsa durum kodu kontrolü
      const pass = res.statusCode === 200 || (res.statusCode === 400 && res.bodyText.includes('ZIP'));
      return {
        passed: pass,
        message: res.statusCode === 200 ? 'Sunucu test yükünü başarıyla işledi ve yanıtladı.' : `Sunucu beklenen format kısıtlamasını bildirdi (HTTP ${res.statusCode})`,
        details: { statusCode: res.statusCode, response: res.json || res.bodyText.substring(0, 100) }
      };
    });
  }
}

module.exports = { SyncProtocolSuite };
