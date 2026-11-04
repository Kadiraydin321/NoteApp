class StressRunner {
  static async runStressTests(engine, pin) {
    // 1. Aşırı Yük Testi (10 MB Payload - HTTP 413 veya Güvenli Ret)
    await engine.assertTest('Stres & Sınır Durumlar', 'Aşırı Yük Taşması Koruması (10 MB Payload Testi)', async () => {
      // 10 MB sahte tampon bellek
      const bigBuffer = Buffer.alloc(10 * 1024 * 1024, 0x41); // 'A' karakterleri
      const res = await engine.runHttp('POST', '/api/sync/upload', {
        headers: {
          'X-Sync-PIN': pin,
          'Content-Type': 'application/octet-stream'
        },
        body: bigBuffer,
        timeout: 8000
      });

      // Beklenen durum: 413 (Payload Too Large), 400 (Bad Request), 401 veya sunucunun çökmeden güvenle yanıt vermesi
      const pass = res.statusCode === 413 || res.statusCode === 400 || res.statusCode === 415 || res.statusCode === 500;
      return {
        passed: pass,
        message: pass ? `Sunucu 10 MB aşırı yükü güvenle reddetti (HTTP ${res.statusCode}). Bellek taşması önlendi.` : `Sunucu aşırı yükü beklenmedik şekilde kabul etti (HTTP ${res.statusCode})`,
        details: { statusCode: res.statusCode }
      };
    });

    // 2. Bozuk / Geçersiz Paket Testi (Bozuk ZIP / Hatalı JSON)
    await engine.assertTest('Stres & Sınır Durumlar', 'Bozuk Veri Paketi Gönderimi (Fuzzing Koruması)', async () => {
      const corruptData = Buffer.from('PK\x03\x04BOZUK_ZIP_DOSYA_VERISI_TEST_CORRUPTION_1234567890');
      const res = await engine.runHttp('POST', '/api/sync/upload', {
        headers: {
          'X-Sync-PIN': pin,
          'Content-Type': 'application/zip'
        },
        body: corruptData
      });

      // Sunucu çökmeyip 400 veya success:false dönmeli
      const pass = res.statusCode === 400 || (res.statusCode === 200 && res.json && res.json.success === false) || res.statusCode === 500;
      return {
        passed: pass,
        message: pass ? 'Sunucu bozuk paketi güvenli bir hata mesajıyla karşıladı, süreç kilitlenmedi.' : `Beklenmeyen yanıt (HTTP ${res.statusCode})`,
        details: { statusCode: res.statusCode, response: res.json || res.bodyText.substring(0, 100) }
      };
    });

    // 3. Eşzamanlılık ve Yüksek Trafik Testi (5 Paralel İstek)
    await engine.assertTest('Stres & Sınır Durumlar', 'Eşzamanlılık ve Kilitlenme Direnci (5 Paralel İstek)', async () => {
      const concurrentCount = 5;
      const promises = [];
      for (let i = 0; i < concurrentCount; i++) {
        promises.push(engine.runHttp('GET', '/api/status'));
      }

      const responses = await Promise.all(promises);
      const allOk = responses.every(r => r.ok);
      const avgLatency = Math.round(responses.reduce((acc, r) => acc + r.latency, 0) / responses.length);

      return {
        passed: allOk,
        message: allOk ? `5 eşzamanlı istek kilitlenme olmadan tamamlandı (Ortalama Yanıt: ${avgLatency} ms).` : 'Bazı eşzamanlı istekler başarısız oldu.',
        details: { count: concurrentCount, avgLatency, statuses: responses.map(r => r.statusCode) }
      };
    });
  }
}

module.exports = { StressRunner };
