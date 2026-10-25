// Not Defterim Renderer Logic (Electron)

const api = window.api;

// State
let allNotes = [];
let displayedNotes = [];
let categories = [];
let selectedNav = 'ALL';
let selectedCategoryId = null;
let selectedTag = null;
let currentNote = null;
let searchQuery = '';
let sortOrder = 'MODIFIED_DESC';
let isMultiSelectMode = false;
let selectedNoteIds = new Set();
let viewMode = 'SPLIT';
let isDarkTheme = true;
let isVaultUnlocked = false;

// Undo / Redo Stacks for Editor
let undoStack = [];
let redoStack = [];
let lastSavedState = { title: '', content: '' };
let autoSaveTimeout = null;

// Drawing Studio State
let drawTool = 'PEN';
let drawColor = '#3b82f6';
let drawStrokeWidth = 4;
let drawPattern = 'BLANK';
let drawElements = [];
let drawRedoStack = [];
let isDrawing = false;
let drawStartPos = { x: 0, y: 0 };
let currentDrawPoints = [];

// Color Presets (Matching Android / Desktop Compose)
const COLOR_PRESETS = [
  { name: 'Varsayılan', value: 0, hex: '#131d2e', lightHex: '#ffffff' },
  { name: 'Güneş Sarısı', value: -13159, hex: '#423800', lightHex: '#fff9c4' },
  { name: 'Mercan Pembesi', value: -13056, hex: '#4a181b', lightHex: '#ffcdd2' },
  { name: 'Gökyüzü Mavisi', value: -4465413, hex: '#103657', lightHex: '#bbdefb' },
  { name: 'Nane Yeşili', value: -3611003, hex: '#143d1a', lightHex: '#c8e6c9' },
  { name: 'Lavanta Moru', value: -1982841, hex: '#391945', lightHex: '#e1bee7' },
  { name: 'Şeftali Turuncusu', value: -8014, hex: '#472800', lightHex: '#ffe0b2' },
  { name: 'Zümrüt Yeşili', value: -3015003, hex: '#064e3b', lightHex: '#d1fae5' },
  { name: 'Koyu Gri', value: -13158601, hex: '#1f2937', lightHex: '#374151' }
];

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', async () => {
  setupEventListeners();
  setupDrawingCanvas();
  await refreshAll();

  // Periodic Reminder Checker (Every 10 seconds)
  setInterval(checkReminders, 10000);

  // Sync Server State Listener
  api.sync.onStateChanged((state) => {
    updateSyncDialogUI(state);
  });
});

// Toast Banner Utility
function showToast(msg) {
  const toast = document.getElementById('toastBanner');
  toast.textContent = msg;
  toast.classList.add('show');
  setTimeout(() => toast.classList.remove('show'), 3500);
}

// -------------------------------------------------------------
// DATA LOADING & REFRESH
// -------------------------------------------------------------
async function refreshAll() {
  categories = await api.db.getAllCategories();
  isVaultUnlocked = await api.crypto.isVaultUnlocked();

  // Fetch active, archived, trash, reminder notes
  const active = await api.db.getActiveNotes();
  const archived = await api.db.getArchivedNotes();
  const trash = await api.db.getTrashNotes();
  const reminders = await api.db.getReminderNotes();

  // Update Badges
  document.getElementById('countAll').textContent = active.length;
  document.getElementById('countPinned').textContent = active.filter(n => n.isPinned).length;
  document.getElementById('countVault').textContent = active.filter(n => n.isLocked).length;
  document.getElementById('countReminders').textContent = reminders.length;
  document.getElementById('countArchive').textContent = archived.length;
  document.getElementById('countTrash').textContent = trash.length;

  renderCategoriesSidebar();
  renderHashtagsSidebar(active);

  // Filter based on selectedNav
  let list = [];
  if (selectedNav === 'ALL') list = active;
  else if (selectedNav === 'PINNED') list = active.filter(n => n.isPinned);
  else if (selectedNav === 'VAULT') list = active.filter(n => n.isLocked);
  else if (selectedNav === 'REMINDERS') list = reminders;
  else if (selectedNav === 'ARCHIVE') list = archived;
  else if (selectedNav === 'TRASH') list = trash;

  if (selectedCategoryId !== null) {
    list = list.filter(n => n.categoryId === selectedCategoryId);
  }

  if (selectedTag !== null) {
    list = list.filter(n => (n.content || '').includes('#' + selectedTag));
  }

  if (searchQuery.trim().length > 0) {
    const q = searchQuery.toLowerCase();
    list = list.filter(n => (n.title || '').toLowerCase().includes(q) || (n.content || '').toLowerCase().includes(q));
  }

  // Sorting
  list.sort((a, b) => {
    if (a.isPinned !== b.isPinned && selectedNav === 'ALL') {
      return a.isPinned ? -1 : 1;
    }
    switch (sortOrder) {
      case 'MODIFIED_ASC': return (a.updatedAt || a.timestamp) - (b.updatedAt || b.timestamp);
      case 'CREATED_DESC': return (b.createdAt || b.timestamp) - (a.createdAt || a.timestamp);
      case 'CREATED_ASC': return (a.createdAt || a.timestamp) - (b.createdAt || b.timestamp);
      case 'TITLE_AZ': return (a.title || '').localeCompare(b.title || '');
      case 'TITLE_ZA': return (b.title || '').localeCompare(a.title || '');
      case 'MODIFIED_DESC':
      default:
        return (b.updatedAt || b.timestamp) - (a.updatedAt || a.timestamp);
    }
  });

  displayedNotes = list;
  renderNotesList();

  // If a note was selected, reload it
  if (currentNote) {
    const fresh = await api.db.getNoteById(currentNote.id);
    if (fresh && !fresh.isDeleted) {
      loadNoteIntoEditor(fresh, false);
    } else if (fresh && fresh.isDeleted && selectedNav !== 'TRASH') {
      clearEditor();
    }
  }
}

// -------------------------------------------------------------
// SIDEBAR RENDERING
// -------------------------------------------------------------
function renderCategoriesSidebar() {
  const container = document.getElementById('categoryList');
  container.innerHTML = '';
  categories.forEach(cat => {
    const item = document.createElement('div');
    item.className = 'cat-item' + (selectedCategoryId === cat.id ? ' active' : '');
    const colorHex = cat.color !== 0 ? '#' + (cat.color >>> 0).toString(16).slice(-6) : '#3b82f6';
    item.innerHTML = `
      <span class="cat-color-dot" style="background-color: ${colorHex}"></span>
      <span style="flex:1;">${escapeHtml(cat.name)}</span>
    `;
    item.onclick = () => {
      selectedCategoryId = selectedCategoryId === cat.id ? null : cat.id;
      selectedTag = null;
      refreshAll();
    };
    container.appendChild(item);
  });
}

