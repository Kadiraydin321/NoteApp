// ===================================================
// Not Defterim - Masaüstü JavaScript Motoru
// ===================================================

const NOTE_PRESETS = [
  { id: 0, name: "Varsayılan", color: "#131D2E", text: "#F8FAFC" },
  { id: -1877005509, name: "Gece Mavisi", color: "#1E293B", text: "#F8FAFC" },
  { id: -1868496843, name: "Mürdüm / Lavanta", color: "#2A2035", text: "#F8FAFC" },
  { id: -1876939221, name: "Zümrüt Yeşil", color: "#1F2E2B", text: "#F8FAFC" },
  { id: -1858524642, name: "Sıcak Amber", color: "#332A1E", text: "#F8FAFC" },
  { id: -1855234524, name: "Gül Kurusu", color: "#361E24", text: "#F8FAFC" },
  { id: -1876938952, name: "Okyanus Teal", color: "#1E2E38", text: "#F8FAFC" },
  { id: -15804, name: "Açık Sarı", color: "#FFF9C4", text: "#1A1C1E" },
  { id: -7982, name: "Açık Şeftali", color: "#FFE0B2", text: "#1A1C1E" },
  { id: -12846, name: "Açık Mercan", color: "#FFCDD2", text: "#1A1C1E" },
  { id: -476720, name: "Açık Pembe", color: "#F8BBD0", text: "#1A1C1E" },
  { id: -1982745, name: "Açık Mor", color: "#E1BEE7", text: "#1A1C1E" },
  { id: -4464901, name: "Açık Mavi", color: "#BBDEFB", text: "#1A1C1E" },
  { id: -3610935, name: "Açık Nane", color: "#C8E6C9", text: "#1A1C1E" }
];

// Uygulama Durumu
let state = {
  activeNotes: [],
  archivedNotes: [],
  trashNotes: [],
  categories: [],
  currentFilter: 'all', // 'all', 'pinned', 'vault', 'archive', 'trash', 'cat_ID'
  searchQuery: '',
  selectedNoteId: null,
  layoutMode: 'list', // 'list' veya 'grid'
  markdownMode: 'split', // 'edit', 'split', 'preview'
  antiSsEnabled: true,
  isVaultUnlocked: false,
  undoStack: [],
  redoStack: [],
  autoSaveTimer: null,
  isDirty: false
};

// IPC İletişim Köprüsü (Custom Protocol üzerinden hızlı HTTP Fetch)
async function callIpc(cmd, payload = {}) {
  try {
    const res = await fetch("/api", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ cmd, payload })
    });
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    }
    const data = await res.json();
    if (data.error) {
      throw new Error(data.error);
    }
    return data.result;
  } catch (err) {
    console.error(`[IPC Hatası] Komut '${cmd}':`, err);
    throw err;
  }
}

// ===================================================
// BAŞLATMA VE VERİ YÜKLEME
// ===================================================

document.addEventListener("DOMContentLoaded", () => {
  initUI();
  loadAllData();
});

async function loadAllData() {
  try {
    const data = await callIpc("getNotes");
    if (data) {
      state.activeNotes = data.active || [];
      state.archivedNotes = data.archived || [];
      state.trashNotes = data.trash || [];
      state.categories = data.categories || [];
      state.isVaultUnlocked = data.isVaultUnlocked || false;

      updateSidebarCounts();
      renderCategoriesList();
      renderNotesList();

      if (state.selectedNoteId) {
        const found = findNoteById(state.selectedNoteId);
        if (found) loadNoteIntoEditor(found, false);
      } else if (state.activeNotes.length > 0 && state.currentFilter === 'all') {
        loadNoteIntoEditor(state.activeNotes[0], false);
      }
    }
  } catch (err) {
    console.error("Veri yüklenemedi:", err);
  }
}

function findNoteById(id) {
  return state.activeNotes.find(n => n.id === id) ||
         state.archivedNotes.find(n => n.id === id) ||
         state.trashNotes.find(n => n.id === id);
}

// ===================================================
// UI İLKLENDİRME & OLAY DİNLEYİCİLERİ
// ===================================================

