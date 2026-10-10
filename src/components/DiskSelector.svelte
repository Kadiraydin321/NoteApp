<!-- src/components/DiskSelector.svelte -->
<script>
  let { selectedSource = null, onSelect = () => {}, isScanning = false } = $props();

  let drives = $state([]);
  let isLoadingDrives = $state(false);
  let showDropdown = $state(false);
  let activeTab = $state('all'); // all | drives | folders | recycle | devices

  $effect(() => {
    loadDrives();
  });

  async function loadDrives() {
    isLoadingDrives = true;
    try {
      if (window.api && window.api.listDrives) {
        drives = await window.api.listDrives();
        // Eğer henüz bir hedef seçilmemişse, ilk sürücüyü (örn: C: Sürücüsü) otomatik seç
        if (!selectedSource && drives.length > 0) {
          const defaultDrive = drives.find(d => d.id === 'drive_c') || drives[0];
          onSelect(defaultDrive);
        }
      }
    } catch (err) {
      console.error('Diskler listelenemedi:', err);
    } finally {
      isLoadingDrives = false;
    }
  }

  async function handleBrowseScanFolder() {
    if (isScanning) return;
    try {
      if (window.api && window.api.selectScanFolder) {
        const folder = await window.api.selectScanFolder();
        if (folder) {
          onSelect({
            id: 'custom_folder',
            type: 'user_folder',
            category: 'folders',
            name: `Özel Klasör (${folder.name})`,
            path: folder.path,
            sizeHuman: 'Klasör',
            isAccessible: true,
            description: 'Kullanıcı tarafından seçilen hedef klasör'
          });
          showDropdown = false;
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
            id: 'image_file',
            type: 'image',
            category: 'images',
            name: file.name,
            path: file.path,
            sizeHuman: file.sizeHuman,
            sizeBytes: file.size,
            isAccessible: true,
            description: 'Disk imaj dosyası'
          });
          showDropdown = false;
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
          name: 'byterescue_test_disk.img (Sentetik Test İmajı)',
          path: result.path,
          sizeHuman: '10.0 MB',
          sizeBytes: result.sizeBytes,
          isAccessible: true,
          description: 'Sıfır riskli sentetik test imajı'
        });
        showDropdown = false;
      }
    } catch (err) {
      console.error('Test imajı oluşturulamadı:', err);
    }
  }

  function handleSelectDrive(item) {
    if (isScanning) return;
    onSelect(item);
    showDropdown = false;
  }

  const filteredDrives = $derived(
    drives.filter(d => {
      if (activeTab === 'all') return true;
      if (activeTab === 'drives') return d.category === 'drives';
      if (activeTab === 'folders') return d.category === 'folders';
      if (activeTab === 'recycle') return d.category === 'recycle';
      if (activeTab === 'devices') return d.category === 'devices';
      return true;
    })
  );

  // Hızlı erişim sürücüleri
  const quickDrives = $derived(
    drives.filter(d => ['drive_c', 'drive_d', 'recycle_c', 'user_desktop'].includes(d.id))
  );
</script>

