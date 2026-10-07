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
</script>

<div class="relative">
  <!-- Seçim Butonu / Kartı -->
  <div class="flex items-center space-x-2">
    <button
      type="button"
      class="flex-1 flex items-center justify-between px-4 py-2.5 bg-[#0b1022] border border-cyan-500/30 hover:border-cyan-400/60 rounded-xl transition-all cursor-pointer select-none text-left"
      onclick={() => !isScanning && (showDropdown = !showDropdown)}
    >
      <div class="flex items-center space-x-3 overflow-hidden">
        <div class="p-2 rounded-lg bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 flex-shrink-0">
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
          <div class="text-[10px] text-cyan-400 font-mono font-bold tracking-wider">TARAMA HEDEFİ (BİLGİSAYAR):</div>
          <div class="text-sm font-semibold text-slate-100 truncate flex items-center space-x-2">
            <span>{selectedSource ? selectedSource.name : 'Taranacak bilgisayar sürücüsü veya klasörü seçin...'}</span>
            {#if selectedSource?.sizeHuman}
              <span class="text-xs font-mono px-2 py-0.5 rounded bg-slate-800 text-cyan-300">
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
      <div class="text-slate-400 pl-2">
        <svg class="w-4 h-4 transition-transform {showDropdown ? 'rotate-180' : ''}" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
        </svg>
      </div>
    </button>

    <!-- Hızlı Klasör Seç Butonu -->
    <button
      type="button"
      class="px-3 py-2.5 bg-purple-500/10 hover:bg-purple-500/20 text-purple-300 border border-purple-500/30 hover:border-purple-400 rounded-xl text-xs font-semibold flex items-center space-x-1.5 transition-all disabled:opacity-50"
      disabled={isScanning}
      onclick={handleBrowseScanFolder}
      title="Bilgisayarınızdan taranacak herhangi bir klasör seçin (Masaüstü, İndirilenler vb.)"
    >
      <svg class="w-4 h-4 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
      </svg>
      <span class="hidden sm:inline">Klasör Seç</span>
    </button>
  </div>

  <!-- Açılır Menü (Dropdown) -->
  {#if showDropdown && !isScanning}
    <!-- Backdrop kapatıcı -->
    <button
      type="button"
      class="fixed inset-0 z-40 bg-transparent w-full h-full cursor-default"
      onclick={() => (showDropdown = false)}
      aria-label="Kapat"
    ></button>

    <div class="absolute left-0 right-0 top-full mt-2 z-50 bg-[#090e1c] border border-cyan-500/40 rounded-2xl shadow-2xl overflow-hidden max-h-[500px] flex flex-col backdrop-blur-xl">
      <!-- Üst Sekmeler -->
      <div class="p-3 bg-[#0d1428] border-b border-slate-800 flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center space-x-1 text-xs font-mono">
          <button
            type="button"
            class="px-2.5 py-1 rounded-lg transition-colors {activeTab === 'all' ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'}"
            onclick={() => (activeTab = 'all')}
          >
            Tümü
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
            class="text-xs text-purple-300 hover:text-purple-200 font-medium px-2 py-1 rounded bg-purple-500/10 hover:bg-purple-500/20 border border-purple-500/30 transition-colors"
            onclick={handleBrowseScanFolder}
          >
            📂 Özel Klasör Seç
          </button>
          <button
            type="button"
            class="text-xs text-slate-400 hover:text-slate-200 font-medium px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 transition-colors"
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
              class="w-full text-left p-3 rounded-xl hover:bg-slate-800/60 transition-all cursor-pointer flex items-center justify-between {selectedSource?.path === item.path ? 'bg-cyan-950/30 border border-cyan-500/30' : ''}"
              onclick={() => handleSelectDrive(item)}
            >
              <div class="flex items-center space-x-3">
                <div class="w-9 h-9 rounded-xl flex items-center justify-center font-bold text-xs {item.category === 'drives' ? 'bg-cyan-500/20 text-cyan-300' : item.category === 'recycle' ? 'bg-amber-500/20 text-amber-300' : 'bg-purple-500/20 text-purple-300'}">
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
                      <span class="text-[10px] px-1.5 py-0.2 rounded bg-slate-800 text-slate-300 font-mono border border-slate-700">
                        {item.badge}
                      </span>
                    {/if}
                  </div>
                  <div class="text-xs text-slate-400 font-mono">
                    {item.path} {#if item.sizeHuman}• {item.sizeHuman}{/if}
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
                    Seçilebilir
                  </span>
                {/if}
              </div>
            </button>
          {/each}
        {/if}
      </div>

      <!-- Alt Hızlı Test Alanı -->
      <div class="p-2.5 bg-[#0a0f1e] border-t border-slate-800 flex items-center justify-between text-xs font-mono text-slate-400">
        <span>Deneme yapmak için:</span>
        <button
          type="button"
          class="text-purple-300 hover:text-purple-200 font-medium px-2.5 py-1 rounded bg-purple-500/10 hover:bg-purple-500/20 border border-purple-500/30 transition-colors"
          onclick={handleCreateTestDisk}
        >
          🧪 10 MB Test Diski Oluştur
        </button>
      </div>
    </div>
  {/if}
</div>