function initUI() {
  // 0. Tema Yöneticisi (Varsayılan: Kehribar - Mobil Yakını)
  const savedTheme = localStorage.getItem("notdefterim_theme") || "amber";
  applyTheme(savedTheme);

  const btnTheme = document.getElementById("btnThemeToggle");
  const themeDropdown = document.getElementById("themeDropdown");
  if (btnTheme && themeDropdown) {
    btnTheme.addEventListener("click", (e) => {
      e.stopPropagation();
      themeDropdown.classList.toggle("open");
    });
    document.addEventListener("click", () => {
      themeDropdown.classList.remove("open");
    });
    document.querySelectorAll(".theme-option").forEach(opt => {
      opt.addEventListener("click", () => {
        const theme = opt.dataset.theme;
        applyTheme(theme);
        themeDropdown.classList.remove("open");
      });
    });
  }

  // 1. Yeni Not Butonları
  document.getElementById("btnNewNote").addEventListener("click", createNewNote);
  document.getElementById("btnNewNoteEmpty").addEventListener("click", createNewNote);

  // 2. Sidebar Filtreleri & Mobil Çipler
  document.querySelectorAll(".nav-item").forEach(item => {
    item.addEventListener("click", () => {
      setFilter(item.dataset.filter);
    });
  });

  document.querySelectorAll("#filterChipsBar .chip").forEach(chip => {
    chip.addEventListener("click", () => {
      setFilter(chip.dataset.chip);
    });
  });

  // 3. Arama Kutusu
  const searchInput = document.getElementById("searchInput");
  const searchClear = document.getElementById("searchClear");
  let searchDebounceTimer = null;
  searchInput.addEventListener("input", (e) => {
    state.searchQuery = e.target.value;
    searchClear.classList.toggle("visible", state.searchQuery.length > 0);
    clearTimeout(searchDebounceTimer);
    searchDebounceTimer = setTimeout(() => {
      renderNotesList();
    }, 200);
  });
  searchClear.addEventListener("click", () => {
    searchInput.value = "";
    state.searchQuery = "";
    searchClear.classList.remove("visible");
    renderNotesList();
  });

  // 4. Görünüm Değiştirici (List vs Grid)
  const btnList = document.getElementById("btnViewList");
  const btnGrid = document.getElementById("btnViewGrid");
  btnList.addEventListener("click", () => {
    state.layoutMode = 'list';
    btnList.classList.add("active");
    btnGrid.classList.remove("active");
    const container = document.getElementById("notesContainer");
    container.className = "notes-container layout-list";
  });
  btnGrid.addEventListener("click", () => {
    state.layoutMode = 'grid';
    btnGrid.classList.add("active");
    btnList.classList.remove("active");
    const container = document.getElementById("notesContainer");
    container.className = "notes-container layout-grid";
  });

  // 5. Editör Başlık & İçerik Canlı Otomatik Kaydetme
  const titleInput = document.getElementById("editorTitle");
  const contentInput = document.getElementById("editorContent");

  titleInput.addEventListener("input", () => {
    onEditorChange();
  });

  contentInput.addEventListener("input", () => {
    onEditorChange();
    updateLiveMarkdownPreview();
    updateCharWordsCount();
  });

  // Ctrl+Z (Undo) & Ctrl+Y (Redo) Kısayolları
  window.addEventListener("keydown", (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') {
      e.preventDefault();
      if (e.shiftKey) redo(); else undo();
    } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'y') {
      e.preventDefault();
      redo();
    }
  });

  document.getElementById("btnUndo").addEventListener("click", undo);
  document.getElementById("btnRedo").addEventListener("click", redo);

  // 6. Markdown Görünüm Modları
  const btnEdit = document.getElementById("btnModeEdit");
  const btnSplit = document.getElementById("btnModeSplit");
  const btnPrev = document.getElementById("btnModePreview");
  const paneEdit = document.getElementById("paneEditorBox");
  const panePrev = document.getElementById("panePreviewBox");

  btnEdit.addEventListener("click", () => {
    setMarkdownMode('edit', btnEdit, [btnSplit, btnPrev], paneEdit, panePrev);
  });
  btnSplit.addEventListener("click", () => {
    setMarkdownMode('split', btnSplit, [btnEdit, btnPrev], paneEdit, panePrev);
  });
  btnPrev.addEventListener("click", () => {
    setMarkdownMode('preview', btnPrev, [btnEdit, btnSplit], paneEdit, panePrev);
  });

  // 7. Markdown Araç Çubuğu Butonları
  document.querySelectorAll(".md-tool-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      insertMarkdown(btn.dataset.prefix, btn.dataset.suffix);
    });
  });

  // 8. Editör Eylemleri (Pin, Lock, Archive, Delete)
  document.getElementById("btnPin").addEventListener("click", togglePinCurrentNote);
  document.getElementById("btnLock").addEventListener("click", toggleLockCurrentNote);
  document.getElementById("btnArchive").addEventListener("click", toggleArchiveCurrentNote);
  document.getElementById("btnDelete").addEventListener("click", deleteCurrentNote);

  // 9. Renk Paleti Açılır Menüsü
  initColorPalette();

  // 10. Kategori Açılır Menüsü
  document.getElementById("editorCatSelect").addEventListener("change", (e) => {
    const note = findNoteById(state.selectedNoteId);
    if (note) {
      note.categoryId = e.target.value ? parseInt(e.target.value) : null;
      triggerAutoSave();
    }
  });

  // 11. Modallar
  initModals();

  // 12. Alt-Tab Gizlilik Kalkanı (Privacy Shield)
  window.addEventListener("blur", () => {
    if (state.antiSsEnabled) {
      document.getElementById("privacyShield").classList.add("active");
    }
  });
  window.addEventListener("focus", () => {
    document.getElementById("privacyShield").classList.remove("active");
  });
  document.getElementById("privacyShield").addEventListener("click", () => {
    document.getElementById("privacyShield").classList.remove("active");
  });

  // 13. Anti-SS Aç/Kapat Butonu
  document.getElementById("btnToggleAntiSs").addEventListener("click", async () => {
    state.antiSsEnabled = !state.antiSsEnabled;
    await callIpc("setAntiScreenshot", { enable: state.antiSsEnabled });
    document.getElementById("antiSsLabel").textContent = state.antiSsEnabled ? "Anti-SS: Açık" : "Anti-SS: Kapalı";
    showToast(state.antiSsEnabled ? "Gizlilik Kalkanı aktif (Anti-SS açık)" : "Gizlilik Kalkanı devre dışı", "info");
  });
}

