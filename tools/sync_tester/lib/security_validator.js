/**
 * Authentication & PIN Security Validator Module
 * Validates PIN verification, auth headers, query params, brute force resistance and edge-case inputs.
 */
const http = require('http');

function makeRequest({ host, port, path, method = 'GET', headers = {}, body = null, timeoutMs = 4000 }) {
  return new Promise((resolve) => {
    const start = process.hrtime.bigint();
    const reqHeaders = {
      'User-Agent': 'SyncTester-Security/1.0',
      'Accept': '*/*',
      ...headers
    };

    if (body) {
      if (!reqHeaders['Content-Type']) {
        reqHeaders['Content-Type'] = 'application/octet-stream';
      }
      reqHeaders['Content-Length'] = Buffer.isBuffer(body) ? body.length : Buffer.byteLength(body);
    }

    const req = http.request(
      {
        hostname: host,
        port,
        path,
        method,
        headers: reqHeaders,
        timeout: timeoutMs
      },
      (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => {
          const diffNs = process.hrtime.bigint() - start;
          const latencyMs = Math.round((Number(diffNs) / 1e6) * 100) / 100;
          resolve({
            ok: true,
            statusCode: res.statusCode,
            headers: res.headers,
            body: Buffer.concat(chunks),
            latencyMs
          });
        });
      }
    );

    req.on('timeout', () => {
      req.destroy();
      resolve({ ok: false, statusCode: 0, error: `İstek zaman aşımına uğradı (${timeoutMs} ms)` });
    });

    req.on('error', (err) => {
      resolve({ ok: false, statusCode: 0, error: err.message });
    });

    if (body) {
      req.write(body);
    }
    req.end();
  });
}

/**
 * Runs security & PIN validation test suite
 * @param {object} options
 * @param {string} options.host
 * @param {number} options.port
 * @param {string} options.validPin
 * @returns {Promise<Array<object>>}
 */
