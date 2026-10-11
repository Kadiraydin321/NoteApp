<!-- src/components/DiskSelector.svelte -->
<script>
  import { onMount } from 'svelte';

  let { selectedSource = null, onSelect = () => {}, isScanning = false } = $props();

  let drives = $state([]);
  let isLoadingDrives = $state(false);

  onMount(async () => {
    await loadDrives();
  });

  async function loadDrives() {
    isLoadingDrives = true;
    try {
      if (typeof window !== 'undefined' && window.api && window.api.listDrives) {
        const result = await window.api.listDrives();
        if (Array.isArray(result) && result.length > 0) {
          drives = result;
        } else {
          drives = getFallbackDrives();
        }
      } else {
        drives = getFallbackDrives();
      }
    } catch (err) {
      console.error('Diskler listelenemedi, yerel hedefler kullanılıyor:', err);
      drives = getFallbackDrives();
    } finally {
      isLoadingDrives = false;
    }

    if (!selectedSource && drives.length > 0) {
      const defaultDrive = drives.find(d => d.id === 'drive_c') || drives[0];
      onSelect(defaultDrive);
    }
  }

  function getFallbackDrives() {
    return [
      {
        id: 'drive_c',
        name: 'C: Sürücüsü (Sistem)',
        path: 'C:\\',
        sizeHuman: '1.82 TB',
        type: 'computer_drive',
        category: 'drives',
        badge: 'Dahili Sürücü',
        isAccessible: true
      },
      {
        id: 'drive_d',
        name: 'D: Sürücüsü (Veri Deposu)',
        path: 'D:\\',
        sizeHuman: '931 GB',
        type: 'computer_drive',
        category: 'drives',
        badge: 'Dahili Sürücü',
        isAccessible: true
      },
      {
        id: 'user_desktop',
        name: 'Masaüstü (Desktop)',
        path: 'C:\\Users\\Birin\\Desktop',
        sizeHuman: 'Klasör',
        type: 'user_folder',
        category: 'folders',
        badge: 'Windows',
        isAccessible: true
      },
      {
        id: 'user_downloads',
        name: 'İndirilenler (Downloads)',
        path: 'C:\\Users\\Birin\\Downloads',
        sizeHuman: 'Klasör',
        type: 'user_folder',
        category: 'folders',
        badge: 'Windows',
        isAccessible: true
      },
      {
        id: 'recycle_c',
        name: 'Geri Dönüşüm Kutusu',
        path: 'C:\\$Recycle.Bin',
        sizeHuman: 'Çöp Kutusu',
        type: 'recycle_bin',
        category: 'recycle',
        badge: 'Silinenler',
        isAccessible: true
      }
    ];
  }

  async function handleBrowseScanFolder() {
    if (isScanning) return;
    try {
      if (typeof window !== 'undefined' && window.api && window.api.selectScanFolder) {
        const folder = await window.api.selectScanFolder();
        if (folder && folder.path) {
          onSelect({
            id: 'custom_folder_' + Date.now(),
            type: 'user_folder',
            category: 'folders',
            name: `Özel Klasör (${folder.name})`,
            path: folder.path,
            sizeHuman: 'Klasör',
            isAccessible: true
          });
        }
      } else {
        alert('Klasör seçimi için sistem arayüzü hazır değil (window.api bekleniyor).');
      }
    } catch (err) {
      console.error('Klasör seçimi hatası:', err);
      alert('Klasör seçilirken bir hata oluştu: ' + err.message);
    }
  }

  async function handleBrowseFile() {
    if (isScanning) return;
    try {
      if (window.api && window.api.selectFile) {
        const file = await window.api.selectFile();
        if (file) {
          onSelect({
            id: 'image_file_' + Date.now(),
            type: 'image',
            category: 'images',
            name: file.name,
            path: file.path,
            sizeHuman: file.sizeHuman,
            sizeBytes: file.size,
            isAccessible: true
          });
        }
      }
    } catch (err) {
      console.error('Dosya seçimi hatası:', err);
    }
  }

  async function handleCreateTestDisk() {
    if (isScanning) return;
    try {
      if (window.api && window.api.createTestImage) {
        const result = await window.api.createTestImage();
        onSelect({
          id: 'test_image',
          type: 'test_image',
          category: 'images',
          name: 'byterescue_test_disk.img (Test İmajı)',
          path: result.path,
          sizeHuman: '10.0 MB',
          sizeBytes: result.sizeBytes,
          isAccessible: true
        });
      }
    } catch (err) {
      console.error('Test imajı oluşturulamadı:', err);
    }
  }

  const driveC = $derived(drives.find(d => d.id === 'drive_c') || drives[0]);
  const driveD = $derived(drives.find(d => d.id === 'drive_d'));
  const userDesktop = $derived(drives.find(d => d.id === 'user_desktop'));
  const userDownloads = $derived(drives.find(d => d.id === 'user_downloads'));
  const recycleBin = $derived(drives.find(d => d.category === 'recycle'));