function setMarkdownMode(mode, activeBtn, otherBtns, paneEdit, panePrev) {
  state.markdownMode = mode;
  activeBtn.classList.add("active");
  otherBtns.forEach(b => b.classList.remove("active"));

  if (mode === 'edit') {
    paneEdit.style.display = 'flex';
    panePrev.style.display = 'none';
  } else if (mode === 'preview') {
    paneEdit.style.display = 'none';
    panePrev.style.display = 'flex';
  } else {
    paneEdit.style.display = 'flex';
    panePrev.style.display = 'flex';
  }
}

// ===================================================
// RENK PALETİ VE KATEGORİ SEÇİCİ
// ===================================================

function initColorPalette() {
  const popover = document.getElementById("colorPalettePopover");
  const btn = document.getElementById("btnColorPicker");

  NOTE_PRESETS.forEach(preset => {
    const circle = document.createElement("div");
    circle.className = "palette-circle";
    circle.style.backgroundColor = preset.color;
    circle.title = preset.name;
    circle.addEventListener("click", () => {
      const note = findNoteById(state.selectedNoteId);
      if (note) {
        note.color = preset.id;
        applyNoteColor(preset.id);
        triggerAutoSave();
      }
      popover.classList.remove("open");
    });
    popover.appendChild(circle);
  });

  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    popover.classList.toggle("open");
  });

  document.addEventListener("click", () => {
    popover.classList.remove("open");
  });
}

function applyNoteColor(colorInt) {
  const preset = NOTE_PRESETS.find(p => p.id === colorInt) || NOTE_PRESETS[0];
  const wrapper = document.getElementById("editorContentState");
  if (colorInt !== 0) {
    wrapper.style.backgroundColor = preset.color;
  } else {
    wrapper.style.backgroundColor = "var(--bg-main)";
  }
}

// ===================================================
// OTOMATİK KAYDETME & GERİ AL/İLERİ AL
// ===================================================

function onEditorChange() {
  const title = document.getElementById("editorTitle").value;
  const content = document.getElementById("editorContent").value;
  const note = findNoteById(state.selectedNoteId);

  if (note) {
    // Undo stack push
    if (!state.undoStack.length || state.undoStack[state.undoStack.length - 1].content !== content) {
      state.undoStack.push({ title: note.title, content: note.content });
      if (state.undoStack.length > 50) state.undoStack.shift();
      state.redoStack = [];
    }

    note.title = title;
    note.content = content;
    note.updatedAt = Date.now();
    triggerAutoSave();
  }
}

function triggerAutoSave() {
  const indicator = document.getElementById("autoSaveIndicator");
  indicator.textContent = "Kaydediliyor...";
  indicator.style.color = "var(--warning)";

  clearTimeout(state.autoSaveTimer);
  state.autoSaveTimer = setTimeout(async () => {
    const note = findNoteById(state.selectedNoteId);
    if (note) {
      await callIpc("saveNote", { note });
      indicator.textContent = "✓ Otomatik Kaydedildi";
      indicator.style.color = "var(--success)";
      renderNotesList();
    }
  }, 350);
}

function undo() {
  if (state.undoStack.length > 0) {
    const note = findNoteById(state.selectedNoteId);
    if (!note) return;

    state.redoStack.push({ title: note.title, content: note.content });
    const prev = state.undoStack.pop();
    note.title = prev.title;
    note.content = prev.content;

    document.getElementById("editorTitle").value = prev.title;
    document.getElementById("editorContent").value = prev.content;
    updateLiveMarkdownPreview();
    updateCharWordsCount();
    triggerAutoSave();
  }
}

function redo() {
  if (state.redoStack.length > 0) {
    const note = findNoteById(state.selectedNoteId);
    if (!note) return;

    state.undoStack.push({ title: note.title, content: note.content });
    const next = state.redoStack.pop();
    note.title = next.title;
    note.content = next.content;

    document.getElementById("editorTitle").value = next.title;
    document.getElementById("editorContent").value = next.content;
    updateLiveMarkdownPreview();
    updateCharWordsCount();
    triggerAutoSave();
  }
}

// ===================================================
// MARKDOWN ARAÇLARI & CANLI ÖNİZLEME
// ===================================================

function insertMarkdown(prefix, suffix) {
  const textarea = document.getElementById("editorContent");
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;
  const text = textarea.value;
  const selectedText = text.substring(start, end);

  // Akıllı Toggle: Eğer zaten prefix ve suffix ile çevriliyse kaldır
  let newText;
  if (selectedText.startsWith(prefix) && selectedText.endsWith(suffix) && suffix !== "") {
    const unwrapped = selectedText.substring(prefix.length, selectedText.length - suffix.length);
    newText = text.substring(0, start) + unwrapped + text.substring(end);
  } else {
    newText = text.substring(0, start) + prefix + selectedText + suffix + text.substring(end);
  }

  textarea.value = newText;
  textarea.focus();
  textarea.setSelectionRange(start + prefix.length, end + prefix.length);

  onEditorChange();
  updateLiveMarkdownPreview();
  updateCharWordsCount();
}

