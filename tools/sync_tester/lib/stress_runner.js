/**
 * Edge Cases & Stress Runner Module
 * Evaluates server stability under oversized payloads, malformed data, and concurrent requests.
 */
const { makeRequest } = require('./security_validator');
const { probeTarget } = require('./discovery_probe');

/**
 * Runs the Edge Cases & Stress Test Suite
 * @param {object} options
 * @param {string} options.host
 * @param {number} options.port
 * @param {string} options.validPin
 * @param {number} options.concurrency - default 5
 * @returns {Promise<Array<object>>}
 */
async function runStressTests({ host, port, validPin, concurrency = 5 }) {
  const tests = [];

  function record(id, title, passed, details, latencyMs = 0, warning = false) {
    tests.push({
      id,
      category: 'SINIR DURUMLAR & STRES / YÜK TESTLERİ',
      title,
      status: passed ? 'PASS' : warning ? 'WARN' : 'FAIL',
      details,
      latencyMs
    });
  }

  // 1. Oversized Payload Test (10 MB dummy buffer)
  {
    const payloadSize = 10 * 1024 * 1024; // 10 MB
    const dummyBuffer = Buffer.alloc(payloadSize, 0x41); // 'A's

    const res = await makeRequest({
      host,
      port,
      path: '/api/sync/upload',
      method: 'POST',
      headers: {
        'x-sync-pin': validPin || '1234',
        'Content-Type': 'application/zip',
        'Content-Length': payloadSize
      },
      body: dummyBuffer,
      timeoutMs: 12000
    });

    // Check if server is still alive
    const postProbe = await probeTarget({ host, port, timeoutMs: 2500 });
    const serverAlive = postProbe.tcpReachable;

    // HTTP 413 Payload Too Large, 400 Bad Request, 401 Unauthorized or even 200 with error is acceptable as long as server doesn't crash
    const safeRejection = res.statusCode === 413 || res.statusCode === 400 || res.statusCode === 413 || (res.statusCode >= 400 && res.statusCode < 500);
    const passed = serverAlive && (safeRejection || res.statusCode === 200 || res.statusCode === 500);

    record(
      'STR-01',
      'Aşırı Yük Güvenlik Testi (10 MB Payload Too Large)',
      passed,
      serverAlive
        ? `10 MB yük sonrası sunucu çökmedi (HTTP: ${res.statusCode}, Ping: ${postProbe.tcpLatencyMs} ms). ${res.statusCode === 413 ? 'Mükemmel: HTTP 413 Payload Too Large döndü.' : 'Sunucu isteği güvenle işledi/tahliye etti.'}`
        : `KRİTİK HATA: 10 MB yük sunucunun çökmesine/yanıt veremez hale gelmesine neden oldu!`,
      res.latencyMs
    );
  }

  // 2. Corrupt / Malformed ZIP Packet
  {
    // Generate 64 KB of random corrupt bytes with broken zip header
    const corruptBuffer = Buffer.alloc(64 * 1024);
    corruptBuffer.write('PK\x03\x04\xFF\xFF', 0); // Fake broken zip header followed by garbage
    for (let i = 6; i < corruptBuffer.length; i++) {
      corruptBuffer[i] = (i * 37) % 256;
    }

    const res = await makeRequest({
      host,
      port,
      path: '/api/sync/upload',
      method: 'POST',
      headers: {
        'x-sync-pin': validPin || '1234',
        'Content-Type': 'application/zip'
      },
      body: corruptBuffer,
      timeoutMs: 6000
    });

    const postProbe = await probeTarget({ host, port, timeoutMs: 2500 });
    const serverAlive = postProbe.tcpReachable;

    // Server should safely report bad request or handled error without unhandled exception crash
    const passed = serverAlive;
    record(
      'STR-02',
      'Bozuk ZIP / Geçersiz İkili Veri Paketi Testi',
      passed,
      serverAlive
        ? `Bozuk veri paketi gönderildiğinde sunucu kararlı kaldı (HTTP: ${res.statusCode}). Unhandled Exception oluşmadı.`
        : `Sunucu bozuk ikili veri paketinde çöktü!`,
      res.latencyMs
    );
  }

  // 3. Concurrency Stress Test
  {
    const clientCount = Math.max(2, concurrency);
    const promises = [];
    const startTime = Date.now();

    for (let i = 0; i < clientCount; i++) {
      // Alternate between status and download requests
      const path = (i % 2 === 0 || !validPin)
        ? '/api/status'
        : `/api/sync/download?pin=${encodeURIComponent(validPin)}`;

      promises.push(
        makeRequest({
          host,
          port,
          path,
          headers: validPin ? { 'x-sync-pin': validPin } : {},
          timeoutMs: 5000
        })
      );
    }

    const responses = await Promise.all(promises);
    const totalElapsedMs = Date.now() - startTime;

    const successfulResponses = responses.filter((r) => r.ok && (r.statusCode === 200 || r.statusCode === 401));
    const allSuccessful = successfulResponses.length === clientCount;

    const latencies = responses.map((r) => r.latencyMs).filter((l) => l >= 0);
    const minLatency = latencies.length ? Math.min(...latencies) : 0;
    const maxLatency = latencies.length ? Math.max(...latencies) : 0;
    const avgLatency = latencies.length ? Math.round(latencies.reduce((a, b) => a + b, 0) / latencies.length) : 0;
    const reqPerSec = totalElapsedMs > 0 ? Math.round((clientCount / (totalElapsedMs / 1000)) * 10) / 10 : 0;

    record(
      'STR-03',
      `Eşzamanlı Çoklu İstemci Testi (${clientCount} Paralel İstek)`,
      allSuccessful,
      allSuccessful
        ? `${clientCount} eşzamanlı istek ${totalElapsedMs} ms sürede tamamlandı. Kilitlenme: 0. Hız: ${reqPerSec} req/sn (Min: ${minLatency}ms, Ort: ${avgLatency}ms, Max: ${maxLatency}ms).`
        : `${clientCount} istekten ${clientCount - successfulResponses.length} tanesi başarısız oldu veya kilitlendi.`,
      avgLatency
    );
  }

  return tests;
}

module.exports = {
  runStressTests
};
