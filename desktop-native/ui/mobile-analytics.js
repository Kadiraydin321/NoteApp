(() => {
  const WORDS_PER_MINUTE = 200;
  const DAILY_GOAL_KEY = "notdefterim_daily_word_goal";
  const HASHTAG_PATTERN = /#([a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-]+)/g;

  function countWords(value) {
    const text = String(value || "").trim();
    return text ? text.split(/\s+/).length : 0;
  }

  function dayKey(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
  }

  function noteDate(note) {
    const timestamp = Number(note.updatedAt || note.timestamp || 0);
    if (!Number.isFinite(timestamp) || timestamp <= 0) return null;
    const date = new Date(timestamp);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  function getDailyGoal() {
    try {
      const stored = Number(localStorage.getItem(DAILY_GOAL_KEY));
      return Number.isInteger(stored) && stored > 0 && stored <= 100000 ? stored : 300;
    } catch (error) {
      console.error("Günlük hedef yüklenemedi:", error);
      showToast("Günlük hedef yüklenemedi. Varsayılan hedef kullanılacak.", "warning");
      return 300;
    }
  }

  function collectAnalytics() {
    const allNotes = [...state.activeNotes, ...state.archivedNotes, ...state.trashNotes];
    const activeNotes = state.activeNotes;
    const visibleContentNotes = activeNotes.filter(note => !note.isLocked || state.isVaultUnlocked);
    const includedNoteCount = visibleContentNotes.length;
    let totalWords = 0;
    let totalCharacters = 0;
    let todayWords = 0;
    let imageCount = 0;
    let audioCount = 0;
    let drawingCount = 0;
    const tagCounts = new Map();
    const categoryCounts = new Map();
    const activityByDay = new Map();
    const activeDays = new Set();
    const activeNoteSet = new Set(activeNotes);
    const now = new Date();
    const today = dayKey(now);

    for (const note of activeNotes) {
      const categoryKey = note.categoryId == null ? "" : String(note.categoryId);
      categoryCounts.set(categoryKey, (categoryCounts.get(categoryKey) || 0) + 1);
    }

    for (const note of allNotes) {
      const date = noteDate(note);
      if (date) {
        const key = dayKey(date);
        activityByDay.set(key, (activityByDay.get(key) || 0) + 1);
        if (activeNoteSet.has(note)) activeDays.add(key);
        if (key === today && (!note.isLocked || state.isVaultUnlocked)) {
          todayWords += countWords(note.title) + countWords(note.content);
        }
      }
    }

    for (const note of visibleContentNotes) {
      const title = String(note.title || "");
      const content = String(note.content || "");
      const noteWords = countWords(title) + countWords(content);
      totalWords += noteWords;
      totalCharacters += title.length + content.length;

      for (const match of content.matchAll(HASHTAG_PATTERN)) {
        const tag = match[1].toLocaleLowerCase("tr-TR");
        tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1);
      }

      for (const attachment of Array.isArray(note.attachments) ? note.attachments : []) {
        const path = String(attachment).toLocaleLowerCase("tr-TR");
        if (/\.(m4a|mp3|wav|aac|ogg|flac)$/.test(path)) audioCount++;
        else if (path.includes("drawing")) drawingCount++;
        else imageCount++;
      }
    }

    const week = [];
    for (let offset = 6; offset >= 0; offset--) {
      const date = new Date(now);
      date.setDate(now.getDate() - offset);
      week.push({
        key: dayKey(date),
        label: new Intl.DateTimeFormat("tr-TR", { weekday: "short" }).format(date).replace(".", ""),
        count: activityByDay.get(dayKey(date)) || 0
      });
    }

    const sortedDays = [...activeDays].sort();
    let currentStreak = 0;
    const cursor = new Date(now);
    if (!activeDays.has(today)) cursor.setDate(cursor.getDate() - 1);
    while (activeDays.has(dayKey(cursor))) {
      currentStreak++;
      cursor.setDate(cursor.getDate() - 1);
    }

    let bestStreak = 0;
    let streak = 0;
    let previousDay = null;
    for (const key of sortedDays) {
      const [year, month, day] = key.split("-").map(Number);
      const currentDay = Date.UTC(year, month - 1, day) / 86400000;
      const consecutive = previousDay !== null && currentDay - previousDay === 1;
      streak = consecutive ? streak + 1 : 1;
      bestStreak = Math.max(bestStreak, streak);
      previousDay = currentDay;
    }
    bestStreak = Math.max(bestStreak, currentStreak);

    const categories = new Map(state.categories.map(category => [String(category.id), category.name]));
    const categoryDistribution = [...categoryCounts.entries()]
      .map(([id, count]) => ({ name: categories.get(id) || "Kategorisiz", count }))
      .sort((left, right) => right.count - left.count);

    return {
      activeCount: activeNotes.length,
      archivedCount: state.archivedNotes.length,
      trashCount: state.trashNotes.length,
      pinnedCount: activeNotes.filter(note => note.isPinned).length,
      lockedCount: activeNotes.filter(note => note.isLocked).length,
      totalWords,
      totalCharacters,
      averageWords: includedNoteCount ? Math.floor(totalWords / includedNoteCount) : 0,
      readingMinutes: totalWords ? Math.max(1, Math.floor(totalWords / WORDS_PER_MINUTE)) : 0,
      todayWords,
      currentStreak,
      bestStreak,
      activeDays: activeDays.size,
      week,
      topTags: [...tagCounts.entries()].sort((left, right) => right[1] - left[1]).slice(0, 8),
      categoryDistribution,
      imageCount,
      audioCount,
      drawingCount,
      lockedContentExcluded: !state.isVaultUnlocked && activeNotes.some(note => note.isLocked)
    };
  }

  function makeElement(tag, className, content) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (content !== undefined) element.textContent = content;
    return element;
  }

  function makeMetric(label, value, detail) {
    const card = makeElement("article", "analytics-metric-card");
    card.append(
      makeElement("span", "analytics-metric-label", label),
      makeElement("strong", "analytics-metric-value", value),
      makeElement("span", "analytics-metric-detail", detail)
    );
    return card;
  }

  function renderAnalytics(modal, goal) {
    const stats = collectAnalytics();
    const content = modal.querySelector(".mobile-analytics-content");
    content.replaceChildren();

    const streak = makeElement("section", "analytics-streak-card");
    const streakCopy = makeElement("div", "analytics-streak-copy");
    streakCopy.append(
      makeElement("span", "analytics-eyebrow", "YAZMA RİTMİ"),
      makeElement("strong", "analytics-streak-number", `${stats.currentStreak} gün`),
      makeElement("span", "analytics-streak-caption", stats.currentStreak ? "Notlarına düzenli olarak geri dönüyorsun." : "Bugün bir not ekleyerek yeni bir seri başlat.")
    );
    const streakBadge = makeElement("div", "analytics-streak-badge", "✦");
    streakBadge.setAttribute("aria-hidden", "true");
    const streakMeta = makeElement("div", "analytics-streak-meta");
    streakMeta.append(
      makeElement("span", "", `En iyi seri · ${stats.bestStreak} gün`),
      makeElement("span", "", `${stats.activeDays} aktif gün`)
    );
    streak.append(streakCopy, streakBadge, streakMeta);
    content.append(streak);

    const metrics = makeElement("section", "analytics-metrics-grid");
    metrics.setAttribute("aria-label", "Not istatistikleri");
    metrics.append(
      makeMetric("Aktif not", stats.activeCount.toLocaleString("tr-TR"), `${stats.pinnedCount} sabit · ${stats.lockedCount} kilitli`),
      makeMetric("Toplam kelime", stats.totalWords.toLocaleString("tr-TR"), `Not başlıkları dahil`),
      makeMetric("Karakter", stats.totalCharacters.toLocaleString("tr-TR"), `Ort. ${stats.averageWords.toLocaleString("tr-TR")} kelime / not`),
      makeMetric("Okuma süresi", `~${stats.readingMinutes} dk`, `${stats.archivedCount} arşiv · ${stats.trashCount} çöp`)
    );
    content.append(metrics);

    const goalCard = makeElement("section", "analytics-card analytics-goal-card");
    const goalHead = makeElement("div", "analytics-card-heading");
    goalHead.append(
      makeElement("div", "analytics-card-title", "Günlük kelime hedefi"),
      makeElement("div", "analytics-card-subtitle", `${stats.todayWords.toLocaleString("tr-TR")} / ${goal.toLocaleString("tr-TR")} kelime bugün`)
    );
    const goalProgress = makeElement("div", "analytics-progress-track");
    goalProgress.setAttribute("role", "progressbar");
    goalProgress.setAttribute("aria-label", "Günlük kelime hedefi");
    goalProgress.setAttribute("aria-valuemin", "0");
    goalProgress.setAttribute("aria-valuemax", String(goal));
    goalProgress.setAttribute("aria-valuenow", String(Math.min(stats.todayWords, goal)));
    const goalFill = makeElement("div", "analytics-progress-fill");
    goalFill.style.width = `${Math.min(100, (stats.todayWords / goal) * 100)}%`;
    goalProgress.append(goalFill);
    const goalForm = makeElement("form", "analytics-goal-form");
    const goalLabel = makeElement("label", "analytics-goal-label", "Hedef");
    const goalInput = makeElement("input", "analytics-goal-input");
    goalInput.type = "number";
    goalInput.min = "1";
    goalInput.max = "100000";
    goalInput.step = "50";
    goalInput.value = String(goal);
    goalInput.setAttribute("aria-label", "Günlük kelime hedefi");
    const goalButton = makeElement("button", "analytics-goal-save", "Uygula");
    goalButton.type = "submit";
    goalLabel.append(goalInput);
    goalForm.append(goalLabel, goalButton);
    goalCard.append(goalHead, goalProgress, goalForm);
    content.append(goalCard);

    const activityCard = makeElement("section", "analytics-card");
    activityCard.append(
      makeElement("div", "analytics-card-title", "Haftalık aktivite"),
      makeElement("div", "analytics-card-subtitle", "Son 7 günde güncellenen notlar")
    );
    const chart = makeElement("div", "analytics-week-chart");
    const maxActivity = Math.max(1, ...stats.week.map(day => day.count));
    for (const day of stats.week) {
      const column = makeElement("div", "analytics-day-column");
      const value = makeElement("span", "analytics-day-value", String(day.count));
      const barTrack = makeElement("div", "analytics-day-track");
      const bar = makeElement("div", "analytics-day-bar");
      bar.style.height = `${Math.max(4, (day.count / maxActivity) * 100)}%`;
      if (day.key === dayKey(new Date())) bar.classList.add("today");
      barTrack.append(bar);
      column.append(value, barTrack, makeElement("span", "analytics-day-label", day.label));
      chart.append(column);
    }
    activityCard.append(chart);
    content.append(activityCard);

    const breakdown = makeElement("div", "analytics-breakdown-grid");
    const tagsCard = makeElement("section", "analytics-card");
    tagsCard.append(makeElement("div", "analytics-card-title", "Öne çıkan etiketler"));
    if (stats.topTags.length) {
      const tags = makeElement("div", "analytics-tag-list");
      for (const [tag, count] of stats.topTags) {
        const chip = makeElement("span", "analytics-tag-chip");
        chip.append(makeElement("b", "", `#${tag}`), makeElement("span", "", String(count)));
        tags.append(chip);
      }
      tagsCard.append(tags);
    } else {
      tagsCard.append(makeElement("p", "analytics-muted", "Not içeriklerine #etiket ekleyerek konuları takip edebilirsin."));
    }

    const categoriesCard = makeElement("section", "analytics-card");
    categoriesCard.append(makeElement("div", "analytics-card-title", "Kategori dağılımı"));
    if (stats.categoryDistribution.length) {
      const categories = makeElement("div", "analytics-category-list");
      const maxCategory = Math.max(1, ...stats.categoryDistribution.map(item => item.count));
      for (const item of stats.categoryDistribution.slice(0, 6)) {
        const row = makeElement("div", "analytics-category-row");
        const label = makeElement("div", "analytics-category-label");
        label.append(makeElement("span", "", item.name), makeElement("b", "", String(item.count)));
        const track = makeElement("div", "analytics-progress-track");
        const fill = makeElement("div", "analytics-progress-fill");
        fill.style.width = `${(item.count / maxCategory) * 100}%`;
        track.append(fill);
        row.append(label, track);
        categories.append(row);
      }
      categoriesCard.append(categories);
    } else {
      categoriesCard.append(makeElement("p", "analytics-muted", "Henüz kategoriye ayrılmış not yok."));
    }
    breakdown.append(tagsCard, categoriesCard);
    content.append(breakdown);

    const mediaCard = makeElement("section", "analytics-card analytics-media-card");
    mediaCard.append(makeElement("div", "analytics-card-title", "Medya envanteri"));
    const mediaItems = makeElement("div", "analytics-media-items");
    for (const [label, value] of [
      ["Görsel", stats.imageCount],
      ["Ses", stats.audioCount],
      ["Çizim", stats.drawingCount]
    ]) {
      const item = makeElement("div", "analytics-media-item");
      item.append(makeElement("strong", "", String(value)), makeElement("span", "", label));
      mediaItems.append(item);
    }
    mediaCard.append(mediaItems);
    content.append(mediaCard);

    if (stats.lockedContentExcluded) {
      const privacyNotice = makeElement("p", "analytics-privacy-note", "Kasa kilitliyken kilitli notların içeriği ve ekleri analize dahil edilmez.");
      content.append(privacyNotice);
    }

    goalForm.addEventListener("submit", event => {
      event.preventDefault();
      const nextGoal = Number(goalInput.value);
      if (!Number.isInteger(nextGoal) || nextGoal < 1 || nextGoal > 100000) {
        goalInput.setAttribute("aria-invalid", "true");
        goalInput.focus();
        return;
      }
      try {
        localStorage.setItem(DAILY_GOAL_KEY, String(nextGoal));
      } catch (error) {
        console.error("Günlük hedef kaydedilemedi:", error);
        showToast("Günlük hedef kaydedilemedi. Depolama alanını kontrol edin.", "warning");
        return;
      }
      renderAnalytics(modal, nextGoal);
      modal.querySelector(".analytics-goal-input")?.focus();
    });
  }

  function openMobileAnalytics() {
    const returnFocusTarget = document.activeElement;
    document.getElementById("mobileAnalyticsModal")?.remove();
    const modal = makeElement("div", "modal-overlay open mobile-analytics-overlay");
    modal.id = "mobileAnalyticsModal";
    modal.setAttribute("role", "dialog");
    modal.setAttribute("aria-modal", "true");
    modal.setAttribute("aria-labelledby", "mobileAnalyticsTitle");

    const dialog = makeElement("div", "mobile-analytics-dialog");
    const header = makeElement("header", "mobile-analytics-header");
    const titleBlock = makeElement("div", "mobile-analytics-title-block");
    titleBlock.append(
      makeElement("span", "analytics-eyebrow", "NOT DEFTERİ"),
      makeElement("h2", "mobile-analytics-title", "İstatistikler & Analitik")
    );
    titleBlock.querySelector("h2").id = "mobileAnalyticsTitle";
    const closeButton = makeElement("button", "mobile-analytics-close", "Kapat");
    closeButton.type = "button";
    closeButton.setAttribute("aria-label", "İstatistikleri kapat");
    header.append(titleBlock, closeButton);
    const content = makeElement("div", "mobile-analytics-content");
    content.setAttribute("role", "region");
    content.setAttribute("aria-label", "Uygulama istatistikleri");
    dialog.append(header, content);
    modal.append(dialog);
    document.body.append(modal);

    let goal = getDailyGoal();
    renderAnalytics(modal, goal);

    const close = () => {
      document.removeEventListener("keydown", onKeyDown);
      modal.remove();
      if (returnFocusTarget instanceof HTMLElement && returnFocusTarget.isConnected && returnFocusTarget.getClientRects().length) {
        returnFocusTarget.focus();
      }
    };
    const onKeyDown = event => {
      if (event.key === "Escape") {
        close();
        return;
      }
      if (event.key !== "Tab") return;

      const focusable = [...dialog.querySelectorAll(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )].filter(element => element.getClientRects().length > 0);
      if (!focusable.length) {
        event.preventDefault();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement;
      if (event.shiftKey && (active === first || !dialog.contains(active))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (active === last || !dialog.contains(active))) {
        event.preventDefault();
        first.focus();
      }
    };
    closeButton.addEventListener("click", close);
    modal.addEventListener("click", event => {
      if (event.target === modal) close();
    });
    document.addEventListener("keydown", onKeyDown);
    closeButton.focus();
  }

  window.openMobileAnalytics = openMobileAnalytics;
  document.getElementById("btnMenuAnalytics")?.addEventListener("click", event => {
    event.preventDefault();
    const menu = document.getElementById("editorMoreMenu");
    const menuButton = document.getElementById("btnEditorMoreActions");
    if (menu) menu.style.display = "none";
    menuButton?.setAttribute("aria-expanded", "false");
    openMobileAnalytics();
  });
})();