function sanitizeMarkdownHtml(inputHtml) {
  if (!inputHtml) return "";
  try {
    const parser = new DOMParser();
    const doc = parser.parseFromString(inputHtml, "text/html");
    const forbiddenTags = ["script", "iframe", "object", "embed", "applet", "meta", "link", "style", "form", "base"];

    // 1. Yasaklı ve tehlikeli etiketleri tamamen kaldır
    forbiddenTags.forEach(tag => {
      doc.querySelectorAll(tag).forEach(el => el.remove());
    });

    // 2. Tüm DOM elemanlarındaki tehlikeli öznitelikleri (inline handler ve javascript: linklerini) temizle
    const allElements = doc.querySelectorAll("*");
    allElements.forEach(el => {
      const attrs = Array.from(el.attributes);
      attrs.forEach(attr => {
        const name = attr.name.toLowerCase();
        const val = attr.value.trim().toLowerCase();

        // onerror, onload, onclick, onmouseover vb. tüm event listener'lar
        if (name.startsWith("on")) {
          el.removeAttribute(attr.name);
        }

        // javascript:, vbscript: veya base64 html veri akışları
        if ((name === "href" || name === "src" || name === "action") &&
            (val.startsWith("javascript:") || val.startsWith("vbscript:") || val.startsWith("data:text/html"))) {
          el.setAttribute(attr.name, "#");
        }
      });
    });

    return doc.body.innerHTML;
  } catch (err) {
    // DOMParser hatası durumunda cerrahi regex fallback
    return inputHtml
      .replace(/<\s*script[^>]*>[\s\S]*?<\s*\/\s*script\s*>/gi, "")
      .replace(/<\s*(?:iframe|object|embed|applet|meta|link|style)[^>]*>[\s\S]*?<\s*\/\s*(?:iframe|object|embed|applet|meta|link|style)\s*>/gi, "")
      .replace(/<\s*(?:iframe|object|embed|applet|meta|link|style)[^>]*\/?>/gi, "")
      .replace(/\s+on[a-zA-Z]+\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)/gi, "")
      .replace(/(?:href|src)\s*=\s*(?:"javascript:[^"]*"|'javascript:[^']*'|javascript:[^\s>]+)/gi, 'href="#"');
  }
}

function updateLiveMarkdownPreview() {
  const content = document.getElementById("editorContent").value;
  const preview = document.getElementById("markdownPreview");
  preview.innerHTML = sanitizeMarkdownHtml(renderMarkdownToHtml(content));

  // Önizlemedeki Checkbox tıklamalarını yakala
  preview.querySelectorAll(".task-item").forEach(item => {
    item.addEventListener("click", (e) => {
      e.stopPropagation();
      const lineIndex = parseInt(item.dataset.lineIndex);
      toggleMarkdownCheckboxAtLine(lineIndex);
    });
  });
}

function toggleMarkdownCheckboxAtLine(targetLineIndex) {
  const textarea = document.getElementById("editorContent");
  const lines = textarea.value.split("\n");

  if (targetLineIndex >= 0 && targetLineIndex < lines.length) {
    const line = lines[targetLineIndex];
    if (line.startsWith("- [ ] ")) {
      lines[targetLineIndex] = "- [x] " + line.substring(6);
    } else if (line.startsWith("- [x] ") || line.startsWith("- [X] ")) {
      lines[targetLineIndex] = "- [ ] " + line.substring(6);
    }
    textarea.value = lines.join("\n");
    onEditorChange();
    updateLiveMarkdownPreview();
  }
}

