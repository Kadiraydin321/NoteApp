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

// Uygulama Durumu (Reaktif İstemci State)
let state = {
  activeNotes: [],
  archivedNotes: [],
  trashNotes: [],
  categories: [],
  currentFilter: 'all', // 'all', 'pinned', 'reminders', 'vault', 'archive', 'trash', 'cat_ID', 'tag_TAG'
  selectedNav: 'all',
  selectedCategoryId: null,
  selectedTag: null,
  searchQuery: '',
  sortOrder: 'updated-desc', // 'updated-desc', 'updated-asc', 'created-desc', 'created-asc', 'title-asc', 'title-desc'
  selectedNoteId: null,
  selectedNoteIds: new Set(),
  batchModeActive: false,
  layoutMode: 'grid', // 'list' veya 'grid'
  markdownMode: 'edit', // 'edit', 'split', 'preview'
  antiSsEnabled: true,
  isVaultUnlocked: false,
  vaultHasPassword: true,
  undoStack: [],
  redoStack: [],
  noteVersions: new Map(), // noteId -> Array<{ timestamp, title, content }>
  autoSaveTimer: null,
  autoSavePending: false,
  isDirty: false
};

// ===================================================
// WRY NATIVE IPC KÖPRÜSÜ & FETCH FALLBACK
// ===================================================

const pendingRequests = new Map();
let requestCounter = 0;
const eventListeners = new Map();

// Rust Backend Action -> API Komutu Eşleme Tablosu
const ACTION_MAP = {
  "db:getNotes": "getNotes",
  "db:createNote": "createNote",
  "db:saveNote": "saveNote",
  "db:moveToTrash": "moveToTrash",
  "db:restoreFromTrash": "restoreFromTrash",
  "db:deletePermanently": "deletePermanently",
  "db:emptyTrash": "emptyTrash",
  "db:getCategories": "getCategories",
  "db:addCategory": "addCategory",
  "db:saveCategory": "addCategory",
  "db:deleteCategory": "deleteCategory",
  "vault:getStatus": "vaultStatus",
  "vault:setup": "vaultSetup",
  "vault:unlock": "vaultUnlock",
  "vault:lock": "vaultLock",
  "sync:getState": "syncGetState",
  "sync:start": "syncStart",
  "sync:stop": "syncStop",
  "sync:refreshPin": "syncRefreshPin",
  "backup:export": "backupExport",
  "backup:import": "backupImport",
  "window:setAntiScreenshot": "setAntiScreenshot"
};

// Rust -> Frontend Yanıt Çözücü (evaluate_script ile tetiklenir)
window.__IPC_RESOLVE__ = function(response) {
  try {
    const res = typeof response === "string" ? JSON.parse(response) : response;
    const { requestId, success, data, error } = res || {};
    if (requestId && pendingRequests.has(requestId)) {
      const { resolve, reject, timeoutId } = pendingRequests.get(requestId);
      if (timeoutId) clearTimeout(timeoutId);
      pendingRequests.delete(requestId);
      if (success !== false && !error) {
        resolve(data !== undefined ? (data.result !== undefined ? data.result : data) : data);
      } else {
        const errMsg = error?.message || (typeof error === "string" ? error : "IPC İşlem Hatası");
        reject(new Error(errMsg));
      }
    }
  } catch (err) {
    console.error("[__IPC_RESOLVE__ Hatası]:", err);
  }
};

// Rust -> Frontend Asenkron Push Olayları (sync:statusChanged, vault:locked vb.)
window.__IPC_EMIT__ = function(eventPayload) {
  try {
    const payload = typeof eventPayload === "string" ? JSON.parse(eventPayload) : eventPayload;
    const { event, data } = payload || {};
    if (event && eventListeners.has(event)) {
      eventListeners.get(event).forEach(fn => {
        try { fn(data); } catch (e) { console.error(`[__IPC_EMIT__ Dinleyici Hatası (${event})]:`, e); }
      });
    }
  } catch (err) {
    console.error("[__IPC_EMIT__ Hatası]:", err);
  }
};

function onNativeEvent(event, callback) {
  if (!eventListeners.has(event)) {
    eventListeners.set(event, new Set());
  }
  eventListeners.get(event).add(callback);
  return () => eventListeners.get(event)?.delete(callback);
}

// Birleşik IPC Çağrı Fonksiyonu
async function callIpc(actionOrCmd, payload = {}) {
  const reqId = `req_${Date.now()}_${++requestCounter}`;
  const mappedCmd = ACTION_MAP[actionOrCmd] || actionOrCmd.replace(/^[a-z]+:/i, "");
  const envelope = {
    action: actionOrCmd,
    cmd: mappedCmd,
    payload,
    requestId: reqId
  };

  // 1. Wry Native IPC (window.ipc.postMessage)
  if (window.ipc && typeof window.ipc.postMessage === "function") {
    return new Promise((resolve, reject) => {
      const timeoutId = setTimeout(() => {
        pendingRequests.delete(reqId);
        // Zaman aşımında fetch fallback'e geç
        fallbackFetch(mappedCmd, payload, actionOrCmd).then(resolve).catch(reject);
      }, 1500);

      pendingRequests.set(reqId, { resolve, reject, timeoutId });

      try {
        window.ipc.postMessage(JSON.stringify(envelope));
      } catch (e) {
        clearTimeout(timeoutId);
        pendingRequests.delete(reqId);
        fallbackFetch(mappedCmd, payload, actionOrCmd).then(resolve).catch(reject);
      }
    });
  }

  // 2. HTTP Fallback (/api)
  return fallbackFetch(mappedCmd, payload, actionOrCmd);
}

async function fallbackFetch(cmd, payload = {}, action = "") {
  try {
    const res = await fetch("/api", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ cmd, action, payload })
    });
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    }
    const data = await res.json();
    if (data.error) {
      throw new Error(data.error);
    }
    return data.result !== undefined ? data.result : data;
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
  initGlobalShortcuts();
  checkStickyModeOnStartup();
  loadAllData();

  // Rust Backend Push Event Dinleyicileri
  onNativeEvent("db:changed", () => {
    loadAllData();
  });

  onNativeEvent("vault:locked", () => {
    state.isVaultUnlocked = false;
    showToast("Kasa kilitlendi", "info");
    loadAllData();
  });
});

function initGlobalShortcuts() {
  window.addEventListener("keydown", (e) => {
    // Ctrl+K veya Ctrl+P: Evrensel Komut Paleti
    if ((e.ctrlKey || e.metaKey) && (e.key.toLowerCase() === 'k' || e.key.toLowerCase() === 'p')) {
      e.preventDefault();
      openCommandPalette();
    }
    // Ctrl+G: İnteraktif İlişki Grafiği
    else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'g') {
      e.preventDefault();
      openGraphModal();
    }
  });
}

function checkStickyModeOnStartup() {
  const params = new URLSearchParams(window.location.search);
  if (params.get("mode") === "sticky") {
    document.body.classList.add("sticky-mode");
    const noteId = parseInt(params.get("id"));
    if (noteId) {
      state.selectedNoteId = noteId;
    }
  }
}

async function loadAllData() {
  try {
    const data = await callIpc("db:getNotes");
    if (data) {
      state.activeNotes = data.active || [];
      state.archivedNotes = data.archived || [];
      state.trashNotes = data.trash || [];
      state.categories = data.categories || [];
      state.isVaultUnlocked = data.isVaultUnlocked || false;

      updateSidebarCounts();
      renderCategoriesList();
      extractTagsAndRender();
      renderNotesList();

      if (state.selectedNoteId) {
        const found = findNoteById(state.selectedNoteId);
        if (found) {
          loadNoteIntoEditor(found, false);
        } else {
          closeEditor({ skipSave: true });
        }
      }
    }
  } catch (err) {
    console.error("Veri yüklenemedi:", err);
  }
}

function findNoteById(id) {
  if (id === undefined || id === null) return null;
  return state.activeNotes.find(n => String(n.id) === String(id)) ||
         state.archivedNotes.find(n => String(n.id) === String(id)) ||
         state.trashNotes.find(n => String(n.id) === String(id)) ||
         null;
}

