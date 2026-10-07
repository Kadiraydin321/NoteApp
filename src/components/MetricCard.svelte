<!-- src/components/MetricCard.svelte -->
<script>
  let { title = '', value = '', subtitle = '', badge = '', accent = 'cyan' } = $props();

  const colorStyles = {
    cyan: {
      border: 'border-cyan-500/30 hover:border-cyan-400/60',
      glow: 'shadow-[0_0_15px_-3px_rgba(6,182,212,0.15)]',
      text: 'text-cyan-400',
      badgeBg: 'bg-cyan-500/10 text-cyan-300 border-cyan-500/20'
    },
    purple: {
      border: 'border-purple-500/30 hover:border-purple-400/60',
      glow: 'shadow-[0_0_15px_-3px_rgba(168,85,247,0.15)]',
      text: 'text-purple-400',
      badgeBg: 'bg-purple-500/10 text-purple-300 border-purple-500/20'
    },
    emerald: {
      border: 'border-emerald-500/30 hover:border-emerald-400/60',
      glow: 'shadow-[0_0_15px_-3px_rgba(16,185,129,0.15)]',
      text: 'text-emerald-400',
      badgeBg: 'bg-emerald-500/10 text-emerald-300 border-emerald-500/20'
    },
    amber: {
      border: 'border-amber-500/30 hover:border-amber-400/60',
      glow: 'shadow-[0_0_15px_-3px_rgba(245,158,11,0.15)]',
      text: 'text-amber-400',
      badgeBg: 'bg-amber-500/10 text-amber-300 border-amber-500/20'
    }
  };

  const currentTheme = $derived(colorStyles[accent] || colorStyles.cyan);
</script>

<div class="relative bg-[#0d1322]/90 backdrop-blur-md rounded-xl p-4 border {currentTheme.border} {currentTheme.glow} transition-all duration-300">
  <div class="flex items-center justify-between mb-2">
    <span class="text-xs font-medium uppercase tracking-wider text-slate-400">{title}</span>
    {#if badge}
      <span class="text-[10px] font-semibold px-2 py-0.5 rounded-full border {currentTheme.badgeBg}">
        {badge}
      </span>
    {/if}
  </div>

  <div class="flex items-baseline space-x-2">
    <div class="text-2xl font-bold font-mono tracking-tight {currentTheme.text}">
      {value}
    </div>
  </div>

  {#if subtitle}
    <div class="mt-1 text-xs text-slate-400 font-mono">
      {subtitle}
    </div>
  {/if}
</div>