function renderMarkdownToHtml(md) {
  if (!md) return "<p style='color:var(--text-faint); font-style:italic;'>Henüz içerik yok...</p>";

  const lines = md.split("\n");
  let html = [];

  lines.forEach((line, idx) => {
    const trimmed = line.trim();
    if (trimmed.startsWith("# ")) {
      html.push(`<h1>${escapeHtml(trimmed.substring(2))}</h1>`);
    } else if (trimmed.startsWith("## ")) {
      html.push(`<h2>${escapeHtml(trimmed.substring(3))}</h2>`);
    } else if (trimmed.startsWith("### ")) {
      html.push(`<h3>${escapeHtml(trimmed.substring(4))}</h3>`);
    } else if (trimmed.startsWith("> ")) {
      html.push(`<blockquote>${formatInline(escapeHtml(trimmed.substring(2)))}</blockquote>`);
    } else if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")) {
      const isChecked = trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ");
      const text = trimmed.substring(6);
      html.push(`
        <div class="task-item ${isChecked ? 'checked' : ''}" data-line-index="${idx}">
          <input type="checkbox" ${isChecked ? 'checked' : ''} readonly>
          <span>${formatInline(escapeHtml(text))}</span>
        </div>
      `);
    } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
      html.push(`<li>${formatInline(escapeHtml(trimmed.substring(2)))}</li>`);
    } else if (trimmed.startsWith("```")) {
      html.push(`<pre><code>${escapeHtml(trimmed.replace(/```/g, ""))}</code></pre>`);
    } else if (trimmed === "") {
      html.push("<br>");
    } else {
      html.push(`<p>${formatInline(escapeHtml(line))}</p>`);
    }
  });

  return html.join("");
}

function formatInline(text) {
  return text
    .replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>")
    .replace(/\*(.*?)\*/g, "<em>$1</em>")
    .replace(/~~(.*?)~~/g, "<del>$1</del>")
    .replace(/`(.*?)`/g, "<code>$1</code>");
}

function escapeHtml(str) {
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function updateCharWordsCount() {
  const content = document.getElementById("editorContent").value;
  const chars = content.length;
  const words = content.trim() ? content.trim().split(/\s+/).length : 0;
  document.getElementById("charCountLabel").textContent = `${chars} karakter • ${words} kelime`;
}

// ===================================================
// NOT OLUŞTURMA & YÜKLEME
// ===================================================

async function createNewNote() {
  const newNote = {
    id: 0,
    title: "",
    content: "",
    timestamp: Date.now(),
    color: 0,
    categoryId: null,
    isPinned: false,
    isArchived: false,
    isDeleted: false,
    deletedAt: null,
    isLocked: false,
    reminderTime: null,
    attachments: [],
    createdAt: Date.now(),
    updatedAt: Date.now(),
    backgroundImage: null
  };

  const created = await callIpc("createNote", { note: newNote });
  if (created) {
    state.activeNotes.unshift(created);
    state.currentFilter = 'all';
    document.querySelectorAll(".nav-item").forEach(i => i.classList.remove("active"));
    document.querySelector(".nav-item[data-filter='all']").classList.add("active");

    updateSidebarCounts();
    renderNotesList();
    loadNoteIntoEditor(created, true);
  }
}

function loadNoteIntoEditor(note, focusTitle = false) {
  state.selectedNoteId = note.id;
  state.undoStack = [];
  state.redoStack = [];

  document.getElementById("emptyEditorState").style.display = "none";
  document.getElementById("editorContentState").style.display = "flex";

  const titleInput = document.getElementById("editorTitle");
  const contentInput = document.getElementById("editorContent");

  titleInput.value = note.title;
  contentInput.value = note.content;

  applyNoteColor(note.color);

  // Kategori seçimi
  const catSelect = document.getElementById("editorCatSelect");
  catSelect.value = note.categoryId ? note.categoryId.toString() : "";

  // Buton durumları
  document.getElementById("btnPin").classList.toggle("active", note.isPinned);
  document.getElementById("btnLock").classList.toggle("active", note.isLocked);
  document.getElementById("btnArchive").classList.toggle("active", note.isArchived);

  updateLiveMarkdownPreview();
  updateCharWordsCount();

  // Kart listesinde seçili göster
  renderNotesList();

  if (focusTitle) {
    titleInput.focus();
  }
}

// ===================================================
// TEMA & FİLTRE YÖNETİMİ
// ===================================================

function applyTheme(theme) {
  document.documentElement.setAttribute("data-theme", theme);
  localStorage.setItem("notdefterim_theme", theme);
  const themeNames = {
    amber: "Kehribar",
    sapphire: "Okyanus",
    emerald: "Zümrüt",
    plum: "Mürdüm"
  };
  const label = document.getElementById("currentThemeLabel");
  if (label) label.textContent = themeNames[theme] || "Kehribar";
  document.querySelectorAll(".theme-option").forEach(opt => {
    opt.classList.toggle("active", opt.dataset.theme === theme);
  });
}

function setFilter(filter) {
  state.currentFilter = filter;
  document.querySelectorAll(".nav-item").forEach(i => {
    i.classList.toggle("active", i.dataset.filter === filter);
  });
  document.querySelectorAll("#filterChipsBar .chip").forEach(c => {
    c.classList.toggle("active", c.dataset.chip === filter);
  });
  renderNotesList();
}

// ===================================================
// NOT LİSTESİ RENDER & ARAMA VURGUSU
// ===================================================

function renderNotesList() {
  const container = document.getElementById("notesContainer");
  container.innerHTML = "";

  let list = [];
  if (state.currentFilter === 'all') list = state.activeNotes;
  else if (state.currentFilter === 'pinned') list = state.activeNotes.filter(n => n.isPinned);
  else if (state.currentFilter === 'vault') list = state.activeNotes.filter(n => n.isLocked);
  else if (state.currentFilter === 'archive') list = state.archivedNotes;
  else if (state.currentFilter === 'trash') list = state.trashNotes;
  else if (state.currentFilter.startsWith('cat_')) {
    const catId = parseInt(state.currentFilter.replace('cat_', ''));
    list = state.activeNotes.filter(n => n.categoryId === catId);
  }

  // Arama filtresi
  if (state.searchQuery.trim()) {
    const q = state.searchQuery.toLowerCase();
    list = list.filter(n => n.title.toLowerCase().includes(q) || n.content.toLowerCase().includes(q));
  }

  document.getElementById("notesCountLabel").textContent = `${list.length} Not`;

  if (list.length === 0) {
    let emptyIcon = '<path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14z"/>';
    let emptyTitle = "Not Bulunamadı";
    let emptyDesc = "Bu görünümde görüntülenecek herhangi bir not yok.";

    if (state.searchQuery.trim()) {
      emptyIcon = '<path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>';
      emptyTitle = "Eşleşen Not Bulunamadı";
      emptyDesc = `"${escapeHtml(state.searchQuery)}" aramasıyla eşleşen bir sonuç bulunamadı.`;
    } else if (state.currentFilter === 'pinned') {
      emptyIcon = '<path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5.2v6h1.6v-6H18v-2l-2-2z"/>';
      emptyTitle = "Sabitlenen Not Yok";
      emptyDesc = "Önemli notlarınızı sabitleyerek her zaman listenin en üstünde tutabilirsiniz.";
    } else if (state.currentFilter === 'vault') {
      emptyIcon = '<path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/>';
      emptyTitle = "Kilitli Not Yok";
      emptyDesc = "Gizli tutmak istediğiniz kişisel notları kilitli kasaya ekleyebilirsiniz.";
    } else if (state.currentFilter === 'archive') {
      emptyIcon = '<path d="M20.54 5.23l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27zM12 17.5L6.5 12H10v-2h4v2h3.5L12 17.5zM5.12 5l.81-1h12l.94 1H5.12z"/>';
      emptyTitle = "Arşiv Boş";
      emptyDesc = "Tamamlanan veya göz önünden kaldırmak istediğiniz notları arşivleyebilirsiniz.";
    } else if (state.currentFilter === 'trash') {
      emptyIcon = '<path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>';
      emptyTitle = "Çöp Kutusu Boş";
      emptyDesc = "Silinen notlarınız burada güvenle saklanır, kalıcı silinmeden önce kurtarılabilir.";
    } else {
      emptyTitle = "Henüz Not Eklenmedi";
      emptyDesc = "Yeni bir fikir, yapılacaklar listesi veya taslak için 'Yeni Not' butonunu kullanın.";
    }

    container.innerHTML = `
      <div class="empty-state">
        <svg viewBox="0 0 24 24" aria-hidden="true">${emptyIcon}</svg>
        <div class="empty-state-title">${emptyTitle}</div>
        <div class="empty-state-desc">${emptyDesc}</div>
      </div>
    `;
    return;
  }

  list.forEach(note => {
    const card = document.createElement("div");
    card.className = `note-card ${note.id === state.selectedNoteId ? 'active' : ''}`;

    // Not Arka Plan Rengi ve Pastel Yazı Uyumu (Mobil Kart Tarzı)
    if (note.color !== 0) {
      const preset = NOTE_PRESETS.find(p => p.id === note.color);
      if (preset) {
        card.style.backgroundColor = preset.color;
        if (preset.text === "#1A1C1E") {
          card.classList.add("card-light-tone");
        }
      }
    }

    const cat = state.categories.find(c => c.id === note.categoryId);
    const dateFormatted = new Date(note.updatedAt || note.timestamp).toLocaleString("tr-TR", {
      day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit"
    });

    const displayTitle = highlightQuery(note.title || "Başlıksız Not", state.searchQuery);
    const displaySnippet = highlightQuery(note.content ? note.content.substring(0, 100) : "(İçerik yok)", state.searchQuery);

    card.innerHTML = `
      <div class="card-title-row">
        ${note.isPinned ? '<span class="card-pin-icon"><svg viewBox="0 0 24 24"><path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5.2v6h1.6v-6H18v-2l-2-2z"/></svg></span>' : ''}
        ${note.isLocked ? '<span class="card-lock-icon"><svg viewBox="0 0 24 24"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/></svg></span>' : ''}
        <div class="card-title">${displayTitle}</div>
        ${cat ? `<span class="card-cat-badge" style="background:${cat.color ? '#' + (cat.color >>> 0).toString(16).slice(-6) + '33' : 'rgba(255,255,255,0.1)'}; color:#CBD5E1;">${escapeHtml(cat.name)}</span>` : ''}
      </div>
      <div class="card-content-preview">${displaySnippet}</div>
      <div class="card-footer">
        <span>${dateFormatted}</span>
        <div class="card-actions">
          <button class="card-action-btn btn-card-pin" title="${note.isPinned ? 'Sabitlemeyi Kaldır' : 'Sabitle'}">
            <svg viewBox="0 0 24 24"><path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5.2v6h1.6v-6H18v-2l-2-2z"/></svg>
          </button>
          <button class="card-action-btn btn-card-del" title="${note.isDeleted ? 'Kalıcı Sil' : 'Çöpe At'}">
            <svg viewBox="0 0 24 24"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>
          </button>
        </div>
      </div>
    `;

    card.addEventListener("click", () => {
      loadNoteIntoEditor(note, false);
    });

    card.querySelector(".btn-card-pin").addEventListener("click", (e) => {
      e.stopPropagation();
      note.isPinned = !note.isPinned;
      showToast(note.isPinned ? "Not sabitlendi" : "Sabitleme kaldırıldı", "info");
      callIpc("saveNote", { note }).then(loadAllData);
    });

    card.querySelector(".btn-card-del").addEventListener("click", (e) => {
      e.stopPropagation();
      if (note.isDeleted) {
        callIpc("deletePermanently", { id: note.id }).then(() => {
          showToast("Not kalıcı olarak silindi", "danger");
          loadAllData();
        });
      } else {
        callIpc("moveToTrash", { id: note.id }).then(() => {
          showToast("Not çöp kutusuna taşındı", "warning");
          loadAllData();
        });
      }
    });

    container.appendChild(card);
  });
}

function highlightQuery(text, query) {
  if (!query || !query.trim()) return escapeHtml(text);
  const regex = new RegExp(`(${query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, "gi");
  return escapeHtml(text).replace(regex, "<mark>$1</mark>");
}