<div class="relative w-full">
  <!-- Seçim Butonu / Kartı -->
  <div class="flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
    <button
      type="button"
      class="flex-1 flex items-center justify-between px-4 py-2.5 bg-[#0b1022]/95 border border-cyan-500/35 hover:border-cyan-400/70 rounded-xl transition-all cursor-pointer select-none text-left shadow-[0_0_15px_rgba(6,182,212,0.1)] group"
      onclick={() => !isScanning && (showDropdown = !showDropdown)}
    >
      <div class="flex items-center space-x-3 overflow-hidden">
        <div class="p-2.5 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/30 flex-shrink-0 group-hover:scale-105 transition-transform shadow-[0_0_10px_rgba(0,245,212,0.2)]">
          {#if selectedSource?.category === 'recycle'}
            <svg class="w-5 h-5 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
          {:else if selectedSource?.category === 'folders'}
            <svg class="w-5 h-5 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
            </svg>
          {:else if selectedSource?.category === 'drives'}
            <svg class="w-5 h-5 text-cyan-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
          {:else}
            <svg class="w-5 h-5 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4" />
            </svg>
          {/if}
        </div>

        <div class="overflow-hidden">
          <div class="text-[10px] text-cyan-400 font-mono font-bold tracking-wider flex items-center space-x-1.5">
            <span>TARAMA HEDEFİ // SOURCE:</span>
            {#if selectedSource?.badge}
              <span class="px-1.5 py-0.2 rounded bg-cyan-500/20 text-cyan-300 text-[9px] border border-cyan-500/30">{selectedSource.badge}</span>
            {/if}
          </div>
          <div class="text-sm font-semibold text-slate-100 truncate flex items-center space-x-2">
            <span>{selectedSource ? selectedSource.name : 'Taranacak bilgisayar sürücüsü veya klasörü seçin...'}</span>
            {#if selectedSource?.sizeHuman}
              <span class="text-xs font-mono px-2 py-0.5 rounded bg-slate-800/90 text-cyan-300 border border-slate-700">
                {selectedSource.sizeHuman}
              </span>
            {/if}
          </div>
          <div class="text-[11px] text-slate-400 font-mono truncate">
            {selectedSource?.path || ''}
          </div>
        </div>
      </div>

      <!-- Açılır ok -->
      <div class="text-slate-400 pl-2 group-hover:text-cyan-400 transition-colors">
        <svg class="w-4 h-4 transition-transform {showDropdown ? 'rotate-180' : ''}" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
        </svg>
      </div>
    </button>

    <!-- Hızlı Klasör Seç Butonu -->
    <div class="flex items-center space-x-2">
      <button
        type="button"
        class="px-3 py-2.5 bg-purple-500/10 hover:bg-purple-500/25 text-purple-300 border border-purple-500/30 hover:border-purple-400 rounded-xl text-xs font-semibold flex items-center space-x-1.5 transition-all shadow-[0_0_10px_rgba(168,85,247,0.15)] disabled:opacity-50"
        disabled={isScanning}
        onclick={handleBrowseScanFolder}
        title="Bilgisayarınızdan taranacak herhangi bir klasör seçin (Masaüstü, İndirilenler vb.)"
      >
        <svg class="w-4 h-4 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
        </svg>
        <span>Klasör Seç</span>
      </button>

      <button
        type="button"
        class="px-3 py-2.5 bg-emerald-500/10 hover:bg-emerald-500/25 text-emerald-300 border border-emerald-500/30 hover:border-emerald-400 rounded-xl text-xs font-semibold flex items-center space-x-1.5 transition-all disabled:opacity-50"
        disabled={isScanning}
        onclick={handleCreateTestDisk}
        title="10 MB Sentetik Test İmajı Oluştur"
      >
        <svg class="w-4 h-4 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z" />
        </svg>
        <span class="hidden md:inline">Test Diski</span>
      </button>
    </div>
  </div>

  <!-- Hızlı Seçim Çipleri (Quick Select Bar) -->
  {#if !isScanning && quickDrives.length > 0}
    <div class="flex items-center space-x-1.5 mt-2 overflow-x-auto pb-0.5 text-[11px] font-mono">
      <span class="text-slate-500 text-[10px] uppercase font-bold flex-shrink-0">HIZLI HEDEF:</span>
      {#each quickDrives as qd}
        <button
          type="button"
          class="px-2 py-0.5 rounded-lg border transition-all flex items-center space-x-1 flex-shrink-0 {selectedSource?.id === qd.id ? 'bg-cyan-500/20 text-cyan-300 border-cyan-400 shadow-[0_0_8px_rgba(0,245,212,0.2)]' : 'bg-slate-900/80 text-slate-400 border-slate-800 hover:text-slate-200 hover:border-slate-700'}"
          onclick={() => handleSelectDrive(qd)}
        >
          <span>{qd.id === 'drive_c' ? '💻 C:' : qd.id === 'drive_d' ? '💾 D:' : qd.id === 'recycle_c' ? '🗑️ Çöp Kutusu' : '🖥️ Masaüstü'}</span>
          {#if qd.sizeHuman && qd.category === 'drives'}
            <span class="text-[9px] text-slate-500">({qd.sizeHuman})</span>
          {/if}
        </button>
      {/each}
    </div>
  {/if}

  <!-- Açılır Menü (Dropdown) -->
  {#if showDropdown && !isScanning}
    <!-- Backdrop kapatıcı -->
    <button
      type="button"
      class="fixed inset-0 z-40 bg-black/40 backdrop-blur-[2px] w-full h-full cursor-default"
      onclick={() => (showDropdown = false)}
      aria-label="Kapat"
    ></button>

    <div class="absolute left-0 right-0 top-full mt-2 z-50 bg-[#090e1c]/98 border border-cyan-500/50 rounded-2xl shadow-[0_10px_40px_rgba(0,0,0,0.8)] overflow-hidden max-h-[520px] flex flex-col backdrop-blur-2xl">
      <!-- Üst Sekmeler -->
      <div class="p-3 bg-[#0d1428] border-b border-slate-800 flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center space-x-1 text-xs font-mono">
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg transition-colors {activeTab === 'all' ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'}"
            onclick={() => (activeTab = 'all')}
          >
            Tümü ({drives.length})
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg transition-colors {activeTab === 'drives' ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'}"
            onclick={() => (activeTab = 'drives')}
          >
            💻 Sürücüler (C:, D:)
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg transition-colors {activeTab === 'folders' ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'}"
            onclick={() => (activeTab = 'folders')}
          >
            📁 Klasörler
          </button>
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg transition-colors {activeTab === 'recycle' ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'}"
            onclick={() => (activeTab = 'recycle')}
          >
            🗑️ Çöp Kutusu
          </button>
        </div>

        <div class="flex items-center space-x-2">
          <button
            type="button"
            class="text-xs text-purple-300 hover:text-purple-200 font-medium px-2.5 py-1 rounded-lg bg-purple-500/10 hover:bg-purple-500/25 border border-purple-500/30 transition-colors"
            onclick={handleBrowseScanFolder}
          >
            📂 Özel Klasör Seç
          </button>
          <button
            type="button"
            class="text-xs text-slate-400 hover:text-slate-200 font-medium px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 transition-colors"
            onclick={handleBrowseFile}
          >
            İmaj (.img)
          </button>
        </div>
      </div>

      <!-- Sürücü & Klasör Listesi -->
      <div class="overflow-y-auto divide-y divide-slate-800/60 p-2">
        {#if filteredDrives.length === 0}
          <div class="p-6 text-center text-xs text-slate-400 font-mono">
            {isLoadingDrives ? 'Bilgisayar sürücüleri taranıyor...' : 'Bu filtrede sürücü bulunamadı.'}
          </div>
        {:else}
          {#each filteredDrives as item}
            <button
              type="button"
              class="w-full text-left p-3 rounded-xl hover:bg-slate-800/70 transition-all cursor-pointer flex items-center justify-between {selectedSource?.path === item.path ? 'bg-cyan-950/40 border border-cyan-500/40 shadow-[0_0_15px_rgba(0,245,212,0.15)]' : ''}"
              onclick={() => handleSelectDrive(item)}
            >
              <div class="flex items-center space-x-3.5">
                <div class="w-10 h-10 rounded-xl flex items-center justify-center font-bold text-sm shadow-inner {item.category === 'drives' ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30' : item.category === 'recycle' ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30' : 'bg-purple-500/20 text-purple-300 border border-purple-500/30'}">
                  {#if item.category === 'drives'}
                    💻
                  {:else if item.category === 'recycle'}
                    🗑️
                  {:else if item.category === 'folders'}
                    📁
                  {:else}
                    ⚙️
                  {/if}
                </div>
                <div>
                  <div class="text-sm font-semibold text-slate-100 flex items-center space-x-2">
                    <span>{item.name}</span>
                    {#if item.badge}
                      <span class="text-[10px] px-2 py-0.2 rounded-full bg-slate-800 text-slate-300 font-mono border border-slate-700">
                        {item.badge}
                      </span>
                    {/if}
                  </div>
                  <div class="text-xs text-slate-400 font-mono flex items-center space-x-2 mt-0.5">
                    <span>{item.path}</span>
                    {#if item.sizeHuman}
                      <span class="text-cyan-400 font-semibold">• {item.sizeHuman}</span>
                    {/if}
                  </div>
                  {#if item.description}
                    <div class="text-[11px] text-slate-500 font-sans mt-0.5">
                      {item.description}
                    </div>
                  {/if}
                </div>
              </div>

              <div>
                {#if item.needsSudo}
                  <span class="text-[10px] font-mono px-2 py-0.5 rounded bg-amber-500/10 text-amber-300 border border-amber-500/30">
                    Sudo Gerekli
                  </span>
                {:else}
                  <span class="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-300 border border-emerald-500/30">
                    Hazır
                  </span>
                {/if}
              </div>
            </button>
          {/each}
        {/if}
      </div>

      <!-- Alt Hızlı Test Alanı -->
      <div class="p-3 bg-[#0a0f1e] border-t border-slate-800 flex items-center justify-between text-xs font-mono text-slate-400">
        <span>Sıfır riskle denemek için:</span>
        <button
          type="button"
          class="text-purple-300 hover:text-purple-200 font-medium px-3 py-1 rounded-lg bg-purple-500/15 hover:bg-purple-500/30 border border-purple-500/40 transition-colors shadow-sm"
          onclick={handleCreateTestDisk}
        >
          🧪 10 MB Sentetik Test İmajı Oluştur
        </button>
      </div>
    </div>
  {/if}
</div>
