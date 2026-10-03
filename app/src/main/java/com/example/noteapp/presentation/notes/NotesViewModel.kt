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
import com.example.noteapp.widget.NotesWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
    TITLE_ZA("Başlık (Z - A)")
}

private val hashtagRegex = Regex("#([a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-]+)")

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
    val selectedTag: String? = null
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val app: Application,
    private val repository: NoteRepository,
    private val settingsManager: AppSettingsManager
) : ViewModel() {

    private val _state = MutableStateFlow(NotesState(layoutMode = settingsManager.settings.value.layoutMode))
    val state: StateFlow<NotesState> = _state.asStateFlow()

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
    }

    private fun cleanUpTrash() {
        viewModelScope.launch {
            val sevenDaysInMillis = 7L * 24L * 60L * 60L * 1000L
            val threshold = System.currentTimeMillis() - sevenDaysInMillis
            repository.cleanUpOldTrashNotes(threshold)
        }
    }

    private fun observeSettings() {
        settingsManager.settings.onEach { settings ->
            _state.value = _state.value.copy(layoutMode = settings.layoutMode)
        }.launchIn(viewModelScope)
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
        recalculateNotes()
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

    /** Run full-text matching, tag extraction, and sorting away from the UI thread. */
    private fun recalculateNotes() {
        derivedNotesJob?.cancel()
        val snapshot = _state.value
        derivedNotesJob = viewModelScope.launch {
            val visibleNotes = withContext(Dispatchers.Default) {
                val filtered = snapshot.notes.filter { note ->
                    val matchesSearch = snapshot.searchQuery.isBlank() ||
                            note.title.contains(snapshot.searchQuery, ignoreCase = true) ||
                            note.content.contains(snapshot.searchQuery, ignoreCase = true)
                    val matchesType = when (snapshot.filterType) {
                        NoteTypeFilter.ALL -> true
                        NoteTypeFilter.PINNED -> note.isPinned
                        NoteTypeFilter.LOCKED -> note.isLocked
                        NoteTypeFilter.MEDIA -> note.attachments.any { !it.endsWith(".mp4") && !it.endsWith(".m4a") }
                        NoteTypeFilter.AUDIO -> note.attachments.any { it.endsWith(".mp4") || it.endsWith(".m4a") }
                        NoteTypeFilter.REMINDERS -> note.reminderTime != null && note.reminderTime > 0
                    }
                    val matchesTag = snapshot.selectedTag == null ||
                            note.content.contains("#${snapshot.selectedTag}", ignoreCase = true) ||
                            note.title.contains("#${snapshot.selectedTag}", ignoreCase = true)
                    matchesSearch && matchesType && matchesTag
                }

                val turkishCollator = java.text.Collator.getInstance(java.util.Locale("tr", "TR")).apply {
                    strength = java.text.Collator.PRIMARY
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
                }
                val (pinned, unpinned) = filtered.partition { it.isPinned }
                pinned.sortedWith(comparator) + unpinned.sortedWith(comparator)
            }

            val latest = _state.value
            if (latest.notes === snapshot.notes &&
                latest.searchQuery == snapshot.searchQuery &&
                latest.filterType == snapshot.filterType &&
                latest.sortOrder == snapshot.sortOrder &&
                latest.selectedTag == snapshot.selectedTag
            ) {
                _state.value = latest.copy(filteredNotes = visibleNotes)
            }
        }
    }

    private fun updateHashtagCache(notes: List<Note>) {
        if (hashtagSourceNotes === notes) return
        hashtagSourceNotes = notes
        hashtagsJob?.cancel()
        hashtagsJob = viewModelScope.launch {
            val tags = withContext(Dispatchers.Default) {
                notes.flatMap { note ->
                    hashtagRegex.findAll("${note.title} ${note.content}").map { it.groupValues[1] }
                }.distinct().sorted()
            }
            if (_state.value.notes === notes) {
                _state.value = _state.value.copy(allHashtags = tags)
            }
        }
    }

    fun extractAllHashtags(): List<String> {
        return _state.value.notes.flatMap { note ->
            hashtagRegex.findAll("${note.title} ${note.content}").map { it.groupValues[1] }
        }.distinct().sorted()
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

    fun onAddCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.insertCategory(Category(name = name.trim()))
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
}