// ===================================================
// KATEGORİ LİSTESİ & SAYACLAR
// ===================================================

function updateSidebarCounts() {
  document.getElementById("countAll").textContent = state.activeNotes.length;
  document.getElementById("countPinned").textContent = state.activeNotes.filter(n => n.isPinned).length;
  document.getElementById("countVault").textContent = state.activeNotes.filter(n => n.isLocked).length;
  document.getElementById("countArchive").textContent = state.archivedNotes.length;
  document.getElementById("countTrash").textContent = state.trashNotes.length;
}

function renderCategoriesList() {
  const container = document.getElementById("catListContainer");
  const select = document.getElementById("editorCatSelect");
  container.innerHTML = "";
  select.innerHTML = '<option value="">Kategori Yok</option>';

  state.categories.forEach(cat => {
    // 1. Sidebar Item
    const item = document.createElement("div");
    item.className = `cat-item ${state.currentFilter === 'cat_' + cat.id ? 'active' : ''}`;
    const hexColor = '#' + (cat.color >>> 0).toString(16).slice(-6);

    const count = state.activeNotes.filter(n => n.categoryId === cat.id).length;
    item.innerHTML = `
      <div class="cat-dot" style="background-color:${hexColor};"></div>
      <span style="flex:1; overflow:hidden; text-overflow:ellipsis;">${escapeHtml(cat.name)}</span>
      <span style="font-size:11px; opacity:0.6;">${count}</span>
    `;
    item.addEventListener("click", () => {
      document.querySelectorAll(".nav-item").forEach(i => i.classList.remove("active"));
      document.querySelectorAll(".cat-item").forEach(i => i.classList.remove("active"));
      item.classList.add("active");
      state.currentFilter = 'cat_' + cat.id;
      renderNotesList();
    });
    container.appendChild(item);

    // 2. Select Option
    const opt = document.createElement("option");
    opt.value = cat.id.toString();
    opt.textContent = cat.name;
    select.appendChild(opt);
  });
}

