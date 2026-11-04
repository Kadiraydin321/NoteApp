#!/usr/bin/env node
/**
 * Cross-Platform LAN / Wi-Fi Sync Test Suite & Verification Framework Runner
 * Zero-dependency Node.js CLI
 */
const path = require('path');
const { probeTarget, scanSubnet } = require('./lib/discovery_probe');
const { runSecurityTests } = require('./lib/security_validator');
const { runIntegrityTests } = require('./lib/integrity_engine');
const { runStressTests } = require('./lib/stress_runner');
const {
  colors,
  banner,
  printSection,
  printTestResult,
  printSummary,
  generateMarkdownReport,
  generateJsonReport
} = require('./lib/reporter');
const MockSyncServer = require('./mock_server');

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    host: '127.0.0.1',
    port: 8765,
    pin: '1234',
    discover: false,
    mock: false,
    concurrency: 5,
    reportMd: 'SYNC_TEST_REPORT.md',
    reportJson: 'sync_test_results.json',
    help: false
  };

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--help' || arg === '-h') options.help = true;
    else if (arg === '--host' && args[i + 1]) options.host = args[++i];
    else if (arg === '--port' && args[i + 1]) options.port = parseInt(args[++i], 10);
    else if (arg === '--pin' && args[i + 1]) options.pin = args[++i];
    else if (arg === '--concurrency' && args[i + 1]) options.concurrency = parseInt(args[++i], 10);
    else if (arg === '--report-md' && args[i + 1]) options.reportMd = args[++i];
    else if (arg === '--report-json' && args[i + 1]) options.reportJson = args[++i];
    else if (arg === '--discover' || arg === '-d') options.discover = true;
    else if (arg === '--mock' || arg === '--spawn-mock' || arg === '-m') options.mock = true;
  }

  return options;
}

function printHelp() {
  banner();
  console.log(`
Kullanım:
  node run_tests.js [seçenekler]

Seçenekler:
  --host <ip>          Hedef sunucu IP adresi (Varsayılan: 127.0.0.1)
  --port <port>        Hedef sunucu portu (Varsayılan: 8765)
  --pin <kod>          Eşleşme PIN kodu (Varsayılan: 1234)
  --discover, -d       Yerel ağdaki (LAN) senkronizasyon sunucularını otomatik ara
  --mock, -m           Dahili Mock Sunucuyu başlat, testleri çalıştır ve kapat (Self-Test)
  --concurrency <sayı> Eşzamanlılık testinde çalışacak paralel istemci sayısı (Varsayılan: 5)
  --report-md <yol>    Üretilecek Markdown rapor dosyası (Varsayılan: SYNC_TEST_REPORT.md)
  --report-json <yol>  Üretilecek JSON rapor dosyası (Varsayılan: sync_test_results.json)
  --help, -h           Bu yardım mesajını göster

Örnekler:
  node run_tests.js --host 192.168.1.50 --port 8765 --pin 4829
  node run_tests.js --mock
  node run_tests.js --discover
`);
}

