package com.example.noteapp.presentation.notes

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.noteapp.data.settings.AppSettingsManager
import com.example.noteapp.data.settings.NotesLayoutMode
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.domain.util.CalendarNoteGrouper
import com.example.noteapp.domain.util.TagSuggestionEngine
import java.time.LocalDate
import com.example.noteapp.widget.NotesWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.noteapp.data.settings.AppSettings
import com.example.noteapp.data.sync.DesktopSyncClient
import com.example.noteapp.data.sync.SyncResult
import javax.inject.Inject

data class QuickSyncState(
    val isOperating: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean? = null
)

enum class NotesViewMode {
    ALL,
    REMINDERS,
    ARCHIVE,
    TRASH
}

enum class NoteTypeFilter {
    ALL,
    PINNED,
    LOCKED,
    MEDIA,
    AUDIO,
    REMINDERS
}

enum class NoteSortOrder(val title: String) {
    MODIFIED_DESC("Değiştirilme (En Yeni)"),
    MODIFIED_ASC("Değiştirilme (En Eski)"),
    CREATED_DESC("Oluşturulma (En Yeni)"),
    CREATED_ASC("Oluşturulma (En Eski)"),
    TITLE_AZ("Başlık (A - Z)"),
    TITLE_ZA("Başlık (Z - A)"),
    COLOR("Renge Göre")
}

private val hashtagRegex = Regex("#([a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-]+)")

private val turkishCollator = java.text.Collator.getInstance(java.util.Locale("tr", "TR")).apply {
    strength = java.text.Collator.PRIMARY
}

