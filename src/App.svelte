<!-- src/App.svelte -->
<script>
  import { onMount } from 'svelte';
  import MetricCard from './components/MetricCard.svelte';
  import HexViewerModal from './components/HexViewerModal.svelte';
  import PreviewModal from './components/PreviewModal.svelte';
  import CustomSignatureModal from './components/CustomSignatureModal.svelte';
  import DiskSelector from './components/DiskSelector.svelte';

  // Durum Değişkenleri
  let selectedSource = $state(null);
  let destinationDir = $state('/tmp/byterescue_recovered');
  let safetyStatus = $state(null);

  // Tarama Seçenekleri
  let selectedCategories = $state(['images', 'documents', 'media', 'archives']);
  let customSignatures = $state([]);
  let sectorAlignment = $state(512); // 512B standart sektör hizalaması

  // Tarama Durumu (idle | running | paused | completed | error | stopped)
  let scanStatus = $state('idle');
  let stats = $state({
    bytesScanned: 0,
    totalBytes: 0,
    filesFound: 0,
    speedMBps: 0,
    etaSeconds: 0,
    percentage: 0
  });

  // Bulunan Dosyalar Listesi
  let foundFiles = $state([]);
  let selectedFileIds = $state(new Set());
  let searchQuery = $state('');
  let activeTab = $state('all'); // all | images | documents | media | archives | custom

  // Konsol Logları (Siber Ticker)
  let consoleLogs = $state([
    { time: new Date().toLocaleTimeString(), text: 'ByteRescue motoru hazır. Taranacak disk veya imaj seçin.', type: 'info' }
  ]);

  // Modal Durumları
  let activeHexItem = $state(null);
  let activePreviewItem = $state(null);
  let showCustomModal = $state(false);
  let recoveryAlert = $state(null);

  onMount(() => {
    // Electron IPC Olay Dinleyicileri
    if (window.api) {
      window.api.onScanProgress((data) => {
        stats = data;
        // Düzenli aralıklarla siber konsola akış ekle
        if (Math.random() < 0.2) {
          addLog(`Sektör bloğu taranıyor: ${formatBytes(data.bytesScanned)} / ${formatBytes(data.totalBytes)} (Hız: ${data.speedMBps} MB/s)`, 'stream');
        }
      });

      window.api.onFileFound((item) => {
        foundFiles = [item, ...foundFiles];
        // Yeni bulunan dosyayı otomatik seç
        selectedFileIds.add(item.id);
        selectedFileIds = new Set(selectedFileIds);
        addLog(`🎯 İMZA TESPİT EDİLDİ: ${item.name} | Tür: ${item.type.toUpperCase()} | Ofset: ${item.hexOffset}`, 'success');
      });

      window.api.onScanCompleted((data) => {
        scanStatus = 'completed';
        addLog(`✅ Tarama tamamlandı! Toplam ${data.files.length} adet dosya başarıyla ayrıştırıldı.`, 'success');
      });

      window.api.onScanError((err) => {
        scanStatus = 'error';
        addLog(`❌ Tarama hatası: ${err}`, 'error');
      });

      window.api.onScanStatusChange(({ status }) => {
        scanStatus = status;
      });
    }

    // İlk güvenlik denetimini yap
    checkSafety();
  });

  function addLog(text, type = 'info') {
    const time = new Date().toLocaleTimeString();
    consoleLogs = [{ time, text, type }, ...consoleLogs.slice(0, 49)];
  }

  async function checkSafety() {
    if (!window.api || !selectedSource?.path) return;
    try {
      safetyStatus = await window.api.checkSafety(selectedSource.path, destinationDir);
    } catch (_) {}
  }

  async function handleSelectDestFolder() {
    if (window.api && window.api.selectFolder) {
      const folder = await window.api.selectFolder();
      if (folder) {
        destinationDir = folder;
        checkSafety();
      }
    }
  }

  function toggleCategory(cat) {
    if (selectedCategories.includes(cat)) {
      selectedCategories = selectedCategories.filter(c => c !== cat);
    } else {
      selectedCategories = [...selectedCategories, cat];
    }
  }

  async function handleStartScan() {
    if (!selectedSource?.path) {
      alert('Lütfen önce taranacak bir disk veya imaj dosyası seçin.');
      return;
    }

    if (selectedCategories.length === 0 && customSignatures.length === 0) {
      alert('Lütfen taranacak en az bir dosya kategorisi veya özel imza seçin.');
      return;
    }

    foundFiles = [];
    selectedFileIds.clear();
    selectedFileIds = new Set();
    scanStatus = 'running';
    addLog(`Ham sektör taraması başlatılıyor: ${selectedSource.path}`, 'info');

    try {
      await window.api.startScan({
        sourcePath: selectedSource.path,
        selectedCategories,
        customSignatures,
        sectorAlign: Number(sectorAlignment) || 512
      });
    } catch (err) {
      scanStatus = 'error';
      addLog(`Başlatma hatası: ${err.message}`, 'error');
    }
  }

  async function handlePauseResume() {
    if (scanStatus === 'running') {
      await window.api.pauseScan();
      scanStatus = 'paused';
      addLog('Tarama duraklatıldı.', 'warning');
    } else if (scanStatus === 'paused') {
      await window.api.resumeScan();
      scanStatus = 'running';
      addLog('Tarama kaldığı sektörden devam ediyor.', 'info');
    }
  }

  async function handleStopScan() {
    await window.api.stopScan();
    scanStatus = 'stopped';
    addLog('Tarama kullanıcı tarafından durduruldu.', 'warning');
  }

  async function handleRecoverSelected() {
    if (selectedFileIds.size === 0) {
      alert('Lütfen kurtarmak için en az bir dosya seçin.');
      return;
    }

    const filesToRecover = foundFiles.filter(f => selectedFileIds.has(f.id));
    await executeRecovery(filesToRecover);
  }

  async function handleRecoverAll() {
    if (foundFiles.length === 0) {
      alert('Kurtarılacak dosya bulunamadı.');
      return;
    }
    await executeRecovery(foundFiles);
  }

  async function executeRecovery(files) {
    if (!selectedSource?.path) return;

    // Güvenlik uyarısı kontrolü
    if (safetyStatus?.level === 'danger') {
      const confirmDanger = confirm(
        'KRİTİK UYARI: Kurtarma hedefi taranan disk bölümü ile aynı!\n' +
        'Devam etmek silinmiş diğer dosyaların üzerine yazılmasına neden olabilir.\n\n' +
        'Yine de devam etmek istiyor musunuz?'
      );
      if (!confirmDanger) return;
    }

    addLog(`${files.length} adet dosya kurtarılıyor -> ${destinationDir}...`, 'info');

    try {
      const result = await window.api.recoverFiles(selectedSource.path, files, destinationDir);
      recoveryAlert = {
        title: 'Kurtarma İşlemi Tamamlandı',
        message: `${result.recoveredCount} dosya başarıyla "${destinationDir}" dizinine kaydedildi. (Hata: ${result.failedCount})`,
        reportPath: result.reportPath
      };
      addLog(`✅ Kurtarma başarılı: ${result.recoveredCount} dosya diske yazıldı.`, 'success');
    } catch (err) {
      addLog(`❌ Kurtarma hatası: ${err.message}`, 'error');
    }
  }

  function handleAddCustomSignature(sig) {
    customSignatures = [...customSignatures, sig];
    addLog(`Yeni özel imza eklendi: [${sig.name}] Uzantı: .${sig.extension} Hex: ${sig.hex}`, 'success');
  }

  // Filtrelenmiş Dosya Listesi
  const filteredFiles = $derived(
    foundFiles.filter(file => {
      // Tab filtreleme
      if (activeTab !== 'all') {
        if (activeTab === 'custom' && file.category !== 'custom') return false;
        if (activeTab !== 'custom' && file.category !== activeTab) return false;
      }
      // Arama filtresi
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        return (
          file.name.toLowerCase().includes(q) ||
          file.type.toLowerCase().includes(q) ||
          file.hexOffset.toLowerCase().includes(q) ||
          file.displayName.toLowerCase().includes(q)
        );
      }
      return true;
    })
  );

  function toggleFileSelection(id) {
    if (selectedFileIds.has(id)) {
      selectedFileIds.delete(id);
    } else {
      selectedFileIds.add(id);
    }
    selectedFileIds = new Set(selectedFileIds);
  }

  function toggleSelectAll() {
    if (selectedFileIds.size === filteredFiles.length) {
      selectedFileIds.clear();
    } else {
      for (const f of filteredFiles) {
        selectedFileIds.add(f.id);
      }
    }
    selectedFileIds = new Set(selectedFileIds);
  }

  function formatBytes(bytes) {
    if (!bytes || bytes <= 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  function formatETA(seconds) {
    if (!seconds || seconds <= 0 || seconds > 86400) return '0 sn';
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return m > 0 ? `${m} dk ${s} sn` : `${s} sn`;
  }
</script>

<div class="flex flex-col w-screen h-screen bg-[#070a13] text-slate-100 cyber-grid-bg overflow-hidden">
  <!-- 1. ÜST BAR (Siber Header) -->
  <header class="h-14 px-6 border-b border-slate-800 bg-[#090d1a]/90 backdrop-blur-md flex items-center justify-between flex-shrink-0 z-30">
    <!-- Sol Logo & Başlık -->
    <div class="flex items-center space-x-3">
      <div class="w-8 h-8 rounded-lg bg-gradient-to-tr from-cyan-500 to-purple-600 flex items-center justify-center shadow-lg shadow-cyan-500/20">
        <svg class="w-5 h-5 text-slate-950 font-bold" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M4 7v10c0 2 1.5 3 3.5 3h9c2 0 3.5-1 3.5-3V7M4 7c0-2 1.5-3 3.5-3h9c2 0 3.5 1 3.5 3M4 7h16" />
        </svg>
      </div>
      <div>
        <div class="flex items-center space-x-2">
          <h1 class="text-sm font-bold tracking-wider font-mono text-cyan-400">BYTERESCUE</h1>
          <span class="text-[10px] px-1.5 py-0.2 rounded bg-cyan-500/10 text-cyan-300 font-mono border border-cyan-500/30">v1.0-RAW</span>
        </div>
        <p class="text-[10px] text-slate-400 font-mono tracking-tight">Ham Sektör & Dosya İmzası Veri Kurtarma Laboratuvarı</p>
      </div>
    </div>

    <!-- Orta Durum Göstergesi -->
    <div class="flex items-center space-x-2 font-mono text-xs">
      <div class="flex items-center space-x-2 px-3 py-1 rounded-full bg-slate-900 border border-slate-800">
        <span class="relative flex h-2 w-2">
          {#if scanStatus === 'running'}
            <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75"></span>
            <span class="relative inline-flex rounded-full h-2 w-2 bg-cyan-500"></span>
          {:else if scanStatus === 'paused'}
            <span class="relative inline-flex rounded-full h-2 w-2 bg-amber-500"></span>
          {:else if scanStatus === 'completed'}
            <span class="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
          {:else}
            <span class="relative inline-flex rounded-full h-2 w-2 bg-slate-500"></span>
          {/if}
        </span>
        <span class="text-slate-300 uppercase tracking-wider font-semibold">
          {#if scanStatus === 'running'}TARANIYOR
          {:else if scanStatus === 'paused'}DURAKLATILDI
          {:else if scanStatus === 'completed'}TAMAMLANDI
          {:else}HAZIR{/if}
        </span>
      </div>
    </div>

    <!-- Sağ Kurtarma Hedefi & Güvenlik Rozeti -->
    <div class="flex items-center space-x-3">
      <!-- Güvenli Koruma Rozeti -->
      {#if safetyStatus}
        <div class="flex items-center space-x-1.5 px-2.5 py-1 rounded-lg text-xs font-mono border {safetyStatus.level === 'danger' ? 'bg-red-500/10 text-red-400 border-red-500/30' : safetyStatus.level === 'caution' ? 'bg-amber-500/10 text-amber-400 border-amber-500/30' : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'}">
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
          </svg>
          <span class="hidden sm:inline">Güvenli Koruma:</span>
          <strong>{safetyStatus.level === 'danger' ? 'Kritik Risk' : safetyStatus.level === 'caution' ? 'Aynı Bölüm' : 'Güvenli'}</strong>
        </div>
      {/if}

      <!-- Hedef Klasör Butonu -->
      <button
        class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-700 text-xs font-mono text-slate-300 transition-colors"
        onclick={handleSelectDestFolder}
        title="Kurtarılan dosyaların yazılacağı klasör"
      >
        <svg class="w-3.5 h-3.5 text-cyan-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
        </svg>
        <span class="max-w-[140px] truncate">{destinationDir}</span>
      </button>
    </div>
  </header>

  <!-- 2. ANA İÇERİK IZGARASI -->
  <main class="flex-1 flex flex-col overflow-hidden p-6 gap-5">
    <!-- A. KONTROL & HEDEF SEÇİM ŞERİDİ -->
    <div class="grid grid-cols-1 lg:grid-cols-[1fr_auto] gap-4 items-center bg-[#0d1322]/80 border border-slate-800/80 rounded-2xl p-4 backdrop-blur-md">
      <!-- Sürücü / İmaj Seçici -->
      <DiskSelector
        selectedSource={selectedSource}
        isScanning={scanStatus === 'running'}
        onSelect={(src) => {
          selectedSource = src;
          addLog(`Tarama hedefi seçildi: ${src.path} (${src.sizeHuman})`, 'info');
          checkSafety();
        }}
      />

      <!-- Aksiyon Butonları -->
      <div class="flex items-center space-x-2.5">
        {#if scanStatus === 'running'}
          <button
            class="px-5 py-2.5 rounded-xl bg-amber-500/20 hover:bg-amber-500/30 text-amber-300 border border-amber-500/40 text-xs font-bold font-mono transition-all flex items-center space-x-2"
            onclick={handlePauseResume}
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 9v6m4-6v6m7-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>DURAKLAT</span>
          </button>

          <button
            class="px-5 py-2.5 rounded-xl bg-red-500/20 hover:bg-red-500/30 text-red-300 border border-red-500/40 text-xs font-bold font-mono transition-all flex items-center space-x-2"
            onclick={handleStopScan}
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 10a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 01-1-1v-4z" />
            </svg>
            <span>DURDUR</span>
          </button>
        {:else if scanStatus === 'paused'}
          <button
            class="px-5 py-2.5 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold font-mono text-xs shadow-lg shadow-cyan-500/20 transition-all flex items-center space-x-2"
            onclick={handlePauseResume}
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>DEVAM ET</span>
          </button>
        {:else}
          <button
            class="px-6 py-2.5 rounded-xl bg-gradient-to-r from-cyan-500 to-emerald-400 hover:from-cyan-400 hover:to-emerald-300 text-slate-950 font-bold font-mono text-xs shadow-lg shadow-cyan-500/25 transition-all flex items-center space-x-2 disabled:opacity-50"
            disabled={!selectedSource}
            onclick={handleStartScan}
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
            <span>DERİN TARAMAYI BAŞLAT</span>
          </button>
        {/if}
      </div>
    </div>

    <!-- B. FORMAT SEÇİM ŞERİDİ & SEKTÖR AYARI -->
    <div class="flex flex-wrap items-center justify-between gap-3 px-2">
      <!-- Kategori Hapları -->
      <div class="flex flex-wrap items-center gap-2">
        <span class="text-xs font-mono text-slate-400 mr-1">İMZA FİLTRELERİ:</span>

        <button
          class="px-3 py-1 rounded-lg text-xs font-mono font-medium transition-all {selectedCategories.includes('images') ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/40' : 'bg-slate-900 text-slate-400 border border-slate-800'}"
          onclick={() => toggleCategory('images')}
        >
          📷 Görseller (PNG, JPG, WEBP, GIF, BMP)
        </button>

        <button
          class="px-3 py-1 rounded-lg text-xs font-mono font-medium transition-all {selectedCategories.includes('documents') ? 'bg-purple-500/20 text-purple-300 border border-purple-500/40' : 'bg-slate-900 text-slate-400 border border-slate-800'}"
          onclick={() => toggleCategory('documents')}
        >
          📄 Belgeler (PDF, DOCX, XLSX, TXT)
        </button>

        <button
          class="px-3 py-1 rounded-lg text-xs font-mono font-medium transition-all {selectedCategories.includes('media') ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40' : 'bg-slate-900 text-slate-400 border border-slate-800'}"
          onclick={() => toggleCategory('media')}
        >
          🎬 Medya (MP4, MKV, MP3, WAV)
        </button>

        <button
          class="px-3 py-1 rounded-lg text-xs font-mono font-medium transition-all {selectedCategories.includes('archives') ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40' : 'bg-slate-900 text-slate-400 border border-slate-800'}"
          onclick={() => toggleCategory('archives')}
        >
          📦 Arşivler (ZIP, 7Z, RAR, TAR)
        </button>

        <button
          class="px-3 py-1 rounded-lg text-xs font-mono font-medium transition-all border border-dashed border-purple-400/50 text-purple-300 hover:bg-purple-500/10 flex items-center space-x-1"
          onclick={() => (showCustomModal = true)}
        >
          <span>+ Özel İmza</span>
          {#if customSignatures.length > 0}
            <span class="ml-1 px-1.5 py-0.2 rounded-full bg-purple-500/30 text-[10px] font-bold">{customSignatures.length}</span>
          {/if}
        </button>
      </div>

      <!-- Sektör Hizalama Seçimi -->
      <div class="flex items-center space-x-2 text-xs font-mono text-slate-400">
        <span>Sektör Adımı:</span>
        <select
          bind:value={sectorAlignment}
          class="bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1 text-slate-200 text-xs focus:outline-none focus:border-cyan-500 font-mono"
        >
          <option value={512}>512 Bayt (Standart Hızlı)</option>
          <option value={4096}>4096 Bayt (4K Gelişmiş)</option>
          <option value={1}>1 Bayt (Bayt Bayt Ultra Derin)</option>
        </select>
      </div>
    </div>

    <!-- C. GERÇEK ZAMANLI METRİK KARTLARI -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <MetricCard
        title="Taranan Veri"
        value={`${formatBytes(stats.bytesScanned)}`}
        subtitle={`${formatBytes(stats.totalBytes)} içinden (%${stats.percentage})`}
        accent="cyan"
        badge={`%${stats.percentage}`}
      />

      <MetricCard
        title="Tarama Hızı"
        value={`${stats.speedMBps} MB/s`}
        subtitle="Ham Disk Çıkartma Hızı"
        accent="emerald"
        badge={stats.speedMBps > 200 ? 'Ultra Hızlı' : 'Aktif'}
      />

      <MetricCard
        title="Bulunan Dosyalar"
        value={`${stats.filesFound} Adet`}
        subtitle="Ayrıştırılmış Sektör İmzaları"
        accent="purple"
        badge={stats.filesFound > 0 ? 'İmza Eşleşti' : 'Bekleniyor'}
      />

      <MetricCard
        title="Tahmini Kalan Süre"
        value={formatETA(stats.etaSeconds)}
        subtitle="İş Parçacığı: Multi-Worker"
        accent="amber"
        badge="ETA"
      />
    </div>

    <!-- D. İLERLEME ÇUBUĞU (Cyber Neon Gradient) -->
    <div class="w-full bg-[#0d1426] border border-slate-800/80 rounded-xl p-1 relative overflow-hidden">
      <div
        class="h-3 rounded-lg bg-gradient-to-r from-cyan-500 via-emerald-400 to-purple-500 transition-all duration-300 relative overflow-hidden shadow-[0_0_15px_rgba(0,245,212,0.3)]"
        style="width: {Math.max(stats.percentage, 0.5)}%"
      >
        <div class="absolute inset-0 bg-white/20 animate-pulse"></div>
      </div>
    </div>

    <!-- E. ALT BÖLÜM: BULUNAN DOSYALAR VE SİBER LOG PANELİ -->
    <div class="flex-1 grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-5 min-h-0 overflow-hidden">
      <!-- SOL: BULUNAN DOSYALAR TABLOSU -->
      <div class="bg-[#0b1022]/90 border border-slate-800 rounded-2xl flex flex-col overflow-hidden backdrop-blur-md">
        <!-- Tablo Filtre & Eylem Başlığı -->
        <div class="p-4 border-b border-slate-800/80 flex flex-wrap items-center justify-between gap-3 bg-[#0d1428]/80">
          <!-- Kategori Sekmeleri -->
          <div class="flex items-center space-x-1 text-xs font-mono">
            {#each [
              { id: 'all', label: 'Tümü' },
              { id: 'images', label: 'Görseller' },
              { id: 'documents', label: 'Belgeler' },
              { id: 'media', label: 'Medya' },
              { id: 'archives', label: 'Arşivler' }
            ] as tab}
              <button
                class="px-3 py-1.5 rounded-lg transition-colors {activeTab === tab.id ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'}"
                onclick={() => (activeTab = tab.id)}
              >
                {tab.label}
              </button>
            {/each}
          </div>

          <!-- Arama Kutusu ve Toplu Kurtarma -->
          <div class="flex items-center space-x-3">
            <div class="relative">
              <input
                type="text"
                bind:value={searchQuery}
                placeholder="Ada veya ofsete göre ara..."
                class="w-48 sm:w-60 px-3 py-1.5 pl-8 bg-slate-900 border border-slate-700/80 rounded-xl text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500 font-mono"
              />
              <svg class="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>

            <button
              class="px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-500 hover:from-emerald-400 hover:to-cyan-400 text-slate-950 font-bold text-xs shadow-md shadow-emerald-500/20 transition-all flex items-center space-x-1.5 disabled:opacity-50"
              disabled={selectedFileIds.size === 0}
              onclick={handleRecoverSelected}
            >
              <svg class="w-3.5 h-3.5 font-bold" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
              <span>Seçilenleri Kurtar ({selectedFileIds.size})</span>
            </button>
          </div>
        </div>

        <!-- Dosyalar Tablo Gövdesi -->
        <div class="flex-1 overflow-auto">
          {#if filteredFiles.length === 0}
            <div class="h-full flex flex-col items-center justify-center p-8 text-center text-slate-500">
              <div class="w-12 h-12 rounded-2xl bg-slate-900 border border-slate-800 flex items-center justify-center text-slate-600 mb-3">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 13h6m-3-3v6m-9 1V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
                </svg>
              </div>
              <p class="text-sm font-medium text-slate-400">Henüz kurtarılabilir dosya listelenmedi</p>
              <p class="text-xs text-slate-500 mt-1 max-w-sm">
                Yukarıdan bir disk imajı seçin veya "Test İmajı Oluştur" butonuna basarak derin taramayı başlatın.
              </p>
            </div>
          {:else}
            <table class="w-full text-left border-collapse text-xs font-mono">
              <thead class="sticky top-0 bg-[#090e1c] border-b border-slate-800 text-slate-400 text-[11px] select-none">
                <tr>
                  <th class="py-2.5 px-3 w-10 text-center">
                    <input
                      type="checkbox"
                      checked={selectedFileIds.size === filteredFiles.length && filteredFiles.length > 0}
                      onchange={toggleSelectAll}
                      class="rounded bg-slate-900 border-slate-700 text-cyan-500 focus:ring-0 cursor-pointer"
                    />
                  </th>
                  <th class="py-2.5 px-3">DOSYA ADI / FORMAT</th>
                  <th class="py-2.5 px-3">DİSK OFSETİ</th>
                  <th class="py-2.5 px-3">BOYUT</th>
                  <th class="py-2.5 px-3">GÜVEN</th>
                  <th class="py-2.5 px-3 text-right">EYLEMLER</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-800/40">
                {#each filteredFiles as file (file.id)}
                  <tr class="hover:bg-slate-800/30 transition-colors {selectedFileIds.has(file.id) ? 'bg-cyan-950/15' : ''}">
                    <!-- Seçim Checkbox -->
                    <td class="py-2.5 px-3 text-center">
                      <input
                        type="checkbox"
                        checked={selectedFileIds.has(file.id)}
                        onchange={() => toggleFileSelection(file.id)}
                        class="rounded bg-slate-900 border-slate-700 text-cyan-500 focus:ring-0 cursor-pointer"
                      />
                    </td>

                    <!-- Dosya Adı ve İkon -->
                    <td class="py-2.5 px-3">
                      <div class="flex items-center space-x-2.5">
                        <span class="w-6 h-6 rounded bg-slate-800 flex items-center justify-center text-[10px] font-bold text-cyan-400 flex-shrink-0">
                          {file.type.slice(0, 3).toUpperCase()}
                        </span>
                        <div class="overflow-hidden">
                          <div class="font-semibold text-slate-100 flex items-center space-x-1.5 truncate">
                            <span>{file.name}</span>
                            {#if file.isRecycleBin}
                              <span class="text-[9px] px-1 py-0.2 rounded bg-amber-500/20 text-amber-300 font-mono">Çöp Kutusu</span>
                            {/if}
                          </div>
                          <div class="text-[10px] text-slate-400 font-sans truncate">
                            {#if file.originalPath}
                              <span class="text-slate-400">Silindiği Konum: </span><span class="text-cyan-300/90 font-mono">{file.originalPath}</span>
                            {:else}
                              {file.displayName}
                            {/if}
                          </div>
                        </div>
                      </div>
                    </td>

                    <!-- Hex Ofset -->
                    <td class="py-2.5 px-3 text-cyan-300 font-medium">
                      {file.hexOffset}
                    </td>

                    <!-- Boyut -->
                    <td class="py-2.5 px-3 text-slate-300">
                      {formatBytes(file.size)}
                    </td>

                    <!-- Güvenilirlik -->
                    <td class="py-2.5 px-3">
                      <span class="px-2 py-0.5 rounded text-[10px] font-semibold {file.confidence >= 90 ? 'bg-emerald-500/10 text-emerald-300 border border-emerald-500/20' : 'bg-amber-500/10 text-amber-300 border border-amber-500/20'}">
                        %{file.confidence}
                      </span>
                    </td>

                    <!-- Aksiyonlar -->
                    <td class="py-2.5 px-3 text-right">
                      <div class="flex items-center justify-end space-x-1.5">
                        <button
                          class="px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-[11px] transition-colors"
                          onclick={() => (activeHexItem = file)}
                          title="Ham 16-Bayt Hex Dökümünü İncele"
                        >
                          Hex
                        </button>

                        <button
                          class="px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-cyan-400 text-[11px] transition-colors"
                          onclick={() => (activePreviewItem = file)}
                          title="Önizle"
                        >
                          Önizle
                        </button>

                        <button
                          class="px-2.5 py-1 rounded bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 text-[11px] font-semibold transition-colors"
                          onclick={() => executeRecovery([file])}
                          title="Hemen Kurtar"
                        >
                          Kurtar
                        </button>
                      </div>
                    </td>
                  </tr>
                {/each}
              </tbody>
            </table>
          {/if}
        </div>
      </div>

      <!-- SAĞ: SİBER KONSOL LOGLARI VE AKTİVİTE AKIŞI -->
      <div class="bg-[#0b1022]/90 border border-slate-800 rounded-2xl flex flex-col overflow-hidden backdrop-blur-md">
        <div class="p-3 border-b border-slate-800 bg-[#0d1428]/80 flex items-center justify-between">
          <div class="flex items-center space-x-2">
            <div class="w-2 h-2 rounded-full bg-cyan-400 animate-pulse"></div>
            <span class="text-xs font-mono font-bold text-slate-300 tracking-wider">CANLI SİBER AKIŞ</span>
          </div>
          <button
            class="text-[10px] text-slate-400 hover:text-slate-200 font-mono"
            onclick={() => (consoleLogs = [])}
          >
            Temizle
          </button>
        </div>

        <div class="flex-1 p-3 overflow-y-auto space-y-2 font-mono text-[11px]">
          {#each consoleLogs as log}
            <div class="flex items-start space-x-2 leading-tight">
              <span class="text-slate-500 flex-shrink-0">[{log.time}]</span>
              <span class="{log.type === 'error' ? 'text-red-400 font-semibold' : log.type === 'success' ? 'text-emerald-400 font-medium' : log.type === 'warning' ? 'text-amber-400' : log.type === 'stream' ? 'text-cyan-400/80 text-[10px]' : 'text-slate-300'} break-all">
                {log.text}
              </span>
            </div>
          {/each}
        </div>
      </div>
    </div>
  </main>

  <!-- 3. MODALLER -->
  {#if activeHexItem && selectedSource}
    <HexViewerModal
      item={activeHexItem}
      sourcePath={selectedSource.path}
      onClose={() => (activeHexItem = null)}
    />
  {/if}

  {#if activePreviewItem && selectedSource}
    <PreviewModal
      item={activePreviewItem}
      sourcePath={selectedSource.path}
      onClose={() => (activePreviewItem = null)}
      onRecover={(file) => executeRecovery([file])}
    />
  {/if}

  {#if showCustomModal}
    <CustomSignatureModal
      onAdd={handleAddCustomSignature}
      onClose={() => (showCustomModal = false)}
    />
  {/if}

  <!-- Kurtarma Başarı Bildirimi (Toast/Alert) -->
  {#if recoveryAlert}
    <div class="fixed bottom-6 right-6 z-50 max-w-md bg-[#0a1124] border border-emerald-500/50 rounded-2xl p-4 shadow-2xl flex items-start space-x-3 text-xs font-mono">
      <div class="p-2 rounded-xl bg-emerald-500/20 text-emerald-400 flex-shrink-0">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
        </svg>
      </div>
      <div class="flex-1">
        <h3 class="font-bold text-slate-100 text-sm">{recoveryAlert.title}</h3>
        <p class="text-slate-300 mt-1">{recoveryAlert.message}</p>
        <p class="text-[10px] text-cyan-400 mt-2 truncate">Rapor: {recoveryAlert.reportPath}</p>
      </div>
      <button
        type="button"
        class="text-slate-400 hover:text-white p-1"
        onclick={() => (recoveryAlert = null)}
        aria-label="Kapat"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  {/if}
</div>