function getActiveOrSelectedNote() {
  let note = null;
  if (state.selectedNoteId) {
    note = findNoteById(state.selectedNoteId);
  }
  if (!note && state.activeNotes && state.activeNotes.length > 0) {
    const edTitle = document.getElementById("editorTitle")?.value;
    if (edTitle) {
      note = state.activeNotes.find(n => n.title === edTitle);
    }
    if (!note) {
      note = state.activeNotes[0];
    }
    if (note) {
      state.selectedNoteId = note.id;
    }
  }
  return note;
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
  document.getElementById("btnNewNoteBoard").addEventListener("click", createNewNote);

  // Kenar çubuğu daralt/genişlet (Keep tarzı), tercih hatırlanır
  const applySidebarState = (collapsed) => {
    document.body.classList.toggle("sidebar-collapsed", collapsed);
    document.getElementById("btnToggleSidebar")?.setAttribute("aria-expanded", String(!collapsed));
  };
  applySidebarState(localStorage.getItem("notdefterim_sidebar") === "collapsed");
  document.getElementById("btnToggleSidebar")?.addEventListener("click", () => {
    const collapsed = !document.body.classList.contains("sidebar-collapsed");
    applySidebarState(collapsed);
    localStorage.setItem("notdefterim_sidebar", collapsed ? "collapsed" : "open");
  });
  document.getElementById("sidebarScrim")?.addEventListener("click", () => applySidebarState(true));

  // Editör penceresi: kapat düğmesi, arka plan ve Esc
  document.getElementById("btnCloseEditor")?.addEventListener("click", () => closeEditor());
  document.getElementById("editorBackdrop")?.addEventListener("click", () => closeEditor());
  document.addEventListener("keydown", (e) => {
    if (e.key !== "Escape" || !document.body.classList.contains("editor-open")) return;
    if (document.body.classList.contains("sticky-mode")) return;
    if (document.querySelector(".modal-overlay.open")) return;
    if (document.getElementById("editorMoreMenu")?.style.display === "block") return;
    if (document.getElementById("colorPalettePopover")?.classList.contains("open")) return;
    if (document.querySelector(".command-palette-overlay, .palette-overlay")) return;
    closeEditor();
  });

  // 2. Sidebar Filtreleri & Mobil Çipler
  document.querySelectorAll(".nav-item").forEach(item => {
    item.addEventListener("click", () => {
      if (!item.dataset.filter) return;
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
  btnGrid.classList.add("active");
  btnList.classList.remove("active");
  document.getElementById("notesContainer").className = "notes-container layout-grid";
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
  // Varsayılan: yalnızca yazma modu (diyalog daha ferah olur)
  setMarkdownMode('edit', btnEdit, [btnSplit, btnPrev], paneEdit, panePrev);

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

  // 10.1 Sıralama Menüsü Açılır/Kapanır
  const btnSort = document.getElementById("btnSortMenu");
  const sortMenu = document.getElementById("sortDropdownMenu");
  if (btnSort && sortMenu) {
    btnSort.addEventListener("click", (e) => {
      e.stopPropagation();
      const isOpen = sortMenu.style.display === "block";
      sortMenu.style.display = isOpen ? "none" : "block";
      btnSort.setAttribute("aria-expanded", !isOpen);
    });
    document.addEventListener("click", () => {
      sortMenu.style.display = "none";
      btnSort.setAttribute("aria-expanded", "false");
    });
    document.querySelectorAll(".sort-item").forEach(item => {
      item.addEventListener("click", () => {
        document.querySelectorAll(".sort-item").forEach(i => i.classList.remove("active"));
        item.classList.add("active");
        state.sortOrder = item.dataset.sort;
        renderNotesList();
        sortMenu.style.display = "none";
        btnSort.setAttribute("aria-expanded", "false");
      });
    });
  }

  // 10.2 Çoklu Not Seçimi (Batch Action Mode)
  initBatchMode();

  // 10.3 3 Nokta Not Eylemleri Menüsü
  const btnMore = document.getElementById("btnEditorMoreActions");
  const moreMenu = document.getElementById("editorMoreMenu");
  if (btnMore && moreMenu) {
    const closeMoreMenu = () => {
      moreMenu.style.display = "none";
      btnMore.setAttribute("aria-expanded", "false");
    };

    btnMore.addEventListener("click", (e) => {
      e.stopPropagation();
      const isOpen = moreMenu.style.display === "block";
      moreMenu.style.display = isOpen ? "none" : "block";
      btnMore.setAttribute("aria-expanded", !isOpen);
    });

    // Menü içine tıklandığında dışa tıklama yöneticisinin menüyü zamansız kapatmasını engelle
    moreMenu.addEventListener("click", (e) => {
      e.stopPropagation();
    });

    // Dışarı tıklanınca kapat
    document.addEventListener("click", () => {
      closeMoreMenu();
    });

    // Güvenli Buton Bağlayıcı (Tıklama Garantili ve Menüyü Kapatan)
    const bindMenuBtn = (elementId, handler) => {
      const el = document.getElementById(elementId);
      if (el) {
        el.addEventListener("click", (e) => {
          e.preventDefault();
          e.stopPropagation();
          closeMoreMenu();
          handler(e);
        });
      }
    };

    // Tüm Menü Butonları (Tek tek eksiksiz bağlandı)
    bindMenuBtn("btnMenuShare", () => openShareModal());
    bindMenuBtn("btnMenuPrint", () => window.print());
    bindMenuBtn("btnMenuGraphThis", () => openGraphModal());
    bindMenuBtn("btnMenuGraph", () => openGraphModal());
    bindMenuBtn("btnMenuOpticalStream", () => startOpticalStream());
    bindMenuBtn("btnMenuAdvanced", () => openAdvancedToolsModal());
    bindMenuBtn("btnMenuReminder", () => openReminderModal());
    bindMenuBtn("btnMenuDuplicate", () => duplicateCurrentNote());
    bindMenuBtn("btnMenuSaveTemplate", () => saveAsTemplate());
    bindMenuBtn("btnMenuHistory", () => openVersionHistoryModal());
    bindMenuBtn("btnMenuStats", () => openStatsModal());
    bindMenuBtn("btnMenuExportVault", () => exportCurrentNoteToVaultFile());
    bindMenuBtn("btnMenuSticky", () => openStickyNote());
  }

  // Doğrudan Araç Çubuğu Butonları (İlişki Grafiği, Yapışkan Not, Optik Akış, Komut Paleti)
  document.getElementById("btnOpenGraph")?.addEventListener("click", openGraphModal);
  document.getElementById("btnOpenSticky")?.addEventListener("click", openStickyNote);
  document.getElementById("btnOpenOpticalStream")?.addEventListener("click", () => startOpticalStream());
  document.getElementById("btnOpenCommandPalette")?.addEventListener("click", openCommandPalette);

  // Hatırlatıcı Butonu (Editör Çubuğu)
  document.getElementById("btnEditorReminder")?.addEventListener("click", () => {
    openReminderModal();
  });

  // 11. Modallar
  initModals();

  // Etiket Yönetimi ve Bağlam Menüsü Başlatıcı
  document.getElementById("btnManageTags")?.addEventListener("click", openTagManagerModal);
  document.getElementById("btnCloseTagManager")?.addEventListener("click", () => {
    document.getElementById("modalTagManager")?.classList.remove("open");
  });
  initContextMenuEvents();

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

// ===================================================
// TOPLU NOT İŞLEMLERİ (BATCH ACTION MODE)
// ===================================================

function initBatchMode() {
  const btnToggle = document.getElementById("btnToggleBatchMode");
  const bar = document.getElementById("batchActionBar");
  const btnCancel = document.getElementById("btnBatchCancel");
  const btnSelectAll = document.getElementById("btnBatchSelectAll");
  const btnPin = document.getElementById("btnBatchPin");
  const btnVault = document.getElementById("btnBatchVault");
  const btnArchive = document.getElementById("btnBatchArchive");
  const btnTrash = document.getElementById("btnBatchTrash");
  const btnDeletePerm = document.getElementById("btnBatchDeletePermanent");

  if (!btnToggle || !bar) return;

  btnToggle.addEventListener("click", () => {
    state.batchModeActive = !state.batchModeActive;
    if (!state.batchModeActive) {
      state.selectedNoteIds.clear();
    }
    btnToggle.classList.toggle("active", state.batchModeActive);
    bar.style.display = state.batchModeActive ? "flex" : "none";
    updateBatchActionBarUI();
    renderNotesList();
  });

  btnCancel?.addEventListener("click", () => {
    state.batchModeActive = false;
    state.selectedNoteIds.clear();
    btnToggle.classList.remove("active");
    bar.style.display = "none";
    renderNotesList();
  });

  btnSelectAll?.addEventListener("click", () => {
    const visibleNotes = getVisibleNotesList();
    if (state.selectedNoteIds.size === visibleNotes.length) {
      state.selectedNoteIds.clear();
    } else {
      visibleNotes.forEach(n => state.selectedNoteIds.add(n.id));
    }
    updateBatchActionBarUI();
    renderNotesList();
  });

  btnPin?.addEventListener("click", async () => {
    if (state.selectedNoteIds.size === 0) return;
    for (const id of state.selectedNoteIds) {
      const note = findNoteById(id);
      if (note) {
        note.isPinned = !note.isPinned;
        await callIpc("db:saveNote", { note });
      }
    }
    showToast(`${state.selectedNoteIds.size} not güncellendi`, "success");
    loadAllData();
  });

  btnVault?.addEventListener("click", async () => {
    if (state.selectedNoteIds.size === 0) return;
    for (const id of state.selectedNoteIds) {
      const note = findNoteById(id);
      if (note) {
        note.isLocked = true;
        await callIpc("db:saveNote", { note });
      }
    }
    showToast(`${state.selectedNoteIds.size} not kilitli kasaya taşındı`, "info");
    loadAllData();
  });

  btnArchive?.addEventListener("click", async () => {
    if (state.selectedNoteIds.size === 0) return;
    for (const id of state.selectedNoteIds) {
      const note = findNoteById(id);
      if (note) {
        note.isArchived = !note.isArchived;
        await callIpc("db:saveNote", { note });
      }
    }
    showToast(`${state.selectedNoteIds.size} not arşivlendi`, "info");
    loadAllData();
  });

  btnTrash?.addEventListener("click", async () => {
    if (state.selectedNoteIds.size === 0) return;
    for (const id of state.selectedNoteIds) {
      await callIpc("db:moveToTrash", { id });
    }
    showToast(`${state.selectedNoteIds.size} not çöp kutusuna taşındı`, "warning");
    state.selectedNoteIds.clear();
    loadAllData();
  });

  btnDeletePerm?.addEventListener("click", async () => {
    if (state.selectedNoteIds.size === 0) return;
    for (const id of state.selectedNoteIds) {
      await callIpc("db:deletePermanently", { id });
    }
    showToast(`${state.selectedNoteIds.size} not kalıcı olarak silindi`, "danger");
    state.selectedNoteIds.clear();
    loadAllData();
  });
}

function getVisibleNotesList() {
  if (state.currentFilter === 'all') return state.activeNotes;
  if (state.currentFilter === 'pinned') return state.activeNotes.filter(n => n.isPinned);
  if (state.currentFilter === 'reminders') return state.activeNotes.filter(n => n.reminderDate && n.reminderDate > 0);
  if (state.currentFilter === 'vault') return state.activeNotes.filter(n => n.isLocked);
  if (state.currentFilter === 'archive') return state.archivedNotes;
  if (state.currentFilter === 'trash') return state.trashNotes;
  return state.activeNotes;
}

function updateBatchActionBarUI() {
  const badge = document.getElementById("batchCountBadge");
  const btnDeletePerm = document.getElementById("btnBatchDeletePermanent");
  const btnTrash = document.getElementById("btnBatchTrash");

  if (badge) badge.textContent = `${state.selectedNoteIds.size} Seçildi`;
  if (state.currentFilter === 'trash') {
    if (btnDeletePerm) btnDeletePerm.style.display = "inline-flex";
    if (btnTrash) btnTrash.style.display = "none";
  } else {
    if (btnDeletePerm) btnDeletePerm.style.display = "none";
    if (btnTrash) btnTrash.style.display = "inline-flex";
  }
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
    wrapper.style.setProperty("--editor-ink", preset.text);
    wrapper.classList.toggle("tone-light", preset.text === "#1A1C1E");
    wrapper.classList.add("has-color");
  } else {
    wrapper.style.backgroundColor = "";
    wrapper.style.removeProperty("--editor-ink");
    wrapper.classList.remove("tone-light", "has-color");
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
    // Undo stack kaydı
    if (!state.undoStack.length || state.undoStack[state.undoStack.length - 1].content !== content || state.undoStack[state.undoStack.length - 1].title !== title) {
      state.undoStack.push({ title: note.title, content: note.content });
      if (state.undoStack.length > 50) state.undoStack.shift();
      state.redoStack = [];
      updateUndoRedoButtons();
    }

    note.title = title;
    note.content = content;
    note.updatedAt = Date.now();
    state.isDirty = true;
    triggerAutoSave();
  }
}

function updateUndoRedoButtons() {
  const btnUndo = document.getElementById("btnUndo");
  const btnRedo = document.getElementById("btnRedo");
  if (btnUndo) {
    btnUndo.style.opacity = state.undoStack.length > 0 ? "1" : "0.4";
    btnUndo.style.pointerEvents = state.undoStack.length > 0 ? "auto" : "none";
  }
  if (btnRedo) {
    btnRedo.style.opacity = state.redoStack.length > 0 ? "1" : "0.4";
    btnRedo.style.pointerEvents = state.redoStack.length > 0 ? "auto" : "none";
  }
}

function triggerAutoSave() {
  const indicator = document.getElementById("autoSaveIndicator");
  if (indicator) {
    indicator.textContent = "Kaydediliyor...";
    indicator.style.color = "var(--warning)";
  }

  clearTimeout(state.autoSaveTimer);
  state.autoSavePending = true;
  state.autoSaveTimer = setTimeout(async () => {
    state.autoSavePending = false;
    const note = findNoteById(state.selectedNoteId);
    if (note) {
      try {
        await callIpc("db:saveNote", { note });
        state.isDirty = false;
        if (indicator) {
          indicator.textContent = "✓ Otomatik Kaydedildi";
          indicator.style.color = "var(--success)";
        }

        // Versiyon geçmişine anlık görüntü kaydet
        recordNoteVersion(note);

        // Sidebar etiketleri ve not kartı güncelle
        extractTagsAndRender();
        renderNotesList();
      } catch (err) {
        console.error("Otomatik kaydetme hatası:", err);
        if (indicator) {
          indicator.textContent = "Kaydetme Hatası!";
          indicator.style.color = "var(--danger)";
        }
      }
    }
  }, 500); // 500ms Debouncer
}

function recordNoteVersion(note) {
  if (!note || !note.id) return;
  if (!state.noteVersions.has(note.id)) {
    state.noteVersions.set(note.id, []);
  }
  const versions = state.noteVersions.get(note.id);
  const last = versions[versions.length - 1];

  // En az 30 saniye geçmişse veya ilk versiyonsa kaydet
  if (!last || (Date.now() - last.timestamp > 30000 && (last.content !== note.content || last.title !== note.title))) {
    versions.push({
      timestamp: Date.now(),
      title: note.title,
      content: note.content
    });
    if (versions.length > 20) versions.shift();
  }
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
    updateUndoRedoButtons();
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
    updateUndoRedoButtons();
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
  updateAudioPlayerForNote(note);
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

  // 1. Önizlemedeki Checkbox tıklamalarını yakala
  preview.querySelectorAll(".task-item").forEach(item => {
    item.addEventListener("click", (e) => {
      e.stopPropagation();
      const lineIndex = parseInt(item.dataset.lineIndex);
      toggleMarkdownCheckboxAtLine(lineIndex);
    });
  });

  // 2. Çift Yönlü Wikilink Tıklamalarını Yakala
  preview.querySelectorAll(".wikilink").forEach(link => {
    link.addEventListener("click", (e) => {
      e.preventDefault();
      e.stopPropagation();
      const targetTitle = link.dataset.target;
      if (targetTitle) {
        navigateToWikilink(targetTitle);
      }
    });
  });
}

function navigateToWikilink(targetTitle) {
  const normalized = targetTitle.trim().toLowerCase();
  const allNotes = [...state.activeNotes, ...state.archivedNotes];
  const found = allNotes.find(n => (n.title || "").trim().toLowerCase() === normalized);

  if (found) {
    loadNoteIntoEditor(found, false);
    showToast(`'${found.title || "Adsız"}' notuna geçildi`, "info");
  } else {
    // Not yoksa otomatik yeni not oluştur
    createNewNoteWithTitle(targetTitle.trim());
  }
}

async function createNewNoteWithTitle(initialTitle) {
  const newNote = {
    id: 0,
    title: initialTitle,
    content: `# ${initialTitle}\n\n`,
    timestamp: Date.now(),
    color: 0,
    categoryId: state.selectedCategoryId || null,
    isPinned: false,
    isArchived: false,
    isDeleted: false,
    deletedAt: null,
    isLocked: false
  };

  try {
    const created = await callIpc("db:createNote", { note: newNote });
    if (created && created.id) {
      newNote.id = created.id;
      state.activeNotes.unshift(newNote);
      renderNotesList();
      loadNoteIntoEditor(newNote, false);
      showToast(`'${initialTitle}' başlıklı yeni not oluşturuldu`, "success");
    }
  } catch (err) {
    console.error("Wikilink not oluşturma hatası:", err);
    showToast("Not oluşturulamadı", "danger");
  }
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
  let inCodeBlock = false;
  let codeBlockLang = "";
  let codeBlockBuffer = [];
  let inMathBlock = false;
  let mathBlockBuffer = [];

  for (let idx = 0; idx < lines.length; idx++) {
    const line = lines[idx];
    const trimmed = line.trim();

    // 1. Çok satırlı Matematik Blokları ($$)
    if (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 2) {
      const formula = trimmed.slice(2, -2).trim();
      html.push(`<div class="math-block" style="text-align:center; padding:14px; font-family:'Cambria Math','Latin Modern Math',serif; font-size:1.15em; background:rgba(255,255,255,0.03); border:1px solid var(--border-subtle); border-radius:8px; margin:10px 0; overflow-x:auto;">${escapeHtml(formula)}</div>`);
      continue;
    } else if (trimmed === "$$") {
      if (inMathBlock) {
        html.push(`<div class="math-block" style="text-align:center; padding:14px; font-family:'Cambria Math','Latin Modern Math',serif; font-size:1.15em; background:rgba(255,255,255,0.03); border:1px solid var(--border-subtle); border-radius:8px; margin:10px 0; overflow-x:auto;">${escapeHtml(mathBlockBuffer.join("\n"))}</div>`);
        mathBlockBuffer = [];
        inMathBlock = false;
      } else {
        inMathBlock = true;
      }
      continue;
    }

    if (inMathBlock) {
      mathBlockBuffer.push(line);
      continue;
    }

    // 2. Çok satırlı Kod & Mermaid Blokları (```)
    if (trimmed.startsWith("```")) {
      if (inCodeBlock) {
        const rawCode = codeBlockBuffer.join("\n");
        if (codeBlockLang === "mermaid") {
          html.push(renderMermaidDiagramSvg(rawCode));
        } else {
          html.push(`<pre class="code-block"><code class="lang-${codeBlockLang}">${escapeHtml(rawCode)}</code></pre>`);
        }
        codeBlockBuffer = [];
        codeBlockLang = "";
        inCodeBlock = false;
      } else {
        inCodeBlock = true;
        codeBlockLang = trimmed.substring(3).trim().toLowerCase();
      }
      continue;
    }

    if (inCodeBlock) {
      codeBlockBuffer.push(line);
      continue;
    }

    // 3. Başlıklar & Blok Elemanları
    if (trimmed.startsWith("# ")) {
      html.push(`<h1>${formatInline(escapeHtml(trimmed.substring(2)))}</h1>`);
    } else if (trimmed.startsWith("## ")) {
      html.push(`<h2>${formatInline(escapeHtml(trimmed.substring(3)))}</h2>`);
    } else if (trimmed.startsWith("### ")) {
      html.push(`<h3>${formatInline(escapeHtml(trimmed.substring(4)))}</h3>`);
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
    } else if (/^\d+\.\s+/.test(trimmed)) {
      const text = trimmed.replace(/^\d+\.\s+/, "");
      html.push(`<li style="list-style-type:decimal;">${formatInline(escapeHtml(text))}</li>`);
    } else if (trimmed === "---" || trimmed === "***") {
      html.push("<hr>");
    } else if (trimmed === "") {
      html.push("<br>");
    } else {
      html.push(`<p>${formatInline(escapeHtml(line))}</p>`);
    }
  }

  if (inCodeBlock && codeBlockBuffer.length > 0) {
    const rawCode = codeBlockBuffer.join("\n");
    if (codeBlockLang === "mermaid") {
      html.push(renderMermaidDiagramSvg(rawCode));
    } else {
      html.push(`<pre class="code-block"><code>${escapeHtml(rawCode)}</code></pre>`);
    }
  }
  if (inMathBlock && mathBlockBuffer.length > 0) {
    html.push(`<div class="math-block" style="text-align:center; padding:14px; font-family:'Cambria Math',serif;">${escapeHtml(mathBlockBuffer.join("\n"))}</div>`);
  }

  return html.join("");
}

// Mermaid SVG Diyagram Üretici (Flowchart / Sequence Parser Fallback)
function renderMermaidDiagramSvg(code) {
  const lines = code.split("\n").map(l => l.trim()).filter(l => l && !l.startsWith("%%"));
  const header = lines[0] || "graph TD";

  // Flowchart veya Graph parsing: A --> B, A[Metin] --> B[Metin]
  const edges = [];
  const nodesMap = new Map();

  lines.slice(1).forEach(l => {
    const edgeMatch = l.match(/([a-zA-Z0-9_-]+)(?:\[(.*?)\])?\s*(-->|---|==>|-\.->)\s*([a-zA-Z0-9_-]+)(?:\[(.*?)\])?/);
    if (edgeMatch) {
      const fromId = edgeMatch[1];
      const fromLabel = edgeMatch[2] || fromId;
      const type = edgeMatch[3];
      const toId = edgeMatch[4];
      const toLabel = edgeMatch[5] || toId;

      if (!nodesMap.has(fromId)) nodesMap.set(fromId, fromLabel);
      if (!nodesMap.has(toId)) nodesMap.set(toId, toLabel);
      edges.push({ from: fromId, to: toId, type });
    }
  });

  const nodesList = Array.from(nodesMap.entries());
  if (nodesList.length === 0) {
    // Basit kod görünümü ile şık mermaid rozeti
    return `
      <div class="mermaid-diagram-box" style="background:var(--bg-main); border:1px solid var(--border-subtle); border-radius:10px; padding:16px; margin:12px 0;">
        <div style="font-size:11px; font-weight:700; color:var(--primary); margin-bottom:8px; display:flex; align-items:center; gap:6px;">
          <span>📊 MERMAID DİYAGRAMI</span>
        </div>
        <pre style="margin:0; font-family:monospace; font-size:12px; color:var(--text-muted);">${escapeHtml(code)}</pre>
      </div>
    `;
  }

  // Dinamik SVG Çizimi (Yatay veya Dikey)
  const isLR = header.includes("LR") || header.includes("RL");
  const nodeW = 120;
  const nodeH = 38;
  const gap = 40;
  const totalW = isLR ? (nodesList.length * (nodeW + gap) + 40) : 480;
  const totalH = isLR ? 160 : (nodesList.length * (nodeH + gap) + 40);

  let svgElements = "";
  const positions = new Map();

  nodesList.forEach(([id, label], i) => {
    const x = isLR ? 30 + i * (nodeW + gap) : (totalW / 2 - nodeW / 2);
    const y = isLR ? (totalH / 2 - nodeH / 2) : (30 + i * (nodeH + gap));
    positions.set(id, { x, y, cx: x + nodeW / 2, cy: y + nodeH / 2 });

    svgElements += `
      <rect x="${x}" y="${y}" width="${nodeW}" height="${nodeH}" rx="8" fill="#1E293B" stroke="var(--primary)" stroke-width="1.5" />
      <text x="${x + nodeW / 2}" y="${y + nodeH / 2 + 5}" fill="#F8FAFC" font-size="12" font-family="'Segoe UI',sans-serif" font-weight="600" text-anchor="middle">${escapeHtml(label)}</text>
    `;
  });

  edges.forEach(({ from, to, type }) => {
    const p1 = positions.get(from);
    const p2 = positions.get(to);
    if (p1 && p2) {
      const isDashed = type.includes(".");
      svgElements += `
        <line x1="${p1.cx}" y1="${isLR ? p1.cy : p1.y + nodeH}" x2="${p2.cx}" y2="${isLR ? p2.cy : p2.y}" stroke="#94A3B8" stroke-width="2" ${isDashed ? 'stroke-dasharray="4 4"' : ''} marker-end="url(#arrow)" />
      `;
    }
  });

  return `
    <div class="mermaid-diagram-box" style="background:var(--bg-main); border:1px solid var(--border-subtle); border-radius:10px; padding:16px; margin:14px 0; overflow-x:auto; text-align:center;">
      <div style="font-size:11px; font-weight:700; color:var(--primary); margin-bottom:12px; display:flex; align-items:center; justify-content:center; gap:6px;">
        <span>📊 MERMAID DİYAGRAMI (${escapeHtml(header)})</span>
      </div>
      <svg viewBox="0 0 ${totalW} ${totalH}" width="100%" style="max-width:${totalW}px; height:auto; display:inline-block;">
        <defs>
          <marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#94A3B8" />
          </marker>
        </defs>
        ${svgElements}
      </svg>
    </div>
  `;
}

function formatInline(text) {
  return text
    // 1. Wikilinks: [[Hedef Not|Görünen İsim]] veya [[Hedef Not]]
    .replace(/\[\[([^\]\|]+)(?:\|([^\]]+))?\]\]/g, (match, target, alias) => {
      const label = alias || target;
      return `<a href="#" class="wikilink" data-target="${escapeHtml(target.trim())}" title="Not: ${escapeHtml(target.trim())}">🔗 ${escapeHtml(label.trim())}</a>`;
    })
    // 2. Satır İçi Matematik Formülleri ($math$)
    .replace(/\$([^\$\n]+)\$/g, (match, formula) => {
      return `<span class="math-inline" style="font-family:'Cambria Math','Latin Modern Math',serif; padding:1px 6px; background:rgba(255,255,255,0.06); border-radius:4px; font-style:italic;" title="Matematik Formülü">${escapeHtml(formula)}</span>`;
    })
    // 3. Kalın & İtalik & Üstü Çizili
    .replace(/\*\*\*(.*?)\*\*\*/g, "<strong><em>$1</em></strong>")
    .replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>")
    .replace(/\*(.*?)\*/g, "<em>$1</em>")
    .replace(/~~(.*?)~~/g, "<del>$1</del>")
    .replace(/`([^`]+)`/g, "<code>$1</code>");
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
  document.body.classList.add("editor-open");

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
  updateAudioPlayerForNote(note);

  // Kart listesinde seçili göster
  renderNotesList();

  if (focusTitle) {
    titleInput.focus();
  }
}

// Editör diyaloğunu kapatır; bekleyen değişiklik varsa önce kaydeder.
async function closeEditor(options = {}) {
  clearTimeout(state.autoSaveTimer);
  const note = findNoteById(state.selectedNoteId);
  const needsSave = !options.skipSave && note && (state.autoSavePending || state.isDirty);
  state.autoSavePending = false;
  state.selectedNoteId = null;
  document.body.classList.remove("editor-open");
  document.getElementById("colorPalettePopover")?.classList.remove("open");
  const moreMenu = document.getElementById("editorMoreMenu");
  if (moreMenu) moreMenu.style.display = "none";
  if (needsSave) {
    try {
      await callIpc("db:saveNote", { note });
      state.isDirty = false;
      recordNoteVersion(note);
    } catch (err) {
      console.error("Kapatırken kaydetme hatası:", err);
    }
    extractTagsAndRender();
  }
  renderNotesList();
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
  state.selectedNav = filter;

  if (!filter.startsWith('cat_')) {
    state.selectedCategoryId = null;
    document.querySelectorAll(".cat-item").forEach(i => i.classList.remove("active"));
  }
  if (!filter.startsWith('tag_')) {
    state.selectedTag = null;
  }

  document.querySelectorAll(".nav-item").forEach(i => {
    i.classList.toggle("active", i.dataset.filter === filter);
  });
  document.querySelectorAll("#filterChipsBar .chip").forEach(c => {
    c.classList.toggle("active", c.dataset.chip === filter);
  });

  renderNotesList();
}

// ===================================================
// NOT SIRALAMA & DİNAMİK ETİKETLER
// ===================================================

function sortNotes(list) {
  const sort = state.sortOrder || "updated-desc";
  return [...list].sort((a, b) => {
    const timeAUpdated = a.updatedAt || a.timestamp || 0;
    const timeBUpdated = b.updatedAt || b.timestamp || 0;
    const timeACreated = a.createdAt || a.timestamp || 0;
    const timeBCreated = b.createdAt || b.timestamp || 0;
    const titleA = (a.title || "").toLowerCase();
    const titleB = (b.title || "").toLowerCase();

    switch (sort) {
      case "updated-asc":
        return timeAUpdated - timeBUpdated;
      case "created-desc":
        return timeBCreated - timeACreated;
      case "created-asc":
        return timeACreated - timeBCreated;
      case "title-asc":
        return titleA.localeCompare(titleB, "tr");
      case "title-desc":
        return titleB.localeCompare(titleA, "tr");
      case "updated-desc":
      default:
        return timeBUpdated - timeAUpdated;
    }
  });
}

function extractTagsAndRender() {
  const tagContainer = document.getElementById("tagListContainer");
  if (!tagContainer) return;
  tagContainer.innerHTML = "";

  const tagCounts = new Map();
  const allNotes = [...state.activeNotes, ...state.archivedNotes];

  allNotes.forEach(note => {
    const text = `${note.title || ""} ${note.content || ""}`;
    const matches = text.match(/#([a-zA-ZçğıöşüÇĞİÖŞÜ0-9_-]+)/g);
    if (matches) {
      const uniqueInNote = new Set(matches.map(m => m.toLowerCase()));
      uniqueInNote.forEach(tag => {
        tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1);
      });
    }
  });

  if (tagCounts.size === 0) {
    tagContainer.innerHTML = `<span style="font-size:11px; color:var(--text-faint); padding:2px 4px;">Etiket yok (#etiket yazın)</span>`;
    return;
  }

  tagCounts.forEach((count, tag) => {
    const btn = document.createElement("button");
    const isActive = state.selectedTag === tag;
    btn.className = `tag-chip ${isActive ? 'active' : ''}`;
    btn.textContent = `${tag} (${count})`;
    btn.title = `${tag} etiketine göre filtrele`;
    btn.addEventListener("click", () => {
      if (state.selectedTag === tag) {
        state.selectedTag = null;
        state.currentFilter = 'all';
      } else {
        state.selectedTag = tag;
        state.currentFilter = `tag_${tag}`;
      }
      renderNotesList();
      extractTagsAndRender();
    });
    tagContainer.appendChild(btn);
  });
}

// ===================================================
// NOT LİSTESİ RENDER & ARAMA VURGUSU
// ===================================================

function renderNotesList() {
  const container = document.getElementById("notesContainer");
  container.innerHTML = "";

  let list = [];
  if (state.currentFilter === 'all') {
    list = state.activeNotes;
  } else if (state.currentFilter === 'pinned') {
    list = state.activeNotes.filter(n => n.isPinned);
  } else if (state.currentFilter === 'reminders') {
    list = state.activeNotes.filter(n => n.reminderDate && n.reminderDate > 0);
  } else if (state.currentFilter === 'vault') {
    list = state.activeNotes.filter(n => n.isLocked);
  } else if (state.currentFilter === 'archive') {
    list = state.archivedNotes;
  } else if (state.currentFilter === 'trash') {
    list = state.trashNotes;
  } else if (state.currentFilter.startsWith('cat_')) {
    const catId = parseInt(state.currentFilter.replace('cat_', ''));
    list = state.activeNotes.filter(n => n.categoryId === catId);
  } else if (state.currentFilter.startsWith('tag_')) {
    const tag = state.selectedTag || state.currentFilter.replace('tag_', '');
    list = state.activeNotes.filter(n => {
      const text = `${n.title || ""} ${n.content || ""}`.toLowerCase();
      return text.includes(tag.toLowerCase());
    });
  }

  // Arama filtresi
  if (state.searchQuery.trim()) {
    const q = state.searchQuery.toLowerCase();
    list = list.filter(n => (n.title || "").toLowerCase().includes(q) || (n.content || "").toLowerCase().includes(q));
  }

  // Sıralama uygula
  list = sortNotes(list);

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

  const ICON_PIN = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5.2v6h1.6v-6H18v-2l-2-2z"/></svg>';
  const ICON_LOCK = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/></svg>';
  const ICON_BELL = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"/></svg>';
  const ICON_TRASH = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>';
  const ICON_ARCHIVE = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.54 5.23l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27zM12 17.5L6.5 12H10v-2h4v2h3.5L12 17.5zM5.12 5l.81-1h12l.94 1H5.12z"/></svg>';

  const buildCard = (note) => {
    const card = document.createElement("article");
    const isSelected = note.id === state.selectedNoteId;
    const isChecked = state.selectedNoteIds.has(note.id);
    card.className = "note-card";
    if (isSelected) card.classList.add("active");
    if (isChecked) card.classList.add("batch-selected");
    if (note.isPinned) card.classList.add("is-pinned");
    if (note.isLocked) card.classList.add("is-locked");
    card.dataset.noteId = String(note.id);
    card.dataset.color = String(note.color || 0);
    card.tabIndex = 0;

    if (note.color !== 0) {
      const preset = NOTE_PRESETS.find(p => p.id === note.color);
      if (preset) {
        card.style.backgroundColor = preset.color;
        card.style.setProperty("--card-ink", preset.text || "inherit");
        if (preset.text === "#1A1C1E") {
          card.classList.add("card-light-tone");
        }
      }
    }

    const cat = state.categories.find(c => c.id === note.categoryId);
    const updated = new Date(note.updatedAt || note.timestamp);
    const dateFormatted = updated.toLocaleDateString("tr-TR", { day: "numeric", month: "short", year: updated.getFullYear() === new Date().getFullYear() ? undefined : "numeric" });

    const displayTitle = highlightQuery(note.title || "", state.searchQuery);
    const hasReminder = note.reminderDate && note.reminderDate > 0;
    const reminderFormatted = hasReminder ? new Date(note.reminderDate).toLocaleDateString("tr-TR", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }) : "";

    const rawContent = note.content || "";
    const checkLines = !note.isLocked
      ? rawContent.split("\n").filter(line => /^\s*(?:[-*]\s+)?\[[ xX]\]/.test(line))
      : [];
    const checklistTotal = checkLines.length;
    const checklistCompleted = checkLines.filter(line => /\[[xX]\]/.test(line)).length;

    let bodyHtml = "";
    if (checklistTotal > 0) {
      const shown = checkLines.slice(0, 6).map(line => {
        const done = /\[[xX]\]/.test(line);
        const text = line.replace(/^\s*(?:[-*]\s+)?\[[ xX]\]\s*/, "");
        return `<li class="card-check${done ? " done" : ""}"><span class="card-check-box" aria-hidden="true"></span><span class="card-check-text">${highlightQuery(text, state.searchQuery)}</span></li>`;
      }).join("");
      const more = checklistTotal > 6 ? `<li class="card-check-more">+${checklistTotal - 6} öğe daha</li>` : "";
      bodyHtml = `<ul class="card-checklist" aria-label="Kontrol listesi">${shown}${more}</ul>`;
    } else {
      const plain = rawContent.replace(/^#{1,6}\s+/gm, "").trim();
      const snippet = plain ? plain.substring(0, 320) : (displayTitle ? "" : "(İçerik yok)");
      bodyHtml = snippet ? `<div class="card-content-preview">${highlightQuery(snippet, state.searchQuery)}</div>` : "";
    }

    const badges = [];
    if (cat) {
      const hex = cat.color ? "#" + (cat.color >>> 0).toString(16).slice(-6) : "#94A3B8";
      badges.push(`<span class="card-cat-badge"><i style="background:${hex};"></i>${escapeHtml(cat.name)}</span>`);
    }
    if (hasReminder) badges.push(`<span class="card-meta-badge" title="Hatırlatıcı: ${reminderFormatted}">${ICON_BELL}${reminderFormatted}</span>`);
    if (note.isLocked) badges.push(`<span class="card-meta-badge" title="Kilitli kasa">${ICON_LOCK}Kasa</span>`);
    if (checklistTotal > 0) badges.push(`<span class="card-meta-badge${checklistCompleted === checklistTotal ? " is-done" : ""}">${checklistCompleted}/${checklistTotal}</span>`);

    const archiveTitle = note.isArchived ? "Arşivden çıkar" : "Arşivle";
    card.innerHTML = `
      ${state.batchModeActive ? `<input type="checkbox" class="batch-checkbox" data-note-id="${note.id}" ${isChecked ? "checked" : ""} aria-label="Notu seç">` : ""}
      <button class="card-pin-toggle btn-card-pin" type="button" title="${note.isPinned ? "Sabitlemeyi kaldır" : "Sabitle"}" aria-label="${note.isPinned ? "Sabitlemeyi kaldır" : "Sabitle"}">${ICON_PIN}</button>
      ${displayTitle ? `<h3 class="card-title">${displayTitle}</h3>` : ""}
      ${bodyHtml}
      ${badges.length ? `<div class="card-badges">${badges.join("")}</div>` : ""}
      <div class="card-footer">
        <span class="card-date">${dateFormatted}</span>
        <div class="card-actions">
          ${note.isDeleted ? "" : `<button class="card-action-btn btn-card-archive" type="button" title="${archiveTitle}" aria-label="${archiveTitle}">${ICON_ARCHIVE}</button>`}
          <button class="card-action-btn btn-card-del" type="button" title="${note.isDeleted ? "Kalıcı sil" : "Çöpe at"}" aria-label="${note.isDeleted ? "Kalıcı sil" : "Çöpe at"}">${ICON_TRASH}</button>
        </div>
      </div>
    `;

    const activate = () => {
      closeNoteContextMenu();
      if (state.batchModeActive) {
        if (state.selectedNoteIds.has(note.id)) {
          state.selectedNoteIds.delete(note.id);
        } else {
          state.selectedNoteIds.add(note.id);
        }
        updateBatchActionBarUI();
        renderNotesList();
        return;
      }
      loadNoteIntoEditor(note, false);
    };
    card.addEventListener("click", activate);
    card.addEventListener("keydown", (e) => {
      if ((e.key === "Enter" || e.key === " ") && e.target === card) {
        e.preventDefault();
        activate();
      }
    });

    card.addEventListener("contextmenu", (e) => {
      e.preventDefault();
      e.stopPropagation();
      openNoteContextMenu(e, note);
    });

    card.querySelector(".btn-card-pin").addEventListener("click", (e) => {
      e.stopPropagation();
      note.isPinned = !note.isPinned;
      showToast(note.isPinned ? "Not sabitlendi" : "Sabitleme kaldırıldı", "info");
      callIpc("saveNote", { note }).then(loadAllData);
    });

    card.querySelector(".btn-card-archive")?.addEventListener("click", (e) => {
      e.stopPropagation();
      note.isArchived = !note.isArchived;
      showToast(note.isArchived ? "Not arşivlendi" : "Not arşivden çıkarıldı", "info");
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

    return card;
  };

  // Sabitlenenler ayrı bir bölüm olarak gösterilir (Keep tarzı)
  const groupable = !["archive", "trash"].includes(state.currentFilter);
  const pinned = groupable ? list.filter(n => n.isPinned) : [];
  const others = groupable ? list.filter(n => !n.isPinned) : list;

  const appendSection = (label, notes) => {
    if (label) {
      const heading = document.createElement("div");
      heading.className = "board-section-label";
      heading.textContent = label;
      container.appendChild(heading);
    }
    notes.forEach((note, index) => {
      const card = buildCard(note);
      card.style.setProperty("--enter-delay", `${Math.min(index, 12) * 28}ms`);
      container.appendChild(card);
    });
  };

  if (pinned.length > 0) {
    appendSection("Sabitlenenler", pinned);
    if (others.length > 0) appendSection("Diğerleri", others);
  } else {
    appendSection("", others);
  }
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
  const elAll = document.getElementById("countAll");
  const elPinned = document.getElementById("countPinned");
  const elVault = document.getElementById("countVault");
  const elReminders = document.getElementById("countReminders");
  const elArchive = document.getElementById("countArchive");
  const elTrash = document.getElementById("countTrash");

  if (elAll) elAll.textContent = state.activeNotes.length;
  if (elPinned) elPinned.textContent = state.activeNotes.filter(n => n.isPinned).length;
  if (elVault) elVault.textContent = state.activeNotes.filter(n => n.isLocked).length;
  if (elReminders) elReminders.textContent = state.activeNotes.filter(n => n.reminderDate && n.reminderDate > 0).length;
  if (elArchive) elArchive.textContent = state.archivedNotes.length;
  if (elTrash) elTrash.textContent = state.trashNotes.length;
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
    callIpc("db:saveNote", { note }).then(loadAllData);
  }
}

function deleteCurrentNote() {
  const note = findNoteById(state.selectedNoteId);
  if (note) {
    callIpc("db:moveToTrash", { id: note.id }).then(() => {
      showToast("Not çöp kutusuna taşındı", "warning");
      closeEditor({ skipSave: true });
      loadAllData();
    });
  }
}

// ===================================================
// DİYALOGLAR (Modals)
// ===================================================

function initModals() {
  // 1. Kasa Modalı (#modalVault)
  const modalVault = document.getElementById("modalVault");
  const vaultTitle = document.getElementById("vaultModalTitle");
  const vaultDesc = document.getElementById("vaultModalDesc");
  const vaultInput = document.getElementById("vaultPasswordInput");
  const btnConfirmVault = document.getElementById("btnConfirmVault");
  const btnCancelVault = document.getElementById("btnCancelVault");

  async function checkAndOpenVaultModal() {
    try {
      const status = await callIpc("vault:getStatus");
      state.vaultHasPassword = status?.hasPassword ?? status?.has_password ?? false;
      state.isVaultUnlocked = status?.isUnlocked ?? status?.is_unlocked ?? false;

      if (state.isVaultUnlocked) {
        showToast("Kasa zaten açık. Kilitlemek için Editördeki Kasa ikonuna tıklayın.", "info");
        state.currentFilter = 'vault';
        renderNotesList();
        return;
      }

      if (!state.vaultHasPassword) {
        vaultTitle.textContent = "Kasa Parolası Oluştur";
        vaultDesc.textContent = "Kilitli notlarınızı şifrelemek için güçlü bir ana parola belirleyin.";
        btnConfirmVault.textContent = "Parolayı Kaydet";
      } else {
        vaultTitle.textContent = "Kasa Kilidini Aç";
        vaultDesc.textContent = "Kilitli notları görüntülemek ve düzenlemek için kasa şifrenizi girin.";
        btnConfirmVault.textContent = "Kilidi Aç";
      }

      vaultInput.value = "";
      modalVault.classList.add("open");
      vaultInput.focus();
    } catch (err) {
      console.error("Kasa durum kontrolü hatası:", err);
      modalVault.classList.add("open");
    }
  }

  btnCancelVault?.addEventListener("click", () => {
    modalVault.classList.remove("open");
    vaultInput.value = "";
  });

  async function submitVaultPassword() {
    const password = vaultInput.value.trim();
    if (!password) {
      showToast("Lütfen parola girin", "warning");
      return;
    }

    try {
      if (!state.vaultHasPassword) {
        const ok = await callIpc("vault:setup", { password });
        if (ok) {
          state.vaultHasPassword = true;
          state.isVaultUnlocked = true;
          modalVault.classList.remove("open");
          showToast("Kasa parolası başarıyla oluşturuldu!", "success");
          loadAllData();
        } else {
          showToast("Kasa parolası kaydedilemedi", "danger");
        }
      } else {
        const ok = await callIpc("vault:unlock", { password });
        if (ok) {
          state.isVaultUnlocked = true;
          modalVault.classList.remove("open");
          showToast("Kasa kilidi açıldı!", "success");
          loadAllData();
        } else {
          showToast("Hatalı kasa parolası!", "danger");
        }
      }
    } catch (err) {
      showToast("Kasa işlemi hatası: " + (err.message || err), "danger");
    }
  }

  btnConfirmVault?.addEventListener("click", submitVaultPassword);
  vaultInput?.addEventListener("keydown", (e) => {
    if (e.key === "Enter") submitVaultPassword();
  });

  // Sidebar Kilitli Kasa öğesine tıklanınca kilitliyse modal aç
  document.querySelectorAll("[data-filter='vault'], [data-chip='vault']").forEach(el => {
    el.addEventListener("click", () => {
      if (!state.isVaultUnlocked) {
        checkAndOpenVaultModal();
      }
    });
  });

  // 2. Kategori Modalı (#modalCategory)
  const modalCat = document.getElementById("modalCategory");
  const catColorContainer = document.getElementById("catColorOptions");
  let selectedCatColor = -12877066; // Varsayılan Mavi

  if (catColorContainer) {
    catColorContainer.innerHTML = "";
    NOTE_PRESETS.slice(0, 8).forEach(preset => {
      const circle = document.createElement("div");
      circle.className = `palette-circle ${preset.id === selectedCatColor ? 'selected' : ''}`;
      circle.style.backgroundColor = preset.color;
      circle.style.cursor = "pointer";
      circle.style.width = "24px";
      circle.style.height = "24px";
      circle.title = preset.name;
      circle.addEventListener("click", () => {
        catColorContainer.querySelectorAll(".palette-circle").forEach(c => c.classList.remove("selected"));
        circle.classList.add("selected");
        selectedCatColor = preset.id;
      });
      catColorContainer.appendChild(circle);
    });
  }

  document.getElementById("btnOpenCatModal")?.addEventListener("click", () => {
    modalCat.classList.add("open");
    document.getElementById("newCatNameInput").value = "";
    document.getElementById("newCatNameInput").focus();
  });

  document.getElementById("btnCancelCat")?.addEventListener("click", () => {
    modalCat.classList.remove("open");
  });

  document.getElementById("btnSaveCat")?.addEventListener("click", async () => {
    const name = document.getElementById("newCatNameInput").value.trim();
    if (name) {
      try {
        await callIpc("db:addCategory", { name, color: selectedCatColor });
        document.getElementById("newCatNameInput").value = "";
        modalCat.classList.remove("open");
        showToast(`'${name}' kategorisi eklendi`, "success");
        loadAllData();
      } catch (err) {
        showToast("Kategori eklenemedi: " + err, "danger");
      }
    } else {
      showToast("Lütfen bir kategori adı girin", "warning");
    }
  });

  // 3. Wi-Fi Senkronizasyon Modalı (#modalSync)
  const modalSync = document.getElementById("modalSync");
  const modalSyncReview = document.getElementById("modalSyncReview");
  // Modal açılınca masaüstü sunucusu başlatılır ve QR üretimi için durum izleme açılır.
  document.getElementById("btnOpenSync")?.addEventListener("click", async () => {
    modalSync.classList.add("open");
    setSyncStatus("Android uygulamasında HTTPS sunucusunu başlatın.");
    setDesktopQrPlaceholder("QR kodu hazırlanıyor…");
    desktopPairingQr = null;
    const qrContainer = document.getElementById("qrCodeContainer");
    if (qrContainer) delete qrContainer.dataset.payload;
    await ensureDesktopSyncServer();
    if (modalSync.classList.contains("open")) startSyncPolling();
  });

  const closeSyncModal = () => {
    modalSync.classList.remove("open");
    stopSyncPolling();
    stopSyncQrCamera();
    pendingAndroidSyncQr = null;
    document.getElementById("syncQrManualInput").value = "";
    document.getElementById("syncPairingDetails").hidden = true;
    modalSyncReview.classList.remove("open");
    modalSyncReview.hidden = true;
    // Modalın kendi başlattığı ve kimsenin bağlanmadığı sunucu açık bırakılmaz
    if (syncModalStartedServer && !(syncLastState?.connected_clients > 0)) {
      syncModalStartedServer = false;
      desktopPairingQr = null;
      callIpc("sync:stop").catch(() => {}).finally(updateSyncStateUI);
    }
  };

  document.getElementById("btnCloseSync")?.addEventListener("click", closeSyncModal);
  document.getElementById("btnToggleServer")?.addEventListener("click", toggleDesktopSyncServer);
  document.getElementById("btnRefreshQr")?.addEventListener("click", () => {
    desktopPairingQr = null;
    const qrContainer = document.getElementById("qrCodeContainer");
    if (qrContainer) delete qrContainer.dataset.payload;
    updateSyncStateUI();
  });
  document.getElementById("btnCopyPin")?.addEventListener("click", () => {
    const pin = document.getElementById("syncPinCode")?.textContent || "";
    if (/^\d+$/.test(pin) && navigator.clipboard) {
      navigator.clipboard.writeText(pin).then(() => showToast("PIN kopyalandı", "success")).catch(() => {});
    }
  });

  modalSync?.addEventListener("click", (e) => {
    if (e.target === modalSync) closeSyncModal();
  });

  document.getElementById("btnStartSyncCamera")?.addEventListener("click", startSyncQrCamera);
  document.getElementById("btnValidateSyncQr")?.addEventListener("click", () => {
    try {
      acceptAndroidSyncQr(document.getElementById("syncQrManualInput").value);
    } catch (error) {
      clearAndroidSyncPairing();
      setSyncStatus(error.message);
    }
  });

  document.getElementById("btnReceiveFromAndroid")?.addEventListener("click", () => {
    openSyncTransferReview("receive");
  });

  document.getElementById("btnSendToAndroid")?.addEventListener("click", () => {
    openSyncTransferReview("send");
  });

  document.getElementById("btnCancelSyncReview")?.addEventListener("click", () => {
    modalSyncReview.classList.remove("open");
    modalSyncReview.hidden = true;
  });

  modalSyncReview?.addEventListener("click", (e) => {
    if (e.target === modalSyncReview) {
      modalSyncReview.classList.remove("open");
      modalSyncReview.hidden = true;
    }
  });

  document.getElementById("btnCloseSyncSuccess")?.addEventListener("click", () => {
    document.getElementById("modalSyncSuccess").classList.remove("open");
    document.getElementById("modalSyncSuccess").hidden = true;
  });

  // 4. Yedekleme & Kurtarma Butonu
  document.getElementById("btnExportBackup")?.addEventListener("click", async () => {
    try {
      const res = await callIpc("backup:export");
      const msg = typeof res === "string" ? res : (res?.filePath ? `Yedek alındı: ${res.filePath}` : "Tüm notlar başarıyla yedeklendi (.zip)");
      showToast(msg, "success");
    } catch (e) {
      showToast("Yedekleme hatası: " + e, "danger");
    }
  });
}

// ===================================================
// DİNAMİK POP-UP & MODAL YÖNETİCİSİ (Paylaşım, İstatistik, Tarihçe)
// ===================================================

function openShareModal() {
  const note = getActiveOrSelectedNote();
  if (!note) {
    showToast("Paylaşılacak bir not bulunamadı!", "warning");
    return;
  }

  const existing = document.getElementById("dynamicModalContainer");
  if (existing) existing.remove();

  const container = document.createElement("div");
  container.id = "dynamicModalContainer";
  container.className = "modal-overlay open";
  container.setAttribute("role", "dialog");
  container.setAttribute("aria-modal", "true");

  container.innerHTML = `
    <div class="modal-dialog" style="max-width:540px;">
      <div class="modal-title">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z"/></svg>
        <span>Notu Paylaş & Dışa Aktar</span>
      </div>
      <div class="modal-desc">
        '<b>${escapeHtml(note.title || "Başlıksız Not")}</b>' notunuzu istediğiniz formatta dışa aktarın veya sosyal medya not kartı olarak kaydedin:
      </div>

      <div style="display:grid; grid-template-columns: 1fr 1fr; gap:12px; margin: 16px 0;">
        <button class="btn-secondary" id="btnShareCopyText" style="padding:12px; display:flex; flex-direction:column; align-items:center; gap:6px;">
          <span style="font-size:20px;">📋</span>
          <span style="font-weight:600;">Düz Metin Kopyala</span>
          <span style="font-size:11px; opacity:0.7;">Panoya aktarır</span>
        </button>

        <button class="btn-secondary" id="btnShareDownloadMd" style="padding:12px; display:flex; flex-direction:column; align-items:center; gap:6px;">
          <span style="font-size:20px;">📝</span>
          <span style="font-weight:600;">Markdown İndir</span>
          <span style="font-size:11px; opacity:0.7;">.md formatında dosya</span>
        </button>

        <button class="btn-secondary" id="btnSharePrintPdf" style="padding:12px; display:flex; flex-direction:column; align-items:center; gap:6px;">
          <span style="font-size:20px;">🖨️</span>
          <span style="font-weight:600;">PDF / Belge Yazdır</span>
          <span style="font-size:11px; opacity:0.7;">Sistem yazıcısı / PDF</span>
        </button>

        <button class="btn-secondary" id="btnShareCardPng" style="padding:12px; display:flex; flex-direction:column; align-items:center; gap:6px; border-color:var(--primary);">
          <span style="font-size:20px;">🖼️</span>
          <span style="font-weight:600; color:var(--primary);">Not Kartı PNG Üret</span>
          <span style="font-size:11px; opacity:0.7;">Şık görsel kart indir</span>
        </button>
      </div>

      <div class="modal-actions">
        <button class="btn-primary" id="btnCloseShareModal" style="width:100%;">Kapat</button>
      </div>
    </div>
  `;

  document.body.appendChild(container);

  container.querySelector("#btnCloseShareModal").addEventListener("click", () => container.remove());
  container.addEventListener("click", (e) => { if (e.target === container) container.remove(); });

  // 1. Düz Metin
  container.querySelector("#btnShareCopyText").addEventListener("click", () => {
    const fullText = `${note.title || "Başlıksız Not"}\n\n${note.content || ""}`;
    navigator.clipboard.writeText(fullText).then(() => {
      showToast("Düz metin panoya kopyalandı", "success");
      container.remove();
    });
  });

  // 2. Markdown İndir
  container.querySelector("#btnShareDownloadMd").addEventListener("click", () => {
    const fullText = `# ${note.title || "Başlıksız Not"}\n\n${note.content || ""}`;
    const blob = new Blob([fullText], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${(note.title || "not").replace(/[\\/:*?"<>|]/g, "_")}.md`;
    a.click();
    URL.revokeObjectURL(url);
    showToast("Markdown dosyası indirildi", "success");
    container.remove();
  });

  // 3. Print / PDF
  container.querySelector("#btnSharePrintPdf").addEventListener("click", () => {
    container.remove();
    window.print();
  });

  // 4. Not Kartı PNG Üretici
  container.querySelector("#btnShareCardPng").addEventListener("click", () => {
    generateNoteCardPng(note);
    container.remove();
  });
}

// HTML Canvas ile Not Kartı PNG Üretici
function generateNoteCardPng(note) {
  const canvas = document.createElement("canvas");
  const width = 1000;
  const height = 600;
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");

  // Arka Plan Rengi ve Gradyan
  const preset = NOTE_PRESETS.find(p => p.id === note.color) || NOTE_PRESETS[0];
  const grad = ctx.createLinearGradient(0, 0, width, height);
  grad.addColorStop(0, preset.color || "#131D2E");
  grad.addColorStop(1, "#0A0E17");
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, width, height);

  // Kart Çerçevesi
  ctx.strokeStyle = "rgba(255, 255, 255, 0.12)";
  ctx.lineWidth = 2;
  ctx.strokeRect(30, 30, width - 60, height - 60);

  // Üst Bar & Logo
  ctx.fillStyle = "#F59E0B";
  ctx.font = "bold 20px 'Segoe UI', sans-serif";
  ctx.fillText("📝 NOT DEFTERİM", 60, 80);

  // Tarih
  const dateStr = new Date(note.updatedAt || note.timestamp).toLocaleDateString("tr-TR", {
    day: "numeric", month: "long", year: "numeric"
  });
  ctx.fillStyle = "rgba(255, 255, 255, 0.5)";
  ctx.font = "16px 'Segoe UI', sans-serif";
  ctx.textAlign = "right";
  ctx.fillText(dateStr, width - 60, 80);
  ctx.textAlign = "left";

  // Çizgi
  ctx.strokeStyle = "rgba(255, 255, 255, 0.1)";
  ctx.beginPath();
  ctx.moveTo(60, 105);
  ctx.lineTo(width - 60, 105);
  ctx.stroke();

  // Başlık
  ctx.fillStyle = "#FFFFFF";
  ctx.font = "bold 32px 'Segoe UI', sans-serif";
  const title = (note.title || "Başlıksız Not").substring(0, 50);
  ctx.fillText(title, 60, 160);

  // İçerik (Satır sarma)
  ctx.fillStyle = "rgba(255, 255, 255, 0.85)";
  ctx.font = "20px 'Segoe UI', sans-serif";
  const rawContent = (note.content || "").replace(/[#*`~>-]/g, "").trim();
  const words = rawContent.split(/\s+/);
  let line = "";
  let y = 220;
  const maxWidth = width - 120;

  for (let i = 0; i < words.length && y < 510; i++) {
    const testLine = line + words[i] + " ";
    const metrics = ctx.measureText(testLine);
    if (metrics.width > maxWidth && i > 0) {
      ctx.fillText(line, 60, y);
      line = words[i] + " ";
      y += 34;
    } else {
      line = testLine;
    }
  }
  if (y < 510) {
    ctx.fillText(line, 60, y);
  }

  // Alt Filigran
  ctx.fillStyle = "rgba(255, 255, 255, 0.35)";
  ctx.font = "14px 'Segoe UI', sans-serif";
  ctx.fillText("Not Defterim Masaüstü Uygulaması ile oluşturuldu", 60, height - 50);

  // İndir
  const link = document.createElement("a");
  link.download = `NotKarti_${(note.title || "not").replace(/[\\/:*?"<>|]/g, "_")}.png`;
  link.href = canvas.toDataURL("image/png");
  link.click();
  showToast("Not kartı görseli oluşturuldu ve indirildi!", "success");
}

// Gelişmiş Araçlar Modalı
function openAdvancedToolsModal() {
  const note = getActiveOrSelectedNote();
  if (!note) {
    showToast("Gelişmiş araçlar için bir not seçilmedi!", "warning");
    return;
  }

  const existing = document.getElementById("dynamicModalContainer");
  if (existing) existing.remove();

  const container = document.createElement("div");
  container.id = "dynamicModalContainer";
  container.className = "modal-overlay open";

  container.innerHTML = `
    <div class="modal-dialog" style="max-width:560px;">
      <div class="modal-title">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z"/></svg>
        <span>Gelişmiş Not Araçları</span>
      </div>

      <div style="display:flex; flex-direction:column; gap:10px; margin:16px 0;">
        <button class="btn-secondary" id="btnAdvHistory" style="display:flex; align-items:center; gap:12px; padding:12px; text-align:left;">
          <span style="font-size:22px;">⏱️</span>
          <div style="flex:1;">
            <div style="font-weight:600;">Versiyon Geçmişi</div>
            <div style="font-size:12px; opacity:0.7;">Bu oturumdaki önceki sürümleri görüntüle ve geri yükle</div>
          </div>
        </button>

        <button class="btn-secondary" id="btnAdvDuplicate" style="display:flex; align-items:center; gap:12px; padding:12px; text-align:left;">
          <span style="font-size:22px;">📑</span>
          <div style="flex:1;">
            <div style="font-weight:600;">Notu Çoğalt</div>
            <div style="font-size:12px; opacity:0.7;">Tüm içerik ve ayarlarla birlikte birebir kopyasını oluştur</div>
          </div>
        </button>

        <button class="btn-secondary" id="btnAdvTemplate" style="display:flex; align-items:center; gap:12px; padding:12px; text-align:left;">
          <span style="font-size:22px;">📋</span>
          <div style="flex:1;">
            <div style="font-weight:600;">Hazır Şablon Uygula</div>
            <div style="font-size:12px; opacity:0.7;">Toplantı notu, sprint planı veya yapılacaklar şablonu yükle</div>
          </div>
        </button>

        <button class="btn-secondary" id="btnAdvStats" style="display:flex; align-items:center; gap:12px; padding:12px; text-align:left;">
          <span style="font-size:22px;">📊</span>
          <div style="flex:1;">
            <div style="font-weight:600;">Ayrıntılı İstatistikler</div>
            <div style="font-size:12px; opacity:0.7;">Okuma süresi, kelime, harf ve başlık analizleri</div>
          </div>
        </button>
      </div>

      <div class="modal-actions">
        <button class="btn-primary" id="btnCloseAdvModal" style="width:100%;">Kapat</button>
      </div>
    </div>
  `;

  document.body.appendChild(container);
  container.querySelector("#btnCloseAdvModal").addEventListener("click", () => container.remove());
  container.addEventListener("click", (e) => { if (e.target === container) container.remove(); });

  container.querySelector("#btnAdvHistory").addEventListener("click", () => {
    container.remove();
    openVersionHistoryModal();
  });
  container.querySelector("#btnAdvDuplicate").addEventListener("click", () => {
    container.remove();
    duplicateCurrentNote();
  });
  container.querySelector("#btnAdvTemplate").addEventListener("click", () => {
    container.remove();
    openTemplatePickerModal();
  });
  container.querySelector("#btnAdvStats").addEventListener("click", () => {
    container.remove();
    openStatsModal();
  });
}

// Versiyon Geçmişi Modalı
function openVersionHistoryModal() {
  const note = getActiveOrSelectedNote();
  if (!note) {
    showToast("Versiyon geçmişi için bir not seçilmedi!", "warning");
    return;
  }

  const versions = state.noteVersions.get(note.id) || [];

  const existing = document.getElementById("dynamicModalContainer");
  if (existing) existing.remove();

  const container = document.createElement("div");
  container.id = "dynamicModalContainer";
  container.className = "modal-overlay open";

  let listHtml = "";
  if (versions.length === 0) {
    listHtml = `<div style="text-align:center; padding:30px 10px; color:var(--text-faint);">Bu not için henüz geçmiş versiyon kaydedilmedi. Düzenleme yaptıkça versiyonlar otomatik birikir.</div>`;
  } else {
    listHtml = versions.map((v, i) => {
      const timeStr = new Date(v.timestamp).toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
      const dateStr = new Date(v.timestamp).toLocaleDateString("tr-TR");
      return `
        <div style="display:flex; align-items:center; justify-content:space-between; padding:10px 12px; background:var(--bg-main); border-radius:8px; margin-bottom:8px; border:1px solid var(--border-subtle);">
          <div>
            <div style="font-weight:600; font-size:14px;">Versiyon #${i + 1} - ${escapeHtml(v.title || "Başlıksız")}</div>
            <div style="font-size:12px; color:var(--text-faint);">${dateStr} ${timeStr} • ${v.content.length} karakter</div>
          </div>
          <button class="btn-secondary btn-restore-version" data-index="${i}" style="font-size:12px; padding:6px 12px;">Geri Yükle</button>
        </div>
      `;
    }).reverse().join("");
  }

  container.innerHTML = `
    <div class="modal-dialog" style="max-width:560px; max-height:80vh; display:flex; flex-direction:column;">
      <div class="modal-title">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z"/></svg>
        <span>Versiyon Geçmişi</span>
      </div>
      <div class="modal-desc">
        Notunuzun önceki kaydedilmiş sürümleri:
      </div>
      <div style="flex:1; overflow-y:auto; margin:12px 0; max-height:360px;">
        ${listHtml}
      </div>
      <div class="modal-actions">
        <button class="btn-primary" id="btnCloseHistModal" style="width:100%;">Kapat</button>
      </div>
    </div>
  `;

  document.body.appendChild(container);
  container.querySelector("#btnCloseHistModal").addEventListener("click", () => container.remove());
  container.addEventListener("click", (e) => { if (e.target === container) container.remove(); });

  container.querySelectorAll(".btn-restore-version").forEach(btn => {
    btn.addEventListener("click", () => {
      const idx = parseInt(btn.dataset.index);
      const targetVersion = versions[idx];
      if (targetVersion) {
        note.title = targetVersion.title;
        note.content = targetVersion.content;
        document.getElementById("editorTitle").value = targetVersion.title;
        document.getElementById("editorContent").value = targetVersion.content;
        updateLiveMarkdownPreview();
        updateCharWordsCount();
        triggerAutoSave();
        showToast(`Versiyon #${idx + 1} geri yüklendi!`, "success");
        container.remove();
      }
    });
  });
}

// İstatistikler Modalı
function openStatsModal() {
  const note = getActiveOrSelectedNote();
  if (!note) {
    showToast("İstatistikleri görüntülemek için bir not seçilmedi!", "warning");
    return;
  }

  const content = note.content || "";
  const chars = content.length;
  const words = content.trim() ? content.trim().split(/\s+/).length : 0;
  const lines = content ? content.split("\n").length : 0;
  const readTimeMin = Math.ceil(words / 200); // 200 kelime/dk
  const tasksTotal = (content.match(/- \[[ xX]\]/g) || []).length;
  const tasksDone = (content.match(/- \[[xX]\]/g) || []).length;

  const existing = document.getElementById("dynamicModalContainer");
  if (existing) existing.remove();

  const container = document.createElement("div");
  container.id = "dynamicModalContainer";
  container.className = "modal-overlay open";

  container.innerHTML = `
    <div class="modal-dialog" style="max-width:480px;">
      <div class="modal-title">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM9 17H7v-7h2v7zm4 0h-2V7h2v10zm4 0h-2v-4h2v4z"/></svg>
        <span>Not İstatistikleri</span>
      </div>

      <div style="display:grid; grid-template-columns: 1fr 1fr; gap:12px; margin:16px 0;">
        <div style="background:var(--bg-main); padding:12px; border-radius:8px; border:1px solid var(--border-subtle); text-align:center;">
          <div style="font-size:24px; font-weight:700; color:var(--primary);">${words}</div>
          <div style="font-size:12px; color:var(--text-faint);">Kelime Sayısı</div>
        </div>
        <div style="background:var(--bg-main); padding:12px; border-radius:8px; border:1px solid var(--border-subtle); text-align:center;">
          <div style="font-size:24px; font-weight:700; color:var(--primary);">${chars}</div>
          <div style="font-size:12px; color:var(--text-faint);">Karakter Sayısı</div>
        </div>
        <div style="background:var(--bg-main); padding:12px; border-radius:8px; border:1px solid var(--border-subtle); text-align:center;">
          <div style="font-size:24px; font-weight:700; color:var(--primary);">${lines}</div>
          <div style="font-size:12px; color:var(--text-faint);">Satır Sayısı</div>
        </div>
        <div style="background:var(--bg-main); padding:12px; border-radius:8px; border:1px solid var(--border-subtle); text-align:center;">
          <div style="font-size:24px; font-weight:700; color:var(--primary);">~${readTimeMin} dk</div>
          <div style="font-size:12px; color:var(--text-faint);">Tahmini Okuma</div>
        </div>
      </div>

      ${tasksTotal > 0 ? `
        <div style="background:var(--bg-main); padding:12px; border-radius:8px; border:1px solid var(--border-subtle); margin-bottom:16px;">
          <div style="display:flex; justify-content:space-between; font-size:13px; margin-bottom:6px;">
            <span>Kontrol Listesi</span>
            <b>${tasksDone} / ${tasksTotal} (${Math.round((tasksDone / tasksTotal) * 100)}%)</b>
          </div>
          <div style="width:100%; height:6px; background:var(--border-subtle); border-radius:3px; overflow:hidden;">
            <div style="width:${(tasksDone / tasksTotal) * 100}%; height:100%; background:var(--success);"></div>
          </div>
        </div>
      ` : ''}

      <div class="modal-actions">
        <button class="btn-primary" id="btnCloseStatsModal" style="width:100%;">Kapat</button>
      </div>
    </div>
  `;

  document.body.appendChild(container);
  container.querySelector("#btnCloseStatsModal").addEventListener("click", () => container.remove());
  container.addEventListener("click", (e) => { if (e.target === container) container.remove(); });
}

// Hatırlatıcı Modalı
function openReminderModal() {
  const note = getActiveOrSelectedNote();
  if (!note) {
    showToast("Hatırlatıcı ayarlamak için bir not seçilmedi!", "warning");
    return;
  }

  const existing = document.getElementById("dynamicModalContainer");
  if (existing) existing.remove();

  const container = document.createElement("div");
  container.id = "dynamicModalContainer";
  container.className = "modal-overlay open";

  const currentDateVal = note.reminderDate ? new Date(note.reminderDate).toISOString().slice(0, 16) : "";

  container.innerHTML = `
    <div class="modal-dialog" style="max-width:440px;">
      <div class="modal-title">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2zm-2 1H8v-6c0-2.48 1.51-4.5 4-4.5s4 2.02 4 4.5v6z"/></svg>
        <span>Hatırlatıcı Ayarla</span>
      </div>
      <div class="modal-desc">
        Notunuz için tarih ve saat seçin. Zamanı geldiğinde haberdar olun:
      </div>

      <div style="margin: 16px 0;">
        <label style="display:block; font-size:12px; color:var(--text-faint); margin-bottom:6px;">Tarih ve Saat:</label>
        <input type="datetime-local" class="modal-input" id="reminderDateInput" value="${currentDateVal}" style="width:100%;">
      </div>

      <div class="modal-actions" style="display:flex; gap:8px;">
        ${note.reminderDate ? `<button class="btn-secondary" id="btnRemoveReminder" style="color:var(--danger);">Hatırlatıcıyı Kaldır</button>` : ''}
        <button class="btn-secondary" id="btnCancelReminder">İptal</button>
        <button class="btn-primary" id="btnSaveReminder">Kaydet</button>
      </div>
    </div>
  `;

  document.body.appendChild(container);
  container.querySelector("#btnCancelReminder").addEventListener("click", () => container.remove());
  container.addEventListener("click", (e) => { if (e.target === container) container.remove(); });

  container.querySelector("#btnSaveReminder").addEventListener("click", async () => {
    const val = container.querySelector("#reminderDateInput").value;
    if (val) {
      note.reminderDate = new Date(val).getTime();
      await callIpc("db:saveNote", { note });
      showToast("Hatırlatıcı kaydedildi", "success");
      updateSidebarCounts();
      renderNotesList();
      container.remove();
    } else {
      showToast("Lütfen geçerli bir tarih seçin", "warning");
    }
  });

  const btnRemove = container.querySelector("#btnRemoveReminder");
  if (btnRemove) {
    btnRemove.addEventListener("click", async () => {
      note.reminderDate = null;
      await callIpc("db:saveNote", { note });
      showToast("Hatırlatıcı kaldırıldı", "info");
      updateSidebarCounts();
      renderNotesList();
      container.remove();
    });
  }
}

// Şablon Seçici Modalı
function openTemplatePickerModal() {
  const note = findNoteById(state.selectedNoteId);
  if (!note) return;

  const templates = [
    {
      name: "Toplantı Notu",
      content: `## 📌 Toplantı Gündemi\n- **Tarih:** ${new Date().toLocaleDateString("tr-TR")}\n- **Katılımcılar:** \n\n### 💬 Görüşülen Konular\n1. \n2. \n\n### ✅ Alınan Kararlar & Aksiyonlar\n- [ ] Görev 1\n- [ ] Görev 2\n`
    },
    {
      name: "Günlük Planlayıcı",
      content: `## 🌅 Günlük Hedefler (${new Date().toLocaleDateString("tr-TR")})\n\n### 🎯 Öncelikli Görevler\n- [ ] 1. Öncelik:\n- [ ] 2. Öncelik:\n- [ ] 3. Öncelik:\n\n### 📝 Günlük Notlar & Fikirler\n- \n\n### 🌙 Gün Sonu Değerlendirmesi\n- `
    },
    {
      name: "Proje İskeleti",
      content: `## 🚀 Proje: [Proje Adı]\n\n### 🎯 Amaç & Kapsam\n\n### 🛠️ Teknolojiler & Mimari\n- \n\n### 📋 Yol Haritası (Milestones)\n- [ ] v1.0 MVP\n- [ ] v1.1 Entegrasyonlar\n`
    }
  ];

  const existing = document.getElementById("dynamicModalContainer");
  if (existing) existing.remove();

  const container = document.createElement("div");
  container.id = "dynamicModalContainer";
  container.className = "modal-overlay open";

  container.innerHTML = `
    <div class="modal-dialog" style="max-width:480px;">
      <div class="modal-title">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z"/></svg>
        <span>Hazır Şablon Uygula</span>
      </div>
      <div class="modal-desc">
        Mevcut notunuza hazır bir şablon içeriği ekleyin:
      </div>
      <div style="display:flex; flex-direction:column; gap:8px; margin:16px 0;">
        ${templates.map((t, idx) => `
          <button class="btn-secondary btn-apply-tmpl" data-index="${idx}" style="text-align:left; padding:10px 14px;">
            <b>${t.name}</b>
          </button>
        `).join("")}
      </div>
      <div class="modal-actions">
        <button class="btn-primary" id="btnCloseTmplModal" style="width:100%;">Kapat</button>
      </div>
    </div>
  `;

  document.body.appendChild(container);
  container.querySelector("#btnCloseTmplModal").addEventListener("click", () => container.remove());
  container.addEventListener("click", (e) => { if (e.target === container) container.remove(); });

  container.querySelectorAll(".btn-apply-tmpl").forEach(btn => {
    btn.addEventListener("click", () => {
      const t = templates[parseInt(btn.dataset.index)];
      note.content = (note.content ? note.content + "\n\n" : "") + t.content;
      document.getElementById("editorContent").value = note.content;
      updateLiveMarkdownPreview();
      updateCharWordsCount();
      triggerAutoSave();
      showToast(`'${t.name}' şablonu uygulandı`, "success");
      container.remove();
    });
  });
}

// Notu Çoğalt
async function duplicateCurrentNote() {
  const note = getActiveOrSelectedNote();
  if (!note) {
    showToast("Çoğaltılacak bir not bulunamadı!", "warning");
    return;
  }

  const duplicated = {
    ...note,
    id: 0,
    title: `Kopya - ${note.title || "Başlıksız Not"}`,
    timestamp: Date.now(),
    createdAt: Date.now(),
    updatedAt: Date.now()
  };

  try {
    const created = await callIpc("db:createNote", { note: duplicated });
    if (created && created.id) {
      duplicated.id = created.id;
      state.activeNotes.unshift(duplicated);
      renderNotesList();
      loadNoteIntoEditor(duplicated, false);
      showToast("Not başarıyla çoğaltıldı!", "success");
    }
  } catch (err) {
    showToast("Not çoğaltılamadı: " + err, "danger");
  }
}

// Şablon Olarak Sakla
function saveAsTemplate() {
  const note = getActiveOrSelectedNote();
  if (!note) {
    showToast("Şablon olarak kaydedilecek not bulunamadı!", "warning");
    return;
  }

  const key = `notdefterim_template_${Date.now()}`;
  localStorage.setItem(key, JSON.stringify({ title: note.title, content: note.content }));
  showToast("Mevcut not şablon olarak tarayıcı belleğine kaydedildi!", "success");
}

// .notevault Dosyası Olarak Dışa Aktar
function exportCurrentNoteToVaultFile() {
  const note = getActiveOrSelectedNote();
  if (!note) {
    showToast(".notevault olarak aktarılacak not bulunamadı!", "warning");
    return;
  }

  const exportData = {
    version: "1.5.0",
    exportedAt: Date.now(),
    type: "notdefterim_vault_note",
    note: {
      title: note.title,
      content: note.content,
      color: note.color,
      categoryId: note.categoryId,
      isPinned: note.isPinned,
      isLocked: note.isLocked
    }
  };

  const jsonStr = JSON.stringify(exportData, null, 2);
  const blob = new Blob([jsonStr], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `${(note.title || "kasa_notu").replace(/[\\/:*?"<>|]/g, "_")}.notevault`;
  a.click();
  URL.revokeObjectURL(url);
  showToast(".notevault dosyası başarıyla dışa aktarıldı!", "success");
}

// Android QR pairing is validated locally; no pairing token is sent before a future
// native transport verifies the QR-pinned TLS certificate.
let pendingAndroidSyncQr = null;
let syncQrCameraStream = null;
let syncQrCameraFrame = 0;
let syncQrScanActive = false;

function isPrivateSyncIpv4(host) {
  const parts = host.split(".");
  if (parts.length !== 4) return false;
  const octets = parts.map(part => Number(part));
  if (octets.some((value, index) => !Number.isInteger(value) || value < 0 || value > 255 || String(value) !== parts[index])) {
    return false;
  }
  return octets[0] === 10 ||
    (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31) ||
    (octets[0] === 192 && octets[1] === 168) ||
    (octets[0] === 169 && octets[1] === 254);
}

function parseAndroidSyncQr(rawText) {
  if (typeof rawText !== "string" || rawText.trim().length === 0 || rawText.length > 4096) {
    throw new Error("QR kod içeriği boş veya beklenen boyutun dışında.");
  }

  let payload;
  try {
    payload = JSON.parse(rawText.trim());
  } catch {
    throw new Error("QR kod geçerli JSON bağlantı bilgisi içermiyor.");
  }

  if (!payload || payload.type !== "noteapp_sync" || payload.version !== 3 || payload.scheme !== "https") {
    throw new Error("Yalnızca Not Defterim Android HTTPS v3 QR kodları kabul edilir.");
  }
  if (!isPrivateSyncIpv4(payload.host)) {
    throw new Error("Android adresi özel yerel IPv4 ağına ait değil.");
  }
  if (!Number.isInteger(payload.port) || payload.port < 1 || payload.port > 65535) {
    throw new Error("QR kodundaki bağlantı noktası geçersiz.");
  }
  if (typeof payload.tls_cert_sha256 !== "string" || !/^[0-9a-f]{64}$/.test(payload.tls_cert_sha256)) {
    throw new Error("QR kodundaki TLS sertifika parmak izi geçersiz.");
  }
  if (typeof payload.pairing_token !== "string" || !/^[A-Za-z0-9_-]{32}$/.test(payload.pairing_token)) {
    throw new Error("QR kodundaki tek kullanımlık eşleşme kodu geçersiz.");
  }
  if (!Number.isSafeInteger(payload.expires_at) || payload.expires_at <= Date.now()) {
    throw new Error("Android QR kodunun süresi dolmuş. Telefonda yeni bir QR kod oluşturun.");
  }

  return payload;
}

function setSyncStatus(message) {
  const status = document.getElementById("syncServerMsg");
  if (status) status.textContent = message;
}

function clearAndroidSyncPairing() {
  pendingAndroidSyncQr = null;
  document.getElementById("syncPairingDetails").hidden = true;
  document.getElementById("syncDeviceAddress").textContent = "—";
  document.getElementById("syncCertificateFingerprint").textContent = "—";
  const review = document.getElementById("modalSyncReview");
  review.classList.remove("open");
  review.hidden = true;
}

function acceptAndroidSyncQr(rawText) {
  const payload = parseAndroidSyncQr(rawText);
  pendingAndroidSyncQr = payload;
  document.getElementById("syncQrManualInput").value = "";
  document.getElementById("syncDeviceAddress").textContent = `${payload.host}:${payload.port}`;
  document.getElementById("syncCertificateFingerprint").textContent =
    payload.tls_cert_sha256.match(/.{1,8}/g).join(" ");
  document.getElementById("syncPairingDetails").hidden = false;
  setSyncStatus("QR doğrulandı. Henüz hiçbir eşleşme bilgisi veya not gönderilmedi.");
}

function openSyncTransferReview(direction) {
  if (!pendingAndroidSyncQr || pendingAndroidSyncQr.expires_at <= Date.now()) {
    pendingAndroidSyncQr = null;
    document.getElementById("syncPairingDetails").hidden = true;
    setSyncStatus("QR kodunun süresi doldu. Android'de yeni bir QR kod oluşturun.");
    showToast("Aktarım için yeni bir Android QR kodu okutun.", "warning");
    return;
  }

  const review = document.getElementById("modalSyncReview");
  document.getElementById("syncReviewDirection").textContent = direction === "receive"
    ? "Android cihazdan bu bilgisayara not aktarılacak."
    : "Bu bilgisayardaki notlar Android cihaza gönderilecek.";
  document.getElementById("syncTransferNoteCount").textContent = "Güvenli bağlantıdan sonra doğrulanacak";
  document.getElementById("syncTransferFileSize").textContent = "ZIP paketi aktarım öncesi ölçülecek";
  document.getElementById("syncTransferProgressPanel").hidden = true;
  document.getElementById("syncTransferUnavailable").hidden = false;
  document.getElementById("btnConfirmSyncTransfer").disabled = true;
  review.hidden = false;
  review.classList.add("open");
  stageNativeSyncTransfer(direction);
}

let stagedSyncDirection = null;
let syncTransferRunning = false;

// Native-only call: no timeout and no HTTP fallback, so credentials never leave the IPC channel.
function callNativeOnly(action, payload) {
  if (!(window.ipc && typeof window.ipc.postMessage === "function")) {
    return Promise.reject(new Error("Yerel masaüstü köprüsü yok."));
  }
  const requestId = `req_${Date.now()}_${++requestCounter}`;
  return new Promise((resolve, reject) => {
    pendingRequests.set(requestId, { resolve, reject, timeoutId: null });
    window.ipc.postMessage(JSON.stringify({ action, cmd: action, payload, requestId }));
  });
}

async function stageNativeSyncTransfer(direction) {
  stagedSyncDirection = null;
  const qr = pendingAndroidSyncQr;
  if (!qr || !(window.ipc && typeof window.ipc.postMessage === "function")) return;
  try {
    const preview = await callNativeOnly("androidSync:preview", { qr: JSON.stringify(qr), direction });
    stagedSyncDirection = direction;
    renderSyncTransferSummary({
      direction: direction === "send" ? "send" : "receive",
      noteCount: preview.noteCount,
      sizeBytes: preview.sizeBytes
    });
    if (direction === "receive") {
      document.getElementById("syncTransferNoteCount").textContent = "Android'den gelen notlar";
      document.getElementById("syncTransferFileSize").textContent =
        "Gelen veri bu bilgisayardaki notların yerine geçer; önce yerel güvenlik yedeği alınır.";
    }
    document.getElementById("syncTransferUnavailable").hidden = true;
    document.getElementById("btnConfirmSyncTransfer").disabled = false;
  } catch (error) {
    setSyncStatus(error.message);
  }
}

async function confirmNativeSyncTransfer() {
  if (!stagedSyncDirection || !pendingAndroidSyncQr || syncTransferRunning) return;
  const direction = stagedSyncDirection;
  const qr = JSON.stringify(pendingAndroidSyncQr);
  syncTransferRunning = true;
  stagedSyncDirection = null;
  pendingAndroidSyncQr = null;
  document.getElementById("btnConfirmSyncTransfer").disabled = true;
  renderSyncTransferProgress(0, 1);
  try {
    await callNativeOnly("androidSync:transfer", { qr, direction, confirmed: true });
  } catch (error) {
    // The "done" event renders the failure; this only covers a missing event.
    console.warn("Android aktarımı başarısız:", error.message);
  } finally {
    syncTransferRunning = false;
  }
}

onNativeEvent("androidSync:event", (data) => {
  if (data.type === "progress") {
    renderSyncTransferProgress(data.done, data.total);
  } else if (data.type === "done") {
    const review = document.getElementById("modalSyncReview");
    review.classList.remove("open");
    review.hidden = true;
    clearAndroidSyncPairing();
    renderSyncTransferResult(data.ok, data.ok ? `${data.message} (${data.notes} not)` : data.message);
    if (data.ok && data.reload) loadAllData();
  }
});

document.getElementById("btnConfirmSyncTransfer")?.addEventListener("click", confirmNativeSyncTransfer);

// Presentation-only helpers: they render supplied values and never dispatch a request.
function formatSyncBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function renderSyncTransferSummary({ direction, noteCount, sizeBytes }) {
  const label = direction === "send"
    ? "Bu bilgisayardaki notlar Android cihaza gönderilecek."
    : "Android cihazdan bu bilgisayara not aktarılacak.";
  document.getElementById("syncReviewDirection").textContent = label;
  document.getElementById("syncTransferNoteCount").textContent = Number.isInteger(noteCount) && noteCount >= 0
    ? `${noteCount} not` : "—";
  document.getElementById("syncTransferFileSize").textContent = `ZIP · ${formatSyncBytes(sizeBytes)}`;
  document.getElementById("syncTransferProgressPanel").hidden = true;
  const review = document.getElementById("modalSyncReview");
  review.hidden = false;
  review.classList.add("open");
}

function renderSyncTransferProgress(receivedBytes, totalBytes) {
  const valid = Number.isFinite(totalBytes) && totalBytes > 0 && Number.isFinite(receivedBytes) && receivedBytes >= 0;
  const percent = valid ? Math.min(100, Math.floor((receivedBytes / totalBytes) * 100)) : 0;
  document.getElementById("syncTransferProgressPanel").hidden = false;
  document.getElementById("syncTransferStatus").textContent = valid ? "Aktarılıyor" : "Boyut doğrulanamadı";
  document.getElementById("syncTransferProgressPercent").textContent = valid ? `${percent}%` : "—";
  document.getElementById("syncTransferProgress").value = percent;
  document.getElementById("syncTransferBytes").textContent = valid
    ? `${formatSyncBytes(receivedBytes)} / ${formatSyncBytes(totalBytes)}` : "Geçerli Content-Length gerekli";
}

function renderSyncTransferResult(ok, message) {
  const modal = document.getElementById("modalSyncSuccess");
  document.getElementById("syncSuccessTitle").textContent = ok ? "Aktarım tamamlandı" : "Aktarım başarısız";
  document.getElementById("syncTransferSuccessMessage").textContent = message;
  modal.hidden = false;
  modal.classList.add("open");
}

// Credential-free preview entry point for the UI states; confirm stays disabled.
window.NotDefterimSyncPreview = Object.freeze({
  summary: renderSyncTransferSummary,
  progress: renderSyncTransferProgress,
  result: renderSyncTransferResult
});

async function startSyncQrCamera() {
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    setSyncStatus("Bu uygulama bağlamında kamera izni kullanılamıyor. QR metnini aşağıya yapıştırın.");
    return;
  }
  if (!("BarcodeDetector" in window)) {
    setSyncStatus("Bu WebView kamera QR çözümlemeyi desteklemiyor. QR metnini aşağıya yapıştırın.");
    return;
  }

  try {
    const detector = new BarcodeDetector({ formats: ["qr_code"] });
    syncQrCameraStream = await navigator.mediaDevices.getUserMedia({
      audio: false,
      video: { facingMode: { ideal: "environment" } }
    });
    const video = document.getElementById("syncCameraVideo");
    video.srcObject = syncQrCameraStream;
    video.hidden = false;
    document.getElementById("syncCameraPlaceholder").hidden = true;
    await video.play();
    syncQrScanActive = true;
    setSyncStatus("Kamerayı Android QR koduna yöneltin.");

    const scanFrame = async () => {
      if (!syncQrScanActive) return;
      try {
        if (video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA) {
          const codes = await detector.detect(video);
          if (codes.length > 0) {
            document.getElementById("syncQrManualInput").value = codes[0].rawValue;
            stopSyncQrCamera();
            acceptAndroidSyncQr(codes[0].rawValue);
            return;
          }
        }
        syncQrCameraFrame = requestAnimationFrame(scanFrame);
      } catch (error) {
        stopSyncQrCamera();
        clearAndroidSyncPairing();
        setSyncStatus(`Kamera QR kodunu okuyamadı: ${error.message || "bilinmeyen hata"}`);
      }
    };
    syncQrCameraFrame = requestAnimationFrame(scanFrame);
  } catch (error) {
    stopSyncQrCamera();
    setSyncStatus(error.name === "NotAllowedError"
      ? "Kamera izni verilmedi. QR metnini aşağıya yapıştırabilirsiniz."
      : `Kamera açılamadı: ${error.message || "bilinmeyen hata"}`);
  }
}

function stopSyncQrCamera() {
  syncQrScanActive = false;
  if (syncQrCameraFrame) {
    cancelAnimationFrame(syncQrCameraFrame);
    syncQrCameraFrame = 0;
  }
  syncQrCameraStream?.getTracks().forEach(track => track.stop());
  syncQrCameraStream = null;
  const video = document.getElementById("syncCameraVideo");
  if (video) {
    video.pause();
    video.srcObject = null;
    video.hidden = true;
  }
  const placeholder = document.getElementById("syncCameraPlaceholder");
  if (placeholder) placeholder.hidden = false;
}

// Wi-Fi Senkronizasyon Durum İzleme & Polling
let syncPollTimer = null;
let desktopPairingQr = null;
let desktopPairingQrPromise = null;

function startSyncPolling() {
  stopSyncPolling();
  updateSyncStateUI();
  syncPollTimer = setInterval(() => {
    const modalSync = document.getElementById("modalSync");
    if (modalSync && modalSync.classList.contains("open")) {
      updateSyncStateUI();
    } else {
      stopSyncPolling();
    }
  }, 2000);
}

function stopSyncPolling() {
  if (syncPollTimer) {
    clearInterval(syncPollTimer);
    syncPollTimer = null;
  }
}

let syncModalStartedServer = false;
let syncUpdateInFlight = false;
let syncLastState = null;

function isUsableLanIp(ip) {
  return typeof ip === "string"
    && ip.trim() !== ""
    && ip !== "undefined"
    && ip !== "null"
    && ip !== "0.0.0.0"
    && !ip.startsWith("127.");
}

function setDesktopQrPlaceholder(html) {
  const qrContainer = document.getElementById("qrCodeContainer");
  if (qrContainer) qrContainer.innerHTML = `<div class="qr-placeholder">${html}</div>`;
}

// Modal açıldığında masaüstü sunucusunu başlatır (QR için sunucu çalışıyor olmalı).
async function ensureDesktopSyncServer() {
  let current = null;
  try {
    current = await callIpc("sync:getState");
  } catch (_) {}
  if (current && current.is_running) return current;
  try {
    const started = await callIpc("sync:start");
    syncModalStartedServer = true;
    return started;
  } catch (err) {
    console.error("sync:start başarısız:", err);
    setDesktopQrPlaceholder("Wi-Fi sunucusu başlatılamadı. Port 8765 başka bir uygulama tarafından kullanılıyor olabilir.");
    return null;
  }
}

async function toggleDesktopSyncServer() {
  const btn = document.getElementById("btnToggleServer");
  if (btn) btn.disabled = true;
  try {
    const current = await callIpc("sync:getState").catch(() => null);
    desktopPairingQr = null;
    if (current && current.is_running) {
      await callIpc("sync:stop");
      syncModalStartedServer = false;
    } else {
      await callIpc("sync:start");
    }
  } catch (err) {
    showToast("Sunucu durumu değiştirilemedi", "danger");
  } finally {
    if (btn) btn.disabled = false;
    updateSyncStateUI();
  }
}

async function updateSyncStateUI() {
  if (syncUpdateInFlight) return;
  syncUpdateInFlight = true;
  try {
    let syncState = null;
    try {
      syncState = await callIpc("sync:getState");
    } catch (e) {
      console.warn("sync:getState çağrısı başarısız:", e);
    }
    syncLastState = syncState;

    const isRunning = !!(syncState && (syncState.is_running ?? syncState.isRunning));
    const rawIp = syncState?.local_ip || syncState?.localIp || syncState?.server_ip || syncState?.serverIp || "";
    const ip = typeof rawIp === "string" ? rawIp.trim() : "";
    const port = Number(syncState?.port) || 8765;
    const pin = syncState?.pairing_pin || syncState?.pairingPin || "----";
    const lastMsg = syncState?.last_message || syncState?.lastMessage || (isRunning ? "Telefon bağlantısı bekleniyor…" : "Sunucu durduruldu");
    if (isUsableLanIp(ip)) state.serverIp = ip;

    const setText = (id, value) => {
      const el = document.getElementById(id);
      if (el) el.textContent = value;
    };
    setText("syncPinCode", isRunning ? pin : "----");
    setText("syncIpAddress", isUsableLanIp(ip) ? `${ip}:${port}` : "—");
    setText("syncDesktopMsg", syncState ? lastMsg : "Yerel senkronizasyon servisine ulaşılamadı");

    const syncDot = document.getElementById("syncStatusDot");
    if (syncDot) {
      syncDot.style.display = isRunning ? "inline-block" : "none";
      syncDot.classList.toggle("sync-active", isRunning);
    }
    const modalDot = document.getElementById("syncDesktopDot");
    if (modalDot) modalDot.classList.toggle("is-on", isRunning);
    const badge = document.getElementById("syncStatusBadge");
    if (badge) {
      badge.textContent = isRunning ? "Çevrimiçi" : "Hazır";
      badge.classList.toggle("online", isRunning);
    }
    const btnToggle = document.getElementById("btnToggleServer");
    if (btnToggle) btnToggle.textContent = isRunning ? "Sunucuyu durdur" : "Sunucuyu başlat";
    const btnRefresh = document.getElementById("btnRefreshQr");
    if (btnRefresh) btnRefresh.hidden = !isRunning;

    const qrContainer = document.getElementById("qrCodeContainer");
    if (!qrContainer) return;

    if (!syncState) {
      desktopPairingQr = null;
      setDesktopQrPlaceholder("Yerel senkronizasyon servisine ulaşılamadı.");
      return;
    }
    if (!isRunning) {
      desktopPairingQr = null;
      setDesktopQrPlaceholder("QR kodu görmek için Wi-Fi sunucusunu başlatın.");
      return;
    }
    if (!isUsableLanIp(ip)) {
      desktopPairingQr = null;
      setDesktopQrPlaceholder("Yerel ağ (Wi-Fi/LAN) IP adresi bulunamadı. Bilgisayarın ağa bağlı olduğundan emin olun.");
      return;
    }

    if (!desktopPairingQr || desktopPairingQr.expires_at <= Date.now() + 15000) {
      if (!desktopPairingQrPromise) {
        desktopPairingQrPromise = callIpc("sync:createPairingToken")
          .then(pairing => {
            desktopPairingQr = pairing;
            return pairing;
          })
          .finally(() => {
            desktopPairingQrPromise = null;
          });
      }
      try {
        desktopPairingQr = await desktopPairingQrPromise;
      } catch (_) {
        desktopPairingQr = null;
        setDesktopQrPlaceholder("QR eşleştirme kodu alınamadı. Sunucunun çalıştığını kontrol edin.");
        return;
      }
    }

    const qrPayload = JSON.stringify({
      type: "noteapp_sync",
      version: 2,
      host: ip,
      port,
      pairing_token: desktopPairingQr.pairing_token,
      expires_at: desktopPairingQr.expires_at
    });
    if (qrContainer.dataset.payload !== qrPayload) {
      qrContainer.innerHTML = renderSimpleQr(qrPayload);
      qrContainer.dataset.payload = qrPayload;
    }
    const secondsLeft = Math.max(0, Math.round((desktopPairingQr.expires_at - Date.now()) / 1000));
    setText("syncQrExpiry", `Kod ${Math.floor(secondsLeft / 60)}:${String(secondsLeft % 60).padStart(2, "0")} içinde yenilenir`);
  } catch (err) {
    console.error("updateSyncStateUI genel hata:", err);
  } finally {
    syncUpdateInFlight = false;
  }
}


// ===================================================
// GERÇEK, TARANABİLİR QR KOD ÜRETİCİ (vendor/qrcode.js - qrcode-generator, MIT)
// Eski elle yazılmış kodlayıcı format bilgisini ters/transpoze yazdığı için
// hiçbir telefon okuyamıyordu; doğrulanmış kütüphaneyle değiştirildi.
// ===================================================

function renderSimpleQr(text) {
  if (typeof window.qrcode !== "function") {
    return '<div class="qr-error">QR kütüphanesi yüklenemedi (vendor/qrcode.js).</div>';
  }
  let qr = null;
  for (const level of ["M", "L"]) {
    try {
      qr = window.qrcode(0, level);
      qr.addData(String(text), "Byte");
      qr.make();
      break;
    } catch (_) {
      qr = null;
    }
  }
  if (!qr) {
    return '<div class="qr-error">QR içeriği çok uzun, kod üretilemedi.</div>';
  }
  const count = qr.getModuleCount();
  const border = 4;
  const total = count + border * 2;
  let path = "";
  for (let r = 0; r < count; r++) {
    for (let c = 0; c < count; c++) {
      if (qr.isDark(r, c)) path += `M${c + border},${r + border}h1v1h-1z`;
    }
  }
  return `<svg class="qr-svg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${total} ${total}" shape-rendering="crispEdges" role="img" aria-label="Wi-Fi Senkronizasyon QR Kodu"><rect width="${total}" height="${total}" fill="#FFFFFF"/><path d="${path}" fill="#000000"/></svg>`;
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

// ===================================================
// YAPIŞKAN NOT (STICKY NOTE) YÖNETİCİSİ
// ===================================================

function openStickyNote(noteId) {
  const id = noteId || state.selectedNoteId;
  if (!id) {
    showToast("Yapışkan nota dönüştürülecek bir not seçilmedi", "warning");
    return;
  }
  const note = findNoteById(id);
  const title = encodeURIComponent(note?.title || "Not");
  const url = `${window.location.pathname}?mode=sticky&id=${id}`;
  window.open(url, `sticky_${id}`, "width=380,height=440,resizable=yes,alwaysOnTop=yes,menubar=no,toolbar=no");
}

// ===================================================
// EVRENSEL KOMUT PALETİ (Ctrl+K / Cmd+K)
// ===================================================

function openCommandPalette() {
  const existing = document.getElementById("commandPaletteModal");
  if (existing) {
    existing.remove();
    return;
  }

  const overlay = document.createElement("div");
  overlay.id = "commandPaletteModal";
  overlay.className = "modal-overlay open";
  overlay.style.zIndex = "10000";

  overlay.innerHTML = `
    <div class="modal-dialog" style="max-width:580px; padding:0; overflow:hidden; border-radius:12px; border:1px solid var(--border-subtle); background:var(--bg-card); box-shadow:0 16px 40px rgba(0,0,0,0.6);">
      <div style="display:flex; align-items:center; padding:14px 16px; border-bottom:1px solid var(--border-subtle); gap:10px;">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="var(--text-faint)"><path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>
        <input type="text" id="cmdPaletteInput" placeholder="Notlarda ara, '>' ile eylem, '#' ile etiket, '@' ile kategori..." style="flex:1; background:transparent; border:none; outline:none; font-size:15px; color:var(--text-main); font-family:inherit;" autocomplete="off" spellcheck="false">
        <span style="font-size:11px; background:var(--border-subtle); color:var(--text-faint); padding:2px 6px; border-radius:4px;">ESC</span>
      </div>

      <div style="display:flex; gap:6px; padding:8px 16px; border-bottom:1px solid var(--border-subtle); font-size:11px; color:var(--text-faint); background:rgba(var(--wash),0.06);">
        <span>İpuçları:</span>
        <span style="color:var(--primary);">> Eylemler</span>
        <span style="color:#10B981;"># Etiketler</span>
        <span style="color:#60A5FA;">@ Kategoriler</span>
      </div>

      <div id="cmdPaletteResults" style="max-height:360px; overflow-y:auto; padding:6px 0;"></div>
    </div>
  `;

  document.body.appendChild(overlay);

  const input = overlay.querySelector("#cmdPaletteInput");
  const resultsBox = overlay.querySelector("#cmdPaletteResults");
  let activeIndex = 0;
  let currentItems = [];

  const actions = [
    { title: "Yeni Not Oluştur", desc: "Boş bir taslak not açar", icon: "📝", run: () => createNewNote() },
    { title: "İlişki Grafiğini Aç", desc: "Tüm notların bağlantı haritasını gösterir (Ctrl+G)", icon: "🕸️", run: () => openGraphModal() },
    { title: "Wi-Fi Senkronizasyon Modalı", desc: "Android cihazla eşleştirme ve yedekleme", icon: "📶", run: () => document.getElementById("btnOpenSync")?.click() },
    { title: "Kasayı Kilitle / Durum Değiştir", desc: "Gizli kasa notlarını güvene al", icon: "🔒", run: () => toggleLockCurrentNote() },
    { title: "PDF / Belge Yazdır", desc: "Mevcut notu yazdır veya PDF olarak kaydet", icon: "🖨️", run: () => window.print() },
    { title: "Yapışkan Not Olarak Aç", desc: "Mevcut notu her zaman üstte kalan küçük pencerede aç", icon: "📌", run: () => openStickyNote() },
    { title: "Optik Veri Akışı (Air-Gapped QR Transfer)", desc: "Ağsız, tamamen çevrimdışı kamera ile dosya ve not aktarımı", icon: "📡", run: () => startOpticalStream() },
    { title: "Tüm Notları Zip Olarak Yedekle", desc: "Veritabanını arşiv dosyasına aktar", icon: "📦", run: () => document.getElementById("btnExportBackup")?.click() },
    { title: "Çoklu Not Seçim Modu", desc: "Toplu silme, arşivleme ve sabitleme", icon: "✓", run: () => document.getElementById("btnToggleBatchMode")?.click() },
    { title: "Kehribar Teması", desc: "Sıcak kehribar renk paletini uygula", icon: "🎨", run: () => applyTheme("amber") },
    { title: "Okyanus Teması", desc: "Derin okyanus mavi paletini uygula", icon: "🎨", run: () => applyTheme("sapphire") },
    { title: "Zümrüt Teması", desc: "Doğal zümrüt yeşil paletini uygula", icon: "🎨", run: () => applyTheme("emerald") },
    { title: "Mürdüm Teması", desc: "Mürdüm ve lavanta paletini uygula", icon: "🎨", run: () => applyTheme("plum") }
  ];

  function renderResults() {
    const query = input.value.trim();
    resultsBox.innerHTML = "";
    currentItems = [];

    if (query.startsWith(">")) {
      // Eylemler
      const q = query.substring(1).trim().toLowerCase();
      currentItems = actions.filter(a => a.title.toLowerCase().includes(q) || a.desc.toLowerCase().includes(q));
    } else if (query.startsWith("#")) {
      // Etiketler
      const q = query.substring(1).trim().toLowerCase();
      const tags = new Set();
      [...state.activeNotes, ...state.archivedNotes].forEach(n => {
        const matches = `${n.title || ""} ${n.content || ""}`.match(/#([a-zA-ZçğıöşüÇĞİÖŞÜ0-9_-]+)/g);
        if (matches) matches.forEach(m => tags.add(m.toLowerCase()));
      });
      currentItems = Array.from(tags)
        .filter(t => t.includes(q))
        .map(t => ({
          title: t,
          desc: "Bu etikete göre notları filtrele",
          icon: "#",
          run: () => {
            state.selectedTag = t;
            state.currentFilter = `tag_${t}`;
            renderNotesList();
          }
        }));
    } else if (query.startsWith("@")) {
      // Kategoriler
      const q = query.substring(1).trim().toLowerCase();
      currentItems = state.categories
        .filter(c => c.name.toLowerCase().includes(q))
        .map(c => ({
          title: c.name,
          desc: "Bu kategorideki notları listele",
          icon: "📁",
          run: () => {
            state.currentFilter = `cat_${c.id}`;
            renderNotesList();
          }
        }));
    } else {
      // Düz Not Araması (Fuzzy Search)
      const q = query.toLowerCase();
      const allNotes = [...state.activeNotes, ...state.archivedNotes];
      currentItems = allNotes
        .filter(n => (n.title || "").toLowerCase().includes(q) || (n.content || "").toLowerCase().includes(q))
        .slice(0, 15)
        .map(n => ({
          title: n.title || "Başlıksız Not",
          desc: n.content ? n.content.substring(0, 70).replace(/\n/g, " ") : "(İçerik yok)",
          icon: n.isPinned ? "📌" : (n.isLocked ? "🔒" : "📄"),
          run: () => loadNoteIntoEditor(n, false)
        }));

      // Eğer eşleşen not azsa eylemleri de ekle
      if (currentItems.length < 3) {
        actions.filter(a => a.title.toLowerCase().includes(q)).forEach(a => currentItems.push(a));
      }
    }

    if (currentItems.length === 0) {
      resultsBox.innerHTML = `<div style="padding:20px; text-align:center; font-size:13px; color:var(--text-faint);">Eşleşen sonuç bulunamadı.</div>`;
      return;
    }

    if (activeIndex >= currentItems.length) activeIndex = 0;

    currentItems.forEach((item, idx) => {
      const el = document.createElement("div");
      const isSelected = idx === activeIndex;
      el.className = `cmd-item ${isSelected ? 'selected' : ''}`;
      el.style.cssText = `display:flex; align-items:center; gap:12px; padding:10px 16px; cursor:pointer; background:${isSelected ? 'rgba(255,255,255,0.08)' : 'transparent'}; border-left:${isSelected ? '3px solid var(--primary)' : '3px solid transparent'};`;

      el.innerHTML = `
        <span style="font-size:18px;">${item.icon}</span>
        <div style="flex:1; overflow:hidden;">
          <div style="font-size:14px; font-weight:600; color:${isSelected ? 'var(--primary)' : 'var(--text-main)'}; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${escapeHtml(item.title)}</div>
          <div style="font-size:12px; color:var(--text-faint); overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${escapeHtml(item.desc)}</div>
        </div>
      `;

      el.addEventListener("mouseenter", () => {
        activeIndex = idx;
        renderSelectionOnly();
      });

      el.addEventListener("click", () => {
        overlay.remove();
        item.run();
      });

      resultsBox.appendChild(el);
    });
  }

  function renderSelectionOnly() {
    const items = resultsBox.querySelectorAll(".cmd-item");
    items.forEach((el, idx) => {
      const isSelected = idx === activeIndex;
      el.style.background = isSelected ? 'rgba(255,255,255,0.08)' : 'transparent';
      el.style.borderLeft = isSelected ? '3px solid var(--primary)' : '3px solid transparent';
      if (isSelected) el.scrollIntoView({ block: 'nearest' });
    });
  }

  input.addEventListener("input", () => {
    activeIndex = 0;
    renderResults();
  });

  input.addEventListener("keydown", (e) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      if (currentItems.length > 0) {
        activeIndex = (activeIndex + 1) % currentItems.length;
        renderSelectionOnly();
      }
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      if (currentItems.length > 0) {
        activeIndex = (activeIndex - 1 + currentItems.length) % currentItems.length;
        renderSelectionOnly();
      }
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (currentItems[activeIndex]) {
        overlay.remove();
        currentItems[activeIndex].run();
      }
    } else if (e.key === "Escape") {
      overlay.remove();
    }
  });

  overlay.addEventListener("click", (e) => {
    if (e.target === overlay) overlay.remove();
  });

  renderResults();
  input.focus();
}

// ===================================================
// İNTERAKTİF NOT İLİŞKİ GRAFİĞİ MOTORU (KNOWLEDGE GRAPH)
// ===================================================

function openGraphModal() {
  const existing = document.getElementById("knowledgeGraphModal");
  if (existing) existing.remove();

  const overlay = document.createElement("div");
  overlay.id = "knowledgeGraphModal";
  overlay.className = "modal-overlay open";
  overlay.style.zIndex = "9999";

  overlay.innerHTML = `
    <div class="modal-dialog" style="max-width:92vw; width:92vw; height:88vh; max-height:88vh; padding:0; display:flex; flex-direction:column; overflow:hidden; border-radius:14px; border:1px solid var(--border-subtle); background:var(--bg-main); box-shadow:0 24px 60px rgba(0,0,0,0.7);">
      <div style="display:flex; align-items:center; justify-content:space-between; padding:12px 20px; border-bottom:1px solid var(--border-subtle); background:var(--bg-card);">
        <div style="display:flex; align-items:center; gap:12px;">
          <span style="font-size:20px;">🕸️</span>
          <span style="font-weight:700; font-size:16px;">Not İlişki Grafiği</span>
          <span class="nav-count" id="graphNodesCount" style="background:var(--border-subtle); padding:2px 8px; border-radius:10px; font-size:12px;">0 Not</span>
          <span class="nav-count" id="graphEdgesCount" style="background:var(--border-subtle); padding:2px 8px; border-radius:10px; font-size:12px;">0 Bağlantı</span>
        </div>

        <div style="display:flex; align-items:center; gap:14px;">
          <label style="display:flex; align-items:center; gap:6px; font-size:12px; color:var(--text-muted); cursor:pointer;">
            <input type="checkbox" id="chkHideOrphans" style="cursor:pointer;">
            <span>Yetim Notları Gizle</span>
          </label>
          <button class="btn-secondary" id="btnGraphReset" style="font-size:12px; padding:5px 12px;">Görünümü Sıfırla</button>
          <button class="btn-primary" id="btnCloseGraphModal" style="font-size:12px; padding:5px 14px;">✕ Kapat</button>
        </div>
      </div>

      <div style="flex:1; position:relative; overflow:hidden; background:#0B1120;">
        <canvas id="knowledgeGraphCanvas" style="width:100%; height:100%; display:block; cursor:grab;"></canvas>
        <div id="graphTooltip" style="position:absolute; display:none; pointer-events:none; background:rgba(15,23,42,0.92); border:1px solid var(--border-subtle); border-radius:6px; padding:6px 10px; font-size:12px; color:#F8FAFC; box-shadow:0 6px 16px rgba(0,0,0,0.4); z-index:10;"></div>
      </div>
    </div>
  `;

  document.body.appendChild(overlay);

  const canvas = overlay.querySelector("#knowledgeGraphCanvas");
  const ctx = canvas.getContext("2d");
  const tooltip = overlay.querySelector("#graphTooltip");
  const chkOrphans = overlay.querySelector("#chkHideOrphans");
  const btnReset = overlay.querySelector("#btnGraphReset");
  const btnClose = overlay.querySelector("#btnCloseGraphModal");
  const labelNodes = overlay.querySelector("#graphNodesCount");
  const labelEdges = overlay.querySelector("#graphEdgesCount");

  let animationFrameId = null;
  let width = canvas.clientWidth || 900;
  let height = canvas.clientHeight || 600;

  function resizeCanvas() {
    width = canvas.parentElement.clientWidth;
    height = canvas.parentElement.clientHeight;
    canvas.width = width * window.devicePixelRatio;
    canvas.height = height * window.devicePixelRatio;
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
  }
  resizeCanvas();

  // 1. Düğüm ve Kenar Verilerini Çıkar
  const allNotes = state.activeNotes;
  const nodesMap = new Map();
  const titleToNode = new Map();

  allNotes.forEach(note => {
    const preset = NOTE_PRESETS.find(p => p.id === note.color) || NOTE_PRESETS[0];
    const n = {
      id: note.id,
      title: note.title || "Başlıksız Not",
      color: preset.color || "#F59E0B",
      noteRef: note,
      degree: 0,
      x: (Math.random() - 0.5) * (width * 0.6) + width / 2,
      y: (Math.random() - 0.5) * (height * 0.6) + height / 2,
      vx: 0,
      vy: 0,
      radius: 8
    };
    nodesMap.set(note.id, n);
    titleToNode.set((note.title || "").trim().toLowerCase(), n);
  });

  const rawEdges = [];
  allNotes.forEach(note => {
    const src = nodesMap.get(note.id);
    const content = note.content || "";
    const matches = content.match(/\[\[([^\]\|]+)(?:\|([^\]]+))?\]\]/g);
    if (matches && src) {
      matches.forEach(m => {
        const rawTarget = m.replace(/^\[\[/, "").replace(/\]\]$/, "").split("|")[0].trim().toLowerCase();
        const tgt = titleToNode.get(rawTarget);
        if (tgt && tgt.id !== src.id) {
          rawEdges.push({ source: src, target: tgt });
          src.degree++;
          tgt.degree++;
        }
      });
    }
  });

  // Dereceye göre dinamik yarıçap (6px - 18px)
  nodesMap.forEach(n => {
    n.radius = Math.min(18, Math.max(7, 7 + n.degree * 2.5));
  });

  let hideOrphans = false;
  let activeNodes = Array.from(nodesMap.values());
  let activeEdges = rawEdges;

  function filterGraphData() {
    if (hideOrphans) {
      activeNodes = Array.from(nodesMap.values()).filter(n => n.degree > 0);
      const activeIds = new Set(activeNodes.map(n => n.id));
      activeEdges = rawEdges.filter(e => activeIds.has(e.source.id) && activeIds.has(e.target.id));
    } else {
      activeNodes = Array.from(nodesMap.values());
      activeEdges = rawEdges;
    }
    labelNodes.textContent = `${activeNodes.length} Not`;
    labelEdges.textContent = `${activeEdges.length} Bağlantı`;
  }
  filterGraphData();

  chkOrphans.addEventListener("change", (e) => {
    hideOrphans = e.target.checked;
    filterGraphData();
  });

  // Kamera: Pan & Zoom
  let panX = 0;
  let panY = 0;
  let scale = 1.0;

  btnReset.addEventListener("click", () => {
    panX = 0;
    panY = 0;
    scale = 1.0;
  });

  // Etkileşimler
  let hoveredNode = null;
  let draggedNode = null;
  let isPanning = false;
  let startX = 0, startY = 0;

  function screenToWorld(sx, sy) {
    return {
      x: (sx - width / 2 - panX) / scale + width / 2,
      y: (sy - height / 2 - panY) / scale + height / 2
    };
  }

  canvas.addEventListener("mousedown", (e) => {
    const rect = canvas.getBoundingClientRect();
    const sx = e.clientX - rect.left;
    const sy = e.clientY - rect.top;
    const pos = screenToWorld(sx, sy);

    const hit = activeNodes.find(n => {
      const dx = n.x - pos.x;
      const dy = n.y - pos.y;
      return (dx * dx + dy * dy) <= (n.radius + 4) * (n.radius + 4);
    });

    if (hit) {
      draggedNode = hit;
      canvas.style.cursor = "grabbing";
    } else {
      isPanning = true;
      startX = sx - panX;
      startY = sy - panY;
      canvas.style.cursor = "move";
    }
  });

  window.addEventListener("mousemove", (e) => {
    const rect = canvas.getBoundingClientRect();
    const sx = e.clientX - rect.left;
    const sy = e.clientY - rect.top;
    const pos = screenToWorld(sx, sy);

    if (draggedNode) {
      draggedNode.x = pos.x;
      draggedNode.y = pos.y;
      draggedNode.vx = 0;
      draggedNode.vy = 0;
      return;
    }

    if (isPanning) {
      panX = sx - startX;
      panY = sy - startY;
      return;
    }

    // Hover kontrolü
    hoveredNode = activeNodes.find(n => {
      const dx = n.x - pos.x;
      const dy = n.y - pos.y;
      return (dx * dx + dy * dy) <= (n.radius + 4) * (n.radius + 4);
    });

    if (hoveredNode) {
      canvas.style.cursor = "pointer";
      tooltip.style.display = "block";
      tooltip.style.left = `${sx + 12}px`;
      tooltip.style.top = `${sy + 12}px`;
      tooltip.innerHTML = `<b>${escapeHtml(hoveredNode.title)}</b><br><span style="opacity:0.7;">${hoveredNode.degree} bağlantı • Tıkla ve Aç</span>`;
    } else {
      canvas.style.cursor = "grab";
      tooltip.style.display = "none";
    }
  });

  window.addEventListener("mouseup", () => {
    if (draggedNode) draggedNode = null;
    isPanning = false;
    canvas.style.cursor = "grab";
  });

  canvas.addEventListener("wheel", (e) => {
    e.preventDefault();
    const zoomFactor = e.deltaY < 0 ? 1.1 : 0.9;
    scale = Math.min(3.0, Math.max(0.25, scale * zoomFactor));
  });

  // Tıklanınca Notu Aç
  canvas.addEventListener("click", (e) => {
    const rect = canvas.getBoundingClientRect();
    const sx = e.clientX - rect.left;
    const sy = e.clientY - rect.top;
    const pos = screenToWorld(sx, sy);

    const hit = activeNodes.find(n => {
      const dx = n.x - pos.x;
      const dy = n.y - pos.y;
      return (dx * dx + dy * dy) <= (n.radius + 4) * (n.radius + 4);
    });

    if (hit && hit.noteRef) {
      cancelAnimationFrame(animationFrameId);
      overlay.remove();
      loadNoteIntoEditor(hit.noteRef, false);
      showToast(`'${hit.title}' notu açıldı`, "info");
    }
  });

  // Kapatma
  btnClose.addEventListener("click", () => {
    cancelAnimationFrame(animationFrameId);
    overlay.remove();
  });
  overlay.addEventListener("click", (e) => {
    if (e.target === overlay) {
      cancelAnimationFrame(animationFrameId);
      overlay.remove();
    }
  });

  // 2. 60 FPS Force-Directed Fizik Motoru
  const kRepulsion = 2200;
  const kSpring = 0.04;
  const springLength = 80;
  const kCenter = 0.012;
  const damping = 0.86;

  function stepPhysics() {
    const cx = width / 2;
    const cy = height / 2;

    // A. Coulomb İtmesi
    for (let i = 0; i < activeNodes.length; i++) {
      const n1 = activeNodes[i];
      for (let j = i + 1; j < activeNodes.length; j++) {
        const n2 = activeNodes[j];
        let dx = n2.x - n1.x;
        let dy = n2.y - n1.y;
        let distSq = dx * dx + dy * dy || 1;
        let dist = Math.sqrt(distSq);

        if (dist < 400) {
          let force = kRepulsion / distSq;
          let fx = (dx / dist) * force;
          let fy = (dy / dist) * force;
          n1.vx -= fx;
          n1.vy -= fy;
          n2.vx += fx;
          n2.vy += fy;
        }
      }
    }

    // B. Hooke Yay Çekimi
    for (const edge of activeEdges) {
      const n1 = edge.source;
      const n2 = edge.target;
      let dx = n2.x - n1.x;
      let dy = n2.y - n1.y;
      let dist = Math.sqrt(dx * dx + dy * dy) || 1;
      let delta = dist - springLength;
      let force = delta * kSpring;
      let fx = (dx / dist) * force;
      let fy = (dy / dist) * force;
      n1.vx += fx;
      n1.vy += fy;
      n2.vx -= fx;
      n2.vy -= fy;
    }

    // C. Merkezleme & Hız Güncelleme
    for (const n of activeNodes) {
      if (n === draggedNode) continue;
      n.vx += (cx - n.x) * kCenter;
      n.vy += (cy - n.y) * kCenter;
      n.vx *= damping;
      n.vy *= damping;
      n.x += n.vx;
      n.y += n.vy;
    }
  }

  // 3. Render Döngüsü
  function draw() {
    stepPhysics();

    ctx.clearRect(0, 0, width, height);
    ctx.save();
    ctx.translate(width / 2 + panX, height / 2 + panY);
    ctx.scale(scale, scale);
    ctx.translate(-width / 2, -height / 2);

    // Bağlı komşu kümesi
    const connectedToHover = new Set();
    if (hoveredNode) {
      connectedToHover.add(hoveredNode.id);
      activeEdges.forEach(e => {
        if (e.source.id === hoveredNode.id) connectedToHover.add(e.target.id);
        if (e.target.id === hoveredNode.id) connectedToHover.add(e.source.id);
      });
    }

    // A. Kenarları Çiz
    for (const edge of activeEdges) {
      const isHighlighted = hoveredNode && (edge.source.id === hoveredNode.id || edge.target.id === hoveredNode.id);
      const isDimmed = hoveredNode && !isHighlighted;

      ctx.beginPath();
      ctx.moveTo(edge.source.x, edge.source.y);
      ctx.lineTo(edge.target.x, edge.target.y);
      ctx.strokeStyle = isHighlighted ? "var(--primary)" : (isDimmed ? "rgba(148,163,184,0.08)" : "rgba(148,163,184,0.25)");
      ctx.lineWidth = isHighlighted ? 2.5 : 1.2;
      ctx.stroke();
    }

    // B. Düğümleri Çiz
    for (const n of activeNodes) {
      const isTarget = hoveredNode && n.id === hoveredNode.id;
      const isConnected = hoveredNode && connectedToHover.has(n.id);
      const isDimmed = hoveredNode && !isConnected;

      ctx.save();
      ctx.globalAlpha = isDimmed ? 0.18 : 1.0;

      // Glow
      if (isTarget) {
        ctx.beginPath();
        ctx.arc(n.x, n.y, n.radius + 8, 0, Math.PI * 2);
        ctx.fillStyle = "rgba(245,158,11,0.25)";
        ctx.fill();
      }

      // Düğüm Gövdesi
      ctx.beginPath();
      ctx.arc(n.x, n.y, n.radius, 0, Math.PI * 2);
      ctx.fillStyle = n.color || "#38BDF8";
      ctx.fill();
      ctx.strokeStyle = isTarget ? "#FFFFFF" : (isConnected ? "var(--primary)" : "rgba(255,255,255,0.2)");
      ctx.lineWidth = isTarget ? 3 : 1.5;
      ctx.stroke();

      // Düğüm Başlığı (Label)
      if (scale > 0.6 || isConnected || n.degree > 1) {
        ctx.font = `${isTarget ? 'bold 12px' : '11px'} 'Segoe UI',sans-serif`;
        ctx.fillStyle = isTarget ? "#FFFFFF" : (isDimmed ? "rgba(248,250,252,0.3)" : "#E2E8F0");
        ctx.textAlign = "center";
        ctx.fillText(n.title, n.x, n.y + n.radius + 14);
      }

      ctx.restore();
    }

    ctx.restore();
    animationFrameId = requestAnimationFrame(draw);
  }

  animationFrameId = requestAnimationFrame(draw);
}

// ===================================================
// OPTİK / KAMERA İLE VERİ AKTARIMI (AIR-GAPPED STREAM)
// ===================================================

/**
 * IEEE 802.3 CRC32 hesaplama fonksiyonu.
 * Java/Android java.util.zip.CRC32 ile birebir aynı unsigned 32-bit checksum üretir.
 */
function computeCrc32(str) {
  let table = window.__crc32Table;
  if (!table) {
    table = new Uint32Array(256);
    for (let i = 0; i < 256; i++) {
      let c = i;
      for (let k = 0; k < 8; k++) {
        c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
      }
      table[i] = c;
    }
    window.__crc32Table = table;
  }
  const bytes = new TextEncoder().encode(str);
  let crc = 0 ^ (-1);
  for (let i = 0; i < bytes.length; i++) {
    crc = (crc >>> 8) ^ table[(crc ^ bytes[i]) & 0xFF];
  }
  return (crc ^ (-1)) >>> 0;
}

let opticalStreamState = {
  frames: [],
  currentIndex: 0,
  isPlaying: true,
  fps: 7,
  timerId: null,
  activeNoteTitle: ""
};

/**
 * Dinamik optik akış projektörünü başlatır.
 * Aktif not ve kategorileri JSON olarak paketler, ~220 baytlık parçalara böler
 * ve Android OpticalTransferManager.kt ile uyumlu çerçeveler üretir.
 */
function startOpticalStream(noteId) {
  const note = (noteId ? (state.activeNotes.find(n => n.id === noteId) || state.allNotes.find(n => n.id === noteId)) : null) ||
               (state.activeNoteId ? (state.activeNotes.find(n => n.id === state.activeNoteId) || state.allNotes.find(n => n.id === state.activeNoteId)) : null) ||
               (state.activeNotes && state.activeNotes.length > 0 ? state.activeNotes[0] : null);

  if (!note) {
    showToast("Aktarılacak not bulunamadı!", "error");
    return;
  }

  // Not ve kategori JSON paketi
  const payloadData = {
    notes: [note],
    categories: state.categories || []
  };
  const payloadStr = JSON.stringify(payloadData);
  const totalCrc = computeCrc32(payloadStr);

  // Android kamera tarayıcısının (<10ms) en rahat okuyabildiği boyut: ~220 karakter
  const chunkSize = 220;
  const chunks = [];
  for (let i = 0; i < payloadStr.length; i += chunkSize) {
    chunks.push(payloadStr.slice(i, i + chunkSize));
  }

  const totalFrames = chunks.length;
  // Android OpticalTransferManager.kt ile %100 eşleşen format: { s, t, d, c }
  const frames = chunks.map((chunk, idx) => JSON.stringify({
    s: idx + 1,
    t: totalFrames,
    d: chunk,
    c: totalCrc
  }));

  opticalStreamState.frames = frames;
  opticalStreamState.currentIndex = 0;
  opticalStreamState.isPlaying = true;
  opticalStreamState.fps = 7;
  opticalStreamState.activeNoteTitle = note.title || "Adsız Not";

  const modal = document.getElementById("modalOpticalStream");
  const qrContainer = document.getElementById("opticalQrContainer");
  const frameBadge = document.getElementById("opticalFrameBadge");
  const progressBar = document.getElementById("opticalProgressBar");
  const btnPlayPause = document.getElementById("btnOpticalPlayPause");
  const btnPrev = document.getElementById("btnOpticalPrev");
  const btnNext = document.getElementById("btnOpticalNext");
  const btnClose = document.getElementById("btnCloseOpticalStream");
  const fpsChips = document.querySelectorAll("#modalOpticalStream .fps-chip");

  if (!modal) return;

  function renderCurrentFrame() {
    if (!opticalStreamState.frames || opticalStreamState.frames.length === 0) return;
    const curIdx = opticalStreamState.currentIndex;
    const frameData = opticalStreamState.frames[curIdx];

    if (qrContainer) {
      qrContainer.innerHTML = renderSimpleQr(frameData);
    }
    if (frameBadge) {
      frameBadge.textContent = `Kare ${curIdx + 1} / ${totalFrames}`;
    }
    if (progressBar) {
      const pct = Math.round(((curIdx + 1) / totalFrames) * 100);
      progressBar.style.width = `${pct}%`;
    }
  }

  function startLoop() {
    stopLoop();
    const intervalMs = Math.round(1000 / opticalStreamState.fps);
    opticalStreamState.timerId = setInterval(() => {
      opticalStreamState.currentIndex = (opticalStreamState.currentIndex + 1) % totalFrames;
      renderCurrentFrame();
    }, intervalMs);
  }

  function stopLoop() {
    if (opticalStreamState.timerId) {
      clearInterval(opticalStreamState.timerId);
      opticalStreamState.timerId = null;
    }
  }

  function updatePlayPauseBtn() {
    if (btnPlayPause) {
      btnPlayPause.textContent = opticalStreamState.isPlaying ? "⏸ Duraklat" : "▶ Oynat";
    }
  }

  // İlk kareyi çiz ve döngüyü başlat
  renderCurrentFrame();
  startLoop();
  updatePlayPauseBtn();

  // FPS çipleri aktifliği
  fpsChips.forEach(chip => {
    const fpsVal = parseInt(chip.dataset.fps, 10);
    if (fpsVal === opticalStreamState.fps) {
      chip.classList.add("active");
    } else {
      chip.classList.remove("active");
    }
  });

  // Olay Dinleyicileri (Tek seferlik bağlama)
  if (!modal.__opticalEventsBound) {
    modal.__opticalEventsBound = true;

    btnPlayPause?.addEventListener("click", () => {
      opticalStreamState.isPlaying = !opticalStreamState.isPlaying;
      updatePlayPauseBtn();
      if (opticalStreamState.isPlaying) {
        startLoop();
      } else {
        stopLoop();
      }
    });

    btnPrev?.addEventListener("click", () => {
      stopLoop();
      opticalStreamState.isPlaying = false;
      updatePlayPauseBtn();
      opticalStreamState.currentIndex = (opticalStreamState.currentIndex - 1 + opticalStreamState.frames.length) % opticalStreamState.frames.length;
      renderCurrentFrame();
    });

    btnNext?.addEventListener("click", () => {
      stopLoop();
      opticalStreamState.isPlaying = false;
      updatePlayPauseBtn();
      opticalStreamState.currentIndex = (opticalStreamState.currentIndex + 1) % opticalStreamState.frames.length;
      renderCurrentFrame();
    });

    fpsChips.forEach(chip => {
      chip.addEventListener("click", () => {
        fpsChips.forEach(c => c.classList.remove("active"));
        chip.classList.add("active");
        const newFps = parseInt(chip.dataset.fps, 10) || 7;
        opticalStreamState.fps = newFps;
        if (opticalStreamState.isPlaying) {
          startLoop();
        }
      });
    });

    const closeModal = () => {
      stopLoop();
      opticalStreamState.isPlaying = false;
      modal.classList.remove("open");
    };

    btnClose?.addEventListener("click", closeModal);

    modal.addEventListener("click", (e) => {
      if (e.target === modal) {
        closeModal();
      }
    });
  }

  // Modalı göster
  modal.classList.add("open");
  showToast(`📡 "${opticalStreamState.activeNoteTitle}" için optik veri akışı başladı (${totalFrames} kare)`, "info");
}

// ===================================================
// FAZ 3.1: SAĞ TIK BAĞLAM MENÜSÜ (CONTEXT MENU)
// ===================================================
let activeContextNote = null;

function closeNoteContextMenu() {
  const menu = document.getElementById("noteContextMenu");
  if (menu) {
    menu.style.display = "none";
  }
  activeContextNote = null;
}

function openNoteContextMenu(e, note) {
  closeNoteContextMenu();
  activeContextNote = note;
  state.selectedNoteId = note.id;

  const menu = document.getElementById("noteContextMenu");
  if (!menu) return;

  // Sabitle / Kasaya Taşı / Sil Etiketleri
  const pinLabel = document.getElementById("ctxPinLabel");
  if (pinLabel) {
    pinLabel.textContent = note.isPinned ? "Sabitlemeyi Kaldır" : "Sabitle";
  }

  const lockLabel = document.getElementById("ctxLockLabel");
  if (lockLabel) {
    lockLabel.textContent = note.isLocked ? "Kasadan Çıkar (Kilidi Aç)" : "Kasaya Taşı / Kilitle";
  }

  const delLabel = document.getElementById("ctxDeleteLabel");
  if (delLabel) {
    delLabel.textContent = note.isDeleted ? "Kalıcı Olarak Sil" : "Çöp Kutusuna Taşı";
  }

  // 14 Pastel & Koyu Renk Paleti (NOTE_PRESETS)
  const paletteContainer = document.getElementById("ctxColorPalette");
  if (paletteContainer) {
    paletteContainer.innerHTML = "";
    NOTE_PRESETS.forEach(preset => {
      const chip = document.createElement("div");
      chip.className = "ctx-color-chip";
      chip.style.backgroundColor = preset.color;
      chip.title = preset.name;
      if (note.color === preset.id) {
        chip.style.outline = "2px solid #FFFFFF";
      }
      chip.addEventListener("click", async (ev) => {
        ev.stopPropagation();
        note.color = preset.id;
        applyNoteColor(preset.id);
        await callIpc("saveNote", { note });
        renderNotesList();
        closeNoteContextMenu();
        showToast(`Not rengi "${preset.name}" yapıldı`, "info");
      });
      paletteContainer.appendChild(chip);
    });
  }

  // Koordinatları hesapla (ekran sınırlarını aşmayacak şekilde)
  const menuWidth = 220;
  const menuHeight = 280;
  let posX = e.clientX;
  let posY = e.clientY;

  if (posX + menuWidth > window.innerWidth) {
    posX = window.innerWidth - menuWidth - 12;
  }
  if (posY + menuHeight > window.innerHeight) {
    posY = window.innerHeight - menuHeight - 12;
  }

  menu.style.left = `${posX}px`;
  menu.style.top = `${posY}px`;
  menu.style.display = "flex";
}

function initContextMenuEvents() {
  const menu = document.getElementById("noteContextMenu");
  if (!menu || menu.__initialized) return;
  menu.__initialized = true;

  document.getElementById("ctxPinNote")?.addEventListener("click", async (e) => {
    e.stopPropagation();
    if (!activeContextNote) return;
    activeContextNote.isPinned = !activeContextNote.isPinned;
    await callIpc("saveNote", { note: activeContextNote });
    showToast(activeContextNote.isPinned ? "Not sabitlendi" : "Sabitleme kaldırıldı", "info");
    closeNoteContextMenu();
    loadAllData();
  });

  document.getElementById("ctxLockNote")?.addEventListener("click", async (e) => {
    e.stopPropagation();
    if (!activeContextNote) return;
    activeContextNote.isLocked = !activeContextNote.isLocked;
    await callIpc("saveNote", { note: activeContextNote });
    showToast(activeContextNote.isLocked ? "Not kilitli kasaya taşındı" : "Not kasadan çıkarıldı", "info");
    closeNoteContextMenu();
    loadAllData();
  });

  document.getElementById("ctxDuplicateNote")?.addEventListener("click", async (e) => {
    e.stopPropagation();
    if (!activeContextNote) return;
    state.selectedNoteId = activeContextNote.id;
    closeNoteContextMenu();
    await duplicateCurrentNote();
  });

  document.getElementById("ctxDeleteNote")?.addEventListener("click", async (e) => {
    e.stopPropagation();
    if (!activeContextNote) return;
    const note = activeContextNote;
    closeNoteContextMenu();
    if (note.isDeleted) {
      await callIpc("deletePermanently", { id: note.id });
      showToast("Not kalıcı olarak silindi", "danger");
    } else {
      await callIpc("moveToTrash", { id: note.id });
      showToast("Not çöp kutusuna taşındı", "warning");
    }
    loadAllData();
  });

  document.addEventListener("click", (e) => {
    if (!menu.contains(e.target)) {
      closeNoteContextMenu();
    }
  });

  window.addEventListener("blur", closeNoteContextMenu);
}

// ===================================================
// FAZ 3.2: ETİKET YÖNETİMİ MODALI (TAG MANAGER)
// ===================================================
function openTagManagerModal() {
  const modal = document.getElementById("modalTagManager");
  const listContainer = document.getElementById("tagManagerList");
  if (!modal || !listContainer) return;

  renderTagManagerList();
  modal.classList.add("open");
}

function renderTagManagerList() {
  const listContainer = document.getElementById("tagManagerList");
  if (!listContainer) return;
  listContainer.innerHTML = "";

  const allNotes = [...state.activeNotes, ...state.archivedNotes, ...state.trashNotes];
  const tagNoteMap = new Map(); // tag -> Set of noteIds

  allNotes.forEach(note => {
    const text = `${note.title || ""} ${note.content || ""}`;
    const matches = text.match(/#([a-zA-ZçğıöşüÇĞİÖŞÜ0-9_-]+)/g);
    const setOfTags = new Set();
    if (matches) {
      matches.forEach(m => setOfTags.add(m.toLowerCase()));
    }
    if (Array.isArray(note.tags)) {
      note.tags.forEach(t => {
        const clean = t.startsWith("#") ? t.toLowerCase() : `#${t.toLowerCase()}`;
        setOfTags.add(clean);
      });
    }

    setOfTags.forEach(tag => {
      if (!tagNoteMap.has(tag)) tagNoteMap.set(tag, new Set());
      tagNoteMap.get(tag).add(note.id);
    });
  });

  if (tagNoteMap.size === 0) {
    listContainer.innerHTML = `<div style="text-align:center; padding:30px; color:var(--text-faint);">Henüz hiç etiket bulunamadı.<br><span style="font-size:12px;">Notlarınıza #etiket yazarak etiket oluşturabilirsiniz.</span></div>`;
    return;
  }

  const sortedTags = Array.from(tagNoteMap.keys()).sort();

  sortedTags.forEach(tag => {
    const noteIds = tagNoteMap.get(tag);
    const row = document.createElement("div");
    row.className = "tag-manage-row";

    row.innerHTML = `
      <div class="tag-manage-info">
        <span class="tag-manage-badge">${escapeHtml(tag)}</span>
        <span class="tag-manage-count">${noteIds.size} not</span>
      </div>
      <div class="tag-manage-actions">
        <button class="btn-secondary-sm btn-rename-tag" title="Etiketi Yeniden Adlandır">✏️ Yeniden Adlandır</button>
        <button class="btn-danger-sm btn-delete-tag" title="Etiketi Tüm Notlardan Sil" style="padding:4px 8px; font-size:12px; background:rgba(239,68,68,0.15); color:#F87171; border:none; border-radius:6px; cursor:pointer;">🗑️ Sil</button>
      </div>
    `;

    // Yeniden Adlandır
    row.querySelector(".btn-rename-tag").addEventListener("click", async () => {
      const rawOld = tag.replace(/^#/, "");
      const newName = prompt(`'${tag}' etiketinin yeni adını girin (Örn: #yeni_etiket):`, tag);
      if (!newName || !newName.trim()) return;
      const cleanNew = (newName.trim().startsWith("#") ? newName.trim() : `#${newName.trim()}`).toLowerCase();
      if (cleanNew === tag) return;

      const rawNew = cleanNew.replace(/^#/, "");
      let updatedCount = 0;

      for (const note of allNotes) {
        if (!noteIds.has(note.id)) continue;
        let changed = false;

        const regex = new RegExp(`#${rawOld}(?![a-zA-ZçğıöşüÇĞİÖŞÜ0-9_-])`, "gi");
        if (note.content && regex.test(note.content)) {
          note.content = note.content.replace(regex, cleanNew);
          changed = true;
        }
        if (note.title && regex.test(note.title)) {
          note.title = note.title.replace(regex, cleanNew);
          changed = true;
        }

        if (Array.isArray(note.tags)) {
          note.tags = note.tags.map(t => {
            const low = t.toLowerCase();
            if (low === tag || low === rawOld) {
              changed = true;
              return rawNew;
            }
            return t;
          });
        }

        if (changed) {
          await callIpc("saveNote", { note });
          updatedCount++;
        }
      }

      showToast(`'${tag}' etiketi '${cleanNew}' olarak ${updatedCount} notta güncellendi!`, "success");
      await loadAllData();
      renderTagManagerList();
    });

    // Sil
    row.querySelector(".btn-delete-tag").addEventListener("click", async () => {
      const ok = confirm(`'${tag}' etiketini ${noteIds.size} nottan kaldırmak istediğinize emin misiniz?`);
      if (!ok) return;

      const rawTag = tag.replace(/^#/, "");
      let updatedCount = 0;

      for (const note of allNotes) {
        if (!noteIds.has(note.id)) continue;
        let changed = false;

        const regex = new RegExp(`\\s*#${rawTag}(?![a-zA-ZçğıöşüÇĞİÖŞÜ0-9_-])`, "gi");
        if (note.content && regex.test(note.content)) {
          note.content = note.content.replace(regex, "");
          changed = true;
        }
        if (note.title && regex.test(note.title)) {
          note.title = note.title.replace(regex, "");
          changed = true;
        }
        if (Array.isArray(note.tags)) {
          const prevLen = note.tags.length;
          note.tags = note.tags.filter(t => t.toLowerCase() !== tag && t.toLowerCase() !== rawTag);
          if (note.tags.length !== prevLen) changed = true;
        }

        if (changed) {
          await callIpc("saveNote", { note });
          updatedCount++;
        }
      }

      showToast(`'${tag}' etiketi ${updatedCount} nottan kaldırıldı.`, "info");
      await loadAllData();
      renderTagManagerList();
    });

    listContainer.appendChild(row);
  });
}

// ===================================================
// FAZ 3.3: SES DOSYASI OYNATICI ÇUBUĞU (AUDIO PLAYER BAR)
// ===================================================
let globalAudio = null;
let isAudioPlaying = false;
let audioBarEventsBound = false;

function detectAudioSource(note) {
  if (!note) return null;

  // 1. Eklerde ses var mı?
  if (Array.isArray(note.attachments)) {
    const audioExt = [".mp3", ".wav", ".m4a", ".ogg", ".aac", ".flac"];
    const found = note.attachments.find(att => {
      const low = String(att).toLowerCase();
      return audioExt.some(ext => low.endsWith(ext)) || low.includes("audio/");
    });
    if (found) {
      return { url: found, name: found.split(/[\\/]/).pop() || "Ekli Ses Dosyası" };
    }
  }

  // 2. İçerikte ses linki veya verisi var mı?
  if (note.content) {
    const urlMatch = note.content.match(/(https?:\/\/[^\s\)]+\.(mp3|wav|m4a|ogg|aac))/i);
    if (urlMatch) {
      const url = urlMatch[1];
      return { url, name: url.split("/").pop() || "Ağ Ses Dosyası" };
    }
    const dataMatch = note.content.match(/(data:audio\/[a-zA-Z0-9]+;base64,[^\s\)"']+)/i);
    if (dataMatch) {
      return { url: dataMatch[1], name: "Gömülü Ses Kaydı" };
    }
  }

  return null;
}

function updateAudioPlayerForNote(note) {
  const bar = document.getElementById("audioPlayerBar");
  if (!bar) return;

  const audioSrc = detectAudioSource(note);
  if (!audioSrc) {
    stopAudioPlayer();
    bar.style.display = "none";
    return;
  }

  bar.style.display = "flex";
  const titleEl = document.getElementById("audioPlayerTitle");
  if (titleEl) titleEl.textContent = audioSrc.name;

  setupAudioPlayer(audioSrc.url);
}

function stopAudioPlayer() {
  if (globalAudio) {
    globalAudio.pause();
    globalAudio.currentTime = 0;
  }
  isAudioPlaying = false;
  const playBtn = document.getElementById("btnAudioPlayPause");
  if (playBtn) playBtn.textContent = "▶";
}

function setupAudioPlayer(src) {
  if (globalAudio) {
    globalAudio.pause();
    globalAudio = null;
  }

  const audio = new Audio(src);
  globalAudio = audio;
  isAudioPlaying = false;

  const playBtn = document.getElementById("btnAudioPlayPause");
  const timeEl = document.getElementById("audioPlayerTime");
  const seekBar = document.getElementById("audioSeekBar");
  const muteBtn = document.getElementById("btnAudioMute");
  const volumeBar = document.getElementById("audioVolumeBar");

  if (playBtn) playBtn.textContent = "▶";
  if (seekBar) seekBar.value = 0;

  const formatTime = (secs) => {
    if (isNaN(secs) || secs < 0) return "00:00";
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    return `${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
  };

  audio.addEventListener("loadedmetadata", () => {
    if (timeEl) {
      timeEl.textContent = `00:00 / ${formatTime(audio.duration)}`;
    }
  });

  audio.addEventListener("timeupdate", () => {
    if (timeEl && audio.duration) {
      timeEl.textContent = `${formatTime(audio.currentTime)} / ${formatTime(audio.duration)}`;
    }
    if (seekBar && audio.duration) {
      seekBar.value = (audio.currentTime / audio.duration) * 100;
    }
  });

  audio.addEventListener("ended", () => {
    isAudioPlaying = false;
    if (playBtn) playBtn.textContent = "▶";
    if (seekBar) seekBar.value = 0;
  });

  if (!audioBarEventsBound) {
    audioBarEventsBound = true;

    playBtn?.addEventListener("click", () => {
      if (!globalAudio) return;
      if (globalAudio.paused) {
        globalAudio.play().then(() => {
          isAudioPlaying = true;
          if (playBtn) playBtn.textContent = "⏸";
        }).catch(err => {
          showToast("Ses oynatılamadı: " + err.message, "danger");
        });
      } else {
        globalAudio.pause();
        isAudioPlaying = false;
        if (playBtn) playBtn.textContent = "▶";
      }
    });

    seekBar?.addEventListener("input", (e) => {
      if (!globalAudio || !globalAudio.duration) return;
      const pct = parseFloat(e.target.value);
      globalAudio.currentTime = (pct / 100) * globalAudio.duration;
    });

    muteBtn?.addEventListener("click", () => {
      if (!globalAudio) return;
      globalAudio.muted = !globalAudio.muted;
      muteBtn.textContent = globalAudio.muted ? "🔇" : "🔊";
    });

    volumeBar?.addEventListener("input", (e) => {
      if (!globalAudio) return;
      globalAudio.volume = parseFloat(e.target.value) / 100;
    });
  }
}
