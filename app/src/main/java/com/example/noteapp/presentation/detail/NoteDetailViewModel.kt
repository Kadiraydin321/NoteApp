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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.text.TextRange
import java.util.Locale
import java.util.ArrayDeque
import javax.inject.Inject

data class NoteHistorySnapshot(
    val title: String,
    val contentValue: TextFieldValue
)

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
    val isExtractingText: Boolean = false,
    val isSaved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val backgroundImage: String? = null,
    val noteFontSize: Float = 16f,
    val backlinks: List<Note> = emptyList(),
    val extractedWikilinks: List<String> = emptyList(),
    val extractedTags: List<String> = emptyList()
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

    // Geri Al / İleri Al (Undo / Redo) Geçici Hafıza
    private val undoStack = ArrayDeque<NoteHistorySnapshot>()
    private val redoStack = ArrayDeque<NoteHistorySnapshot>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var lastCommittedSnapshot: NoteHistorySnapshot? = null
    private var historyDebounceJob: Job? = null
    private var autoSaveJob: Job? = null
    private var isInternalHistoryRestoring = false

    private var currentRecordingFile: File? = null

    init {
        val noteId = savedStateHandle.get<Long>("noteId") ?: -1L
        if (noteId != -1L) {
            viewModelScope.launch {
                repository.getNoteById(noteId)?.let { note ->
                    val initialContent = TextFieldValue(note.content)
                    _state.value = _state.value.copy(
                        currentNoteId = note.id,
                        title = note.title,
                        contentValue = initialContent,
                        color = note.color,
                        isPinned = note.isPinned,
                        isLocked = note.isLocked,
                        categoryId = note.categoryId,
                        reminderTime = note.reminderTime,
                        attachments = note.attachments,
                        createdAt = if (note.createdAt != 0L) note.createdAt else note.timestamp,
                        updatedAt = if (note.updatedAt != 0L) note.updatedAt else note.timestamp,
                        backgroundImage = note.backgroundImage,
                        extractedWikilinks = extractWikilinks(note.content),
                        extractedTags = extractHashtags(note.content)
                    )
                    refreshBacklinks()
                    lastCommittedSnapshot = NoteHistorySnapshot(note.title, initialContent)
                    undoStack.clear()
                    redoStack.clear()
                    _canUndo.value = false
                    _canRedo.value = false
                }
            }
        } else {
            // Yeni not için ayarlardaki varsayılan rengi ata
            val defaultColor = settingsManager.settings.value.defaultNoteColor
            if (defaultColor != 0) {
                _state.value = _state.value.copy(color = defaultColor)
            }
            lastCommittedSnapshot = NoteHistorySnapshot("", TextFieldValue(""))
        }
        _state.value = _state.value.copy(noteFontSize = settingsManager.settings.value.noteFontSize)
    }

    fun onFontSizeChange(size: Float) {
        val clamped = size.coerceIn(12f, 24f)
        _state.value = _state.value.copy(noteFontSize = clamped)
        settingsManager.setNoteFontSize(clamped)
    }

    fun onTitleChange(title: String) {
        if (_state.value.title == title) return
        _state.value = _state.value.copy(title = title)
        onTextOrTitleChanged()
    }

    fun onContentValueChange(value: TextFieldValue) {
        val textChanged = _state.value.contentValue.text != value.text
        _state.value = _state.value.copy(contentValue = value)
        if (textChanged) {
            onTextOrTitleChanged()
        }
    }

    private fun onTextOrTitleChanged() {
        if (isInternalHistoryRestoring) return

        // Yeni değişiklik yapıldığında ileri alma hafızası temizlenir
        if (redoStack.isNotEmpty()) {
            redoStack.clear()
            _canRedo.value = false
        }

        // Otomatik kaydetme (debounce 500ms)
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(500)
            saveNoteQuietly()
        }

        // Geri alma hafızasına kaydetme (debounce 400ms)
        historyDebounceJob?.cancel()
        historyDebounceJob = viewModelScope.launch {
            delay(400)
            commitCurrentStateToUndo()
        }
    }

    private fun commitCurrentStateToUndo() {
        val currentTitle = _state.value.title
        val currentContent = _state.value.contentValue
        val last = lastCommittedSnapshot

        if (last == null || last.title != currentTitle || last.contentValue.text != currentContent.text) {
            if (last != null) {
                undoStack.addLast(last)
                if (undoStack.size > 50) {
                    undoStack.removeFirst()
                }
                _canUndo.value = true
            }
            lastCommittedSnapshot = NoteHistorySnapshot(currentTitle, currentContent)
        }
    }

    fun undo() {
        historyDebounceJob?.cancel()
        val currentTitle = _state.value.title
        val currentContent = _state.value.contentValue
        val last = lastCommittedSnapshot

        if (last != null && (last.title != currentTitle || last.contentValue.text != currentContent.text)) {
            undoStack.addLast(last)
            lastCommittedSnapshot = NoteHistorySnapshot(currentTitle, currentContent)
        }

        if (undoStack.isEmpty()) return

        val currentSnapshot = NoteHistorySnapshot(_state.value.title, _state.value.contentValue)
        redoStack.addLast(currentSnapshot)
        if (redoStack.size > 50) redoStack.removeFirst()
        _canRedo.value = true

        val previous = undoStack.removeLast()
        _canUndo.value = undoStack.isNotEmpty()

        isInternalHistoryRestoring = true
        lastCommittedSnapshot = previous
        _state.value = _state.value.copy(
            title = previous.title,
            contentValue = previous.contentValue
        )
        isInternalHistoryRestoring = false

        saveNoteQuietly()
    }

    fun redo() {
        if (redoStack.isEmpty()) return

        val currentSnapshot = NoteHistorySnapshot(_state.value.title, _state.value.contentValue)
        undoStack.addLast(currentSnapshot)
        if (undoStack.size > 50) undoStack.removeFirst()
        _canUndo.value = true

        val next = redoStack.removeLast()
        _canRedo.value = redoStack.isNotEmpty()

        isInternalHistoryRestoring = true
        lastCommittedSnapshot = next
        _state.value = _state.value.copy(
            title = next.title,
            contentValue = next.contentValue
        )
        isInternalHistoryRestoring = false

        saveNoteQuietly()
    }

    fun onColorChange(color: Int) {
        _state.value = _state.value.copy(color = color)
        saveNoteQuietly()
    }

    fun onTogglePin() {
        _state.value = _state.value.copy(isPinned = !_state.value.isPinned)
        saveNoteQuietly()
    }

    fun onToggleLock() {
        _state.value = _state.value.copy(isLocked = !_state.value.isLocked)
        saveNoteQuietly()
    }

    fun onSetReminder(timeInMillis: Long?) {
        _state.value = _state.value.copy(reminderTime = timeInMillis)
        saveNoteQuietly()
    }

    val categories: StateFlow<List<Category>> = repository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onCategoryChange(categoryId: Long?) {
        _state.value = _state.value.copy(categoryId = categoryId)
        saveNoteQuietly()
    }

    fun onAddCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.insertCategory(Category(name = name.trim()))
            _state.value = _state.value.copy(categoryId = newId)
            saveNoteQuietly()
        }
    }

    fun onAddImage(uri: Uri) {
        val savedPath = FileStorageHelper.saveImageFromUri(app, uri)
        if (savedPath != null) {
            _state.value = _state.value.copy(
                attachments = _state.value.attachments + savedPath
            )
            saveNoteQuietly()
        }
    }

    fun onAddAttachment(path: String) {
        _state.value = _state.value.copy(
            attachments = _state.value.attachments + path
        )
        saveNoteQuietly()
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
                saveNoteQuietly()
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

    fun startAudioRecording() {
        if (!_state.value.isRecordingAudio) {
            toggleAudioRecording()
        }
    }

    fun stopAudioRecording() {
        if (_state.value.isRecordingAudio) {
            toggleAudioRecording()
        }
    }

    fun insertAudioTimestamp(seconds: Int? = null) {
        val sec = seconds ?: 0
        val minPart = sec / 60
        val secPart = sec % 60
        val tag = String.format(Locale.getDefault(), "[%02d:%02d]", minPart, secPart)
        val currentTfv = _state.value.contentValue
        val curPos = currentTfv.selection.start.coerceIn(0, currentTfv.text.length)
        val newText = StringBuilder(currentTfv.text).insert(curPos, " $tag ").toString()
        _state.value = _state.value.copy(
            contentValue = TextFieldValue(newText, selection = TextRange(curPos + tag.length + 2))
        )
        onTextOrTitleChanged()
    }

    fun extractWikilinks(content: String): List<String> {
        val regex = Regex("\\[\\[([^\\]]+)\\]\\]")
        return regex.findAll(content).map { it.groupValues[1].trim() }.distinct().toList()
    }

    fun extractHashtags(content: String): List<String> {
        val regex = Regex("#([a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-]+)")
        return regex.findAll(content).map { it.groupValues[1] }.distinct().toList()
    }

    fun refreshBacklinks() {
        val title = _state.value.title
        val currentId = _state.value.currentNoteId ?: -1L
        if (title.isBlank()) {
            _state.value = _state.value.copy(backlinks = emptyList())
            return
        }
        viewModelScope.launch {
            val links = repository.getNotesLinkingTo(title, currentId)
            _state.value = _state.value.copy(backlinks = links)
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
        saveNoteQuietly()
    }

    fun onUpdateAttachment(oldPath: String, newPath: String) {
        com.example.noteapp.media.ImageBackupManager.recordEditBackup(app, oldPath, newPath)
        val updated = _state.value.attachments.map { if (it == oldPath) newPath else it }
        _state.value = _state.value.copy(attachments = updated)
        saveNoteQuietly()
    }

    fun revertImageEdit(currentEditedPath: String, onReverted: (oldPath: String) -> Unit = {}): Boolean {
        val previousPath = com.example.noteapp.media.ImageBackupManager.revertToPreviousVersion(app, currentEditedPath)
        if (previousPath != null) {
            val updated = _state.value.attachments.map { if (it == currentEditedPath) previousPath else it }
            _state.value = _state.value.copy(attachments = updated)
            saveNoteQuietly()
            onReverted(previousPath)
            return true
        }
        return false
    }

    fun canRevertImage(path: String): Boolean {
        return com.example.noteapp.media.ImageBackupManager.hasPreviousVersion(app, path)
    }

    fun extractTextFromImage(imagePath: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isExtractingText = true)
            val extractedText = com.example.noteapp.media.OcrHelper.extractTextFromImage(app, imagePath)
            if (!extractedText.isNullOrBlank()) {
                val currentText = _state.value.contentValue.text
                val newText = if (currentText.isBlank()) extractedText else "$currentText\n\n[Görselden Çıkarılan Metin]\n$extractedText"
                _state.value = _state.value.copy(contentValue = androidx.compose.ui.text.input.TextFieldValue(newText))
                saveNoteQuietly()
            }
            _state.value = _state.value.copy(isExtractingText = false)
            onResult(extractedText)
        }
    }

    fun findNoteByTitle(targetTitle: String, onFound: (Long?) -> Unit) {
        viewModelScope.launch {
            val allNotes = repository.getAllNotes()
            val found = allNotes.find { it.title.equals(targetTitle, ignoreCase = true) && !it.isDeleted }
            if (found != null) {
                onFound(found.id)
            } else {
                val newNote = Note(
                    title = targetTitle,
                    content = "# $targetTitle\n\n*Bu not [[${_state.value.title.ifBlank { "Önceki Not" }}]] üzerinden oluşturuldu.*\n",
                    timestamp = System.currentTimeMillis()
                )
                val newId = repository.insertNote(newNote)
                onFound(newId)
            }
        }
    }

    fun setSharedContent(sharedText: String, sharedTitle: String?) {
        val currentText = _state.value.contentValue.text
        val newText = if (currentText.isBlank()) sharedText else "$currentText\n\n$sharedText"
        
        var currentTitle = _state.value.title
        if (currentTitle.isBlank() && !sharedTitle.isNullOrBlank()) {
            currentTitle = sharedTitle
        }
        
        _state.value = _state.value.copy(
            contentValue = androidx.compose.ui.text.input.TextFieldValue(newText),
            title = currentTitle
        )
        saveNoteQuietly()
    }

    fun deleteNote(onDeleted: () -> Unit) {
        val noteId = _state.value.currentNoteId
        if (noteId != null && noteId != -1L) {
            viewModelScope.launch {
                val note = repository.getNoteById(noteId)
                if (note != null) {
                    repository.updateNote(note.copy(isDeleted = true, deletedAt = System.currentTimeMillis()))
                    alarmScheduler.cancel(noteId)
                    NotesWidgetProvider.updateAllWidgets(app)
                }
                onDeleted()
            }
        } else {
            onDeleted()
        }
    }

    fun onBackgroundImageChange(imagePath: String?) {
        _state.value = _state.value.copy(backgroundImage = imagePath)
        saveNoteQuietly()
    }

    fun checkAndDiscardIfEmptyOrSave(): Boolean {
        autoSaveJob?.cancel()
        val currentState = _state.value
        val isBlankNote = currentState.title.isBlank() &&
                currentState.contentValue.text.isBlank() &&
                currentState.attachments.isEmpty() &&
                currentState.backgroundImage == null

        val noteId = currentState.currentNoteId ?: 0L
        if (isBlankNote) {
            if (noteId != 0L && noteId != -1L) {
                viewModelScope.launch {
                    repository.deleteNotesPermanently(listOf(noteId))
                    alarmScheduler.cancel(noteId)
                    NotesWidgetProvider.updateAllWidgets(app)
                }
            }
            return true
        } else {
            saveNoteQuietly()
            return false
        }
    }

    fun saveNoteQuietly() {
        val currentState = _state.value
        val noteId = currentState.currentNoteId ?: 0L
        val isBlankNote = currentState.title.isBlank() &&
                currentState.contentValue.text.isBlank() &&
                currentState.attachments.isEmpty() &&
                currentState.backgroundImage == null

        if (isBlankNote) {
            if (noteId != 0L && noteId != -1L) {
                viewModelScope.launch {
                    repository.deleteNotesPermanently(listOf(noteId))
                    alarmScheduler.cancel(noteId)
                    NotesWidgetProvider.updateAllWidgets(app)
                }
            }
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
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
                timestamp = now,
                createdAt = currentState.createdAt,
                updatedAt = now,
                backgroundImage = currentState.backgroundImage
            )
            val savedId = repository.insertNote(note)
            if (_state.value.currentNoteId == null || _state.value.currentNoteId == 0L) {
                _state.value = _state.value.copy(currentNoteId = savedId)
            }

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

            _state.value = _state.value.copy(isSaved = true, updatedAt = now)
        }
    }

    fun saveNote() {
        checkAndDiscardIfEmptyOrSave()
    }

    fun duplicateCurrentNote(onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val currentState = _state.value
            val titleCopy = if (currentState.title.isNotBlank()) "${currentState.title} (Kopya)" else "Kopya Not"
            val now = System.currentTimeMillis()
            val newNote = Note(
                title = titleCopy,
                content = currentState.contentValue.text,
                timestamp = now,
                createdAt = now,
                updatedAt = now,
                color = currentState.color,
                isPinned = false,
                isLocked = currentState.isLocked,
                categoryId = currentState.categoryId,
                attachments = currentState.attachments,
                backgroundImage = currentState.backgroundImage
            )
            val newId = repository.insertNote(newNote)
            NotesWidgetProvider.updateAllWidgets(app)
            onDone(newId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        if (_state.value.isRecordingAudio) {
            audioRecorder.stop()
        }
        checkAndDiscardIfEmptyOrSave()
    }
}