function renderHashtagsSidebar(notes) {
  const tagListEl = document.getElementById('tagList');
  const tagsHeader = document.getElementById('tagsHeader');
  tagListEl.innerHTML = '';

  const tagCounts = new Map();
  const tagRegex = /#([a-zA-Z0-9çğıöşüÇĞİÖŞÜ_-]+)/g;

  notes.forEach(note => {
    const text = (note.title || '') + ' ' + (note.content || '');
    const matches = text.match(tagRegex);
    if (matches) {
      matches.forEach(m => {
        const raw = m.slice(1);
        tagCounts.set(raw, (tagCounts.get(raw) || 0) + 1);
      });
    }
  });

  const sortedTags = Array.from(tagCounts.keys()).sort();
  if (sortedTags.length === 0) {
    tagsHeader.style.display = 'none';
    return;
  }

  tagsHeader.style.display = 'flex';
  sortedTags.forEach(tag => {
    const item = document.createElement('div');
    item.className = 'tag-item' + (selectedTag === tag ? ' active' : '');
    item.innerHTML = `
      <span style="color: #f59e0b; font-weight: 600;">#</span>
      <span style="flex:1;">${escapeHtml(tag)}</span>
      <span class="badge">${tagCounts.get(tag)}</span>
    `;
    item.onclick = () => {
      selectedTag = selectedTag === tag ? null : tag;
      refreshAll();
    };
    tagListEl.appendChild(item);
  });
}

// -------------------------------------------------------------
// NOTES LIST RENDERING
// -------------------------------------------------------------
function renderNotesList() {
  const container = document.getElementById('noteCardsContainer');
  container.innerHTML = '';
  document.getElementById('notesCountLabel').textContent = `${displayedNotes.length} Not`;

  const btnEmptyTrash = document.getElementById('btnEmptyTrash');
  btnEmptyTrash.style.display = (selectedNav === 'TRASH' && displayedNotes.length > 0) ? 'block' : 'none';

  if (displayedNotes.length === 0) {
    container.innerHTML = `
      <div style="text-align: center; color: var(--text-subtle); padding: 40px 10px;">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="width: 44px; height: 44px; margin-bottom: 8px; opacity: 0.5;">
          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
        </svg>
        <div>Henüz not bulunmuyor</div>
      </div>
    `;
    return;
  }

  displayedNotes.forEach(note => {
    const isSelected = currentNote && currentNote.id === note.id;
    const card = document.createElement('div');
    card.className = 'note-card' + (isSelected ? ' selected' : '');

    // Card background custom tint
    if (note.color !== 0) {
      const preset = COLOR_PRESETS.find(p => p.value === note.color);
      if (preset) {
        card.style.backgroundColor = isDarkTheme ? preset.hex : preset.lightHex;
      }
    }

    const cat = categories.find(c => c.id === note.categoryId);
    const catDot = cat ? `<span class="note-card-cat-badge">${escapeHtml(cat.name)}</span>` : '';

    const isChecked = selectedNoteIds.has(note.id);
    const checkboxHtml = isMultiSelectMode
      ? `<input type="checkbox" class="card-checkbox" ${isChecked ? 'checked' : ''}>`
      : '';

    let reminderHtml = '';
    if (note.reminderTime && note.reminderTime > 0) {
      const d = new Date(note.reminderTime);
      reminderHtml = `<span title="Hatırlatıcı: ${d.toLocaleString('tr-TR')}"><svg class="card-icon reminder" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path></svg></span>`;
    }

    const pinnedHtml = note.isPinned
      ? `<svg class="card-icon pinned" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="17" x2="12" y2="22"></line><path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z"></path></svg>`
      : '';

    const lockedHtml = note.isLocked
      ? `<svg class="card-icon locked" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>`
      : '';

    let snippet = note.content || '';
    if (note.isLocked && !isVaultUnlocked) {
      snippet = '🔒 Gizli Kasa İçeriği';
    } else {
      snippet = snippet.replace(/[#*`_~\[\]]/g, '').trim();
    }

    const dateStr = formatDate(note.updatedAt || note.timestamp);

    card.innerHTML = `
      <div class="note-card-top">
        ${checkboxHtml}
        <div class="note-card-title">${escapeHtml(note.title || 'Başlıksız')}</div>
        <div class="note-card-badges">
          ${reminderHtml}
          ${pinnedHtml}
          ${lockedHtml}
        </div>
      </div>
      <div class="note-card-snippet">${escapeHtml(snippet || 'İçerik yok')}</div>
      <div class="note-card-footer">
        <span>${dateStr}</span>
        ${catDot}
      </div>
    `;

    card.onclick = (e) => {
      if (e.target.classList.contains('card-checkbox')) {
        if (e.target.checked) selectedNoteIds.add(note.id);
        else selectedNoteIds.delete(note.id);
        updateMultiSelectUI();
        return;
      }
      if (isMultiSelectMode) {
        if (selectedNoteIds.has(note.id)) selectedNoteIds.delete(note.id);
        else selectedNoteIds.add(note.id);
        updateMultiSelectUI();
        renderNotesList();
        return;
      }
      loadNoteIntoEditor(note, true);
    };

    container.appendChild(card);
  });
}

// -------------------------------------------------------------
// EDITOR ENGINE & MARKDOWN PARSER
// -------------------------------------------------------------
async function loadNoteIntoEditor(note, savePrevious = true) {
  if (savePrevious && currentNote && currentNote.id !== note.id) {
    await saveCurrentNoteImmediate();
  }

  currentNote = { ...note };

  let contentToDisplay = note.content || '';
  if (note.isLocked && !isVaultUnlocked) {
    contentToDisplay = '🔒 Gizli Kasa İçeriği. Düzenlemek için şifreyi giriniz.';
  } else if (note.isLocked && isVaultUnlocked) {
    contentToDisplay = await api.crypto.decryptContent(note.content);
  }

  document.getElementById('editorTitleInput').value = note.title || '';
  document.getElementById('editorTextarea').value = contentToDisplay;

  lastSavedState = { title: note.title || '', content: contentToDisplay };
  undoStack = [];
  redoStack = [];
  updateUndoRedoButtons();

  document.getElementById('editorEmptyState').style.display = 'none';
  document.getElementById('editorActiveState').style.display = 'flex';

  updateEditorHeaderUI();
  updateLiveMarkdownPreview();
  updateBacklinks();
  updateAttachmentsUI();
  updateCharCount();
  renderNotesList();
}

function clearEditor() {
  currentNote = null;
  document.getElementById('editorEmptyState').style.display = 'flex';
  document.getElementById('editorActiveState').style.display = 'none';
  renderNotesList();
}

function updateEditorHeaderUI() {
  if (!currentNote) return;

  // Category Pill
  const cat = categories.find(c => c.id === currentNote.categoryId);
  const catLabel = document.getElementById('editorCatLabel');
  const catDot = document.getElementById('editorCatDot');
  if (cat) {
    catLabel.textContent = cat.name;
    catDot.style.backgroundColor = cat.color !== 0 ? '#' + (cat.color >>> 0).toString(16).slice(-6) : '#3b82f6';
  } else {
    catLabel.textContent = 'Kategori Yok';
    catDot.style.backgroundColor = 'transparent';
  }

  // Pin & Lock & Reminder buttons state
  const btnPin = document.getElementById('btnPinNote');
  btnPin.classList.toggle('active', Boolean(currentNote.isPinned));

  const btnLock = document.getElementById('btnLockVault');
  btnLock.classList.toggle('active-amber', Boolean(currentNote.isLocked));

  const btnReminder = document.getElementById('btnSetReminder');
  btnReminder.classList.toggle('active', Boolean(currentNote.reminderTime && currentNote.reminderTime > 0));

  const btnBg = document.getElementById('btnBackgroundImage');
  btnBg.classList.toggle('active-emerald', Boolean(currentNote.backgroundImage));

  // Editor background tint
  const editorBox = document.getElementById('editorActiveState');
  if (currentNote.color !== 0) {
    const preset = COLOR_PRESETS.find(p => p.value === currentNote.color);
    if (preset) {
      editorBox.style.backgroundColor = isDarkTheme ? preset.hex : preset.lightHex;
    }
  } else {
    editorBox.style.backgroundColor = 'var(--bg-editor)';
  }
}

// Live Markdown Parser
function updateLiveMarkdownPreview() {
  const content = document.getElementById('editorTextarea').value;
  const preview = document.getElementById('previewContent');

  const lines = content.split('\n');
  let html = '';
  let inCodeBlock = false;
  let codeBuffer = [];

  lines.forEach((line, index) => {
    // Code block ```
    if (line.trim().startsWith('```')) {
      if (inCodeBlock) {
        html += `<pre><code>${escapeHtml(codeBuffer.join('\n'))}</code></pre>`;
        codeBuffer = [];
        inCodeBlock = false;
      } else {
        inCodeBlock = true;
      }
      return;
    }

    if (inCodeBlock) {
      codeBuffer.push(line);
      return;
    }

    // Checkbox lists - [ ] / - [x]
    const checkMatch = line.match(/^\s*-\s*\[([ xX])\]\s*(.*)$/);
    if (checkMatch) {
      const isChecked = checkMatch[1].toLowerCase() === 'x';
      const text = parseInlineMarkdown(checkMatch[2]);
      html += `
        <div class="task-item ${isChecked ? 'checked' : ''}" data-line="${index}">
          <input type="checkbox" ${isChecked ? 'checked' : ''} onclick="toggleMarkdownCheckbox(${index}, this.checked)">
          <span>${text}</span>
        </div>
      `;
      return;
    }

    // Bullet lists -
    if (line.match(/^\s*-\s*(.*)$/)) {
      const bulletText = line.replace(/^\s*-\s*/, '');
      html += `<li>${parseInlineMarkdown(bulletText)}</li>`;
      return;
    }

    // Headings
    if (line.startsWith('# ')) {
      html += `<h1>${parseInlineMarkdown(line.slice(2))}</h1>`;
      return;
    }
    if (line.startsWith('## ')) {
      html += `<h2>${parseInlineMarkdown(line.slice(3))}</h2>`;
      return;
    }
    if (line.startsWith('### ')) {
      html += `<h3>${parseInlineMarkdown(line.slice(4))}</h3>`;
      return;
    }

    // Blockquote >
    if (line.startsWith('> ')) {
      html += `<blockquote>${parseInlineMarkdown(line.slice(2))}</blockquote>`;
      return;
    }

    // Horizontal Rule ---
    if (line.trim() === '---') {
      html += `<hr style="border:none; border-top: 1px solid var(--border-color); margin: 12px 0;">`;
      return;
    }

    // Blank line
    if (line.trim().length === 0) {
      html += `<div style="height: 8px;"></div>`;
      return;
    }

    // Normal Paragraph
    html += `<p>${parseInlineMarkdown(line)}</p>`;
  });

  preview.innerHTML = html;
}

// Inline Markdown (Bold, Italic, Strikethrough, Code, Wikilinks, Hashtags)
function parseInlineMarkdown(text) {
  let res = escapeHtml(text);

  // Bold **bold**
  res = res.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');

  // Italic *italic*
  res = res.replace(/\*(.*?)\*/g, '<em>$1</em>');

  // Strikethrough ~~strike~~
  res = res.replace(/~~(.*?)~~/g, '<del>$1</del>');

  // Inline code `code`
  res = res.replace(/`([^`]+)`/g, '<code>$1</code>');

  // Wikilinks [[Note Title]]
  res = res.replace(/\[\[(.*?)\]\]/g, (match, title) => {
    return `<span class="wikilink" onclick="handleWikilinkClick('${title.replace(/'/g, "\\'")}')">[[${title}]]</span>`;
  });

  // Hashtags #tag
  res = res.replace(/#([a-zA-Z0-9çğıöşüÇĞİÖŞÜ_-]+)/g, (match, tag) => {
    return `<span class="hashtag" onclick="handleTagClick('${tag}')">#${tag}</span>`;
  });

  return res;
}

