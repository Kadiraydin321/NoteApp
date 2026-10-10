<!-- src/components/HexViewerModal.svelte -->
<script>
  let { item = null, sourcePath = '', onClose = () => {} } = $props();

  let dumpData = $state(null);
  let isLoading = $state(true);
  let selectedLength = $state(256);
  let searchFilter = $state('');
  let copiedText = $state('');

  $effect(() => {
    if (item && sourcePath) {
      loadHex(selectedLength);
    }
  });

  async function loadHex(length) {
    isLoading = true;
    try {
      if (window.api && window.api.getHexDump) {
        dumpData = await window.api.getHexDump(sourcePath, item.offset, length, item.filePath);
      }
    } catch (err) {
      console.error('Hex dökümü hatası:', err);
    } finally {
      isLoading = false;
    }
  }

  function copyToClipboard(text, label) {
    navigator.clipboard.writeText(text);
    copiedText = label;
    setTimeout(() => { copiedText = ''; }, 2000);
  }

  const filteredRows = $derived(
    dumpData?.rows ? dumpData.rows.filter(r => {
      if (!searchFilter.trim()) return true;
      const q = searchFilter.toLowerCase();
      return (
        r.offset.toLowerCase().includes(q) ||
        r.hexFirst.toLowerCase().includes(q) ||
        r.hexSecond.toLowerCase().includes(q) ||
        r.ascii.toLowerCase().includes(q)
      );
    }) : []
  );
</script>

<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md">
  <div class="relative w-full max-w-4xl bg-[#090e1c] border border-cyan-500/50 rounded-2xl shadow-[0_0_50px_rgba(0,245,212,0.2)] overflow-hidden flex flex-col max-h-[88vh]">
    <!-- Modal Başlık -->
    <div class="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-[#0d1426]">
      <div class="flex items-center space-x-3">
        <div class="p-2.5 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/30 shadow-[0_0_15px_rgba(0,245,212,0.2)]">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
          </svg>
        </div>
        <div>
          <h2 class="text-base font-semibold text-slate-100 flex items-center space-x-2">
            <span>Ham Hex İnceleyici</span>
            <span class="text-xs px-2 py-0.5 rounded bg-cyan-500/20 text-cyan-300 font-mono border border-cyan-500/30">{item?.name}</span>
          </h2>
          <p class="text-xs text-slate-400 font-mono mt-0.5">
            Disk Ofseti: <span class="text-cyan-400 font-semibold">{item?.hexOffset}</span> ({item?.offset.toLocaleString()} bayt)
          </p>
        </div>
      </div>

      <!-- Sağ Kontroller -->
      <div class="flex items-center space-x-3">
        <!-- Arama Filtresi -->
        <div class="relative hidden sm:block">
          <input
            type="text"
            bind:value={searchFilter}
            placeholder="Hex veya ASCII ara..."
            class="px-2.5 py-1 pl-7 bg-slate-900 border border-slate-700 rounded-lg text-xs font-mono text-slate-200 placeholder-slate-500 focus:outline-none focus:border-cyan-500 w-36"
          />
          <svg class="w-3.5 h-3.5 text-slate-400 absolute left-2 top-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </div>

        <div class="flex items-center space-x-1 bg-slate-900/90 rounded-lg p-1 border border-slate-800 text-xs font-mono">
          {#each [128, 256, 512, 1024] as len}
            <button
              type="button"
              class="px-2.5 py-1 rounded transition-colors {selectedLength === len ? 'bg-cyan-500 text-slate-950 font-bold shadow-sm' : 'text-slate-400 hover:text-slate-200'}"
              onclick={() => { selectedLength = len; loadHex(len); }}
            >
              {len}B
            </button>
          {/each}
        </div>

        <button
          type="button"
          class="p-2 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors"
          onclick={onClose}
          aria-label="Kapat"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    </div>

    <!-- Hex Tablo Alanı -->
    <div class="flex-1 overflow-auto p-6 font-mono text-xs select-text bg-[#070b16]">
      {#if isLoading}
        <div class="flex flex-col items-center justify-center h-64 space-y-3">
          <div class="w-8 h-8 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin"></div>
          <span class="text-slate-400 text-xs font-mono">Sektör baytları okunuyor...</span>
        </div>
      {:else if dumpData && dumpData.rows}
        <div class="border border-slate-800/80 rounded-xl overflow-hidden bg-[#0a0f1e]/90 shadow-inner">
          <!-- Hex Başlıkları -->
          <div class="grid grid-cols-[100px_1fr_180px] px-4 py-2 bg-slate-900/90 border-b border-slate-800 text-slate-400 font-semibold tracking-wider text-[11px]">
            <div>OFSET</div>
            <div class="grid grid-cols-16 text-center text-[11px] text-cyan-400/90 font-bold">
              00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F
            </div>
            <div class="text-right">ASCII DÖKÜM</div>
          </div>

          <!-- Satırlar -->
          <div class="divide-y divide-slate-900/60">
            {#each filteredRows as row}
              <div class="grid grid-cols-[100px_1fr_180px] px-4 py-1.5 hover:bg-cyan-950/30 transition-colors font-mono">
                <!-- Ofset -->
                <div class="text-slate-400 font-medium">{row.offset}</div>

                <!-- Hex Değerler -->
                <div class="text-slate-200 tracking-wider">
                  <span class="text-cyan-300 font-semibold">{row.hexFirst}</span>
                  <span class="text-slate-600 mx-2 font-bold">|</span>
                  <span class="text-purple-300 font-semibold">{row.hexSecond}</span>
                </div>

                <!-- ASCII Karakterler -->
                <div class="text-right text-emerald-400 font-mono tracking-widest bg-slate-950/50 px-2 rounded border border-slate-800/40">
                  {row.ascii}
                </div>
              </div>
            {/each}
          </div>
        </div>
      {:else}
        <div class="text-center py-12 text-slate-500">Hex verisi okunamadı.</div>
      {/if}
    </div>

    <!-- Alt Çubuk -->
    <div class="flex items-center justify-between px-6 py-3.5 border-t border-slate-800 bg-[#0d1426] text-xs font-mono text-slate-400">
      <div class="flex items-center space-x-4">
        <span>Toplam Okunan: <strong class="text-slate-200">{dumpData?.totalBytes || 0} bayt</strong></span>
        <span>Tür İmzası: <strong class="text-cyan-400 font-bold">{item?.type.toUpperCase()}</strong></span>
      </div>

      <div class="flex items-center space-x-2.5">
        {#if copiedText}
          <span class="text-emerald-400 text-xs font-sans font-medium animate-pulse">{copiedText} kopyalandı!</span>
        {/if}
        <button
          type="button"
          class="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 transition-colors"
          onclick={() => copyToClipboard(item?.hexOffset, 'Ofset adresi')}
        >
          Ofseti Kopyala
        </button>
        <button
          type="button"
          class="px-3.5 py-1.5 rounded-lg bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold transition-colors shadow-sm"
          onclick={onClose}
        >
          Tamam
        </button>
      </div>
    </div>
  </div>
</div>