async function main() {
  const opts = parseArgs();

  if (opts.help) {
    printHelp();
    process.exit(0);
  }

  banner();

  // 1. Discovery Mode
  if (opts.discover) {
    printSection('LAN / Yerel Ağ Otomatik Keşif Taraması (Discovery)');
    console.log(`📡 Yerel alt ağlar taranıyor (Port: ${opts.port})...`);

    const discovered = await scanSubnet({
      port: opts.port,
      concurrency: 30,
      onProgress: (p) => {
        process.stdout.write(`\r⏳ Taranan adres: ${p.current}/${p.total} | Bulunan sunucu: ${p.found}`);
      }
    });

    console.log('\n');

    if (discovered.length === 0) {
      console.log(`${colors.yellow}⚠️  Ağda aktif senkronizasyon sunucusu bulunamadı.${colors.reset}`);
      console.log(`İpucu: Uygulamada "Wi-Fi Senkronizasyonu Başlat" seçeneğinin açık olduğundan emin olun.`);
      process.exit(0);
    }

    console.log(`${colors.green}🎯 Bulunan Senkronizasyon Sunucuları:${colors.reset}`);
    discovered.forEach((d, idx) => {
      console.log(` ${idx + 1}. http://${d.host}:${d.port} - ${d.serverInfo?.appName} (${d.httpLatencyMs} ms)`);
    });

    console.log(`\nBulunan sunucu ile test başlatmak için:`);
    console.log(`  node run_tests.js --host ${discovered[0].host} --port ${discovered[0].port} --pin <PIN_KODU>\n`);
    process.exit(0);
  }

  let spawnedMock = null;
  let targetHost = opts.host;
  let targetPort = opts.port;
  let validPin = opts.pin;

  // 2. Mock Server Mode (Self-Test)
  if (opts.mock) {
    printSection('Dahili Referans Mock Sunucu Başlatılıyor');
    targetPort = 8769; // Use unique port for mock tests
    validPin = '1234';
    spawnedMock = new MockSyncServer({ port: targetPort, pin: validPin });
    try {
      await spawnedMock.start();
      console.log(`${colors.green}✅ Mock Sync Server başarıyla başlatıldı: http://127.0.0.1:${targetPort}${colors.reset}`);
      console.log(`🔑 Test Eşleşme PIN Kodu: ${validPin}`);
      targetHost = '127.0.0.1';
    } catch (err) {
      console.error(`${colors.red}❌ Mock sunucu başlatılamadı:${colors.reset}`, err.message);
      process.exit(1);
    }
  }

  const startTime = Date.now();
  const allTests = [];

  // Phase 1: Discovery & Ping Probe
  printSection('Aşama 1: Keşif ve Bağlantı Yoklaması (Discovery & Ping Probe)');
  const probe = await probeTarget({ host: targetHost, port: targetPort, timeoutMs: 3000 });

  const probePass = probe.tcpReachable && probe.isSyncServer;
  const probeTest = {
    id: 'DISC-01',
    category: 'KEŞİF & BAĞLANTI (PROBE)',
    title: 'Hedef Sunucu Erişilebilirliği ve /api/status Durum Sorgusu',
    status: probePass ? 'PASS' : probe.tcpReachable ? 'WARN' : 'FAIL',
    details: probePass
      ? `Sunucu kimliği: "${probe.serverInfo?.appName}", Not Sayısı: ${probe.serverInfo?.notesCount ?? 0}, PIN Zorunlu: ${probe.serverInfo?.requiresPin}`
      : probe.tcpReachable
      ? `TCP portu açık ancak /api/status geçersiz yanıt verdi (HTTP ${probe.httpStatus}).`
      : `Bağlantı kurulamadı: ${probe.error}`,
    latencyMs: probe.httpLatencyMs >= 0 ? probe.httpLatencyMs : probe.tcpLatencyMs
  };
  allTests.push(probeTest);
  printTestResult(probeTest);

  if (!probe.tcpReachable && !opts.mock) {
    console.log(`\n${colors.yellow}⚠️  Sunucuya bağlanılamadı (${targetHost}:${targetPort}).${colors.reset}`);
    console.log(`Dahili Mock Sunucu ile testleri denemek isterseniz:\n  node run_tests.js --mock\n`);
    process.exit(1);
  }

  // Phase 2: Security & PIN Validation
  printSection('Aşama 2: Güvenlik ve Eşleşme PIN Doğrulama Motoru');
  const secTests = await runSecurityTests({
    host: targetHost,
    port: targetPort,
    validPin
  });
  for (const t of secTests) {
    allTests.push(t);
    printTestResult(t);
  }

  // Phase 3: Bidirectional Payload & Data Integrity
  printSection('Aşama 3: Çift Yönlü Veri Aktarımı ve Bütünlük Motoru (SHA-256)');
  const integrityTests = await runIntegrityTests({
    host: targetHost,
    port: targetPort,
    validPin
  });
  for (const t of integrityTests) {
    allTests.push(t);
    printTestResult(t);
  }

  // Phase 4: Edge Cases & Stress Runner
  printSection('Aşama 4: Sınır Durumlar ve Stres/Dayanıklılık Testleri');
  const stressTests = await runStressTests({
    host: targetHost,
    port: targetPort,
    validPin,
    concurrency: opts.concurrency
  });
  for (const t of stressTests) {
    allTests.push(t);
    printTestResult(t);
  }

  // Cleanup mock if spawned
  if (spawnedMock) {
    await spawnedMock.stop();
  }

  // Phase 5: Summarize & Export Reports
  const totalDuration = Date.now() - startTime;
  const passedCount = allTests.filter((t) => t.status === 'PASS').length;
  const failedCount = allTests.filter((t) => t.status === 'FAIL').length;
  const warnCount = allTests.filter((t) => t.status === 'WARN').length;
  const totalCount = allTests.length;
  const passRate = totalCount > 0 ? Math.round((passedCount / totalCount) * 100) : 0;

  const targetInfo = {
    host: targetHost,
    port: targetPort,
    appName: probe.serverInfo?.appName || 'Sync Server',
    notesCount: probe.serverInfo?.notesCount ?? 0,
    requiresPin: probe.serverInfo?.requiresPin ?? true
  };

  const summary = {
    total: totalCount,
    passed: passedCount,
    failed: failedCount,
    warnings: warnCount,
    passRate,
    durationMs: totalDuration,
    target: targetInfo
  };

  printSummary(summary);

  const mdFile = generateMarkdownReport({
    summary,
    tests: allTests,
    target: targetInfo,
    outputPath: opts.reportMd
  });
  const jsonFile = generateJsonReport({
    summary,
    tests: allTests,
    target: targetInfo,
    outputPath: opts.reportJson
  });

  console.log(`📄 Markdown Raporu Üretildi : ${colors.bright}${mdFile}${colors.reset}`);
  console.log(`💾 JSON Raporu Kaydedildi   : ${colors.bright}${jsonFile}${colors.reset}\n`);

  if (failedCount > 0) {
    process.exit(1);
  }
}

main().catch((err) => {
  console.error('\nBeklenmeyen Test Motoru Hatası:', err);
  process.exit(1);
});