// Checkbox Toggle in Preview pane directly updates editorTextarea and DB
window.toggleMarkdownCheckbox = async function(lineIndex, newChecked) {
  const textarea = document.getElementById('editorTextarea');
  const lines = textarea.value.split('\n');
  if (lineIndex < lines.length) {
    const prefix = newChecked ? '- [x] ' : '- [ ] ';
    lines[lineIndex] = prefix + lines[lineIndex].replace(/^\s*-\s*\[([ xX])\]\s*/, '');
    textarea.value = lines.join('\n');
    updateLiveMarkdownPreview();
    await saveCurrentNoteImmediate();
  }
};

// Wikilink Click Handler
window.handleWikilinkClick = async function(title) {
  if (!title || !title.trim()) return;
  const cleanTitle = title.trim();

  const existing = await api.db.getNoteByTitle(cleanTitle);
  if (existing) {
    await saveCurrentNoteImmediate();
    loadNoteIntoEditor(existing, false);
    showToast(`📄 "${cleanTitle}" notuna geçildi.`);
  } else {
    // Create new note with this title
    await saveCurrentNoteImmediate();
    const newId = await api.db.insertNote({
      title: cleanTitle,
      content: '',
      timestamp: Date.now()
    });
    await refreshAll();
    const created = await api.db.getNoteById(newId);
    if (created) {
      loadNoteIntoEditor(created, false);
      showToast(`✨ "${cleanTitle}" başlıklı yeni not oluşturuldu.`);
    }
  }
};

// Hashtag Click Handler
window.handleTagClick = function(tag) {
  selectedTag = tag;
  refreshAll();
  showToast(`🏷️ #${tag} etiketine göre filtrelendi.`);
};

// Backlinks Panel
async function updateBacklinks() {
  const panel = document.getElementById('backlinksPanel');
  const listEl = document.getElementById('backlinksList');
  if (!currentNote || !currentNote.title || currentNote.title.trim().length === 0) {
    panel.style.display = 'none';
    return;
  }

  const linkingNotes = await api.db.getNotesLinkingTo(currentNote.title, currentNote.id);
  if (linkingNotes.length === 0) {
    panel.style.display = 'none';
    return;
  }

  panel.style.display = 'flex';
  document.getElementById('backlinksTitleText').textContent = `Bu Nota Bağlantı Verenler (${linkingNotes.length}):`;
  listEl.innerHTML = '';

  linkingNotes.forEach(linkNote => {
    const chip = document.createElement('span');
    chip.className = 'backlink-chip';
    chip.textContent = linkNote.title || 'Başlıksız';
    chip.onclick = async () => {
      await saveCurrentNoteImmediate();
      loadNoteIntoEditor(linkNote, false);
    };
    listEl.appendChild(chip);
  });
}

// Attachments UI
function updateAttachmentsUI() {
  const container = document.getElementById('attachmentChips');
  container.innerHTML = '';
  if (!currentNote || !currentNote.attachments) return;

  currentNote.attachments.forEach(attPath => {
    const chip = document.createElement('div');
    chip.className = 'att-chip';
    const filename = attPath.split(/[\\/]/).pop();
    chip.innerHTML = `
      <svg style="width:13px; height:13px; color: var(--primary);" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg>
      <span>${escapeHtml(filename)}</span>
      <span class="remove-att" title="Eki Kaldır">✕</span>
    `;

    chip.querySelector('span:first-of-type').onclick = () => {
      api.system.openAttachment(attPath);
    };

    chip.querySelector('.remove-att').onclick = async (e) => {
      e.stopPropagation();
      currentNote.attachments = currentNote.attachments.filter(p => p !== attPath);
      await saveCurrentNoteImmediate();
      updateAttachmentsUI();
    };

    container.appendChild(chip);
  });
}

