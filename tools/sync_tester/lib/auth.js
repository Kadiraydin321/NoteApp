class AuthValidator {
  static async runAuthTests(engine, pin) {
    // 1. PIN olmadan erişim denemesi (401 beklenir)
    await engine.assertTest('Güvenlik & Yetkilendirme', 'Eksik PIN Koruması (HTTP 401 Doğrulaması)', async () => {
      const res = await engine.runHttp('GET', '/api/sync/download');
      const pass = res.statusCode === 401;
      return {
        passed: pass,
        message: pass ? 'Sunucu eksik PIN ile erişimi HTTP 401 ile başarıyla engelledi.' : `Beklenen HTTP 401, Alınan: ${res.statusCode}`,
        details: { statusCode: res.statusCode }
      };
    });

    // 2. Yanlış PIN ile erişim denemesi (401 beklenir)
    await engine.assertTest('Güvenlik & Yetkilendirme', 'Hatalı PIN Koruması (HTTP 401 Doğrulaması)', async () => {
      const fakePin = '9999' === pin ? '0000' : '9999';
      const res = await engine.runHttp('GET', '/api/sync/download', {
        headers: { 'X-Sync-PIN': fakePin }
      });
      const pass = res.statusCode === 401;
      return {
        passed: pass,
        message: pass ? 'Sunucu hatalı PIN denemesini HTTP 401 ile reddetti.' : `Beklenen HTTP 401, Alınan: ${res.statusCode}`,
        details: { fakePin, statusCode: res.statusCode }
      };
    });

    // 3. Geçerli PIN - 'X-Sync-PIN' Başlığı Testi
    if (pin) {
      await engine.assertTest('Güvenlik & Yetkilendirme', "Geçerli PIN - 'X-Sync-PIN' Header Doğrulaması", async () => {
        const res = await engine.runHttp('GET', '/api/sync/download', {
          headers: { 'X-Sync-PIN': pin }
        });
        const pass = res.statusCode === 200;
        return {
          passed: pass,
          message: pass ? "Sunucu 'X-Sync-PIN' başlığı ile başarıyla yetkilendirdi (HTTP 200)." : `Hata kodu: ${res.statusCode}`,
          details: { statusCode: res.statusCode }
        };
      });

      // 4. Geçerli PIN - 'X-Pairing-PIN' Başlığı Testi
      await engine.assertTest('Güvenlik & Yetkilendirme', "Geçerli PIN - 'X-Pairing-PIN' Alternatif Başlık Desteği", async () => {
        const res = await engine.runHttp('GET', '/api/sync/download', {
          headers: { 'X-Pairing-PIN': pin }
        });
        const pass = res.statusCode === 200;
        return {
          passed: pass,
          message: pass ? "Sunucu 'X-Pairing-PIN' başlığı ile başarıyla yetkilendirdi (HTTP 200)." : `Hata kodu: ${res.statusCode}`,
          details: { statusCode: res.statusCode }
        };
      });

      // 5. Geçerli PIN - URL Parametresi Testi (?pin=...)
      await engine.assertTest('Güvenlik & Yetkilendirme', 'Geçerli PIN - URL Sorgu Parametresi (?pin=...) Desteği', async () => {
        const res = await engine.runHttp('GET', `/api/sync/download?pin=${pin}`);
        const pass = res.statusCode === 200;
        return {
          passed: pass,
          message: pass ? 'Sunucu URL parametresindeki PIN ile başarıyla yetkilendirdi (HTTP 200).' : `Hata kodu: ${res.statusCode}`,
          details: { statusCode: res.statusCode }
        };
      });
    }
  }
}

module.exports = { AuthValidator };
