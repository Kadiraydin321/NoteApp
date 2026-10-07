<!-- src/components/CustomSignatureModal.svelte -->
<script>
  let { onAdd = () => {}, onClose = () => {} } = $props();

  let name = $state('');
  let hexBytes = $state('');
  let extension = $state('');
  let maxSizeMB = $state(50);
  let errorMessage = $state('');

  function handleSubmit() {
    errorMessage = '';
    const cleanHex = hexBytes.replace(/[\s\-,:]/g, '');

    if (!cleanHex) {
      errorMessage = 'Lütfen Magic Byte hex dizesi girin (Örn: FF D8 FF).';
      return;
    }

    if (cleanHex.length % 2 !== 0 || !/^[0-9A-Fa-f]+$/.test(cleanHex)) {
      errorMessage = 'Geçersiz Hex formatı! Yalnızca 0-9 ve A-F karakterleri içermeli ve çift sayıda olmalıdır.';
      return;
    }

    if (!extension.trim()) {
      errorMessage = 'Lütfen dosya uzantısını girin (Örn: dat veya bin).';
      return;
    }

    onAdd({
      name: name.trim() || `Özel ${extension.toUpperCase()}`,
      hex: cleanHex.toUpperCase(),
      extension: extension.trim().replace(/^\./, '').toLowerCase(),
      maxSizeMB: Number(maxSizeMB) || 50
    });

    onClose();
  }
</script>

<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
  <div class="relative w-full max-w-md bg-[#090e1c] border border-purple-500/40 rounded-2xl shadow-2xl overflow-hidden flex flex-col">
    <!-- Başlık -->
    <div class="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-[#0d1426]">
      <div class="flex items-center space-x-3">
        <div class="p-2 rounded-lg bg-purple-500/10 text-purple-400 border border-purple-500/30">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
        </div>
        <div>
          <h2 class="text-base font-semibold text-slate-100">Özel İmza Ekle</h2>
          <p class="text-xs text-slate-400">Yeni bir dosya formatı veya Magic Byte tanımlayın</p>
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

    <!-- Form -->
    <div class="p-6 space-y-4">
      {#if errorMessage}
        <div class="p-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-xs flex items-center space-x-2">
          <svg class="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>{errorMessage}</span>
        </div>
      {/if}

      <div>
        <label for="sig-name" class="block text-xs font-medium text-slate-300 mb-1">Format Adı / Açıklama</label>
        <input
          id="sig-name"
          type="text"
          bind:value={name}
          placeholder="Örn: Özel Kriptolu Veritabanı"
          class="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-purple-500"
        />
      </div>

      <div>
        <label for="sig-hex" class="block text-xs font-medium text-slate-300 mb-1">
          Magic Bytes (Hex Başlık) <span class="text-purple-400">*</span>
        </label>
        <input
          id="sig-hex"
          type="text"
          bind:value={hexBytes}
          placeholder="Örn: 89 50 4E 47 veya 25 50 44 46"
          class="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-slate-100 font-mono text-sm focus:outline-none focus:border-purple-500"
        />
        <p class="text-[11px] text-slate-500 mt-1 font-mono">Boşluklu veya bitişik onaltılık baytlar</p>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div>
          <label for="sig-ext" class="block text-xs font-medium text-slate-300 mb-1">
            Uzantı <span class="text-purple-400">*</span>
          </label>
          <input
            id="sig-ext"
            type="text"
            bind:value={extension}
            placeholder="Örn: dat"
            class="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-slate-100 font-mono text-sm focus:outline-none focus:border-purple-500"
          />
        </div>

        <div>
          <label for="sig-max" class="block text-xs font-medium text-slate-300 mb-1">Maksimum Boyut (MB)</label>
          <input
            id="sig-max"
            type="number"
            bind:value={maxSizeMB}
            min="1"
            max="1024"
            class="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-slate-100 font-mono text-sm focus:outline-none focus:border-purple-500"
          />
        </div>
      </div>
    </div>

    <!-- Alt Butonlar -->
    <div class="flex items-center justify-end space-x-3 px-6 py-4 border-t border-slate-800 bg-[#0d1426]">
      <button
        class="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition-colors"
        onclick={onClose}
      >
        İptal
      </button>
      <button
        class="px-5 py-2 rounded-xl bg-gradient-to-r from-purple-500 to-indigo-500 hover:from-purple-400 hover:to-indigo-400 text-white font-bold text-xs shadow-lg shadow-purple-500/20 transition-all"
        onclick={handleSubmit}
      >
        İmzayı Kaydet
      </button>
    </div>
  </div>
</div>
