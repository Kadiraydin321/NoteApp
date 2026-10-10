<!-- src/components/AboutModal.svelte -->
<script>
  let { onClose = () => {} } = $props();
</script>

<div class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md">
  <div class="relative w-full max-w-lg bg-[#090e1c] border border-cyan-500/50 rounded-2xl shadow-[0_0_50px_rgba(0,245,212,0.2)] overflow-hidden flex flex-col">
    <!-- Üst Işık Gradyanı -->
    <div class="h-1 bg-gradient-to-r from-cyan-400 via-purple-500 to-emerald-400"></div>

    <!-- Başlık & Çarpı Butonu -->
    <div class="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-[#0d1426]">
      <div class="flex items-center space-x-2">
        <span class="w-2.5 h-2.5 rounded-full bg-cyan-400 animate-ping"></span>
        <h2 class="text-xs font-mono font-bold tracking-wider text-cyan-400 uppercase">HAKKINDA // ABOUT</h2>
      </div>

      <button
        type="button"
        class="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors"
        onclick={onClose}
        aria-label="Kapat"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>

    <!-- İçerik -->
    <div class="p-6 flex flex-col items-center text-center space-y-4">
      <!-- Logo & Parlama -->
      <div class="relative">
        <div class="absolute -inset-2 bg-gradient-to-r from-cyan-500 to-purple-600 rounded-2xl blur-lg opacity-50 animate-pulse"></div>
        <img
          src="./icon.png"
          alt="ByteRescue Logo"
          class="relative w-24 h-24 rounded-2xl object-cover shadow-2xl border border-cyan-400/40"
        />
      </div>

      <!-- Başlık & İmza -->
      <div>
        <h3 class="text-xl font-bold font-mono tracking-wider text-slate-100 flex items-center justify-center space-x-2">
          <span>BYTERESCUE</span>
          <span class="text-xs px-2 py-0.5 rounded bg-cyan-500/20 text-cyan-300 border border-cyan-500/30">v1.2</span>
        </h3>
        <p class="text-xs font-mono text-cyan-400 font-semibold mt-1">
          Kurtarıcı: <span class="text-slate-100 font-bold">Kadir</span>
        </p>
      </div>

      <!-- Açıklama -->
      <p class="text-xs text-slate-300 leading-relaxed max-w-md font-sans">
        ByteRescue; dosya sistemi metaverileri silinmiş veya hasar görmüş olsa dahi, 
        doğrudan ham disk sektörlerini ve Windows Geri Dönüşüm Kutusu ($Recycle.Bin) indekslerini tarayarak 
        fotoğraf, belge, video ve arşivlerinizi kurtaran profesyonel bir adli bilişim masaüstü yazılımıdır.
      </p>

      <!-- Özellik Rozetleri -->
      <div class="grid grid-cols-2 gap-2 w-full pt-2 text-left font-mono text-[11px]">
        <div class="p-2.5 rounded-xl bg-slate-900/90 border border-slate-800 text-slate-300 flex items-center space-x-2">
          <span class="text-cyan-400">⚡</span>
          <span>64KB Kayan Örtüşme Taraması</span>
        </div>
        <div class="p-2.5 rounded-xl bg-slate-900/90 border border-slate-800 text-slate-300 flex items-center space-x-2">
          <span class="text-emerald-400">🛡️</span>
          <span>Güvenli Kurtarma Koruması</span>
        </div>
        <div class="p-2.5 rounded-xl bg-slate-900/90 border border-slate-800 text-slate-300 flex items-center space-x-2">
          <span class="text-purple-400">🔍</span>
          <span>Canlı Hex & ASCII Görüntüleyici</span>
        </div>
        <div class="p-2.5 rounded-xl bg-slate-900/90 border border-slate-800 text-slate-300 flex items-center space-x-2">
          <span class="text-amber-400">💻</span>
          <span>Windows C: / D: / $Recycle.Bin</span>
        </div>
      </div>
    </div>

    <!-- Alt Kapat Butonu -->
    <div class="flex items-center justify-between px-6 py-3.5 border-t border-slate-800 bg-[#0d1426] text-xs font-mono">
      <span class="text-slate-500">© 2026 Kadir. ByteRescue Veri Kurtarıcı.</span>
      <button
        type="button"
        class="px-5 py-1.5 rounded-xl bg-gradient-to-r from-cyan-500 to-purple-600 hover:from-cyan-400 hover:to-purple-500 text-slate-950 font-bold transition-all shadow-md shadow-cyan-500/20"
        onclick={onClose}
      >
        Kapat
      </button>
    </div>
  </div>
</div>
