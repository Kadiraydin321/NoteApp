<!-- src/components/DiskSelector.svelte -->
<script>
  import { onMount } from 'svelte';

  let { selectedSource = null, onSelect = () => {}, isScanning = false } = $props();

  let drives = $state([]);
  let isLoadingDrives = $state(false);
  let showAllModal = $state(false);
  let modalTab = $state('all'); // all | drives | folders | recycle | devices

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

    // İlk açılışta C: sürücüsü veya ilk sürücüyü varsayılan seç
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
        isAccessible: true,
        description: 'Windows ana yerel sürücüsü ve sistem'
      },
      {
        id: 'drive_d',
        name: 'D: Sürücüsü (Veri Deposu)',
        path: 'D:\\',
        sizeHuman: '931 GB',
        type: 'computer_drive',
        category: 'drives',
        badge: 'Dahili Sürücü',
        isAccessible: true,
        description: 'İkincil veri ve depolama diski'
      },
      {
        id: 'user_desktop',
        name: 'Masaüstü (Desktop)',
        path: 'C:\\Users\\Birin\\Desktop',
        sizeHuman: 'Klasör',
        type: 'user_folder',
        category: 'folders',
        badge: 'Windows',
        isAccessible: true,
        description: 'Masaüstünden silinen dosyalar'
      },
      {
        id: 'recycle_c',
        name: 'Geri Dönüşüm Kutusu',
        path: 'C:\\$Recycle.Bin',
        sizeHuman: 'Çöp Kutusu',
        type: 'recycle_bin',
        category: 'recycle',
        badge: 'Silinenler',
        isAccessible: true,
        description: 'Windows Geri Dönüşüm Kutusu'
      }
    ];
  }

  async function handleBrowseScanFolder() {
    if (isScanning) return;
    try {
      if (window.api && window.api.selectScanFolder) {
        const folder = await window.api.selectScanFolder();
        if (folder) {
          onSelect({
            id: 'custom_folder_' + Date.now(),
            type: 'user_folder',
            category: 'folders',
            name: `Özel Klasör (${folder.name})`,
            path: folder.path,
            sizeHuman: 'Klasör',
            isAccessible: true,
            description: 'Kullanıcı tarafından seçilen hedef klasör'
          });
          showAllModal = false;
        }
      }
    } catch (err) {
      console.error('Klasör seçimi hatası:', err);
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
            isAccessible: true,
            description: 'Disk imaj dosyası'
          });
          showAllModal = false;
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
          isAccessible: true,
          description: 'Sıfır riskli sentetik test imajı'
        });
        showAllModal = false;
      }
    } catch (err) {
      console.error('Test imajı oluşturulamadı:', err);
    }
  }

  // Hızlı erişim sürücüleri
  const driveC = $derived(drives.find(d => d.id === 'drive_c') || drives[0]);
  const driveD = $derived(drives.find(d => d.id === 'drive_d'));
  const userDesktop = $derived(drives.find(d => d.id === 'user_desktop'));
  const recycleBin = $derived(drives.find(d => d.category === 'recycle'));

  const filteredModalDrives = $derived(
    drives.filter(d => {
      if (modalTab === 'all') return true;
      if (modalTab === 'drives') return d.category === 'drives';
      if (modalTab === 'folders') return d.category === 'folders';
      if (modalTab === 'recycle') return d.category === 'recycle';
      if (modalTab === 'devices') return d.category === 'devices';
      return true;
    })
  );
</script>

