<!-- src/components/PreviewModal.svelte -->
<script>
  let { item = null, sourcePath = '', onClose = () => {}, onRecover = () => {} } = $props();

  let previewData = $state(null);
  let isLoading = $state(true);

  $effect(() => {
    if (item && sourcePath) {
      loadPreview();
    }
  });

  async function loadPreview() {
    isLoading = true;
    try {
      if (window.api && window.api.getFilePreview) {
        previewData = await window.api.getFilePreview(sourcePath, item.offset, item.size, item.mime, item.filePath);
      }
    } catch (err) {
      console.error('Önizleme hatası:', err);
    } finally {
      isLoading = false;
    }
  }

  function formatBytes(bytes) {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }
</script>

<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
  <div class="relative w-full max-w-3xl bg-[#090e1c] border border-cyan-500/40 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[85vh]">
    <!-- Başlık -->
    <div class="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-[#0d1426]">
      <div class="flex items-center space-x-3">
        <div class="p-2 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
          </svg>
        </div>
        <div>
          <h2 class="text-base font-semibold text-slate-100 flex items-center space-x-2">
            <span>Dosya Önizleme</span>
            <span class="text-xs px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 font-mono">{item?.name}</span>
          </h2>
          <p class="text-xs text-slate-400 font-mono mt-0.5">
            Format: <span class="text-slate-200">{item?.displayName}</span> | Boyut: <span class="text-slate-200">{formatBytes(item?.size)}</span>
          </p>
        </div>
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

    <!-- Önizleme Gövdesi -->
    <div class="flex-1 overflow-auto p-6 flex items-center justify-center bg-[#060914] min-h-[300px]">
      {#if isLoading}
        <div class="flex flex-col items-center justify-center space-y-3">
          <div class="w-8 h-8 border-2 border-emerald-400 border-t-transparent rounded-full animate-spin"></div>
          <span class="text-slate-400 text-xs font-mono">Dosya ham sektörlerden çözümleniyor...</span>
        </div>
      {:else if previewData?.type === 'image' && previewData?.dataUri}
        <div class="relative max-w-full max-h-[50vh] flex items-center justify-center p-2 bg-[#0b1020] rounded-xl border border-slate-800">
          <img
            src={previewData.dataUri}
            alt={item?.name}
            class="max-w-full max-h-[48vh] object-contain rounded-lg shadow-lg"
          />
        </div>
      {:else if previewData?.type === 'text'}
        <div class="w-full h-full max-h-[50vh] overflow-auto p-4 bg-[#0a0f1e] rounded-xl border border-slate-800 font-mono text-xs text-emerald-300 whitespace-pre-wrap select-text">
          {previewData.content}
        </div>
      {:else if previewData?.type === 'pdf'}
        <div class="w-full h-full max-h-[50vh] overflow-auto p-4 bg-[#0a0f1e] rounded-xl border border-slate-800 font-mono text-xs text-slate-300 select-text">
          <div class="text-xs text-cyan-400 font-semibold mb-2">PDF Başlık ve Metin Akışı Önizlemesi:</div>
          <div class="whitespace-pre-wrap text-slate-400">{previewData.snippet}</div>
        </div>
      {:else}
        <div class="flex flex-col items-center justify-center text-center p-8 space-y-3">
          <div class="w-16 h-16 rounded-2xl bg-slate-800/80 flex items-center justify-center text-slate-400">
            <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
            </svg>
          </div>
          <div class="text-sm font-semibold text-slate-200">Bu dosya türü için doğrudan görsel önizleme desteklenmiyor.</div>
          <div class="text-xs text-slate-400 max-w-sm">
            Ham baytları incelemek için <strong>Hex İnceleyici</strong>'yi kullanabilir veya dosyayı doğrudan bilgisayarınıza kurtarabilirsiniz.
          </div>
        </div>
      {/if}
    </div>

    <!-- Alt Çubuk -->
    <div class="flex items-center justify-between px-6 py-4 border-t border-slate-800 bg-[#0d1426]">
      <div class="flex items-center space-x-3 text-xs font-mono">
        <span class="text-slate-400">Doğruluk:</span>
        <span class="px-2 py-0.5 rounded font-semibold {item?.confidence >= 90 ? 'bg-emerald-500/20 text-emerald-300' : 'bg-amber-500/20 text-amber-300'}">
          %{item?.confidence} Güven
        </span>
      </div>

      <div class="flex items-center space-x-3">
        <button
          class="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition-colors"
          onclick={onClose}
        >
          Kapat
        </button>
        <button
          class="px-5 py-2 rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-500 hover:from-emerald-400 hover:to-cyan-400 text-slate-950 font-bold text-xs shadow-lg shadow-emerald-500/20 transition-all"
          onclick={() => { onRecover(item); onClose(); }}
        >
          Bu Dosyayı Kurtar
        </button>
      </div>
    </div>
  </div>
</div>
