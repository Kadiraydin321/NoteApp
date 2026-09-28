package com.example.noteapp.presentation.notes

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class NotesState(
    val notes: List<Note> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val searchQuery: String = "",
    val viewMode: NotesViewMode = NotesViewMode.ALL
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val app: Application,
    private val repository: NoteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NotesState())
    val state: StateFlow<NotesState> = _state.asStateFlow()

    private var notesJob: Job? = null

    init {
        loadNotes()
        loadCategories()
    }

    fun setViewMode(mode: NotesViewMode) {
        _state.value = _state.value.copy(viewMode = mode, selectedCategory = null)
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
        _state.value = _state.value.copy(selectedCategory = category)
        loadNotes()
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
            repository.insertCategory(Category(name = name))
        }
    }

    fun onDeleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }
}