<div class="w-full flex flex-col gap-1.5">
  <!-- 1. YATAY HIZLI HEDEF ÇUBUĞU (ASLA ALTA SARKMAYAN TEMİZ ÇİPLER) -->
  <div class="flex flex-wrap items-center gap-1.5 text-xs font-mono">
    <span class="text-slate-400 font-bold mr-1 text-[11px] flex items-center space-x-1">
      <span class="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-pulse"></span>
      <span>HEDEF:</span>
    </span>

    <!-- C: Sürücüsü -->
    {#if driveC}
      <button
        type="button"
        class="px-2.5 py-1 rounded-lg border transition-all flex items-center space-x-1 font-semibold {selectedSource?.path === driveC.path ? 'bg-cyan-500/20 text-cyan-300 border-cyan-400 shadow-[0_0_10px_rgba(0,245,212,0.3)]' : 'bg-slate-900/90 text-slate-300 border-slate-800 hover:border-slate-700 hover:text-white'}"
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
        class="px-2.5 py-1 rounded-lg border transition-all flex items-center space-x-1 font-semibold {selectedSource?.path === driveD.path ? 'bg-cyan-500/20 text-cyan-300 border-cyan-400 shadow-[0_0_10px_rgba(0,245,212,0.3)]' : 'bg-slate-900/90 text-slate-300 border-slate-800 hover:border-slate-700 hover:text-white'}"
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
        class="px-2.5 py-1 rounded-lg border transition-all flex items-center space-x-1 font-semibold {selectedSource?.path === userDesktop.path ? 'bg-purple-500/20 text-purple-300 border-purple-400 shadow-[0_0_10px_rgba(168,85,247,0.3)]' : 'bg-slate-900/90 text-slate-300 border-slate-800 hover:border-slate-700 hover:text-white'}"
        onclick={() => onSelect(userDesktop)}
        disabled={isScanning}
      >
        <span>🖥️ Masaüstü</span>
      </button>
    {/if}

    <!-- Çöp Kutusu -->
    {#if recycleBin}
      <button
        type="button"
        class="px-2.5 py-1 rounded-lg border transition-all flex items-center space-x-1 font-semibold {selectedSource?.path === recycleBin.path ? 'bg-amber-500/20 text-amber-300 border-amber-400 shadow-[0_0_10px_rgba(245,158,11,0.3)]' : 'bg-slate-900/90 text-slate-300 border-slate-800 hover:border-slate-700 hover:text-white'}"
        onclick={() => onSelect(recycleBin)}
        disabled={isScanning}
      >
        <span>🗑️ Çöp Kutusu</span>
      </button>
    {/if}

    <!-- Klasör Seç -->
    <button
      type="button"
      class="px-2.5 py-1 rounded-lg bg-purple-500/10 hover:bg-purple-500/20 text-purple-300 border border-purple-500/30 text-xs font-semibold transition-all flex items-center space-x-1"
      onclick={handleBrowseScanFolder}
      disabled={isScanning}
      title="Taranacak herhangi bir klasör seçin"
    >
      <svg class="w-3.5 h-3.5 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
      </svg>
      <span>Klasör Seç</span>
    </button>

    <!-- İmaj (.img) -->
    <button
      type="button"
      class="px-2 py-1 rounded-lg bg-slate-900/90 hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-800 text-xs transition-all"
      onclick={handleBrowseFile}
      disabled={isScanning}
      title="Disk imaj dosyası aç (.img, .raw, .dd)"
    >
      <span>İmaj</span>
    </button>

    <!-- Test Diski -->
    <button
      type="button"
      class="px-2.5 py-1 rounded-lg bg-emerald-500/10 hover:bg-emerald-500/25 text-emerald-300 border border-emerald-500/30 text-xs transition-all flex items-center space-x-1 font-semibold"
      onclick={handleCreateTestDisk}
      disabled={isScanning}
      title="10 MB Sentetik Test İmajı Oluştur"
    >
      <span>🧪 Test Diski</span>
    </button>

    <!-- Tüm Sürücüler Modal Açıcı -->
    <button
      type="button"
      class="px-2.5 py-1 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-400 hover:text-slate-200 border border-slate-800 text-xs transition-all font-mono"
      onclick={() => (showAllModal = true)}
      disabled={isScanning}
      title="Tüm sürücü ve aygıtları listele"
    >
      <span>Daha Fazla ({drives.length}) ▾</span>
    </button>
  </div>

  <!-- 2. AKTİF SEÇİLEN HEDEF BİLGİ SATIRI (KOMPAKT) -->
  <div class="flex items-center space-x-2 text-[11px] font-mono text-slate-400 truncate">
    <span class="text-cyan-400 font-semibold">Taranacak Konum:</span>
    <span class="text-slate-200 font-bold">{selectedSource?.name || 'Hedef Seçilmedi'}</span>
    <span class="text-slate-500 truncate font-mono text-[10px]">({selectedSource?.path || ''})</span>
    {#if selectedSource?.sizeHuman}
      <span class="px-1.5 py-0.2 rounded bg-slate-800 text-cyan-300 text-[10px] border border-slate-700">{selectedSource.sizeHuman}</span>
    {/if}
  </div>
</div>

<!-- TÜM SÜRÜCÜLER VE AYGITLAR MODAL PENCERESİ (MERKEZİ VE FERAH) -->
{#if showAllModal}
  <div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
    <div class="relative w-full max-w-2xl bg-[#090e1c] border border-cyan-500/40 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[80vh]">
      <!-- Modal Başlık -->
      <div class="flex items-center justify-between px-5 py-3.5 border-b border-slate-800 bg-[#0d1426]">
        <div class="flex items-center space-x-2">
          <span class="text-cyan-400 font-bold font-mono text-xs uppercase">💻 Bilgisayar Sürücüleri ve Hedefler</span>
          <span class="text-[10px] px-2 py-0.2 rounded bg-cyan-500/20 text-cyan-300 font-mono">Toplam {drives.length}</span>
        </div>
        <button
          type="button"
          class="p-1 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors"
          onclick={() => (showAllModal = false)}
          aria-label="Kapat"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- Filtre Sekmeleri -->
      <div class="px-5 py-2.5 bg-[#0a0f1e] border-b border-slate-800/80 flex items-center space-x-1.5 text-xs font-mono">
        <button
          type="button"
          class="px-2.5 py-1 rounded-lg transition-colors {modalTab === 'all' ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'}"
          onclick={() => (modalTab = 'all')}
        >
          Tümü ({drives.length})
        </button>
        <button
          type="button"
          class="px-2.5 py-1 rounded-lg transition-colors {modalTab === 'drives' ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'}"
          onclick={() => (modalTab = 'drives')}
        >
          Sürücüler
        </button>
        <button
          type="button"
          class="px-2.5 py-1 rounded-lg transition-colors {modalTab === 'folders' ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'}"
          onclick={() => (modalTab = 'folders')}
        >
          Klasörler
        </button>
        <button
          type="button"
          class="px-2.5 py-1 rounded-lg transition-colors {modalTab === 'recycle' ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'}"
          onclick={() => (modalTab = 'recycle')}
        >
          Çöp Kutusu
        </button>
      </div>

      <!-- Sürücü Listesi -->
      <div class="flex-1 overflow-y-auto p-3 space-y-1.5">
        {#each filteredModalDrives as item}
          <button
            type="button"
            class="w-full text-left p-3 rounded-xl hover:bg-slate-800/70 transition-all flex items-center justify-between {selectedSource?.path === item.path ? 'bg-cyan-950/40 border border-cyan-500/40' : 'bg-slate-900/60 border border-slate-800/60'}"
            onclick={() => {
              onSelect(item);
              showAllModal = false;
            }}
          >
            <div class="flex items-center space-x-3">
              <div class="w-8 h-8 rounded-lg flex items-center justify-center font-bold text-sm {item.category === 'drives' ? 'bg-cyan-500/20 text-cyan-300' : item.category === 'recycle' ? 'bg-amber-500/20 text-amber-300' : 'bg-purple-500/20 text-purple-300'}">
                {#if item.category === 'drives'}💻{:else if item.category === 'recycle'}🗑️{:else}📁{/if}
              </div>
              <div>
                <div class="text-xs font-semibold text-slate-100 flex items-center space-x-2">
                  <span>{item.name}</span>
                  {#if item.badge}
                    <span class="text-[9px] px-1.5 py-0.2 rounded bg-slate-800 text-slate-400 font-mono border border-slate-700">{item.badge}</span>
                  {/if}
                </div>
                <div class="text-[11px] text-slate-400 font-mono">{item.path}</div>
              </div>
            </div>

            {#if item.sizeHuman}
              <span class="text-xs font-mono text-cyan-400 font-semibold">{item.sizeHuman}</span>
            {/if}
          </button>
        {/each}
      </div>

      <!-- Modal Alt Kapat -->
      <div class="p-3 border-t border-slate-800 bg-[#0d1426] flex justify-end">
        <button
          type="button"
          class="px-4 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-mono"
          onclick={() => (showAllModal = false)}
        >
          Kapat
        </button>
      </div>
    </div>
  </div>
{/if}
