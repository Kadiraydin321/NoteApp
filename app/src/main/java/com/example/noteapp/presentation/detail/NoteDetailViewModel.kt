package com.example.noteapp.presentation.detail

import android.app.Application
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.media.AudioPlayer
import com.example.noteapp.media.AudioRecorder
import com.example.noteapp.media.FileStorageHelper
import com.example.noteapp.notification.AlarmScheduler
import com.example.noteapp.data.settings.AppSettingsManager
import com.example.noteapp.widget.NotesWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class NoteDetailState(
    val currentNoteId: Long? = null,
    val title: String = "",
    val contentValue: TextFieldValue = TextFieldValue(""),
    val color: Int = 0,
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    val categoryId: Long? = null,
    val reminderTime: Long? = null,
    val attachments: List<String> = emptyList(),
    val isRecordingAudio: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val currentPlayingPath: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val app: Application,
    private val repository: NoteRepository,
    private val alarmScheduler: AlarmScheduler,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer,
    private val settingsManager: AppSettingsManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(NoteDetailState())
    val state: StateFlow<NoteDetailState> = _state.asStateFlow()

    private var currentRecordingFile: File? = null

    init {
        val noteId = savedStateHandle.get<Long>("noteId") ?: -1L
        if (noteId != -1L) {
            viewModelScope.launch {
                repository.getNoteById(noteId)?.let { note ->
                    _state.value = _state.value.copy(
                        currentNoteId = note.id,
                        title = note.title,
                        contentValue = TextFieldValue(note.content),
                        color = note.color,
                        isPinned = note.isPinned,
                        isLocked = note.isLocked,
                        categoryId = note.categoryId,
                        reminderTime = note.reminderTime,
                        attachments = note.attachments
                    )
                }
            }
        } else {
            // Yeni not için ayarlardaki varsayılan rengi ata
            val defaultColor = settingsManager.settings.value.defaultNoteColor
            if (defaultColor != 0) {
                _state.value = _state.value.copy(color = defaultColor)
            }
        }
    }

    fun onTitleChange(title: String) {
        _state.value = _state.value.copy(title = title)
    }

    fun onContentValueChange(value: TextFieldValue) {
        _state.value = _state.value.copy(contentValue = value)
    }

    fun onColorChange(color: Int) {
        _state.value = _state.value.copy(color = color)
    }

    fun onTogglePin() {
        _state.value = _state.value.copy(isPinned = !_state.value.isPinned)
    }

    fun onToggleLock() {
        _state.value = _state.value.copy(isLocked = !_state.value.isLocked)
    }

    fun onSetReminder(timeInMillis: Long?) {
        _state.value = _state.value.copy(reminderTime = timeInMillis)
    }

    val categories: StateFlow<List<Category>> = repository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onCategoryChange(categoryId: Long?) {
        _state.value = _state.value.copy(categoryId = categoryId)
    }

    fun onAddImage(uri: Uri) {
        val savedPath = FileStorageHelper.saveImageFromUri(app, uri)
        if (savedPath != null) {
            _state.value = _state.value.copy(
                attachments = _state.value.attachments + savedPath
            )
        }
    }

    fun onAddAttachment(path: String) {
        _state.value = _state.value.copy(
            attachments = _state.value.attachments + path
        )
    }

    fun toggleAudioRecording() {
        if (_state.value.isRecordingAudio) {
            // Stop recording
            audioRecorder.stop()
            currentRecordingFile?.let { file ->
                _state.value = _state.value.copy(
                    attachments = _state.value.attachments + file.absolutePath,
                    isRecordingAudio = false
                )
            }
            currentRecordingFile = null
        } else {
            // Start recording
            val file = FileStorageHelper.createAudioRecordFile(app)
            currentRecordingFile = file
            audioRecorder.start(file)
            _state.value = _state.value.copy(isRecordingAudio = true)
        }
    }

    fun toggleAudioPlayback(path: String) {
        if (_state.value.isPlayingAudio && _state.value.currentPlayingPath == path) {
            audioPlayer.stop()
            _state.value = _state.value.copy(isPlayingAudio = false, currentPlayingPath = null)
        } else {
            audioPlayer.playFile(File(path)) {
                _state.value = _state.value.copy(isPlayingAudio = false, currentPlayingPath = null)
            }
            _state.value = _state.value.copy(isPlayingAudio = true, currentPlayingPath = path)
        }
    }

    fun onDeleteAttachment(path: String) {
        FileStorageHelper.deleteFile(path)
        _state.value = _state.value.copy(
            attachments = _state.value.attachments - path
        )
    }

    fun onUpdateAttachment(oldPath: String, newPath: String) {
        com.example.noteapp.media.ImageBackupManager.recordEditBackup(app, oldPath, newPath)
        val updated = _state.value.attachments.map { if (it == oldPath) newPath else it }
        _state.value = _state.value.copy(attachments = updated)
        saveNote()
    }

    fun revertImageEdit(currentEditedPath: String, onReverted: (oldPath: String) -> Unit = {}): Boolean {
        val previousPath = com.example.noteapp.media.ImageBackupManager.revertToPreviousVersion(app, currentEditedPath)
        if (previousPath != null) {
            val updated = _state.value.attachments.map { if (it == currentEditedPath) previousPath else it }
            _state.value = _state.value.copy(attachments = updated)
            saveNote()
            onReverted(previousPath)
            return true
        }
        return false
    }

    fun canRevertImage(path: String): Boolean {
        return com.example.noteapp.media.ImageBackupManager.hasPreviousVersion(app, path)
    }

    fun deleteNote(onDeleted: () -> Unit) {
        val noteId = _state.value.currentNoteId
        if (noteId != null && noteId != -1L) {
            viewModelScope.launch {
                val note = repository.getNoteById(noteId)
                if (note != null) {
                    repository.updateNote(note.copy(isDeleted = true))
                    alarmScheduler.cancel(noteId)
                    NotesWidgetProvider.updateAllWidgets(app)
                }
                onDeleted()
            }
        } else {
            onDeleted()
        }
    }

    fun saveNote() {
        val currentState = _state.value
        if (currentState.title.isBlank() && currentState.contentValue.text.isBlank()) return

        viewModelScope.launch {
            val noteId = currentState.currentNoteId ?: 0
            val note = Note(
                id = noteId,
                title = currentState.title,
                content = currentState.contentValue.text,
                color = currentState.color,
                isPinned = currentState.isPinned,
                isLocked = currentState.isLocked,
                categoryId = currentState.categoryId,
                reminderTime = currentState.reminderTime,
                attachments = currentState.attachments,
                timestamp = System.currentTimeMillis()
            )
            val savedId = repository.insertNote(note)

            // Hatırlatıcı planlama
            val finalId = if (noteId != 0L) noteId else savedId
            if (currentState.reminderTime != null && currentState.reminderTime > System.currentTimeMillis()) {
                alarmScheduler.schedule(
                    noteId = finalId,
                    title = currentState.title,
                    content = currentState.contentValue.text,
                    timeInMillis = currentState.reminderTime
                )
            } else if (currentState.reminderTime == null) {
                alarmScheduler.cancel(finalId)
            }

            // Ana ekran widget'ını otomatik güncelle
            NotesWidgetProvider.updateAllWidgets(app)

            _state.value = _state.value.copy(isSaved = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        if (_state.value.isRecordingAudio) {
            audioRecorder.stop()
        }
    }
}
