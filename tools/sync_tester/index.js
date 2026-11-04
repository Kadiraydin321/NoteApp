#!/usr/bin/env node

/**
 * Cross-Platform LAN / Wi-Fi Sync Tester CLI
 * Bağımsız, Herhangi bir projede kullanılabilir test aracı.
 */

const path = require('path');
const { SyncTestEngine } = require('./lib/engine');
const { DiscoveryProbe } = require('./lib/discovery');
const { AuthValidator } = require('./lib/auth');
const { SyncProtocolSuite } = require('./lib/sync_protocol');
const { StressRunner } = require('./lib/stress');
const { TestReporter } = require('./lib/reporter');

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    host: '127.0.0.1',
    port: 8765,
    pin: null,
    skipStress: false,
    autoProbe: true
  };

  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a === '--host' && args[i + 1]) options.host = args[++i];
    else if (a === '--port' && args[i + 1]) options.port = parseInt(args[++i], 10);
    else if (a === '--pin' && args[i + 1]) options.pin = args[++i];
    else if (a === '--no-stress') options.skipStress = true;
    else if (a === '--help' || a === '-h') {
      console.log(`
Cross-Platform Wi-Fi Sync Tester
Kullanım:
  node index.js [seçenekler]

Seçenekler:
  --host <ip>       Hedef sunucu IP adresi (Varsayılan: 127.0.0.1)
  --port <port>     Hedef port numarası (Varsayılan: 8765)
  --pin <pin>       Eşleşme PIN kodu (örn: 1234)
  --no-stress       Stres ve aşırı yük testlerini atla
  --help, -h        Bu yardım mesajını göster
`);
      process.exit(0);
    }
  }

  return options;
}

async function main() {
  const options = parseArgs();
  console.log(`\n🔍 Wi-Fi Senkronizasyon Test Aracı Başlatılıyor...`);
  console.log(`   Hedef: http://${options.host}:${options.port}\n`);

  const engine = new SyncTestEngine({
    host: options.host,
    port: options.port,
    pin: options.pin
  });

  // 1. Ağ ve Port Kontrolü
  const isOpen = await DiscoveryProbe.checkPortOpen(options.host, options.port);
  if (!isOpen) {
    console.error(`❌ [BAĞLANTI HATASI] ${options.host}:${options.port} portuna bağlanılamadı!`);
    console.error(`   Lütfen hedef cihazda masaüstü uygulamasını başlatın veya Wi-Fi senkronizasyonunu açın.\n`);
    process.exit(1);
  }

  // 2. Health & Status Yoklaması
  await engine.assertTest('Ağ ve Keşif', 'Port ve TCP Soket Erişilebilirliği', async () => {
    return { passed: true, message: `Port ${options.port} açık ve dinliyor.` };
  });

  let serverInfo = null;
  await engine.assertTest('Ağ ve Keşif', 'Sunucu Kimliği ve Durum Sorgulama (/api/status)', async () => {
    const probe = await DiscoveryProbe.probeServer(engine);
    if (probe.online) {
      serverInfo = probe.info;
      return {
        passed: true,
        message: `Sunucu çevrimiçi (${probe.endpoint}). Uygulama: ${serverInfo.appName || serverInfo.app || 'Bilinmiyor'}, Not Sayısı: ${serverInfo.notesCount ?? 0}, Gecikme: ${probe.latency} ms`,
        details: probe.info
      };
    } else {
      return {
        passed: false,
        message: `Sunucu durum yanıtı vermedi. (HTTP ${probe.statusCode || 'Soket Hatası'})`
      };
    }
  });

  // 3. Eşleşme PIN Doğrulaması
  await AuthValidator.runAuthTests(engine, options.pin);

  // 4. Veri ve Protokol Testleri
  await SyncProtocolSuite.runProtocolTests(engine, options.pin);

  // 5. Stres ve Sınır Durum Testleri
  if (!options.skipStress) {
    await StressRunner.runStressTests(engine, options.pin);
  }

  // 6. Raporlama
  const summary = engine.getSummary();
  TestReporter.printConsole(summary, options.host, options.port);
  const reports = TestReporter.saveReports(summary, __dirname, options.host, options.port);

  console.log(`📄 Raporlar kaydedildi:`);
  console.log(`   - Markdown : ${reports.mdPath}`);
  console.log(`   - JSON     : ${reports.jsonPath}\n`);

  if (summary.failed > 0) {
    process.exit(1);
  }
}

main().catch(err => {
  console.error('Kritik Hata:', err);
  process.exit(1);
});