function updateCharCount() {
  const content = document.getElementById('editorTextarea').value;
  const charCount = content.length;
  const wordCount = content.trim().length === 0 ? 0 : content.trim().split(/\s+/).length;
  document.getElementById('charCountLabel').textContent = `${charCount} karakter • ${wordCount} kelime`;
}

// Auto-Save and Immediate Save
function queueAutoSave() {
  commitToUndo();
  clearTimeout(autoSaveTimeout);
  autoSaveTimeout = setTimeout(() => {
    saveCurrentNoteImmediate();
  }, 400);
}

async function saveCurrentNoteImmediate() {
  if (!currentNote) return;
  const title = document.getElementById('editorTitleInput').value;
  let content = document.getElementById('editorTextarea').value;

  if (currentNote.isLocked && isVaultUnlocked) {
    content = await api.crypto.encryptContent(content);
  }

  const updated = {
    ...currentNote,
    title,
    content,
    updatedAt: Date.now()
  };

  await api.db.updateNote(updated);
  currentNote = updated;
}

// -------------------------------------------------------------
// UNDO / REDO SYSTEM
// -------------------------------------------------------------
function commitToUndo() {
  const title = document.getElementById('editorTitleInput').value;
  const content = document.getElementById('editorTextarea').value;

  if (title !== lastSavedState.title || content !== lastSavedState.content) {
    undoStack.push({ ...lastSavedState });
    if (undoStack.length > 50) undoStack.shift();
    lastSavedState = { title, content };
    redoStack = [];
    updateUndoRedoButtons();
  }
}

function undo() {
  if (undoStack.length > 0) {
    const current = {
      title: document.getElementById('editorTitleInput').value,
      content: document.getElementById('editorTextarea').value
    };
    redoStack.push(current);

    const prev = undoStack.pop();
    document.getElementById('editorTitleInput').value = prev.title;
    document.getElementById('editorTextarea').value = prev.content;
    lastSavedState = prev;
    updateUndoRedoButtons();
    updateLiveMarkdownPreview();
    updateCharCount();
    saveCurrentNoteImmediate();
  }
}

function redo() {
  if (redoStack.length > 0) {
    const current = {
      title: document.getElementById('editorTitleInput').value,
      content: document.getElementById('editorTextarea').value
    };
    undoStack.push(current);

    const next = redoStack.pop();
    document.getElementById('editorTitleInput').value = next.title;
    document.getElementById('editorTextarea').value = next.content;
    lastSavedState = next;
    updateUndoRedoButtons();
    updateLiveMarkdownPreview();
    updateCharCount();
    saveCurrentNoteImmediate();
  }
}

function updateUndoRedoButtons() {
  document.getElementById('btnMdUndo').disabled = undoStack.length === 0;
  document.getElementById('btnMdRedo').disabled = redoStack.length === 0;
}

// Markdown Quick Insert Toolbar
function insertMarkdownToken(prefix, suffix = '') {
  const textarea = document.getElementById('editorTextarea');
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;
  const text = textarea.value;
  const selectedText = text.substring(start, end);

  commitToUndo();
  const replacement = prefix + selectedText + suffix;
  textarea.value = text.substring(0, start) + replacement + text.substring(end);
  textarea.selectionStart = start + prefix.length;
  textarea.selectionEnd = start + prefix.length + selectedText.length;
  textarea.focus();

  updateLiveMarkdownPreview();
  updateCharCount();
  saveCurrentNoteImmediate();
}

