package com.example.noteapp.presentation.notes

import android.app.Application
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NotesViewMode {
    ALL,
    ARCHIVE,
    TRASH
}

enum class NoteTypeFilter {
    ALL,
    PINNED,
    LOCKED,
    MEDIA,
    AUDIO
}

enum class NoteSortOrder {
    DATE_DESC,
    DATE_ASC,
    TITLE_AZ
}

data class NotesState(
    val notes: List<Note> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val searchQuery: String = "",
    val viewMode: NotesViewMode = NotesViewMode.ALL,
    val layoutMode: NotesLayoutMode = NotesLayoutMode.STAGGERED_GRID,
    val selectedNoteIds: Set<Long> = emptySet(),
    val filterType: NoteTypeFilter = NoteTypeFilter.ALL,
    val sortOrder: NoteSortOrder = NoteSortOrder.DATE_DESC
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

    init {
        loadNotes()
        loadCategories()
        observeSettings()
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
        _state.value = _state.value.copy(filterType = filter)
    }

    fun setSortOrder(order: NoteSortOrder) {
        _state.value = _state.value.copy(sortOrder = order)
    }

    fun setViewMode(mode: NotesViewMode) {
        _state.value = _state.value.copy(
            viewMode = mode,
            selectedCategory = null,
            selectedNoteIds = emptySet()
        )
        loadNotes()
    }

    private fun loadNotes() {
        notesJob?.cancel()
        val flow = when (_state.value.viewMode) {
            NotesViewMode.ALL -> {
                val cat = _state.value.selectedCategory
                if (cat != null) repository.getNotesByCategory(cat.id)
                else repository.getActiveNotes()
            }
            NotesViewMode.ARCHIVE -> repository.getArchivedNotes()
            NotesViewMode.TRASH -> repository.getTrashNotes()
        }

        notesJob = flow.onEach { notes ->
            _state.value = _state.value.copy(notes = notes)
        }.launchIn(viewModelScope)
    }

    private fun loadCategories() {
        repository.getAllCategories().onEach { categories ->
            _state.value = _state.value.copy(categories = categories)
        }.launchIn(viewModelScope)
    }

    fun onSearchQueryChanged(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun onCategorySelect(category: Category?) {
        _state.value = _state.value.copy(selectedCategory = category, selectedNoteIds = emptySet())
        loadNotes()
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
            repository.updateNote(note.copy(isDeleted = true))
            NotesWidgetProvider.updateAllWidgets(app)
        }
    }

    fun onRestoreNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isDeleted = false))
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