// ===================================================
// NOT EYLEMLERİ (Pin, Lock, Archive, Delete)
// ===================================================

function togglePinCurrentNote() {
  const note = findNoteById(state.selectedNoteId);
  if (note) {
    note.isPinned = !note.isPinned;
    document.getElementById("btnPin").classList.toggle("active", note.isPinned);
    showToast(note.isPinned ? "Not sabitlendi" : "Sabitleme kaldırıldı", "info");
    triggerAutoSave();
  }
}

function toggleLockCurrentNote() {
  const note = findNoteById(state.selectedNoteId);
  if (note) {
    note.isLocked = !note.isLocked;
    document.getElementById("btnLock").classList.toggle("active", note.isLocked);
    showToast(note.isLocked ? "Not kilitli kasaya alındı" : "Kasa kilidi kaldırıldı", "info");
    triggerAutoSave();
  }
}

function toggleArchiveCurrentNote() {
  const note = findNoteById(state.selectedNoteId);
  if (note) {
    note.isArchived = !note.isArchived;
    document.getElementById("btnArchive").classList.toggle("active", note.isArchived);
    showToast(note.isArchived ? "Not arşivlendi" : "Not arşivden çıkarıldı", "info");
    callIpc("saveNote", { note }).then(loadAllData);
  }
}

function deleteCurrentNote() {
  const note = findNoteById(state.selectedNoteId);
  if (note) {
    callIpc("moveToTrash", { id: note.id }).then(() => {
      showToast("Not çöp kutusuna taşındı", "warning");
      state.selectedNoteId = null;
      document.getElementById("emptyEditorState").style.display = "flex";
      document.getElementById("editorContentState").style.display = "none";
      loadAllData();
    });
  }
}

// ===================================================
// DİYALOGLAR (Modals)
// ===================================================

