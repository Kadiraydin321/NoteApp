package com.example.noteapp.presentation.detail

import android.app.Application
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.model.NoteHistory
import com.example.noteapp.domain.model.NoteTemplate
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.domain.util.WaveformHelper
import com.example.noteapp.data.template.TemplateManager
import com.example.noteapp.media.AudioPlayer
import com.example.noteapp.media.AudioRecorder
import com.example.noteapp.media.FileStorageHelper
import com.example.noteapp.media.FocusAudioSynthesizer
import com.example.noteapp.media.FocusSoundType
import com.example.noteapp.notification.AlarmScheduler
import com.example.noteapp.data.settings.AppSettingsManager
import com.example.noteapp.data.settings.NoteGoalManager
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
private val WIKILINK_REGEX = Regex("\\[\\[([^\\]]+)\\]\\]")
private val HASHTAG_REGEX = Regex("#([a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-]+)")

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
    val extractedTags: List<String> = emptyList(),
    val isZenMode: Boolean = false,
    val isSoundPlaying: Boolean = false,
    val currentFocusSound: FocusSoundType = FocusSoundType.PINK_NOISE,
    val noteHistoryList: List<NoteHistory> = emptyList(),
    val audioPlaybackSpeed: Float = 1.0f,
    val recordingDurationSec: Int = 0,
    val recordingAmplitudes: List<Float> = emptyList(),
    val audioProgressFraction: Float = 0f,
    val audioCurrentPositionMs: Int = 0,
    val audioDurationMs: Int = 0,
    val wordGoal: Int? = null,
    val availableTags: List<String> = emptyList()
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
    private val templateManager: TemplateManager,
    private val focusAudioSynthesizer: FocusAudioSynthesizer,
    private val noteGoalManager: NoteGoalManager,
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
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    init {
        val noteId = savedStateHandle.get<Long>("noteId") ?: -1L
        if (noteId != -1L) {
            viewModelScope.launch {
                repository.getNoteById(noteId)?.let { note ->
                    val initialContent = TextFieldValue(note.content)
                    val goal = noteGoalManager.getWordGoal(note.id)
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
                        extractedTags = extractHashtags(note.content),
                        wordGoal = goal
                    )
                    refreshBacklinks()
                    lastCommittedSnapshot = NoteHistorySnapshot(note.title, initialContent)
                    undoStack.clear()
                    redoStack.clear()
                    _canUndo.value = false
                    _canRedo.value = false
                    restoreEditHistory(note.id)
                    refreshNoteHistory(note.id)
                }
            }
        } else {
            // Yeni not için ayarlardaki varsayılan rengi ata
            val defaultColor = settingsManager.settings.value.defaultNoteColor
            if (defaultColor != 0) {
                _state.value = _state.value.copy(color = defaultColor)
            }
            lastCommittedSnapshot = NoteHistorySnapshot("", TextFieldValue(""))

            val templateId = savedStateHandle.get<String>("templateId")
            if (!templateId.isNullOrBlank()) {
                templateManager.getAllTemplates().find { it.id == templateId }?.let { tmpl ->
                    applyTemplate(tmpl)
                }
            }
        }
        _state.value = _state.value.copy(noteFontSize = settingsManager.settings.value.noteFontSize)
        viewModelScope.launch {
            val allNotes = repository.getAllNotes()
            val tags = allNotes.flatMap { note ->
                HASHTAG_REGEX.findAll("${note.title} ${note.content}").map { it.groupValues[1].trim() }
            }.filter { it.isNotBlank() }.distinct().sorted()
            _state.value = _state.value.copy(availableTags = tags)
        }
    }

    fun onFontSizeChange(size: Float) {
        val clamped = size.coerceIn(12f, 24f)
        _state.value = _state.value.copy(noteFontSize = clamped)
        settingsManager.setNoteFontSize(clamped)
    }

    fun setWordGoal(goal: Int?) {
        _state.value = _state.value.copy(wordGoal = goal)
        val noteId = _state.value.currentNoteId
        if (noteId != null && noteId > 0L) {
            noteGoalManager.setWordGoal(noteId, goal)
        }
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

    fun duplicateNote(onComplete: (Long) -> Unit) {
        duplicateCurrentNote(onComplete)
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

    fun onAddCategory(name: String, color: Int = 0) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.insertCategory(Category(name = name.trim(), color = color))
            _state.value = _state.value.copy(categoryId = newId)
            saveNoteQuietly()
        }
    }

    fun onAddCategory(name: String) {
        onAddCategory(name, 0)
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
            recordingJob?.cancel()
            recordingJob = null
            audioRecorder.stop()
            currentRecordingFile?.let { file ->
                _state.value = _state.value.copy(
                    attachments = _state.value.attachments + file.absolutePath,
                    isRecordingAudio = false,
                    recordingDurationSec = 0,
                    recordingAmplitudes = emptyList()
                )
                saveNoteQuietly()
            }
            currentRecordingFile = null
        } else {
            // Start recording
            val file = FileStorageHelper.createAudioRecordFile(app)
            currentRecordingFile = file
            audioRecorder.start(file)
            _state.value = _state.value.copy(
                isRecordingAudio = true,
                recordingDurationSec = 0,
                recordingAmplitudes = emptyList()
            )
            recordingJob?.cancel()
            recordingJob = viewModelScope.launch {
                var tick = 0
                while (_state.value.isRecordingAudio) {
                    delay(100)
                    tick++
                    val amp = audioRecorder.getMaxAmplitude()
                    val normalized = WaveformHelper.normalizeAmplitude(amp)
                    val currentAmps = _state.value.recordingAmplitudes
                    _state.value = _state.value.copy(
                        recordingAmplitudes = (currentAmps + normalized).takeLast(24),
                        recordingDurationSec = tick / 10
                    )
                }
            }
        }
    }

    fun cancelAudioRecording() {
        if (_state.value.isRecordingAudio) {
            recordingJob?.cancel()
            recordingJob = null
            audioRecorder.stop()
            currentRecordingFile?.let { file ->
                try { file.delete() } catch (_: Exception) {}
            }
            currentRecordingFile = null
            _state.value = _state.value.copy(
                isRecordingAudio = false,
                recordingDurationSec = 0,
                recordingAmplitudes = emptyList()
            )
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
        return WIKILINK_REGEX.findAll(content).map { it.groupValues[1].trim() }.distinct().toList()
    }

    fun extractHashtags(content: String): List<String> {
        return HASHTAG_REGEX.findAll(content).map { it.groupValues[1] }.distinct().toList()
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
            playbackJob?.cancel()
            playbackJob = null
            audioPlayer.stop()
            _state.value = _state.value.copy(
                isPlayingAudio = false,
                currentPlayingPath = null,
                audioProgressFraction = 0f,
                audioCurrentPositionMs = 0,
                audioDurationMs = 0
            )
        } else {
            playbackJob?.cancel()
            val speed = _state.value.audioPlaybackSpeed
            audioPlayer.playFile(File(path), speed) {
                playbackJob?.cancel()
                playbackJob = null
                _state.value = _state.value.copy(
                    isPlayingAudio = false,
                    currentPlayingPath = null,
                    audioProgressFraction = 0f,
                    audioCurrentPositionMs = 0,
                    audioDurationMs = 0
                )
            }
            val initialDuration = audioPlayer.duration
            _state.value = _state.value.copy(
                isPlayingAudio = true,
                currentPlayingPath = path,
                audioProgressFraction = 0f,
                audioCurrentPositionMs = 0,
                audioDurationMs = initialDuration
            )
            playbackJob = viewModelScope.launch {
                while (_state.value.isPlayingAudio) {
                    val pos = audioPlayer.currentPosition
                    val dur = audioPlayer.duration
                    val frac = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                    _state.value = _state.value.copy(
                        audioProgressFraction = frac,
                        audioCurrentPositionMs = pos,
                        audioDurationMs = dur
                    )
                    delay(100)
                }
            }
        }
    }

    fun seekAudioBy(seconds: Int) {
        audioPlayer.seekBy(seconds * 1000)
        val pos = audioPlayer.currentPosition
        val dur = audioPlayer.duration
        val frac = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
        _state.value = _state.value.copy(
            audioProgressFraction = frac,
            audioCurrentPositionMs = pos,
            audioDurationMs = dur
        )
    }

    fun seekAudioToFraction(fraction: Float) {
        val dur = audioPlayer.duration
        if (dur > 0) {
            val targetMs = (dur * fraction.coerceIn(0f, 1f)).toInt()
            audioPlayer.seekTo(targetMs)
            _state.value = _state.value.copy(
                audioProgressFraction = fraction.coerceIn(0f, 1f),
                audioCurrentPositionMs = targetMs,
                audioDurationMs = dur
            )
        }
    }

    fun cycleAudioPlaybackSpeed() {
        val nextSpeed = when (_state.value.audioPlaybackSpeed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        audioPlayer.setPlaybackSpeed(nextSpeed)
        _state.value = _state.value.copy(audioPlaybackSpeed = nextSpeed)
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
            val finalGoalId = if (noteId != 0L) noteId else savedId
            _state.value.wordGoal?.let { goal ->
                noteGoalManager.setWordGoal(finalGoalId, goal)
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

            recordVersionSnapshot(finalId, currentState.title, currentState.contentValue.text)

            _state.value = _state.value.copy(isSaved = true, updatedAt = now)
        }
    }

    private var lastRecordedVersionContent: String? = null
    private var lastRecordedVersionTitle: String? = null

    private fun recordVersionSnapshot(noteId: Long, title: String, content: String) {
        if (noteId <= 0L) return
        if (title.isBlank() && content.isBlank()) return
        if (title == lastRecordedVersionTitle && content == lastRecordedVersionContent) return

        val charDiff = kotlin.math.abs(content.length - (lastRecordedVersionContent?.length ?: 0))
        val shouldRecord = lastRecordedVersionContent == null || charDiff >= 15 || title != lastRecordedVersionTitle

        if (shouldRecord) {
            lastRecordedVersionTitle = title
            lastRecordedVersionContent = content

            viewModelScope.launch {
                val words = countWords(content)
                val summary = if (lastRecordedVersionContent == null) "İlk sürüm" else "Otomatik yedek"
                val history = NoteHistory(
                    noteId = noteId,
                    title = title,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    wordCount = words,
                    changeSummary = summary
                )
                repository.insertNoteHistory(history)
                repository.pruneOldHistory(noteId, 30)
                refreshNoteHistory(noteId)
            }
        }
    }

    fun refreshNoteHistory(noteId: Long? = _state.value.currentNoteId) {
        if (noteId == null || noteId <= 0L) return
        viewModelScope.launch {
            repository.getHistoryForNote(noteId).collect { list ->
                _state.value = _state.value.copy(noteHistoryList = list)
            }
        }
    }

    fun restoreNoteVersion(history: NoteHistory) {
        val newContent = TextFieldValue(history.content, TextRange(history.content.length))
        _state.value = _state.value.copy(
            title = history.title,
            contentValue = newContent,
            extractedWikilinks = extractWikilinks(history.content),
            extractedTags = extractHashtags(history.content)
        )
        onTextOrTitleChanged()

        _state.value.currentNoteId?.let { id ->
            viewModelScope.launch {
                val words = countWords(history.content)
                val snapshot = NoteHistory(
                    noteId = id,
                    title = history.title,
                    content = history.content,
                    timestamp = System.currentTimeMillis(),
                    wordCount = words,
                    changeSummary = "Sürüm geri yüklendi"
                )
                repository.insertNoteHistory(snapshot)
                refreshNoteHistory(id)
            }
        }
    }

    // Odaklanma (Zen) Modu & Rahatlatıcı Ortam Sesi
    fun toggleZenMode() {
        val nextMode = !_state.value.isZenMode
        _state.value = _state.value.copy(isZenMode = nextMode)
        if (!nextMode && _state.value.isSoundPlaying) {
            focusAudioSynthesizer.stop()
            _state.value = _state.value.copy(isSoundPlaying = false)
        }
    }

    fun toggleFocusSound() {
        if (_state.value.isSoundPlaying) {
            focusAudioSynthesizer.stop()
            _state.value = _state.value.copy(isSoundPlaying = false)
        } else {
            focusAudioSynthesizer.start(_state.value.currentFocusSound)
            _state.value = _state.value.copy(isSoundPlaying = true)
        }
    }

    fun selectFocusSound(soundType: FocusSoundType) {
        _state.value = _state.value.copy(currentFocusSound = soundType)
        if (_state.value.isSoundPlaying) {
            focusAudioSynthesizer.start(soundType)
        }
    }

    fun setFocusSoundVolume(volume: Float) {
        focusAudioSynthesizer.setVolume(volume)
    }

    // Şablon İşlemleri
    fun getAvailableTemplates(): List<NoteTemplate> = templateManager.getAllTemplates()

    fun applyTemplate(template: NoteTemplate) {
        val (interpolatedTitle, interpolatedContent) = templateManager.interpolateTemplate(
            template = template,
            userTitleInput = _state.value.title.takeIf { it.isNotBlank() }
        )
        val currentText = _state.value.contentValue.text
        val finalContent = if (currentText.isBlank()) {
            interpolatedContent
        } else {
            "$currentText\n\n$interpolatedContent"
        }
        val finalTitle = if (_state.value.title.isBlank()) interpolatedTitle else _state.value.title

        _state.value = _state.value.copy(
            title = finalTitle,
            contentValue = TextFieldValue(finalContent, TextRange(finalContent.length)),
            extractedWikilinks = extractWikilinks(finalContent),
            extractedTags = extractHashtags(finalContent)
        )
        onTextOrTitleChanged()
    }

    fun saveCurrentAsCustomTemplate(title: String, description: String, iconEmoji: String) {
        templateManager.saveCustomTemplate(
            title = title,
            description = description,
            content = _state.value.contentValue.text,
            iconEmoji = iconEmoji
        )
    }

    fun deleteCustomTemplate(templateId: String) {
        templateManager.deleteCustomTemplate(templateId)
    }

    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
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
        recordingJob?.cancel()
        playbackJob?.cancel()
        audioPlayer.stop()
        focusAudioSynthesizer.stop()
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
