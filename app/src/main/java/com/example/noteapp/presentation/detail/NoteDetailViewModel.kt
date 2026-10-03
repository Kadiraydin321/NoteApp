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
import com.example.noteapp.data.security.NoteCryptoManager
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import androidx.compose.ui.text.TextRange
import java.util.Locale
import java.util.ArrayDeque
import javax.inject.Inject

private val emptyChecklistLineRegex = Regex("^(?:[-*+]\\s*)?\\[[ xX]?\\]\\s*$")

private fun String.isEffectivelyBlankNoteContent(): Boolean =
    lineSequence().all { line -> line.isBlank() || emptyChecklistLineRegex.matches(line.trim()) }

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
    val isArchived: Boolean = false,
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
    private val cryptoManager: NoteCryptoManager,
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
    private val draftHistoryId = savedStateHandle.get<String>(KEY_DRAFT_HISTORY_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_DRAFT_HISTORY_ID] = it }

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
                        isArchived = note.isArchived,
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
                    restoreEditHistory(note.id)
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
            persistEditHistory()
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

        persistEditHistory()
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

        persistEditHistory()
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

    fun archiveCurrentNote(onDone: () -> Unit = {}) {
        val current = _state.value
        val id = current.currentNoteId ?: return
        if (id <= 0L) return
        viewModelScope.launch {
            val existing = repository.getNoteById(id) ?: return@launch
            val now = System.currentTimeMillis()
            repository.updateNote(
                existing.copy(
                    title = current.title,
                    content = current.contentValue.text,
                    color = current.color,
                    isPinned = current.isPinned,
                    isLocked = current.isLocked,
                    isArchived = !current.isArchived,
                    categoryId = current.categoryId,
                    reminderTime = current.reminderTime,
                    attachments = current.attachments,
                    backgroundImage = current.backgroundImage,
                    updatedAt = now,
                    timestamp = now
                )
            )
            _state.value = _state.value.copy(isArchived = !current.isArchived)
            persistEditHistory()
            onDone()
        }
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
        val currentAttachments = _state.value.attachments
        val oldFilePath = runCatching { File(oldPath).canonicalPath }.getOrDefault(oldPath)
        val updated = if (currentAttachments.any { path ->
                path == oldPath || runCatching { File(path).canonicalPath }.getOrDefault(path) == oldFilePath
            }) {
            currentAttachments.map { path ->
                if (path == oldPath || runCatching { File(path).canonicalPath }.getOrDefault(path) == oldFilePath) newPath else path
            }
        } else {
            // State may have been refreshed while the editor was open; keep the saved edit visible.
            currentAttachments + newPath
        }
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

    private fun editHistoryFile(key: String): File = File(
        File(app.noBackupFilesDir, "edit_sessions"),
        "$key.session"
    )

    private fun currentEditHistoryKey(): String =
        _state.value.currentNoteId?.takeIf { it > 0L }?.let { "note_$it" }
            ?: "draft_$draftHistoryId"

    private fun appendSnapshot(array: JSONArray, snapshot: NoteHistorySnapshot) {
        array.put(
            JSONObject()
                .put("title", snapshot.title)
                .put("content", snapshot.contentValue.text)
                .put("selectionStart", snapshot.contentValue.selection.start)
                .put("selectionEnd", snapshot.contentValue.selection.end)
        )
    }

    private fun readSnapshots(array: JSONArray): List<NoteHistorySnapshot> =
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val content = item.optString("content", "")
                val max = content.length
                val start = item.optInt("selectionStart", max).coerceIn(0, max)
                val end = item.optInt("selectionEnd", start).coerceIn(0, max)
                add(
                    NoteHistorySnapshot(
                        title = item.optString("title", ""),
                        contentValue = TextFieldValue(content, TextRange(start, end))
                    )
                )
            }
        }

    private fun persistEditHistory() {
        val current = _state.value
        val payload = JSONObject().apply {
            put("updatedAt", System.currentTimeMillis())
            put("current", JSONObject().apply {
                put("title", current.title)
                put("content", current.contentValue.text)
                put("selectionStart", current.contentValue.selection.start)
                put("selectionEnd", current.contentValue.selection.end)
            })
            put("undo", JSONArray().also { array -> undoStack.forEach { appendSnapshot(array, it) } })
            put("redo", JSONArray().also { array -> redoStack.forEach { appendSnapshot(array, it) } })
        }.toString()

        // History can contain plaintext from a locked note, so never write an unencrypted fallback.
        val encryptedPayload = cryptoManager.encrypt(payload)
        if (!cryptoManager.isEncrypted(encryptedPayload)) return

        runCatching {
            val file = editHistoryFile(currentEditHistoryKey())
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(encryptedPayload)
            if (!temp.renameTo(file)) {
                file.writeText(encryptedPayload)
                temp.delete()
            }
        }
    }

    private fun restoreEditHistory(noteId: Long) {
        val file = editHistoryFile("note_$noteId")
        if (!file.exists()) return

        runCatching {
            val encoded = file.readText()
            if (!cryptoManager.isEncrypted(encoded)) error("Unencrypted edit history")
            val payload = JSONObject(cryptoManager.decrypt(encoded))
            val updatedAt = payload.optLong("updatedAt", 0L)
            if (updatedAt <= 0L || System.currentTimeMillis() - updatedAt > EDIT_HISTORY_TTL_MS) {
                file.delete()
                return
            }

            val current = payload.optJSONObject("current") ?: return
            val content = current.optString("content", "")
            val start = current.optInt("selectionStart", content.length).coerceIn(0, content.length)
            val end = current.optInt("selectionEnd", start).coerceIn(0, content.length)
            val recovered = NoteHistorySnapshot(
                title = current.optString("title", ""),
                contentValue = TextFieldValue(content, TextRange(start, end))
            )

            undoStack.clear()
            readSnapshots(payload.optJSONArray("undo") ?: JSONArray()).takeLast(50).forEach(undoStack::addLast)
            redoStack.clear()
            readSnapshots(payload.optJSONArray("redo") ?: JSONArray()).takeLast(50).forEach(redoStack::addLast)
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = redoStack.isNotEmpty()
            lastCommittedSnapshot = recovered
            _state.value = _state.value.copy(
                title = recovered.title,
                contentValue = recovered.contentValue,
                extractedWikilinks = extractWikilinks(content),
                extractedTags = extractHashtags(content)
            )
            if (_state.value.isLocked) refreshBacklinks()
        }.onFailure {
            file.delete()
        }
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
                currentState.contentValue.text.isEffectivelyBlankNoteContent() &&
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
                currentState.contentValue.text.isEffectivelyBlankNoteContent() &&
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
                isArchived = currentState.isArchived,
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
            if (noteId == 0L) {
                editHistoryFile("draft_$draftHistoryId").delete()
                persistEditHistory()
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

    private companion object {
        const val KEY_DRAFT_HISTORY_ID = "note_edit_history_draft_id"
        const val EDIT_HISTORY_TTL_MS = 60 * 60 * 1000L
    }
}