@Immutable
data class NotesState(
    val notes: List<Note> = emptyList(),
    val filteredNotes: List<Note> = emptyList(),
    val allHashtags: List<String> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val searchQuery: String = "",
    val viewMode: NotesViewMode = NotesViewMode.ALL,
    val layoutMode: NotesLayoutMode = NotesLayoutMode.STAGGERED_GRID,
    val selectedNoteIds: Set<Long> = emptySet(),
    val filterType: NoteTypeFilter = NoteTypeFilter.ALL,
    val sortOrder: NoteSortOrder = NoteSortOrder.MODIFIED_DESC,
    val selectedTag: String? = null,
    val trashRetentionPolicy: com.example.noteapp.data.settings.TrashRetentionPolicy = com.example.noteapp.data.settings.TrashRetentionPolicy.DAYS_30,
    val recentSearches: List<String> = emptyList(),
    val isAgendaModeEnabled: Boolean = false,
    val selectedAgendaDate: LocalDate? = null
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val app: Application,
    private val repository: NoteRepository,
    private val settingsManager: AppSettingsManager,
    private val desktopSyncClient: DesktopSyncClient
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsManager.settings

    private val _quickSyncState = MutableStateFlow(QuickSyncState())
    val quickSyncState: StateFlow<QuickSyncState> = _quickSyncState.asStateFlow()

    private val _state = MutableStateFlow(NotesState(layoutMode = settingsManager.settings.value.layoutMode))
    val state: StateFlow<NotesState> = _state.asStateFlow()

    private val searchPrefs = app.getSharedPreferences("app_search_history", android.content.Context.MODE_PRIVATE)

    private var notesJob: Job? = null
    private var derivedNotesJob: Job? = null
    private var hashtagSourcesJob: Job? = null
    private var hashtagsJob: Job? = null
    private var hashtagSourceNotes: List<Note>? = null

    init {
        cleanUpTrash()
        loadNotes()
        loadCategories()
        loadHashtags()
        observeSettings()
        _state.value = _state.value.copy(recentSearches = loadRecentSearches())
    }

    private fun loadRecentSearches(): List<String> {
        val raw = searchPrefs.getString("recent_queries", null) ?: return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }
    }

    private fun saveRecentSearchesToPrefs(list: List<String>) {
        searchPrefs.edit().putString("recent_queries", list.joinToString("|||")).apply()
    }

    fun saveSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        val current = _state.value.recentSearches.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val pruned = current.take(8)
        _state.value = _state.value.copy(recentSearches = pruned)
        saveRecentSearchesToPrefs(pruned)
    }

    fun removeRecentSearch(query: String) {
        val updated = _state.value.recentSearches.filter { it != query }
        _state.value = _state.value.copy(recentSearches = updated)
        saveRecentSearchesToPrefs(updated)
    }

    fun clearRecentSearches() {
        _state.value = _state.value.copy(recentSearches = emptyList())
        saveRecentSearchesToPrefs(emptyList())
    }

    private fun cleanUpTrash() {
        viewModelScope.launch {
            val policy = settingsManager.settings.value.trashRetentionPolicy
            if (policy.days > 0) {
                val retentionMillis = policy.days.toLong() * 24L * 60L * 60L * 1000L
                val threshold = System.currentTimeMillis() - retentionMillis
                repository.cleanUpOldTrashNotes(threshold)
            }
        }
    }

    private fun observeSettings() {
        settingsManager.settings.onEach { settings ->
            _state.value = _state.value.copy(
                layoutMode = settings.layoutMode,
                trashRetentionPolicy = settings.trashRetentionPolicy
            )
        }.launchIn(viewModelScope)
    }

    fun setTrashRetentionPolicy(policy: com.example.noteapp.data.settings.TrashRetentionPolicy) {
        settingsManager.setTrashRetentionPolicy(policy)
        cleanUpTrash()
    }

    fun setLayoutMode(mode: NotesLayoutMode) {
        settingsManager.setLayoutMode(mode)
    }

    fun setFilterType(filter: NoteTypeFilter) {
        _state.value = _state.value.copy(
            viewMode = NotesViewMode.ALL,
            filterType = filter,
            selectedCategory = null,
            selectedTag = null,
            selectedNoteIds = emptySet()
        )
        loadNotes()
        recalculateNotes()
    }

    fun setSortOrder(order: NoteSortOrder) {
        _state.value = _state.value.copy(sortOrder = order)
        recalculateNotes()
    }

    fun setViewMode(mode: NotesViewMode) {
        if (mode == NotesViewMode.TRASH) {
            cleanUpTrash()
        }
        _state.value = _state.value.copy(
            viewMode = mode,
            filterType = NoteTypeFilter.ALL,
            selectedCategory = null,
            selectedTag = null,
            selectedNoteIds = emptySet()
        )
        loadNotes()
        recalculateNotes()
    }

    private fun loadNotes() {
        notesJob?.cancel()
        val flow = when (_state.value.viewMode) {
            NotesViewMode.ALL -> {
                val cat = _state.value.selectedCategory
                val tag = _state.value.selectedTag
                if (cat == null && tag == null) {
                    repository.getActiveNotes()
                } else {
                    combine(
                        repository.getActiveNotes(),
                        repository.getArchivedNotes()
                    ) { activeNotes, archivedNotes ->
                        (activeNotes + archivedNotes)
                            .distinctBy { it.id }
                            .filter { note ->
                                (cat == null || note.categoryId == cat.id) &&
                                        (tag == null ||
                                                note.content.contains("#$tag", ignoreCase = true) ||
                                                note.title.contains("#$tag", ignoreCase = true))
                            }
                    }
                }
            }
            NotesViewMode.REMINDERS -> combine(
                repository.getActiveNotes(),
                repository.getArchivedNotes()
            ) { activeNotes, archivedNotes ->
                (activeNotes + archivedNotes).distinctBy { it.id }
            }
            NotesViewMode.ARCHIVE -> repository.getArchivedNotes()
            NotesViewMode.TRASH -> repository.getTrashNotes()
        }

        notesJob = flow.onEach { notes ->
            val finalNotes = if (_state.value.viewMode == NotesViewMode.REMINDERS) {
                notes.filter { it.reminderTime != null && it.reminderTime > 0 }
            } else {
                notes
            }
            _state.value = _state.value.copy(notes = finalNotes)
            recalculateNotes()
        }.launchIn(viewModelScope)
    }

    private fun loadHashtags() {
        hashtagSourcesJob?.cancel()
        hashtagSourcesJob = combine(
            repository.getActiveNotes(),
            repository.getArchivedNotes()
        ) { activeNotes, archivedNotes ->
            (activeNotes + archivedNotes).distinctBy { it.id }
        }.onEach(::updateHashtagCache).launchIn(viewModelScope)
    }

    private fun loadCategories() {
        repository.getAllCategories().onEach { categories ->
            _state.value = _state.value.copy(categories = categories)
        }.launchIn(viewModelScope)
    }

    fun onSearchQueryChanged(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        recalculateNotes(debounce = query.isNotBlank())
    }

    fun onCategorySelect(category: Category?) {
        _state.value = _state.value.copy(
            viewMode = NotesViewMode.ALL,
            filterType = NoteTypeFilter.ALL,
            selectedCategory = category,
            selectedTag = null,
            selectedNoteIds = emptySet()
        )
        loadNotes()
        recalculateNotes()
    }

    fun onSelectTag(tag: String?) {
        _state.value = _state.value.copy(
            viewMode = NotesViewMode.ALL,
            filterType = NoteTypeFilter.ALL,
            selectedCategory = null,
            selectedTag = tag,
            selectedNoteIds = emptySet()
        )
        loadNotes()
        recalculateNotes()
    }

    fun onToggleAgendaMode() {
        val next = !_state.value.isAgendaModeEnabled
        _state.value = _state.value.copy(
            isAgendaModeEnabled = next,
            selectedAgendaDate = if (next) (_state.value.selectedAgendaDate ?: LocalDate.now()) else null
        )
        recalculateNotes()
    }

    fun onSelectAgendaDate(date: LocalDate?) {
        _state.value = _state.value.copy(selectedAgendaDate = date)
        recalculateNotes()
    }

    /** Run full-text matching, tag extraction, and sorting away from the UI thread. */
    private fun recalculateNotes(debounce: Boolean = false) {
        derivedNotesJob?.cancel()
        val snapshot = _state.value
        derivedNotesJob = viewModelScope.launch {
            if (debounce) {
                delay(120)
            }
            val visibleNotes = withContext(Dispatchers.Default) {
                val query = snapshot.searchQuery.trim()
                val hasQuery = query.isNotEmpty()
                val selectedTag = snapshot.selectedTag
                val tagToken = if (selectedTag != null) "#$selectedTag" else null

                val filtered = snapshot.notes.filter { note ->
                    val matchesSearch = !hasQuery ||
                            note.title.contains(query, ignoreCase = true) ||
                            note.content.contains(query, ignoreCase = true)
                    if (!matchesSearch) return@filter false

                    val matchesType = when (snapshot.filterType) {
                        NoteTypeFilter.ALL -> true
                        NoteTypeFilter.PINNED -> note.isPinned
                        NoteTypeFilter.LOCKED -> note.isLocked
                        NoteTypeFilter.MEDIA -> note.attachments.any { !it.endsWith(".mp4") && !it.endsWith(".m4a") }
                        NoteTypeFilter.AUDIO -> note.attachments.any { it.endsWith(".mp4") || it.endsWith(".m4a") }
                        NoteTypeFilter.REMINDERS -> note.reminderTime != null && note.reminderTime > 0
                    }
                    if (!matchesType) return@filter false

                    val matchesTag = tagToken == null ||
                            note.content.contains(tagToken, ignoreCase = true) ||
                            note.title.contains(tagToken, ignoreCase = true)
                    if (!matchesTag) return@filter false

                    val matchesAgendaDate = !snapshot.isAgendaModeEnabled || snapshot.selectedAgendaDate == null ||
                            CalendarNoteGrouper.filterNotesByDate(listOf(note), snapshot.selectedAgendaDate).isNotEmpty()
                    matchesAgendaDate
                }

                fun modifiedTime(note: Note): Long = when {
                    note.updatedAt > 0L -> note.updatedAt
                    note.timestamp > 0L -> note.timestamp
                    else -> note.id
                }
                fun createdTime(note: Note): Long = when {
                    note.createdAt > 0L -> note.createdAt
                    note.timestamp > 0L -> note.timestamp
                    else -> note.id
                }
                val comparator: Comparator<Note> = when (snapshot.sortOrder) {
                    NoteSortOrder.MODIFIED_DESC -> compareByDescending { modifiedTime(it) }
                    NoteSortOrder.MODIFIED_ASC -> compareBy { modifiedTime(it) }
                    NoteSortOrder.CREATED_DESC -> compareByDescending { createdTime(it) }
                    NoteSortOrder.CREATED_ASC -> compareBy { createdTime(it) }
                    NoteSortOrder.TITLE_AZ -> Comparator { a, b ->
                        turkishCollator.compare(a.title.ifBlank { a.content }.trim(), b.title.ifBlank { b.content }.trim())
                    }
                    NoteSortOrder.TITLE_ZA -> Comparator { a, b ->
                        turkishCollator.compare(b.title.ifBlank { b.content }.trim(), a.title.ifBlank { a.content }.trim())
                    }
                    NoteSortOrder.COLOR -> compareByDescending<Note> { it.color != 0 }
                        .thenBy { it.color }
                        .thenByDescending { modifiedTime(it) }
                }
                val (pinned, unpinned) = filtered.partition { it.isPinned }
                pinned.sortedWith(comparator) + unpinned.sortedWith(comparator)
            }

            val latest = _state.value
            if (latest.notes === snapshot.notes &&
                latest.searchQuery == snapshot.searchQuery &&
                latest.filterType == snapshot.filterType &&
                latest.sortOrder == snapshot.sortOrder &&
                latest.selectedTag == snapshot.selectedTag &&
                latest.isAgendaModeEnabled == snapshot.isAgendaModeEnabled &&
                latest.selectedAgendaDate == snapshot.selectedAgendaDate
            ) {
                _state.value = latest.copy(filteredNotes = visibleNotes)
            }
        }
    }

    private fun updateHashtagCache(notes: List<Note>) {
        hashtagsJob?.cancel()
        hashtagsJob = viewModelScope.launch {
            val tags = withContext(Dispatchers.Default) {
                val tagSet = LinkedHashSet<String>()
                for (note in notes) {
                    val combined = "${note.title} ${note.content}"
                    if ('#' in combined) {
                        for (match in hashtagRegex.findAll(combined)) {
                            val clean = match.groupValues[1].trim()
                            if (clean.isNotEmpty()) {
                                tagSet.add(clean)
                            }
                        }
                    }
                }
                tagSet.sortedWith(turkishCollator)
            }
            _state.value = _state.value.copy(allHashtags = tags)
        }
    }

    fun extractAllHashtags(): List<String> {
        return _state.value.notes.flatMap { note ->
            hashtagRegex.findAll("${note.title} ${note.content}").map { it.groupValues[1].trim() }
        }.filter { it.isNotEmpty() }.distinct().sortedWith(turkishCollator)
    }

    fun getTagUsageCounts(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        val notes = _state.value.notes
        for (tag in _state.value.allHashtags) {
            val pattern = Regex("#(?i)${Regex.escape(tag)}(?![a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-])")
            val count = notes.count { pattern.containsMatchIn("${it.title} ${it.content}") }
            counts[tag] = count
        }
        return counts
    }

    fun renameTagAcrossNotes(oldTag: String, newTag: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val notes = repository.getAllNotes()
            for (note in notes) {
                val updatedTitle = TagSuggestionEngine.replaceTagInText(note.title, oldTag, newTag)
                val updatedContent = TagSuggestionEngine.replaceTagInText(note.content, oldTag, newTag)
                if (updatedTitle != note.title || updatedContent != note.content) {
                    repository.updateNote(note.copy(title = updatedTitle, content = updatedContent, updatedAt = System.currentTimeMillis()))
                }
            }
            onDone()
        }
    }

    fun deleteTagAcrossNotes(tagToDelete: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val notes = repository.getAllNotes()
            for (note in notes) {
                val updatedTitle = TagSuggestionEngine.removeTagFromText(note.title, tagToDelete)
                val updatedContent = TagSuggestionEngine.removeTagFromText(note.content, tagToDelete)
                if (updatedTitle != note.title || updatedContent != note.content) {
                    repository.updateNote(note.copy(title = updatedTitle, content = updatedContent, updatedAt = System.currentTimeMillis()))
                }
            }
            onDone()
        }
    }

    // Çoklu Seçim Fonksiyonları
    fun toggleNoteSelection(noteId: Long) {
        val current = _state.value.selectedNoteIds.toMutableSet()
        if (current.contains(noteId)) {
            current.remove(noteId)
        } else {
            current.add(noteId)
        }
        _state.value = _state.value.copy(selectedNoteIds = current)
    }

    fun selectAllNotes(displayedNotes: List<Note>) {
        val allIds = displayedNotes.map { it.id }.toSet()
        if (_state.value.selectedNoteIds.size == allIds.size) {
            _state.value = _state.value.copy(selectedNoteIds = emptySet())
        } else {
            _state.value = _state.value.copy(selectedNoteIds = allIds)
        }
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedNoteIds = emptySet())
    }

    fun deleteSelectedNotes() {
        val ids = _state.value.selectedNoteIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (_state.value.viewMode == NotesViewMode.TRASH) {
                repository.deleteNotesPermanently(ids)
            } else {
                repository.moveNotesToTrash(ids)
            }
            _state.value = _state.value.copy(selectedNoteIds = emptySet())
            NotesWidgetProvider.updateAllWidgets(app)
        }
    }

    fun updateCategoryForSelected(categoryId: Long?) {
        val ids = _state.value.selectedNoteIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.updateNotesCategory(ids, categoryId)
            _state.value = _state.value.copy(selectedNoteIds = emptySet())
            NotesWidgetProvider.updateAllWidgets(app)
        }
    }

    fun updateColorForSelected(color: Int) {
        val ids = _state.value.selectedNoteIds
        val selectedNotes = _state.value.notes.filter { it.id in ids }
        if (selectedNotes.isEmpty()) return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            selectedNotes.forEach { note ->
                repository.updateNote(note.copy(color = color, updatedAt = now))
            }
            _state.value = _state.value.copy(selectedNoteIds = emptySet())
            NotesWidgetProvider.updateAllWidgets(app)
        }
    }

    fun togglePinForSelected() {
        val ids = _state.value.selectedNoteIds
        val selectedNotes = _state.value.notes.filter { it.id in ids }
        if (selectedNotes.isEmpty()) return
        val anyUnpinned = selectedNotes.any { !it.isPinned }
        viewModelScope.launch {
            selectedNotes.forEach { note ->
                repository.updateNote(note.copy(isPinned = anyUnpinned))
            }
            _state.value = _state.value.copy(selectedNoteIds = emptySet())
            NotesWidgetProvider.updateAllWidgets(app)
        }
    }

    fun mergeSelectedNotes(moveToTrashOriginals: Boolean = true, customTitle: String? = null, onComplete: () -> Unit = {}) {
        val ids = _state.value.selectedNoteIds
        val selectedNotes = _state.value.notes.filter { it.id in ids }
        if (selectedNotes.size < 2) return

        viewModelScope.launch {
            val mergedNote = com.example.noteapp.domain.util.NoteMerger.mergeNotes(selectedNotes, customTitle)
            repository.insertNote(mergedNote)
            if (moveToTrashOriginals) {
                repository.moveNotesToTrash(ids.toList(), System.currentTimeMillis())
            }
            _state.value = _state.value.copy(selectedNoteIds = emptySet())
            NotesWidgetProvider.updateAllWidgets(app)
            onComplete()
        }
    }

    fun onDuplicateNote(note: Note) {
        viewModelScope.launch {
            val copyTitle = if (note.title.isNotBlank()) "${note.title} (Kopya)" else "Kopya Not"
            val duplicated = note.copy(
                id = 0,
                title = copyTitle,
                timestamp = System.currentTimeMillis()
            )
            repository.insertNote(duplicated)
            NotesWidgetProvider.updateAllWidgets(app)
        }
    }

    fun onPinNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned))
            NotesWidgetProvider.updateAllWidgets(app)
        }
    }

    fun onArchiveNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isArchived = !note.isArchived))
            NotesWidgetProvider.updateAllWidgets(app)
        }
    }

    fun onMoveToTrash(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isDeleted = true, deletedAt = System.currentTimeMillis()))
            NotesWidgetProvider.updateAllWidgets(app)
        }
    }

    fun onRestoreNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isDeleted = false, deletedAt = null))
            NotesWidgetProvider.updateAllWidgets(app)
        }
    }

    fun onDeleteNotePermanently(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
            NotesWidgetProvider.updateAllWidgets(app)
        }
    }

    fun onEmptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
            NotesWidgetProvider.updateAllWidgets(app)
        }
    }

    fun onAddCategory(name: String, color: Int = 0) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.insertCategory(Category(name = name.trim(), color = color))
        }
    }

    fun onAddCategory(name: String) {
        onAddCategory(name, 0)
    }

    fun onUpdateCategoryColor(category: Category, color: Int) {
        viewModelScope.launch {
            repository.insertCategory(category.copy(color = color))
        }
    }

    fun onRenameCategory(category: Category, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.insertCategory(category.copy(name = newName.trim()))
        }
    }

    fun onDeleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    /**
     * Ana ekrandan son kullanılan IP ve PIN ile doğrudan masaüstüne notları aktarır.
     */
    fun quickSyncSend() {
        val host = settings.value.lastSyncHost
        val pin = settings.value.lastSyncPin
        if (host.isBlank() || pin.length != 4) {
            _quickSyncState.value = QuickSyncState(
                message = "Lütfen önce Ayarlar > Wi-Fi Senkronizasyonu'ndan bilgisayar IP ve PIN kodunu belirleyin.",
                isSuccess = false
            )
            return
        }
        viewModelScope.launch {
            _quickSyncState.value = QuickSyncState(isOperating = true)
            val result = desktopSyncClient.sendNotesToDesktop(
                hostOrUrl = host,
                pin = pin,
                clearExisting = false,
                vaultPassword = settings.value.masterPin
            )
            when (result) {
                is SyncResult.Success -> {
                    _quickSyncState.value = QuickSyncState(isOperating = false, message = result.message, isSuccess = true)
                }
                is SyncResult.Error -> {
                    _quickSyncState.value = QuickSyncState(isOperating = false, message = result.message, isSuccess = false)
                }
                is SyncResult.NeedsPassword -> {
                    _quickSyncState.value = QuickSyncState(isOperating = false, message = result.message, isSuccess = false)
                }
            }
        }
    }

    /**
     * Ana ekrandan son kullanılan IP ve PIN ile doğrudan masaüstünden notları çeker.
     */
    fun quickSyncPull() {
        val host = settings.value.lastSyncHost
        val pin = settings.value.lastSyncPin
        if (host.isBlank() || pin.length != 4) {
            _quickSyncState.value = QuickSyncState(
                message = "Lütfen önce Ayarlar > Wi-Fi Senkronizasyonu'ndan bilgisayar IP ve PIN kodunu belirleyin.",
                isSuccess = false
            )
            return
        }
        viewModelScope.launch {
            _quickSyncState.value = QuickSyncState(isOperating = true)
            val result = desktopSyncClient.pullNotesFromDesktop(
                hostOrUrl = host,
                pin = pin,
                clearExisting = false,
                vaultPassword = settings.value.masterPin
            )
            when (result) {
                is SyncResult.Success -> {
                    NotesWidgetProvider.updateAllWidgets(app)
                    _quickSyncState.value = QuickSyncState(isOperating = false, message = result.message, isSuccess = true)
                }
                is SyncResult.Error -> {
                    _quickSyncState.value = QuickSyncState(isOperating = false, message = result.message, isSuccess = false)
                }
                is SyncResult.NeedsPassword -> {
                    _quickSyncState.value = QuickSyncState(isOperating = false, message = result.message, isSuccess = false)
                }
            }
        }
    }

    fun dismissQuickSyncMessage() {
        _quickSyncState.value = QuickSyncState()
    }
}