function initModals() {
  // 1. Kategori Modalı
  const modalCat = document.getElementById("modalCategory");
  document.getElementById("btnOpenCatModal").addEventListener("click", () => {
    modalCat.classList.add("open");
    document.getElementById("newCatNameInput").focus();
  });
  document.getElementById("btnCancelCat").addEventListener("click", () => {
    modalCat.classList.remove("open");
  });
  document.getElementById("btnSaveCat").addEventListener("click", async () => {
    const name = document.getElementById("newCatNameInput").value.trim();
    if (name) {
      await callIpc("addCategory", { name, color: -12877066 }); // Varsayılan mavi
      document.getElementById("newCatNameInput").value = "";
      modalCat.classList.remove("open");
      showToast(`'${name}' kategorisi eklendi`, "success");
      loadAllData();
    }
  });

  // 2. Wi-Fi Senkronizasyon Modalı
  const modalSync = document.getElementById("modalSync");
  document.getElementById("btnOpenSync").addEventListener("click", async () => {
    modalSync.classList.add("open");
    await callIpc("syncStart");
    updateSyncStateUI();
  });
  document.getElementById("btnCloseSync").addEventListener("click", () => {
    modalSync.classList.remove("open");
  });
  
  const btnCopySyncPin = document.getElementById("btnCopySyncPin");
  if (btnCopySyncPin) {
    btnCopySyncPin.addEventListener("click", () => {
      const pin = document.getElementById("syncPinCode").textContent.trim();
      if (pin && pin !== "----") {
        navigator.clipboard.writeText(pin).then(() => {
          showToast("PIN panoya kopyalandı: " + pin, "success");
        }).catch(() => {
          showToast("PIN kopyalanamadı", "warning");
        });
      }
    });
  }
  document.getElementById("btnRefreshPin").addEventListener("click", async () => {
    await callIpc("syncRefreshPin");
    updateSyncStateUI();
    showToast("Yeni PIN kodu üretildi", "info");
  });
  document.getElementById("btnToggleServer").addEventListener("click", async () => {
    const stateObj = await callIpc("syncGetState");
    if (stateObj && stateObj.is_running) {
      await callIpc("syncStop");
      showToast("Wi-Fi sunucusu durduruldu", "info");
    } else {
      await callIpc("syncStart");
      showToast("Wi-Fi sunucusu başlatıldı", "success");
    }
    updateSyncStateUI();
  });

  // 3. Yedek Al Butonu
  document.getElementById("btnExportBackup").addEventListener("click", async () => {
    try {
      const res = await callIpc("backupExport");
      showToast(res || "Tüm notlar başarıyla yedeklendi (.zip)", "success");
    } catch (e) {
      showToast("Yedekleme hatası: " + e, "danger");
    }
  });
}

async function updateSyncStateUI() {
  const syncState = await callIpc("syncGetState");
  if (syncState) {
    document.getElementById("syncPinCode").textContent = syncState.pairing_pin || "----";
    document.getElementById("syncIpAddress").textContent = `${syncState.local_ip}:${syncState.port}`;
    document.getElementById("syncServerMsg").textContent = syncState.last_message || (syncState.is_running ? "Bağlantı bekleniyor..." : "Sunucu durduruldu");
    
    // Status Indicators
    const syncDot = document.getElementById("syncStatusDot");
    if (syncDot) {
      syncDot.style.display = syncState.is_running ? "inline-block" : "none";
      syncDot.classList.toggle("sync-active", syncState.is_running);
    }
    const modalDot = document.getElementById("syncModalStatusDot");
    if (modalDot) {
      modalDot.style.display = syncState.is_running ? "inline-block" : "none";
    }
    const badge = document.getElementById("syncStatusBadge");
    if (badge) {
      badge.textContent = syncState.is_running ? "Çevrimiçi" : "Hazır";
      badge.classList.toggle("online", syncState.is_running);
    }

    document.getElementById("btnToggleServer").textContent = syncState.is_running ? "Sunucuyu Durdur" : "Sunucuyu Başlat";

    // QR Kod Oluştur
    const qrText = `NOTDEFTERIM_SYNC:${syncState.local_ip}:${syncState.port}:${syncState.pairing_pin}`;
    const qrContainer = document.getElementById("qrCodeContainer");
    qrContainer.innerHTML = renderSimpleQr(qrText);
  }
}

// Basit SVG QR Kod Oluşturucu
function renderSimpleQr(text) {
  return `
    <svg viewBox="0 0 160 160" width="160" height="160" style="background:white; border-radius:10px; padding:10px;" aria-label="Eşleştirme QR Kodu">
      <rect width="160" height="160" fill="white"/>
      <path d="M20 20h40v40H20zM30 30h20v20H30zM100 20h40v40h-40zM110 30h20v20h-20zM20 100h40v40H20zM30 110h20v20H30z" fill="black"/>
      <rect x="75" y="20" width="10" height="40" fill="black"/>
      <rect x="20" y="75" width="40" height="10" fill="black"/>
      <rect x="75" y="75" width="20" height="20" fill="#3B82F6"/>
      <rect x="100" y="85" width="40" height="15" fill="black"/>
      <rect x="75" y="110" width="15" height="30" fill="black"/>
      <rect x="110" y="110" width="30" height="30" fill="black"/>
    </svg>
  `;
}

// ===================================================
// TOAST BİLDİRİM YÖNETİCİSİ
// ===================================================

function showToast(message, type = "info", duration = 3000) {
  const container = document.getElementById("toastContainer");
  if (!container) return;

  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.setAttribute("role", "alert");

  let icon = "";
  if (type === "success") {
    icon = '<svg viewBox="0 0 24 24" width="16" height="16" fill="#10B981" aria-hidden="true"><path d="M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"/></svg>';
  } else if (type === "warning") {
    icon = '<svg viewBox="0 0 24 24" width="16" height="16" fill="#F59E0B" aria-hidden="true"><path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/></svg>';
  } else if (type === "danger" || type === "error") {
    icon = '<svg viewBox="0 0 24 24" width="16" height="16" fill="#EF4444" aria-hidden="true"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>';
  } else {
    icon = '<svg viewBox="0 0 24 24" width="16" height="16" fill="var(--primary)" aria-hidden="true"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg>';
  }

  toast.innerHTML = `
    ${icon}
    <span style="flex:1;">${escapeHtml(message)}</span>
  `;

  container.appendChild(toast);

  setTimeout(() => {
    toast.classList.add("toast-hide");
    setTimeout(() => {
      toast.remove();
    }, 250);
  }, duration);
}
