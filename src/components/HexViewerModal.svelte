<!-- src/components/HexViewerModal.svelte -->
<script>
  let { item = null, sourcePath = '', onClose = () => {} } = $props();

  let dumpData = $state(null);
  let isLoading = $state(true);
  let selectedLength = $state(256);
  let hoveredByte = $state(null);
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
</script>

<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
  <div class="relative w-full max-w-4xl bg-[#090e1c] border border-cyan-500/40 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[85vh]">
    <!-- Modal Başlık -->
    <div class="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-[#0d1426]">
      <div class="flex items-center space-x-3">
        <div class="p-2 rounded-lg bg-cyan-500/10 text-cyan-400 border border-cyan-500/30">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
          </svg>
        </div>
        <div>
          <h2 class="text-base font-semibold text-slate-100 flex items-center space-x-2">
            <span>Ham Hex İnceleyici</span>
            <span class="text-xs px-2 py-0.5 rounded bg-cyan-500/20 text-cyan-300 font-mono">{item?.name}</span>
          </h2>
          <p class="text-xs text-slate-400 font-mono mt-0.5">
            Disk Ofseti: <span class="text-cyan-400 font-semibold">{item?.hexOffset}</span> ({item?.offset.toLocaleString()} bayt)
          </p>
        </div>
      </div>

      <!-- Sağ Kontroller -->
      <div class="flex items-center space-x-3">
        <div class="flex items-center space-x-1 bg-slate-900/90 rounded-lg p-1 border border-slate-800 text-xs">
          {#each [128, 256, 512, 1024] as len}
            <button
              class="px-2.5 py-1 rounded transition-colors {selectedLength === len ? 'bg-cyan-500 text-slate-950 font-semibold' : 'text-slate-400 hover:text-slate-200'}"
              onclick={() => { selectedLength = len; loadHex(len); }}
            >
              {len}B
            </button>
          {/each}
        </div>

        <button
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
        <div class="border border-slate-800/80 rounded-xl overflow-hidden bg-[#0a0f1e]/80">
          <!-- Hex Başlıkları -->
          <div class="grid grid-cols-[100px_1fr_180px] px-4 py-2 bg-slate-900/80 border-b border-slate-800 text-slate-400 font-semibold tracking-wider">
            <div>OFSET</div>
            <div class="grid grid-cols-16 text-center text-[11px] text-cyan-400/80">
              00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F
            </div>
            <div class="text-right">ASCII DÖKÜM</div>
          </div>

          <!-- Satırlar -->
          <div class="divide-y divide-slate-900/60">
            {#each dumpData.rows as row}
              <div class="grid grid-cols-[100px_1fr_180px] px-4 py-1.5 hover:bg-cyan-950/20 transition-colors font-mono">
                <!-- Ofset -->
                <div class="text-slate-400 font-medium">{row.offset}</div>

                <!-- Hex Değerler -->
                <div class="text-slate-200 tracking-wider">
                  <span class="text-cyan-300 font-medium">{row.hexFirst}</span>
                  <span class="text-slate-600 mx-2">|</span>
                  <span class="text-purple-300 font-medium">{row.hexSecond}</span>
                </div>

                <!-- ASCII Karakterler -->
                <div class="text-right text-emerald-400 font-mono tracking-widest bg-slate-950/40 px-2 rounded">
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
    <div class="flex items-center justify-between px-6 py-3 border-t border-slate-800 bg-[#0d1426] text-xs font-mono text-slate-400">
      <div class="flex items-center space-x-4">
        <span>Toplam Okunan: <strong class="text-slate-200">{dumpData?.totalBytes || 0} bayt</strong></span>
        <span>Tür İmzası: <strong class="text-cyan-400">{item?.type.toUpperCase()}</strong></span>
      </div>

      <div class="flex items-center space-x-2">
        {#if copiedText}
          <span class="text-emerald-400 text-xs font-sans font-medium animate-pulse">{copiedText} kopyalandı!</span>
        {/if}
        <button
          class="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 transition-colors"
          onclick={() => copyToClipboard(item?.hexOffset, 'Ofset adresi')}
        >
          Ofseti Kopyala
        </button>
        <button
          class="px-3 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-slate-950 font-semibold transition-colors"
          onclick={onClose}
        >
          Tamam
        </button>
      </div>
    </div>
  </div>
</div>