// -------------------------------------------------------------
// EVENT LISTENERS SETUP
// -------------------------------------------------------------
function setupEventListeners() {
  // New Note
  document.getElementById('btnNewNote').onclick = createNewNote;
  document.getElementById('btnNewNoteEmpty').onclick = createNewNote;

  // Nav Items
  document.querySelectorAll('.nav-item').forEach(item => {
    item.onclick = () => {
      document.querySelectorAll('.nav-item').forEach(i => i.classList.remove('active'));
      item.classList.add('active');
      selectedNav = item.getAttribute('data-nav');
      selectedCategoryId = null;
      selectedTag = null;
      refreshAll();
    };
  });

  // Search input
  const searchInput = document.getElementById('searchInput');
  const btnClearSearch = document.getElementById('btnClearSearch');
  searchInput.oninput = () => {
    searchQuery = searchInput.value;
    btnClearSearch.style.display = searchQuery.length > 0 ? 'block' : 'none';
    refreshAll();
  };
  btnClearSearch.onclick = () => {
    searchInput.value = '';
    searchQuery = '';
    btnClearSearch.style.display = 'none';
    refreshAll();
  };

  // Sort Menu
  const btnSortMenu = document.getElementById('btnSortMenu');
  const sortDropdown = document.getElementById('sortMenuDropdown');
  btnSortMenu.onclick = (e) => {
    e.stopPropagation();
    btnSortMenu.parentElement.classList.toggle('open');
  };
  sortDropdown.querySelectorAll('.dropdown-item').forEach(item => {
    item.onclick = () => {
      sortOrder = item.getAttribute('data-sort');
      sortDropdown.querySelectorAll('.dropdown-item').forEach(i => i.classList.remove('active'));
      item.classList.add('active');
      btnSortMenu.parentElement.classList.remove('open');
      refreshAll();
    };
  });

  // Multi-Select Toggle
  const btnMultiSelect = document.getElementById('btnMultiSelect');
  btnMultiSelect.onclick = () => {
    isMultiSelectMode = !isMultiSelectMode;
    btnMultiSelect.classList.toggle('active', isMultiSelectMode);
    if (!isMultiSelectMode) selectedNoteIds.clear();
    updateMultiSelectUI();
    renderNotesList();
  };

  document.getElementById('btnBatchCancel').onclick = () => {
    isMultiSelectMode = false;
    btnMultiSelect.classList.remove('active');
    selectedNoteIds.clear();
    updateMultiSelectUI();
    renderNotesList();
  };

  document.getElementById('btnBatchTrash').onclick = async () => {
    if (selectedNoteIds.size > 0) {
      if (selectedNav === 'TRASH') {
        await api.db.deleteNotesPermanently(Array.from(selectedNoteIds));
        showToast(`${selectedNoteIds.size} not kalıcı olarak silindi.`);
      } else {
        await api.db.moveNotesToTrash(Array.from(selectedNoteIds));
        showToast(`${selectedNoteIds.size} not çöpe taşındı.`);
      }
      selectedNoteIds.clear();
      updateMultiSelectUI();
      await refreshAll();
    }
  };

  // Editor Inputs
  const editorTitleInput = document.getElementById('editorTitleInput');
  const editorTextarea = document.getElementById('editorTextarea');

  editorTitleInput.oninput = queueAutoSave;
  editorTextarea.oninput = () => {
    updateLiveMarkdownPreview();
    updateCharCount();
    queueAutoSave();
  };

  // Undo / Redo
  document.getElementById('btnMdUndo').onclick = undo;
  document.getElementById('btnMdRedo').onclick = redo;

  // View Mode Pill Buttons
  document.querySelectorAll('.view-mode-pill .pill-btn').forEach(btn => {
    btn.onclick = () => {
      document.querySelectorAll('.view-mode-pill .pill-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      viewMode = btn.getAttribute('data-mode');
      const body = document.getElementById('editorBody');
      body.className = 'editor-body ' + viewMode.toLowerCase() + '-mode';
    };
  });

  // Markdown Toolbar Quick Buttons
  document.querySelectorAll('.md-tool-btn[data-tag]').forEach(btn => {
    btn.onclick = () => {
      const tag = btn.getAttribute('data-tag');
      switch (tag) {
        case 'bold': insertMarkdownToken('**', '**'); break;
        case 'italic': insertMarkdownToken('*', '*'); break;
        case 'strike': insertMarkdownToken('~~', '~~'); break;
        case 'h1': insertMarkdownToken('# '); break;
        case 'h2': insertMarkdownToken('## '); break;
        case 'checkbox': insertMarkdownToken('- [ ] '); break;
        case 'bullet': insertMarkdownToken('- '); break;
        case 'code': insertMarkdownToken('`', '`'); break;
        case 'quote': insertMarkdownToken('> '); break;
        case 'wikilink': insertMarkdownToken('[[', ']]'); break;
        case 'hashtag': insertMarkdownToken('#'); break;
      }
    };
  });

  // Category Selector in Editor
  const btnCatSelect = document.getElementById('btnCategorySelect');
  const catMenu = document.getElementById('categoryDropdownMenu');
  btnCatSelect.onclick = (e) => {
    e.stopPropagation();
    renderCategoryDropdownOptions();
    btnCatSelect.parentElement.classList.toggle('open');
  };

  // Color Palette Picker
  const btnColorPicker = document.getElementById('btnColorPicker');
  const colorMenu = document.getElementById('colorPaletteMenu');
  btnColorPicker.onclick = (e) => {
    e.stopPropagation();
    renderColorPaletteOptions();
    btnColorPicker.parentElement.classList.toggle('open');
  };

  // Editor Actions
  document.getElementById('btnPinNote').onclick = async () => {
    if (!currentNote) return;
    currentNote.isPinned = !currentNote.isPinned;
    await saveCurrentNoteImmediate();
    updateEditorHeaderUI();
    renderNotesList();
  };

  document.getElementById('btnLockVault').onclick = async () => {
    if (!currentNote) return;
    if (!currentNote.isLocked) {
      const hasPwd = await api.crypto.hasMasterPassword();
      if (!hasPwd) {
        openVaultModal();
        return;
      }
      currentNote.isLocked = true;
      showToast('🔒 Not gizli kasaya eklendi.');
    } else {
      currentNote.isLocked = false;
      showToast('🔓 Not kasadan çıkarıldı.');
    }
    await saveCurrentNoteImmediate();
    updateEditorHeaderUI();
    renderNotesList();
  };

  document.getElementById('btnArchiveNote').onclick = async () => {
    if (!currentNote) return;
    currentNote.isArchived = !currentNote.isArchived;
    await saveCurrentNoteImmediate();
    showToast(currentNote.isArchived ? '📦 Not arşivlendi.' : 'Not arşivden çıkarıldı.');
    await refreshAll();
  };

  document.getElementById('btnDeleteNote').onclick = async () => {
    if (!currentNote) return;
    if (selectedNav === 'TRASH') {
      await api.db.deletePermanently(currentNote.id);
      showToast('Not kalıcı olarak silindi.');
    } else {
      await api.db.moveToTrash(currentNote.id);
      showToast('Not çöp kutusuna taşındı.');
    }
    clearEditor();
    await refreshAll();
  };

  document.getElementById('btnDuplicateNote').onclick = async () => {
    if (!currentNote) return;
    const clone = {
      ...currentNote,
      id: undefined,
      title: (currentNote.title || 'Not') + ' (Kopya)',
      timestamp: Date.now(),
      createdAt: Date.now(),
      updatedAt: Date.now()
    };
    const newId = await api.db.insertNote(clone);
    await refreshAll();
    const created = await api.db.getNoteById(newId);
    if (created) loadNoteIntoEditor(created, false);
    showToast('📋 Notun kopyası oluşturuldu.');
  };

  document.getElementById('btnExportMarkdown').onclick = async () => {
    if (!currentNote) return;
    const title = document.getElementById('editorTitleInput').value;
    const content = document.getElementById('editorTextarea').value;
    const res = await api.system.exportMarkdown(title, content);
    if (res.success) {
      showToast('Markdown dosyası kaydedildi.');
    }
  };

  document.getElementById('btnBackgroundImage').onclick = async () => {
    if (!currentNote) return;
    if (currentNote.backgroundImage) {
      currentNote.backgroundImage = null;
      await saveCurrentNoteImmediate();
      updateEditorHeaderUI();
      showToast('Arka plan görseli kaldırıldı.');
    } else {
      const chosen = await api.system.pickBackgroundImage();
      if (chosen) {
        currentNote.backgroundImage = chosen;
        await saveCurrentNoteImmediate();
        updateEditorHeaderUI();
        showToast('Arka plan görseli eklendi.');
      }
    }
  };

  document.getElementById('btnAddAttachment').onclick = async () => {
    if (!currentNote) return;
    const attPath = await api.system.pickAttachment();
    if (attPath) {
      currentNote.attachments = [...(currentNote.attachments || []), attPath];
      await saveCurrentNoteImmediate();
      updateAttachmentsUI();
      showToast('Ek başarıyla eklendi.');
    }
  };

  // Toggle Theme
  document.getElementById('btnToggleTheme').onclick = () => {
    isDarkTheme = !isDarkTheme;
    document.body.className = isDarkTheme ? 'dark-theme' : 'light-theme';
    document.getElementById('themeText').textContent = isDarkTheme ? 'Açık Tema' : 'Koyu Tema';
    updateEditorHeaderUI();
    renderNotesList();
  };

  // Modals Triggers
  document.getElementById('btnAddCategory').onclick = () => openCategoryModal();
  document.getElementById('btnSyncDialog').onclick = () => openSyncModal();
  document.getElementById('btnBackupDialog').onclick = () => openBackupDialog();
  document.getElementById('btnSecurityDialog').onclick = () => openSecurityModal();
  document.getElementById('btnSetReminder').onclick = () => openReminderModal();
  document.getElementById('btnOpenDrawing').onclick = () => openDrawingModal();

  // Close dropdowns on outside click
  document.addEventListener('click', () => {
    document.querySelectorAll('.dropdown.open').forEach(d => d.classList.remove('open'));
  });
}

function updateMultiSelectUI() {
  const bar = document.getElementById('multiSelectBar');
  if (isMultiSelectMode && selectedNoteIds.size > 0) {
    bar.style.display = 'flex';
    document.getElementById('selectedCountText').textContent = `${selectedNoteIds.size} seçildi`;
  } else {
    bar.style.display = 'none';
  }
}

async function createNewNote() {
  await saveCurrentNoteImmediate();
  const newNote = {
    title: '',
    content: '',
    timestamp: Date.now(),
    color: 0,
    categoryId: selectedCategoryId,
    isPinned: false,
    isArchived: false,
    isDeleted: false,
    isLocked: false,
    attachments: []
  };
  const newId = await api.db.insertNote(newNote);
  await refreshAll();
  const created = await api.db.getNoteById(newId);
  if (created) {
    loadNoteIntoEditor(created, false);
    document.getElementById('editorTitleInput').focus();
  }
}

function renderCategoryDropdownOptions() {
  const menu = document.getElementById('categoryDropdownMenu');
  menu.innerHTML = `
    <div class="dropdown-item" onclick="selectEditorCategory(null)">Kategori Yok</div>
  `;
  categories.forEach(cat => {
    const item = document.createElement('div');
    item.className = 'dropdown-item';
    const colorHex = cat.color !== 0 ? '#' + (cat.color >>> 0).toString(16).slice(-6) : '#3b82f6';
    item.innerHTML = `
      <span class="cat-dot" style="background-color: ${colorHex}; display:inline-block; margin-right: 6px;"></span>
      <span>${escapeHtml(cat.name)}</span>
    `;
    item.onclick = () => selectEditorCategory(cat.id);
    menu.appendChild(item);
  });
}

window.selectEditorCategory = async function(catId) {
  if (!currentNote) return;
  currentNote.categoryId = catId;
  await saveCurrentNoteImmediate();
  updateEditorHeaderUI();
  renderNotesList();
};

function renderColorPaletteOptions() {
  const menu = document.getElementById('colorPaletteMenu');
  menu.innerHTML = '';
  COLOR_PRESETS.forEach(preset => {
    const dot = document.createElement('button');
    dot.className = 'color-dot-btn';
    dot.style.backgroundColor = isDarkTheme ? preset.hex : preset.lightHex;
    dot.title = preset.name;
    dot.onclick = async () => {
      if (!currentNote) return;
      currentNote.color = preset.value;
      await saveCurrentNoteImmediate();
      updateEditorHeaderUI();
      renderNotesList();
    };
    menu.appendChild(dot);
  });
}

// -------------------------------------------------------------
// DRAWING STUDIO ENGINE (HTML5 CANVAS)
// -------------------------------------------------------------
function setupDrawingCanvas() {
  const canvas = document.getElementById('drawingCanvas');
  const ctx = canvas.getContext('2d');

  const palette = document.getElementById('drawColorPalette');
  palette.innerHTML = '';
  const colors = ['#3b82f6', '#ef4444', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#ffffff', '#000000'];
  colors.forEach(c => {
    const btn = document.createElement('button');
    btn.className = 'color-dot-btn';
    btn.style.backgroundColor = c;
    btn.onclick = () => { drawColor = c; };
    palette.appendChild(btn);
  });

  document.querySelectorAll('.draw-tool-btn').forEach(btn => {
    btn.onclick = () => {
      document.querySelectorAll('.draw-tool-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      drawTool = btn.getAttribute('data-tool');
    };
  });

  document.getElementById('drawPatternSelect').onchange = (e) => {
    drawPattern = e.target.value;
    redrawCanvas();
  };

  document.getElementById('drawStrokeWidth').oninput = (e) => {
    drawStrokeWidth = Number(e.target.value);
  };

  document.getElementById('btnDrawUndo').onclick = () => {
    if (drawElements.length > 0) {
      drawRedoStack.push(drawElements.pop());
      redrawCanvas();
    }
  };

  document.getElementById('btnDrawRedo').onclick = () => {
    if (drawRedoStack.length > 0) {
      drawElements.push(drawRedoStack.pop());
      redrawCanvas();
    }
  };

  document.getElementById('btnDrawClear').onclick = () => {
    if (drawElements.length > 0) {
      drawRedoStack = [...drawElements];
      drawElements = [];
      redrawCanvas();
    }
  };

  // Mouse / Touch drawing events
  canvas.onmousedown = (e) => {
    isDrawing = true;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    drawStartPos = { x, y };
    currentDrawPoints = [{ x, y }];
  };

  canvas.onmousemove = (e) => {
    if (!isDrawing) return;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    if (drawTool === 'PEN' || drawTool === 'HIGHLIGHTER' || drawTool === 'ERASER') {
      currentDrawPoints.push({ x, y });
      redrawCanvas();
      drawStrokePreview(currentDrawPoints);
    } else {
      redrawCanvas();
      drawShapePreview(drawStartPos, { x, y });
    }
  };

  canvas.onmouseup = (e) => {
    if (!isDrawing) return;
    isDrawing = false;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    if (drawTool === 'PEN' || drawTool === 'HIGHLIGHTER' || drawTool === 'ERASER') {
      currentDrawPoints.push({ x, y });
      drawElements.push({
        type: 'FREEHAND',
        tool: drawTool,
        color: drawColor,
        width: drawStrokeWidth,
        points: [...currentDrawPoints]
      });
    } else {
      drawElements.push({
        type: 'SHAPE',
        tool: drawTool,
        color: drawColor,
        width: drawStrokeWidth,
        start: drawStartPos,
        end: { x, y }
      });
    }

    drawRedoStack = [];
    redrawCanvas();
  };

  document.getElementById('btnSaveDrawing').onclick = async () => {
    const dataUrl = canvas.toDataURL('image/png');
    const res = await api.system.saveDrawing(dataUrl);
    if (res.success && currentNote) {
      currentNote.attachments = [...(currentNote.attachments || []), res.filePath];
      // Append markdown image link into note
      insertMarkdownToken(`\n\n![Çizim](${res.filePath})\n\n`);
      await saveCurrentNoteImmediate();
      updateAttachmentsUI();
      closeModal('modalDrawing');
      showToast('🎨 Çizim nota başarıyla eklendi.');
    }
  };

  document.getElementById('btnCloseDrawing').onclick = () => closeModal('modalDrawing');
}

function redrawCanvas() {
  const canvas = document.getElementById('drawingCanvas');
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);

  // Background pattern
  canvas.className = '';
  if (drawPattern === 'LINED') canvas.classList.add('pattern-lined');
  if (drawPattern === 'GRID') canvas.classList.add('pattern-grid');
  if (drawPattern === 'CHALKBOARD') canvas.classList.add('pattern-chalkboard');

  drawElements.forEach(el => {
    ctx.save();
    ctx.lineWidth = el.width;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    if (el.tool === 'HIGHLIGHTER') {
      ctx.globalAlpha = 0.35;
      ctx.strokeStyle = el.color;
    } else if (el.tool === 'ERASER') {
      ctx.globalCompositeOperation = 'destination-out';
      ctx.strokeStyle = 'rgba(0,0,0,1)';
    } else {
      ctx.globalAlpha = 1.0;
      ctx.strokeStyle = el.color;
    }

    if (el.type === 'FREEHAND') {
      if (el.points.length > 1) {
        ctx.beginPath();
        ctx.moveTo(el.points[0].x, el.points[0].y);
        for (let i = 1; i < el.points.length; i++) {
          ctx.lineTo(el.points[i].x, el.points[i].y);
        }
        ctx.stroke();
      }
    } else if (el.type === 'SHAPE') {
      drawShape(ctx, el.tool, el.start, el.end);
    }
    ctx.restore();
  });
}

function drawStrokePreview(points) {
  const canvas = document.getElementById('drawingCanvas');
  const ctx = canvas.getContext('2d');
  if (points.length < 2) return;

  ctx.save();
  ctx.lineWidth = drawStrokeWidth;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';

  if (drawTool === 'HIGHLIGHTER') {
    ctx.globalAlpha = 0.35;
    ctx.strokeStyle = drawColor;
  } else if (drawTool === 'ERASER') {
    ctx.globalCompositeOperation = 'destination-out';
    ctx.strokeStyle = 'rgba(0,0,0,1)';
  } else {
    ctx.globalAlpha = 1.0;
    ctx.strokeStyle = drawColor;
  }

  ctx.beginPath();
  ctx.moveTo(points[0].x, points[0].y);
  for (let i = 1; i < points.length; i++) {
    ctx.lineTo(points[i].x, points[i].y);
  }
  ctx.stroke();
  ctx.restore();
}

function drawShapePreview(start, end) {
  const canvas = document.getElementById('drawingCanvas');
  const ctx = canvas.getContext('2d');
  ctx.save();
  ctx.lineWidth = drawStrokeWidth;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.strokeStyle = drawColor;
  drawShape(ctx, drawTool, start, end);
  ctx.restore();
}

function drawShape(ctx, tool, start, end) {
  ctx.beginPath();
  if (tool === 'LINE') {
    ctx.moveTo(start.x, start.y);
    ctx.lineTo(end.x, end.y);
  } else if (tool === 'ARROW') {
    ctx.moveTo(start.x, start.y);
    ctx.lineTo(end.x, end.y);
    const angle = Math.atan2(end.y - start.y, end.x - start.x);
    const headLen = 14;
    ctx.lineTo(end.x - headLen * Math.cos(angle - Math.PI / 6), end.y - headLen * Math.sin(angle - Math.PI / 6));
    ctx.moveTo(end.x, end.y);
    ctx.lineTo(end.x - headLen * Math.cos(angle + Math.PI / 6), end.y - headLen * Math.sin(angle + Math.PI / 6));
  } else if (tool === 'RECT') {
    ctx.strokeRect(start.x, start.y, end.x - start.x, end.y - start.y);
    return;
  } else if (tool === 'CIRCLE') {
    const radius = Math.sqrt(Math.pow(end.x - start.x, 2) + Math.pow(end.y - start.y, 2));
    ctx.arc(start.x, start.y, radius, 0, 2 * Math.PI);
  }
  ctx.stroke();
}

function openDrawingModal() {
  drawElements = [];
  drawRedoStack = [];
  openModal('modalDrawing');
  redrawCanvas();
}

// -------------------------------------------------------------
// REMINDERS SYSTEM
// -------------------------------------------------------------
function openReminderModal() {
  if (!currentNote) return;
  const input = document.getElementById('reminderDatetimeInput');
  const btnClear = document.getElementById('btnClearReminder');

  if (currentNote.reminderTime && currentNote.reminderTime > 0) {
    btnClear.style.display = 'block';
    const d = new Date(currentNote.reminderTime);
    input.value = new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  } else {
    btnClear.style.display = 'none';
    const now = new Date();
    now.setHours(now.getHours() + 2, 0, 0, 0);
    input.value = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  }

  // Preset Chips
  document.querySelectorAll('.chip-preset').forEach(chip => {
    chip.onclick = () => {
      const type = chip.getAttribute('data-preset');
      const d = new Date();
      if (type === 'today19') {
        d.setHours(19, 0, 0, 0);
      } else if (type === 'tomorrow09') {
        d.setDate(d.getDate() + 1);
        d.setHours(9, 0, 0, 0);
      } else if (type === 'tomorrow19') {
        d.setDate(d.getDate() + 1);
        d.setHours(19, 0, 0, 0);
      }
      input.value = new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    };
  });

  document.getElementById('btnSaveReminder').onclick = async () => {
    const val = input.value;
    if (val) {
      const timestamp = new Date(val).getTime();
      currentNote.reminderTime = timestamp;
      await saveCurrentNoteImmediate();
      updateEditorHeaderUI();
      closeModal('modalReminder');
      showToast('⏰ Hatırlatıcı başarıyla ayarlandı.');
      await refreshAll();
    }
  };

  btnClear.onclick = async () => {
    currentNote.reminderTime = null;
    await saveCurrentNoteImmediate();
    updateEditorHeaderUI();
    closeModal('modalReminder');
    showToast('Hatırlatıcı kaldırıldı.');
    await refreshAll();
  };

  document.getElementById('btnCloseReminder').onclick = () => closeModal('modalReminder');

  openModal('modalReminder');
}

async function checkReminders() {
  const notes = await api.db.getReminderNotes();
  const now = Date.now();
  for (const n of notes) {
    if (n.reminderTime && n.reminderTime <= now && n.reminderTime > 0) {
      // Trigger Notification
      api.system.showNotification('⏰ Hatırlatıcı: ' + (n.title || 'Not'), n.content || '');
      showToast(`⏰ Hatırlatıcı: ${n.title || 'Not'}`);
      // Clear reminder to not re-trigger endlessly
      await api.db.updateNote({ ...n, reminderTime: null });
      if (currentNote && currentNote.id === n.id) {
        currentNote.reminderTime = null;
        updateEditorHeaderUI();
      }
    }
  }
}

// -------------------------------------------------------------
// WI-FI SYNC MODAL
// -------------------------------------------------------------
async function openSyncModal() {
  openModal('modalSync');
  const state = await api.sync.getState();
  updateSyncDialogUI(state);

  document.getElementById('btnStartSyncServer').onclick = async () => {
    await api.sync.start();
    const st = await api.sync.getState();
    updateSyncDialogUI(st);
  };

  document.getElementById('btnStopSyncServer').onclick = async () => {
    await api.sync.stop();
    const st = await api.sync.getState();
    updateSyncDialogUI(st);
  };

  document.getElementById('btnRefreshPin').onclick = async () => {
    await api.sync.refreshPin();
    const st = await api.sync.getState();
    updateSyncDialogUI(st);
  };

  document.getElementById('btnCloseSyncModal').onclick = () => {
    closeModal('modalSync');
    refreshAll();
  };
}

function updateSyncDialogUI(state) {
  const statusBadge = document.getElementById('syncServerStatusText');
  const urlText = document.getElementById('syncServerUrlText');
  const pinBadge = document.getElementById('syncPinBadge');
  const btnStart = document.getElementById('btnStartSyncServer');
  const btnStop = document.getElementById('btnStopSyncServer');
  const statusDot = document.getElementById('syncStatusDot');

  if (state.isRunning) {
    statusBadge.textContent = 'Aktif';
    statusBadge.className = 'status-badge online';
    urlText.textContent = `http://${state.localIp}:${state.port}`;
    pinBadge.textContent = state.pairingPin;
    btnStart.style.display = 'none';
    btnStop.style.display = 'inline-block';
    statusDot.classList.add('active');
  } else {
    statusBadge.textContent = 'Kapalı';
    statusBadge.className = 'status-badge';
    urlText.textContent = 'http://127.0.0.1:8765';
    pinBadge.textContent = '----';
    btnStart.style.display = 'inline-block';
    btnStop.style.display = 'none';
    statusDot.classList.remove('active');
  }
}

// -------------------------------------------------------------
// BACKUP & RESTORE DIALOG
// -------------------------------------------------------------
async function openBackupDialog() {
  const exportBtn = confirm('Yedek Almak (Dışa Aktarmak) için Tamam, Yedeği Yüklemek (İçe Aktarmak) için İptal butonuna basınız.');
  if (exportBtn) {
    const useVault = confirm('Yedeğinizi Kasa Şifresi ile şifrelemek (AES-256-GCM .notevault) istiyor musunuz?');
    let vaultPwd = null;
    if (useVault) {
      vaultPwd = prompt('Lütfen yedek için güçlü bir şifre belirleyin:');
      if (!vaultPwd) return;
    }
    const res = await api.backup.exportBackup(vaultPwd);
    if (res.success) {
      showToast('✅ ' + res.message);
    } else if (res.message !== 'İptal edildi') {
      showToast('❌ ' + res.message);
    }
  } else {
    // Import
    let vaultPwd = null;
    const res = await api.backup.importBackup(vaultPwd);
    if (res.needsPassword) {
      vaultPwd = prompt('Bu yedek Kasa Kilidi ile korunuyor. Lütfen şifreyi giriniz:');
      if (vaultPwd) {
        const retryRes = await api.backup.importBackup(vaultPwd);
        if (retryRes.success) {
          showToast('✅ ' + retryRes.message);
          await refreshAll();
        } else {
          showToast('❌ ' + retryRes.message);
        }
      }
    } else if (res.success) {
      showToast('✅ ' + res.message);
      await refreshAll();
    } else if (res.message !== 'İptal edildi') {
      showToast('❌ ' + res.message);
    }
  }
}

// -------------------------------------------------------------
// SECURITY & PRIVACY MODAL
// -------------------------------------------------------------
async function openSecurityModal() {
  openModal('modalSecurity');

  const checkAntiSs = document.getElementById('checkScreenshotProtection');
  checkAntiSs.onchange = async () => {
    await api.system.setScreenshotProtection(checkAntiSs.checked);
    showToast(checkAntiSs.checked ? '🛡️ Ekran koruması aktif.' : 'Ekran koruması devre dışı.');
  };

  document.getElementById('btnChangeVaultPassword').onclick = () => {
    closeModal('modalSecurity');
    openVaultModal(true);
  };

  document.getElementById('btnCloseSecurityModal').onclick = () => closeModal('modalSecurity');
}

// -------------------------------------------------------------
// VAULT PASSWORD MODAL
// -------------------------------------------------------------
async function openVaultModal(forceSetup = false) {
  const hasPwd = await api.crypto.hasMasterPassword();
  const isSetup = forceSetup || !hasPwd;

  const modal = document.getElementById('modalVault');
  const title = document.getElementById('vaultModalTitle');
  const desc = document.getElementById('vaultModalDesc');
  const input = document.getElementById('vaultPasswordInput');
  const submitBtn = document.getElementById('btnVaultSubmit');

  input.value = '';
  if (isSetup) {
    title.textContent = '🔐 Kasa Ana Şifresi Belirleyin';
    desc.textContent = 'Gizli notlarınızı korumak için en az 4 karakterden oluşan bir şifre giriniz.';
    submitBtn.textContent = 'Şifreyi Kaydet';
  } else {
    title.textContent = '🔓 Gizli Kasa Kilidini Açın';
    desc.textContent = 'Korumalı notlarınızı görüntülemek için ana şifrenizi giriniz.';
    submitBtn.textContent = 'Kilidi Aç';
  }

  submitBtn.onclick = async () => {
    const pwd = input.value;
    if (!pwd || pwd.length < 4) {
      alert('Şifre en az 4 karakter olmalıdır.');
      return;
    }

    if (isSetup) {
      const ok = await api.crypto.setupMasterPassword(pwd);
      if (ok) {
        isVaultUnlocked = true;
        closeModal('modalVault');
        showToast('🔓 Ana şifre oluşturuldu ve kasa açıldı.');
        await refreshAll();
      } else {
        alert('Şifre oluşturulamadı.');
      }
    } else {
      const ok = await api.crypto.unlockVault(pwd);
      if (ok) {
        isVaultUnlocked = true;
        closeModal('modalVault');
        showToast('🔓 Kasa kilidi açıldı.');
        await refreshAll();
      } else {
        alert('Hatalı ana şifre!');
      }
    }
  };

  document.getElementById('btnVaultCancel').onclick = () => closeModal('modalVault');
  openModal('modalVault');
}

// -------------------------------------------------------------
// CATEGORY MODAL
// -------------------------------------------------------------
function openCategoryModal() {
  openModal('modalCategory');
  const listEl = document.getElementById('existingCategoriesManageList');
  const pickerEl = document.getElementById('newCatColorPicker');
  let selectedNewCatColor = 0;

  // Render color options for new category
  pickerEl.innerHTML = '';
  const colors = [0, -13159, -13056, -4465413, -3611003, -1982841, -8014, -3015003];
  colors.forEach(col => {
    const dot = document.createElement('button');
    dot.className = 'color-dot-btn';
    dot.style.backgroundColor = col !== 0 ? '#' + (col >>> 0).toString(16).slice(-6) : '#3b82f6';
    dot.onclick = () => { selectedNewCatColor = col; };
    pickerEl.appendChild(dot);
  });

  function renderExisting() {
    listEl.innerHTML = '';
    categories.forEach(cat => {
      const row = document.createElement('div');
      row.style.display = 'flex';
      row.style.justifyContent = 'space-between';
      row.style.alignItems = 'center';
      row.style.padding = '6px 0';
      row.style.borderBottom = '1px solid var(--border-color)';

      const colorHex = cat.color !== 0 ? '#' + (cat.color >>> 0).toString(16).slice(-6) : '#3b82f6';
      row.innerHTML = `
        <div style="display:flex; align-items:center; gap:8px;">
          <span class="cat-dot" style="background-color: ${colorHex}"></span>
          <span>${escapeHtml(cat.name)}</span>
        </div>
        <button class="btn-icon-danger" title="Sil">✕</button>
      `;

      row.querySelector('button').onclick = async () => {
        await api.db.deleteCategory(cat.id);
        categories = await api.db.getAllCategories();
        renderExisting();
        await refreshAll();
      };
      listEl.appendChild(row);
    });
  }
  renderExisting();

  document.getElementById('btnSaveNewCategory').onclick = async () => {
    const name = document.getElementById('newCategoryName').value.trim();
    if (!name) return;
    await api.db.insertCategory({ name, color: selectedNewCatColor });
    document.getElementById('newCategoryName').value = '';
    categories = await api.db.getAllCategories();
    renderExisting();
    await refreshAll();
    showToast(`📁 "${name}" kategorisi eklendi.`);
  };

  document.getElementById('btnCloseCategoryModal').onclick = () => closeModal('modalCategory');
}

// -------------------------------------------------------------
// EMPTY TRASH MODAL
// -------------------------------------------------------------
document.getElementById('btnEmptyTrash').onclick = () => {
  openModal('modalEmptyTrashConfirm');
  document.getElementById('btnConfirmEmptyTrash').onclick = async () => {
    await api.db.emptyTrash();
    closeModal('modalEmptyTrashConfirm');
    showToast('Çöp kutusu boşaltıldı.');
    clearEditor();
    await refreshAll();
  };
  document.getElementById('btnCancelEmptyTrash').onclick = () => closeModal('modalEmptyTrashConfirm');
};

// -------------------------------------------------------------
// MODAL HELPERS
// -------------------------------------------------------------
function openModal(id) {
  document.getElementById(id).classList.add('open');
}

function closeModal(id) {
  document.getElementById(id).classList.remove('open');
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function formatDate(ts) {
  if (!ts) return '';
  const d = new Date(ts);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
  }
  return d.toLocaleDateString('tr-TR', { day: 'numeric', month: 'short' });
}
