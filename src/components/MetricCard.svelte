<!-- src/components/MetricCard.svelte -->
<script>
  let { title = '', value = '', subtitle = '', badge = '', accent = 'cyan' } = $props();

  const colorStyles = {
    cyan: {
      border: 'border-cyan-500/25 hover:border-cyan-400/50',
      glow: 'shadow-[0_0_12px_-3px_rgba(6,182,212,0.12)]',
      text: 'text-cyan-400',
      badgeBg: 'bg-cyan-500/10 text-cyan-300 border-cyan-500/20'
    },
    purple: {
      border: 'border-purple-500/25 hover:border-purple-400/50',
      glow: 'shadow-[0_0_12px_-3px_rgba(168,85,247,0.12)]',
      text: 'text-purple-400',
      badgeBg: 'bg-purple-500/10 text-purple-300 border-purple-500/20'
    },
    emerald: {
      border: 'border-emerald-500/25 hover:border-emerald-400/50',
      glow: 'shadow-[0_0_12px_-3px_rgba(16,185,129,0.12)]',
      text: 'text-emerald-400',
      badgeBg: 'bg-emerald-500/10 text-emerald-300 border-emerald-500/20'
    },
    amber: {
      border: 'border-amber-500/25 hover:border-amber-400/50',
      glow: 'shadow-[0_0_12px_-3px_rgba(245,158,11,0.12)]',
      text: 'text-amber-400',
      badgeBg: 'bg-amber-500/10 text-amber-300 border-amber-500/20'
    }
  };

  const currentTheme = $derived(colorStyles[accent] || colorStyles.cyan);
</script>

<div class="relative bg-[#0b1022]/95 backdrop-blur-md rounded-xl p-2.5 px-3.5 border {currentTheme.border} {currentTheme.glow} transition-all duration-200 flex items-center justify-between">
  <div class="min-w-0">
    <div class="flex items-center space-x-1.5">
      <span class="text-[10px] font-mono uppercase tracking-wider text-slate-400">{title}</span>
      {#if badge}
        <span class="text-[9px] font-semibold px-1.5 py-0.2 rounded-full border {currentTheme.badgeBg}">
          {badge}
        </span>
      {/if}
    </div>
    <div class="text-base font-bold font-mono tracking-tight {currentTheme.text} mt-0.5 truncate">
      {value}
    </div>
  </div>

  {#if subtitle}
    <div class="text-[10px] text-slate-500 font-mono text-right max-w-[130px] truncate ml-2">
      {subtitle}
    </div>
  {/if}
</div>