</script>

<div class="flex flex-col gap-1.5 w-full">
  <!-- 1. YATAY VE NET HEDEF BUTONLARI -->
  <div class="flex flex-wrap items-center gap-1.5 text-xs font-mono">
    <span class="text-slate-400 font-bold text-[11px] mr-1 flex items-center space-x-1 flex-shrink-0">
      <span class="w-2 h-2 rounded-full bg-cyan-400 animate-pulse"></span>
      <span>HEDEF:</span>
    </span>

    <!-- C: Sürücüsü -->
    {#if driveC}
      <button
        type="button"
        class="px-3 py-1.5 rounded-xl border text-xs font-mono font-bold transition-all flex items-center space-x-1.5 flex-shrink-0 cursor-pointer {selectedSource?.path === driveC.path ? 'bg-cyan-500/25 text-cyan-300 border-cyan-400 shadow-[0_0_12px_rgba(0,245,212,0.35)] scale-[1.02]' : 'bg-slate-900/90 text-slate-300 border-slate-800 hover:border-slate-700 hover:text-white'}"
        onclick={() => onSelect(driveC)}
        disabled={isScanning}
      >
        <span>💻 C:</span>
        {#if driveC.sizeHuman}<span class="text-[10px] text-slate-400 font-normal">({driveC.sizeHuman})</span>{/if}
      </button>
    {/if}

    <!-- D: Sürücüsü -->
    {#if driveD}
      <button
        type="button"
        class="px-3 py-1.5 rounded-xl border text-xs font-mono font-bold transition-all flex items-center space-x-1.5 flex-shrink-0 cursor-pointer {selectedSource?.path === driveD.path ? 'bg-cyan-500/25 text-cyan-300 border-cyan-400 shadow-[0_0_12px_rgba(0,245,212,0.35)] scale-[1.02]' : 'bg-slate-900/90 text-slate-300 border-slate-800 hover:border-slate-700 hover:text-white'}"
        onclick={() => onSelect(driveD)}
        disabled={isScanning}
      >
        <span>💾 D:</span>
        {#if driveD.sizeHuman}<span class="text-[10px] text-slate-400 font-normal">({driveD.sizeHuman})</span>{/if}
      </button>
    {/if}

    <!-- Masaüstü -->
    {#if userDesktop}
      <button
        type="button"
        class="px-3 py-1.5 rounded-xl border text-xs font-mono font-bold transition-all flex items-center space-x-1.5 flex-shrink-0 cursor-pointer {selectedSource?.path === userDesktop.path ? 'bg-purple-500/25 text-purple-300 border-purple-400 shadow-[0_0_12px_rgba(168,85,247,0.35)] scale-[1.02]' : 'bg-slate-900/90 text-slate-300 border-slate-800 hover:border-slate-700 hover:text-white'}"
        onclick={() => onSelect(userDesktop)}
        disabled={isScanning}
      >
        <span>🖥️ Masaüstü</span>
      </button>
    {/if}

    <!-- İndirilenler -->
    {#if userDownloads}
      <button
        type="button"
        class="px-3 py-1.5 rounded-xl border text-xs font-mono font-bold transition-all flex items-center space-x-1.5 flex-shrink-0 cursor-pointer {selectedSource?.path === userDownloads.path ? 'bg-purple-500/25 text-purple-300 border-purple-400 shadow-[0_0_12px_rgba(168,85,247,0.35)] scale-[1.02]' : 'bg-slate-900/90 text-slate-300 border-slate-800 hover:border-slate-700 hover:text-white'}"
        onclick={() => onSelect(userDownloads)}
        disabled={isScanning}
      >
        <span>📥 İndirilenler</span>
      </button>
    {/if}

    <!-- Çöp Kutusu -->
    {#if recycleBin}
      <button
        type="button"
        class="px-3 py-1.5 rounded-xl border text-xs font-mono font-bold transition-all flex items-center space-x-1.5 flex-shrink-0 cursor-pointer {selectedSource?.path === recycleBin.path ? 'bg-amber-500/25 text-amber-300 border-amber-400 shadow-[0_0_12px_rgba(245,158,11,0.35)] scale-[1.02]' : 'bg-slate-900/90 text-slate-300 border-slate-800 hover:border-slate-700 hover:text-white'}"
        onclick={() => onSelect(recycleBin)}
        disabled={isScanning}
      >
        <span>🗑️ Çöp Kutusu</span>
      </button>
    {/if}

    <!-- Özel Klasör Butonu -->
    <button
      type="button"
      class="px-3 py-1.5 rounded-xl bg-purple-500/15 hover:bg-purple-500/30 text-purple-300 border border-purple-500/40 text-xs font-mono font-semibold transition-all flex items-center space-x-1 flex-shrink-0 cursor-pointer"
      onclick={handleBrowseScanFolder}
      disabled={isScanning}
      title="Taranacak herhangi bir klasör seçin"
    >
      <svg class="w-3.5 h-3.5 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
      </svg>
      <span>Klasör Seç</span>
    </button>

    {#if selectedSource && selectedSource.id && selectedSource.id.startsWith('custom_folder')}
      <button
        type="button"
        class="px-3 py-1.5 rounded-xl border text-xs font-mono font-bold transition-all flex items-center space-x-1.5 flex-shrink-0 cursor-pointer bg-purple-500/25 text-purple-300 border-purple-400 shadow-[0_0_12px_rgba(168,85,247,0.35)] scale-[1.02]"
      >
        <span>📂 {selectedSource.name}</span>
      </button>
    {/if}

    <!-- İmaj (.img) -->
    <button
      type="button"
      class="px-2.5 py-1.5 rounded-xl bg-slate-900/90 hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-800 text-xs font-mono transition-all flex-shrink-0 cursor-pointer"
      onclick={handleBrowseFile}
      disabled={isScanning}
      title="Disk imaj dosyası aç (.img, .raw, .dd)"
    >
      <span>İmaj (.img)</span>
    </button>

    <!-- Test Diski -->
    <button
      type="button"
      class="px-2.5 py-1.5 rounded-xl bg-emerald-500/15 hover:bg-emerald-500/30 text-emerald-300 border border-emerald-500/40 text-xs font-mono transition-all flex items-center space-x-1 font-semibold flex-shrink-0 cursor-pointer"
      onclick={handleCreateTestDisk}
      disabled={isScanning}
      title="10 MB Sentetik Test İmajı Oluştur"
    >
      <span>🧪 Test Diski</span>
    </button>
  </div>

  <!-- 2. AKTİF SEÇİLEN HEDEF AYRINTISI -->
  <div class="flex items-center space-x-2 text-[11px] font-mono text-slate-400 truncate pl-0.5">
    <span class="text-cyan-400 font-semibold">Taranacak Konum:</span>
    <span class="text-slate-100 font-bold">{selectedSource?.name || 'C: Sürücüsü (Sistem)'}</span>
    <span class="text-slate-500 font-mono text-[10px] truncate">({selectedSource?.path || 'C:\\'})</span>
    {#if selectedSource?.sizeHuman}
      <span class="px-1.5 py-0.2 rounded bg-slate-800 text-cyan-300 text-[10px] border border-slate-700">{selectedSource.sizeHuman}</span>
    {/if}
  </div>
</div>