async function runSecurityTests({ host, port, validPin }) {
  const tests = [];

  // Helper to record test
  function record(id, title, passed, details, latencyMs = 0, warning = false) {
    tests.push({
      id,
      category: 'GÜVENLİK & KİMLİK DOĞRULAMA',
      title,
      status: passed ? 'PASS' : warning ? 'WARN' : 'FAIL',
      details,
      latencyMs
    });
  }

  // 1. Missing PIN on download endpoint
  {
    const res = await makeRequest({ host, port, path: '/api/sync/download' });
    const passed = res.ok && res.statusCode === 401;
    record(
      'SEC-01',
      'PIN Başlığı Olmadan İndirme Reddi (HTTP 401)',
      passed,
      passed
        ? `PIN olmadan yapılan istek başarıyla HTTP 401 ile reddedildi.`
        : `Beklenen HTTP 401, Alınan: ${res.statusCode} (${res.error || 'Yanıt içeriği incelendi'})`,
      res.latencyMs
    );
  }

  // 2. Invalid PIN in X-Sync-PIN header
  {
    const wrongPin = validPin === '9999' ? '0000' : '9999';
    const res = await makeRequest({
      host,
      port,
      path: '/api/sync/download',
      headers: { 'x-sync-pin': wrongPin }
    });
    const passed = res.ok && res.statusCode === 401;
    record(
      'SEC-02',
      'Geçersiz PIN ile İndirme Reddi (X-Sync-PIN: 9999)',
      passed,
      passed
        ? `Hatalı PIN ile yapılan istek güvenli şekilde HTTP 401 ile durduruldu.`
        : `Hatalı PIN reddedilmedi! HTTP Kodu: ${res.statusCode}`,
      res.latencyMs
    );
  }

  // 3. Valid PIN in X-Sync-PIN header
  if (validPin) {
    const res = await makeRequest({
      host,
      port,
      path: '/api/sync/download',
      headers: { 'x-sync-pin': validPin }
    });
    const passed = res.ok && res.statusCode === 200;
    record(
      'SEC-03',
      'Doğru PIN ile Başarılı Yetkilendirme (X-Sync-PIN)',
      passed,
      passed
        ? `Geçerli PIN (${validPin}) kabul edildi, HTTP 200 alındı. İçerik boyutu: ${res.body.length} bayt.`
        : `Geçerli PIN kabul edilmedi! HTTP Kodu: ${res.statusCode} Hata: ${res.error || res.body.toString('utf-8').slice(0, 100)}`,
      res.latencyMs
    );
  } else {
    record(
      'SEC-03',
      'Doğru PIN Testi (X-Sync-PIN)',
      false,
      'Test atlandı: Doğrulanacak geçerli PIN parametresi (--pin) girilmedi.',
      0,
      true
    );
  }

  // 4. Valid PIN via Query Parameter (?pin=...)
  if (validPin) {
    const res = await makeRequest({
      host,
      port,
      path: `/api/sync/download?pin=${encodeURIComponent(validPin)}`
    });
    const passed = res.ok && res.statusCode === 200;
    record(
      'SEC-04',
      'URL Parametresi ile Yetkilendirme (?pin=...) Doğrulaması',
      passed,
      passed
        ? `Query parametresindeki PIN kabul edildi, web tarayıcı doğrudan indirme desteği teyit edildi.`
        : `URL parametresindeki PIN kabul edilmedi. HTTP Kodu: ${res.statusCode}`,
      res.latencyMs
    );
  }

  // 5. Compatibility Header (X-Pairing-PIN)
  if (validPin) {
    const res = await makeRequest({
      host,
      port,
      path: '/api/sync/download',
      headers: { 'x-pairing-pin': validPin }
    });
    // Note: some servers only check x-sync-pin, warn if 401 but not fatal
    const passed = res.ok && res.statusCode === 200;
    record(
      'SEC-05',
      'Alternatif Başlık Desteği (X-Pairing-PIN)',
      passed,
      passed
        ? `'X-Pairing-PIN' başlığı başarıyla destekleniyor (HTTP 200).`
        : `Sunucu yalnızca 'X-Sync-PIN' başlığını veya URL parametresini dinliyor (HTTP ${res.statusCode}).`,
      res.latencyMs,
      !passed
    );
  }

  // 6. Injection & Boundary PIN values
  const boundaryTests = [
    { name: 'Boş Dize ("")', pin: '' },
    { name: 'Boşluk Karakteri ("   ")', pin: '   ' },
    { name: 'Negatif Değer ("-1")', pin: '-1' },
    { name: 'Aşırı Uzun Değer ("1234567890123456")', pin: '1234567890123456' },
    { name: 'SQL Injection Koruması ("\' OR 1=1 --")', pin: "' OR 1=1 --" },
    { name: 'Özel Karakter ("<script>")', pin: '<script>' }
  ];

  let boundaryPassed = 0;
  for (const b of boundaryTests) {
    const res = await makeRequest({
      host,
      port,
      path: '/api/sync/download',
      headers: { 'x-sync-pin': b.pin }
    });
    if (res.ok && res.statusCode === 401) {
      boundaryPassed++;
    }
  }

  record(
    'SEC-06',
    'Sınır Değer ve SQL/XSS Enjeksiyon Koruması',
    boundaryPassed === boundaryTests.length,
    `${boundaryTests.length} sınır durumdan ${boundaryPassed} tanesi güvenli şekilde HTTP 401 ile reddedildi.`,
    0
  );

  // 7. Brute-force & Concurrency Resilience Test (10 rapid invalid PIN attempts)
  {
    const attempts = 10;
    const promises = [];
    const startTime = Date.now();

    for (let i = 0; i < attempts; i++) {
      const randomWrongPin = Math.floor(1000 + Math.random() * 8999).toString();
      promises.push(
        makeRequest({
          host,
          port,
          path: '/api/sync/download',
          headers: { 'x-sync-pin': randomWrongPin },
          timeoutMs: 2000
        })
      );
    }

    const responses = await Promise.all(promises);
    const elapsedMs = Date.now() - startTime;
    const all401 = responses.every((r) => r.ok && r.statusCode === 401);
    const avgLatency = Math.round(elapsedMs / attempts);

    record(
      'SEC-07',
      'Seri Hatalı PIN / Brute-Force Dayanıklılık Testi',
      all401,
      all401
        ? `${attempts} eşzamanlı hatalı PIN denemesi sunucu kilitlenmeden ${elapsedMs} ms içinde tamamlandı (Ort. ${avgLatency} ms/istek).`
        : `Bazı istekler başarısız oldu veya sunucu kilitlendi.`,
      avgLatency
    );
  }

  return tests;
}

module.exports = {
  makeRequest,
  runSecurityTests
};
