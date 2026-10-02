package com.example.noteapp.presentation.detail

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.noteapp.domain.model.Category
import com.example.noteapp.presentation.components.AttachmentList
import com.example.noteapp.presentation.components.KeepCheckboxIcon
import com.example.noteapp.presentation.components.MarkdownPreview
import com.example.noteapp.presentation.components.MarkdownVisualTransformation
import com.example.noteapp.presentation.components.applyMarkdownWrap
import com.example.noteapp.presentation.components.applyPrefixToLine
import com.example.noteapp.presentation.components.insertTextAtCursor
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt

val NoteColors = listOf(
    Color.Transparent,  // 0: Standart / Varsayılan Tema Rengi
    Color(0xFFFFF9C4), // Açık Sarı (Klasik Not Rengi)
    Color(0xFFFFE0B2), // Açık Şeftali
    Color(0xFFFFCDD2), // Açık Mercan / Gül
    Color(0xFFF8BBD0), // Açık Pembe
    Color(0xFFE1BEE7), // Açık Mor / Lavanta
    Color(0xFFC5CAE9), // Açık İndigo
    Color(0xFFBBDEFB), // Açık Gökyüzü Mavisi
    Color(0xFFB2DFDB), // Açık Teal
    Color(0xFFC8E6C9), // Açık Nane / Yeşil
    Color(0xFFD7CCC8), // Sıcak Bej
    Color(0xFFCFD8DC), // Soğuk Kayrak Gri
    Color(0xFF37474F), // Koyu Mavi Gri
    Color(0xFF1E1E1E)  // Gece Siyahı
)

enum class NoteToolCategory {
    NONE,
    MEDIA,
    FORMAT,
    COLOR,
    CATEGORY
}

private fun isChecklistLine(line: String): Boolean {
    val trimmed = line.trimStart()
    return trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") ||
            trimmed.startsWith("* [ ] ") || trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") ||
            trimmed.startsWith("[ ] ") || trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ")
}

private fun isCheckedLine(line: String): Boolean {
    val trimmed = line.trimStart()
    return trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") ||
            trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") ||
            trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ")
}

private fun getChecklistPrefixLength(line: String): Int {
    val trimmed = line.trimStart()
    val leadingSpacesCount = line.length - trimmed.length
    val prefixLen = when {
        trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> 6
        trimmed.startsWith("* [ ] ") || trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") -> 6
        trimmed.startsWith("[ ] ") || trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ") -> 4
        else -> 0
    }
    return leadingSpacesCount + prefixLen
}

private fun getLineStartOffset(rawText: String, lineIndex: Int): Int {
    var offset = 0
    val lines = rawText.lines()
    for (i in 0 until lineIndex.coerceAtMost(lines.size)) {
        offset += lines[i].length + 1 // +1 for newline character
    }
    return offset.coerceAtMost(rawText.length)
}

private fun stripChecklist(content: String): String {
    val lines = content.lines()
    return lines.joinToString("\n") { line ->
        val trimmed = line.trimStart()
        val leadingSpaces = line.substring(0, line.length - trimmed.length)
        when {
            trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> leadingSpaces + trimmed.substring(6)
            trimmed.startsWith("* [ ] ") || trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") -> leadingSpaces + trimmed.substring(6)
            trimmed.startsWith("[ ] ") || trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ") -> leadingSpaces + trimmed.substring(4)
            else -> line
        }
    }
}

private fun convertToChecklist(content: String): String {
    if (content.isBlank()) return "- [ ] "
    val lines = content.lines()
    return lines.joinToString("\n") { line ->
        val trimmed = line.trimStart()
        val leadingSpaces = line.substring(0, line.length - trimmed.length)
        if (trimmed.isEmpty()) {
            line
        } else if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") ||
            trimmed.startsWith("* [ ] ") || trimmed.startsWith("* [x] ") || trimmed.startsWith("[ ] ") || trimmed.startsWith("[x] ")) {
            line
        } else if (trimmed.startsWith("#") || trimmed.startsWith(">") || trimmed.startsWith("```") || trimmed.startsWith("---") || trimmed.startsWith("***")) {
            // Markdown başlıkları (#), alıntılar (>), kod blokları ve ayırıcı çizgiler checklist'e dönüştürülmez
            line
        } else {
            // Var olan liste/madde işaretlerini (- , * , • , + , 1. vb.) temizle ki çift işaret olmasın
            val cleaned = when {
                trimmed.startsWith("- ") -> trimmed.removePrefix("- ").trimStart()
                trimmed.startsWith("* ") && !trimmed.startsWith("**") -> trimmed.removePrefix("* ").trimStart()
                trimmed.startsWith("• ") -> trimmed.removePrefix("• ").trimStart()
                trimmed.startsWith("+ ") -> trimmed.removePrefix("+ ").trimStart()
                trimmed.matches(Regex("""^\d+[\.\)]\s+.*""")) -> trimmed.replaceFirst(Regex("""^\d+[\.\)]\s+"""), "")
                else -> trimmed
            }
            leadingSpaces + "- [ ] " + cleaned
        }
    }
}

private fun extractItemText(line: String): String {
    val trimmed = line.trimStart()
    return when {
        trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> trimmed.substring(6)
        trimmed.startsWith("* [ ] ") || trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") -> trimmed.substring(6)
        trimmed.startsWith("[ ] ") || trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ") -> trimmed.substring(4)
        else -> line
    }
}

private fun toggleChecklistLine(content: String, lineIndex: Int): String {
    val lines = content.lines().toMutableList()
    if (lineIndex in lines.indices) {
        val line = lines[lineIndex]
        val trimmed = line.trimStart()
        val leadingSpaces = line.substring(0, line.length - trimmed.length)
        val toggled = when {
            trimmed.startsWith("- [ ] ") -> "- [x] " + trimmed.substring(6)
            trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> "- [ ] " + trimmed.substring(6)
            trimmed.startsWith("* [ ] ") -> "* [x] " + trimmed.substring(6)
            trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") -> "* [ ] " + trimmed.substring(6)
            trimmed.startsWith("[ ] ") -> "[x] " + trimmed.substring(4)
            trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ") -> "[ ] " + trimmed.substring(4)
            else -> "- [x] $trimmed"
        }
        lines[lineIndex] = leadingSpaces + toggled
    }
    return lines.joinToString("\n")
}

private fun updateChecklistItemText(content: String, lineIndex: Int, newText: String): String {
    val lines = content.lines().toMutableList()
    if (lineIndex in lines.indices) {
        val line = lines[lineIndex]
        val trimmed = line.trimStart()
        val leadingSpaces = line.substring(0, line.length - trimmed.length)
        val prefix = when {
            trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> "- [x] "
            trimmed.startsWith("- [ ] ") -> "- [ ] "
            trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") -> "* [x] "
            trimmed.startsWith("* [ ] ") -> "* [ ] "
            trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ") -> "[x] "
            trimmed.startsWith("[ ] ") -> "[ ] "
            else -> "- [ ] "
        }
        lines[lineIndex] = leadingSpaces + prefix + newText
    }
    return lines.joinToString("\n")
}

private fun deleteChecklistLine(content: String, lineIndex: Int): String {
    val lines = content.lines().toMutableList()
    if (lineIndex in lines.indices) {
        lines.removeAt(lineIndex)
    }
    return lines.joinToString("\n")
}

private fun addChecklistLine(content: String, afterIndex: Int? = null): String {
    val lines = content.lines().toMutableList()
    if (afterIndex != null && afterIndex in lines.indices) {
        lines.add(afterIndex + 1, "- [ ] ")
    } else {
        val lastIdx = lines.indexOfLast { isChecklistLine(it) }
        if (lastIdx >= 0) {
            lines.add(lastIdx + 1, "- [ ] ")
        } else {
            lines.add("- [ ] ")
        }
    }
    return lines.joinToString("\n")
}

private fun splitChecklistLine(content: String, lineIndex: Int, currentText: String, nextText: String): String {
    val lines = content.lines().toMutableList()
    if (lineIndex in lines.indices) {
        val line = lines[lineIndex]
        val trimmed = line.trimStart()
        val leadingSpaces = line.substring(0, line.length - trimmed.length)
        val prefix = if (trimmed.startsWith("- [x] ") || trimmed.startsWith("[x] ")) "- [x] " else "- [ ] "
        lines[lineIndex] = leadingSpaces + prefix + currentText
        lines.add(lineIndex + 1, leadingSpaces + "- [ ] " + nextText)
    }
    return lines.joinToString("\n")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    state: NoteDetailState,
    categories: List<Category> = emptyList(),
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onCategoryChange: (Long?) -> Unit = {},
    onAddCategory: (String) -> Unit = {},
    onTitleChange: (String) -> Unit,
    onContentValueChange: (TextFieldValue) -> Unit,
    onColorChange: (Int) -> Unit,
    onBackgroundImageChange: (String?) -> Unit = {},
    onTogglePin: () -> Unit,
    onToggleLock: () -> Unit,
    onSetReminder: (Long?) -> Unit,
    onAddImageUri: (android.net.Uri) -> Unit,
    onToggleAudioRecording: () -> Unit,
    onToggleAudioPlayback: (String) -> Unit,
    onDeleteAttachment: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onRevertImageEdit: (String) -> Unit,
    canRevertImage: (String) -> Boolean,
    onDeleteNoteClick: () -> Unit,
    onAddDrawingClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    autoAction: String? = null,
    justEditedImagePath: String? = null,
    onClearJustEditedImage: () -> Unit = {},
    onExtractText: (String) -> Unit = {},
    isExtractingText: Boolean = false,
    onFontSizeChange: (Float) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    onNavigateToNote: (Long) -> Unit = {},
    onDuplicateNote: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val trailingFocusRequester = remember { FocusRequester() }
    val hasChecklistItems = remember(state.contentValue.text) {
        state.contentValue.text.lines().any { isChecklistLine(it) }
    }
    var activeChecklistLineIdx by remember { mutableStateOf<Int?>(null) }
    var isTrailingActive by remember { mutableStateOf(false) }
    val lineFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val lineTfvs = remember { mutableStateMapOf<Int, TextFieldValue>() }

    val requestActiveFocus: () -> Unit = {
        runCatching {
            if (hasChecklistItems) {
                if (isTrailingActive) {
                    trailingFocusRequester.requestFocus()
                } else if (activeChecklistLineIdx != null) {
                    lineFocusRequesters[activeChecklistLineIdx]?.requestFocus()
                } else {
                    lineFocusRequesters[0]?.requestFocus() ?: focusRequester.requestFocus()
                }
            } else {
                focusRequester.requestFocus()
            }
        }
    }

    val applyWrap: (String, String) -> Unit = { prefix, suffix ->
        val raw = state.contentValue.text
        val currentLines = raw.lines()
        if (hasChecklistItems && activeChecklistLineIdx == null && !isTrailingActive) {
            val firstChecklist = currentLines.indexOfFirst { isChecklistLine(it) }.takeIf { it >= 0 } ?: 0
            activeChecklistLineIdx = firstChecklist
            val itemStart = getLineStartOffset(raw, firstChecklist) + getChecklistPrefixLength(currentLines.getOrElse(firstChecklist) { "" })
            val itemTextLen = extractItemText(currentLines.getOrElse(firstChecklist) { "" }).length
            val newSelection = TextRange(itemStart, itemStart + itemTextLen)
            val syncedValue = state.contentValue.copy(selection = newSelection)
            applyMarkdownWrap(syncedValue, prefix, suffix, onContentValueChange, requestActiveFocus)
        } else {
            applyMarkdownWrap(state.contentValue, prefix, suffix, onContentValueChange, requestActiveFocus)
        }
    }

    val applyPrefix: (String) -> Unit = { prefix ->
        val raw = state.contentValue.text
        val currentLines = raw.lines()
        if (hasChecklistItems && activeChecklistLineIdx == null && !isTrailingActive) {
            val firstChecklist = currentLines.indexOfFirst { isChecklistLine(it) }.takeIf { it >= 0 } ?: 0
            activeChecklistLineIdx = firstChecklist
            val itemStart = getLineStartOffset(raw, firstChecklist) + getChecklistPrefixLength(currentLines.getOrElse(firstChecklist) { "" })
            val syncedValue = state.contentValue.copy(selection = TextRange(itemStart))
            applyPrefixToLine(syncedValue, prefix, onContentValueChange, requestActiveFocus)
        } else {
            applyPrefixToLine(state.contentValue, prefix, onContentValueChange, requestActiveFocus)
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var isPreviewMode by remember { mutableStateOf(false) }
    var hideMarkdownTokens by remember { mutableStateOf(true) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showStatsDialog by remember { mutableStateOf(false) }
    var revertConfirmPath by remember { mutableStateOf<String?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var activeToolCategory by remember { mutableStateOf(NoteToolCategory.NONE) }
    var showOutlineDialog by remember { mutableStateOf(false) }
    var isBacklinksExpanded by rememberSaveable { mutableStateOf(true) }
    var moveCheckedToBottom by rememberSaveable { mutableStateOf(false) }
    var isCompletedSectionExpanded by rememberSaveable { mutableStateOf(true) }

    // Donanımsal veya jest ile geri tuşuna basıldığında
    BackHandler {
        onBackClick()
    }

    // Görsel düzenlemeden yeni dönüldüyse geri alma için anında snackbar göster
    LaunchedEffect(justEditedImagePath) {
        justEditedImagePath?.let { editedPath ->
            val result = snackbarHostState.showSnackbar(
                message = "Görsel düzenlendi. İsterseniz önceki haline dönebilirsiniz.",
                actionLabel = "Geri Al",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                onRevertImageEdit(editedPath)
            }
            onClearJustEditedImage()
        }
    }

    // Kilitli not güvenliği: Not kilitliyse ekranda olduğu sürece FLAG_SECURE aktif olsun!
    // Bu sayede hem kullanıcı ekrandayken ekran görüntüsü (screenshot) alınamaz,
    // hem de son uygulamalar (recents/task switcher) anında siyah/sansürlü görünür.
    DisposableEffect(state.isLocked) {
        val window = (context as? android.app.Activity)?.window
        if (state.isLocked) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Sayfa Arka Plan Rengi ve Kontrast Hesabı
    val baseNoteColor = if (state.color != 0 && state.color != Color.Transparent.toArgb()) {
        Color(state.color)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val animatedBgColor by animateColorAsState(
        targetValue = baseNoteColor,
        animationSpec = tween(durationMillis = 250),
        label = "animatedNoteBg"
    )

    // Açık veya koyu arkaplana göre metin ve ikon renkleri (Karanlık/Aydınlık temadan bağımsız doğru zıt kontrast)
    val isDarkBackground = animatedBgColor.luminance() < 0.48f
    val contentColor = if (isDarkBackground) Color(0xFFF8F9FA) else Color(0xFF14171A)
    val secondaryContentColor = if (isDarkBackground) Color(0xFFD0D4D9) else Color(0xFF494D55)
    val hintColor = if (isDarkBackground) Color(0x99F8F9FA) else Color(0x9914171A)
    val topBarIconColor = if (isDarkBackground) Color(0xFFF0F1F5) else Color(0xFF191C20)
    val deleteButtonColor = if (isDarkBackground) Color(0xFFFF6B6B) else Color(0xFFBA1A1A)
    val pinButtonColor = if (state.isPinned) (if (isDarkBackground) Color(0xFF80D8FF) else Color(0xFF00668B)) else topBarIconColor
    val dockIconColor = MaterialTheme.colorScheme.onSurface

    // Medya ve Ses Seçicileri
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            onAddImageUri(tempCameraUri!!)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAddImageUri(uri)
        }
    }

    val backgroundImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = com.example.noteapp.media.FileStorageHelper.saveImageFromUri(context, uri)
            if (savedPath != null) {
                onBackgroundImageChange(savedPath)
            }
        }
    }

    fun launchCamera() {
        try {
            val photoFile = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Kamera başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Not İçi Arama Durumu
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    val searchMatches = remember(state.contentValue.text, searchQuery) {
        if (searchQuery.isBlank()) emptyList<Int>()
        else {
            val list = mutableListOf<Int>()
            var idx = state.contentValue.text.indexOf(searchQuery, 0, ignoreCase = true)
            while (idx >= 0) {
                list.add(idx)
                idx = state.contentValue.text.indexOf(searchQuery, idx + 1, ignoreCase = true)
            }
            list
        }
    }

    val activeMatchStartIndex = if (searchMatches.isNotEmpty() && currentMatchIndex in searchMatches.indices) {
        searchMatches[currentMatchIndex]
    } else null

    LaunchedEffect(currentMatchIndex, searchMatches) {
        if (searchMatches.isNotEmpty() && currentMatchIndex in searchMatches.indices) {
            val start = searchMatches[currentMatchIndex]
            val end = (start + searchQuery.length).coerceAtMost(state.contentValue.text.length)
            onContentValueChange(
                state.contentValue.copy(
                    selection = TextRange(start, end)
                )
            )
            // Eşleşmenin konumuna sayfayı yumuşakça kaydır
            if (state.contentValue.text.isNotEmpty() && scrollState.maxValue > 0) {
                val ratio = start.toFloat() / state.contentValue.text.length
                val targetScroll = (ratio * scrollState.maxValue).toInt().coerceIn(0, scrollState.maxValue)
                scrollState.animateScrollTo(targetScroll)
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onToggleAudioRecording()
        }
    }

    // Widget'tan otomatik aksiyonla açıldıysa (tek seferlik çalışması için rememberSaveable ile korunur)
    var autoActionExecuted by rememberSaveable(autoAction) { mutableStateOf(false) }

    LaunchedEffect(autoAction) {
        if (!autoActionExecuted && !autoAction.isNullOrBlank()) {
            autoActionExecuted = true
            when (autoAction) {
                "image" -> {
                    showImageSourceDialog = true
                }
                "voice" -> {
                    audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
                "draw" -> {
                    onAddDrawingClick()
                }
                "checklist" -> {
                    if (state.contentValue.text.isBlank()) {
                        onContentValueChange(TextFieldValue("- [ ] ", selection = TextRange(6)))
                    } else if (!state.contentValue.text.lines().any { isChecklistLine(it) }) {
                        val converted = state.contentValue.text.lines().joinToString("\n") { if (it.isBlank()) it else "- [ ] $it" }
                        onContentValueChange(TextFieldValue(converted))
                    } else {
                        val updated = addChecklistLine(state.contentValue.text)
                        onContentValueChange(TextFieldValue(updated))
                    }
                }
            }
        }
    }

    // Hatırlatıcı Tarih/Saat Seçici
    fun showDateTimePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        onSetReminder(calendar.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // Notu Panoya Kopyalama
    fun copyNoteToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val fullText = buildString {
            if (state.title.isNotBlank()) {
                appendLine(state.title)
                appendLine()
            }
            append(state.contentValue.text)
        }
        val clip = ClipData.newPlainText("Not İçeriği", fullText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Not panoya kopyalandı", Toast.LENGTH_SHORT).show()
    }

    // Notu Paylaşma (Düz Metin veya Markdown Formatında)
    fun shareNote(asMarkdown: Boolean = false) {
        val fullText = if (asMarkdown) {
            buildString {
                if (state.title.isNotBlank()) appendLine("# ${state.title}\n")
                append(state.contentValue.text)
            }
        } else {
            buildString {
                if (state.title.isNotBlank()) {
                    appendLine(state.title)
                    appendLine()
                }
                append(state.contentValue.text)
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (asMarkdown) "text/markdown" else "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, state.title.ifBlank { "Not" })
            putExtra(Intent.EXTRA_TEXT, fullText)
        }
        context.startActivity(Intent.createChooser(intent, if (asMarkdown) "Markdown Notunu Paylaş" else "Notu Paylaş"))
    }

    // Kelime, Karakter ve Okuma Süresi Hesaplama
    val wordCount = remember(state.contentValue.text) {
        val words = state.contentValue.text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        words.size
    }
    val charCount = state.contentValue.text.length
    val readingTime = max(1, (wordCount / 180f).roundToInt().coerceAtLeast(1))

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Notu Sil") },
            text = { Text("Bu not çöp kutusuna taşınacaktır. Onaylıyor musunuz?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteNoteClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (revertConfirmPath != null) {
        AlertDialog(
            onDismissRequest = { revertConfirmPath = null },
            icon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Görsel Düzenlemesini Geri Al") },
            text = { Text("Bu görseli son yapılan düzenlemelerden önceki orijinal haline geri döndürmek istiyor musunuz?") },
            confirmButton = {
                Button(onClick = {
                    val target = revertConfirmPath
                    revertConfirmPath = null
                    if (target != null) {
                        onRevertImageEdit(target)
                        scope.launch {
                            snackbarHostState.showSnackbar("Görsel önceki haline geri döndürüldü.")
                        }
                    }
                }) {
                    Text("Eski Haline Dön")
                }
            },
            dismissButton = {
                TextButton(onClick = { revertConfirmPath = null }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            icon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Görsel Ekle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showImageSourceDialog = false
                                launchCamera()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Fotoğraf Çek (Kamera)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showImageSourceDialog = false
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Galeriden Seç", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImageSourceDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = animatedBgColor,
        contentColor = contentColor,
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                currentMatchIndex = 0
                            },
                            placeholder = { Text("Notta ara...", color = hintColor) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = if (isDarkBackground) Color.White else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = animatedBgColor,
                        titleContentColor = contentColor,
                        actionIconContentColor = topBarIconColor,
                        navigationIconContentColor = topBarIconColor
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Aramayı Kapat", tint = topBarIconColor)
                        }
                    },
                    actions = {
                        if (searchMatches.isNotEmpty()) {
                            Text(
                                "${currentMatchIndex + 1}/${searchMatches.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(onClick = {
                                if (searchMatches.isNotEmpty()) {
                                    currentMatchIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else searchMatches.size - 1
                                }
                            }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Önceki Eşleşme", tint = topBarIconColor)
                            }
                            IconButton(onClick = {
                                if (searchMatches.isNotEmpty()) {
                                    currentMatchIndex = if (currentMatchIndex < searchMatches.size - 1) currentMatchIndex + 1 else 0
                                }
                            }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Sonraki Eşleşme", tint = topBarIconColor)
                            }
                        } else if (searchQuery.isNotBlank()) {
                            Text(
                                "0/0",
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                )
            } else {
                val isCompactScreen = LocalConfiguration.current.screenWidthDp < 400

                TopAppBar(
                    title = { },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = topBarIconColor,
                        actionIconContentColor = topBarIconColor,
                        navigationIconContentColor = topBarIconColor
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = topBarIconColor)
                        }
                    },
                    actions = {
                        // Geri Al (Undo) - Yalnızca geri alınabilir bir işlem olduğunda yer kaplar
                        AnimatedVisibility(
                            visible = canUndo,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            IconButton(onClick = onUndo) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Geri Al",
                                    tint = topBarIconColor
                                )
                            }
                        }

                        // İleri Al (Redo) - Yalnızca ileri alınabilir bir işlem olduğunda yer kaplar
                        AnimatedVisibility(
                            visible = canRedo,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            IconButton(onClick = onRedo) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "İleri Al",
                                    tint = topBarIconColor
                                )
                            }
                        }

                        // 1. Sabitleme (Pin) Butonu
                        IconButton(onClick = onTogglePin) {
                            Icon(
                                imageVector = if (state.isPinned) Icons.Default.PushPin else Icons.Default.OutlinedFlag,
                                contentDescription = "Sabitle",
                                tint = pinButtonColor
                            )
                        }

                        // 2. Notu Sil Butonu (Geniş ekranlarda doğrudan üst çubukta, küçük ekranlarda 3 nokta menüsünde)
                        if (!isCompactScreen) {
                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Notu Sil",
                                    tint = deleteButtonColor
                                )
                            }
                        }

                        // 3. Kaydet Butonu
                        IconButton(onClick = onSaveClick) {
                            Icon(Icons.Default.Done, contentDescription = "Kaydet", tint = topBarIconColor)
                        }

                        // 4. Not İçi Arama Butonu
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Notta Ara", tint = topBarIconColor)
                        }

                        // 5. Üç Nokta Menüsü
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Daha Fazla Seçenek", tint = topBarIconColor)
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                // Önizleme / Düzenleme Modu
                                DropdownMenuItem(
                                    text = { Text(if (isPreviewMode) "Düzenleme Moduna Geç" else "Önizleme Moduna Geç") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isPreviewMode) Icons.Default.Edit else Icons.Default.RemoveRedEye,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        isPreviewMode = !isPreviewMode
                                        showMoreMenu = false
                                    }
                                )

                                // Notu Çoğalt (Duplicate Note)
                                DropdownMenuItem(
                                    text = { Text("Notu Çoğalt") },
                                    leadingIcon = { Icon(Icons.Default.CopyAll, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        onDuplicateNote()
                                    }
                                )

                                // Kilit Butonu
                                DropdownMenuItem(
                                    text = { Text(if (state.isLocked) "Kilidi Kaldır" else "Notu Kilitle") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (state.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = if (state.isLocked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                        )
                                    },
                                    onClick = {
                                        onToggleLock()
                                        showMoreMenu = false
                                    }
                                )

                                // Hatırlatıcı
                                DropdownMenuItem(
                                    text = { Text(if (state.reminderTime != null) "Hatırlatıcıyı Düzenle" else "Hatırlatıcı Ekle") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (state.reminderTime != null) Icons.Default.AlarmOn else Icons.Default.AddAlert,
                                            contentDescription = null,
                                            tint = if (state.reminderTime != null) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                        )
                                    },
                                    onClick = {
                                        showDateTimePicker()
                                        showMoreMenu = false
                                    }
                                )

                                HorizontalDivider()

                                // Markdown Olarak Paylaş
                                DropdownMenuItem(
                                    text = { Text("Markdown Olarak Paylaş") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        shareNote(asMarkdown = true)
                                    }
                                )

                                // Panoya Kopyala
                                DropdownMenuItem(
                                    text = { Text("Panoya Kopyala") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        copyNoteToClipboard()
                                        showMoreMenu = false
                                    }
                                )

                                HorizontalDivider()

                                // İçindekiler Tablosu (Outline)
                                val hasHeadings = remember(state.contentValue.text) {
                                    state.contentValue.text.lines().any { it.trimStart().startsWith("#") }
                                }
                                if (hasHeadings) {
                                    DropdownMenuItem(
                                        text = { Text("İçindekiler (Başlıklar)") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null) },
                                        onClick = {
                                            showOutlineDialog = true
                                            showMoreMenu = false
                                        }
                                    )
                                }

                                // Markdown İşaretleri Gizleme / Gösterme
                                DropdownMenuItem(
                                    text = { Text(if (hideMarkdownTokens) "Biçim İşaretlerini Göster (*, #)" else "Biçim İşaretlerini Gizle (Temiz)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (hideMarkdownTokens) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        hideMarkdownTokens = !hideMarkdownTokens
                                        showMoreMenu = false
                                    }
                                )

                                // Onay Kutularına Dönüştür / Kaldır
                                DropdownMenuItem(
                                    text = { Text(if (hasChecklistItems) "Onay Kutularını Kaldır" else "Onay Kutularına Dönüştür") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (hasChecklistItems) Icons.Default.CheckBoxOutlineBlank else Icons.Default.CheckBox,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        val currentText = state.contentValue.text
                                        if (hasChecklistItems) {
                                            val stripped = stripChecklist(currentText)
                                            onContentValueChange(TextFieldValue(stripped, selection = TextRange(stripped.length)))
                                        } else {
                                            val converted = convertToChecklist(currentText)
                                            onContentValueChange(TextFieldValue(converted, selection = TextRange(converted.length)))
                                        }
                                        showMoreMenu = false
                                    }
                                )

                                // Tamamlananları Alta Taşı / Sıralı Göster (Google Keep & Apple Notes tarzı)
                                if (hasChecklistItems) {
                                    DropdownMenuItem(
                                        text = { Text(if (moveCheckedToBottom) "Maddeleri Metin Sırasında Göster" else "Tamamlananları Alta Taşı") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (moveCheckedToBottom) Icons.Default.FormatLineSpacing else Icons.Default.VerticalAlignBottom,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            moveCheckedToBottom = !moveCheckedToBottom
                                            showMoreMenu = false
                                        }
                                    )
                                }

                                HorizontalDivider()

                                // Not İstatistikleri
                                DropdownMenuItem(
                                    text = { Text("Not İstatistikleri") },
                                    leadingIcon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                                    onClick = {
                                        showStatsDialog = true
                                        showMoreMenu = false
                                    }
                                )

                                // Notu Sil (Menü içi hızlı silme)
                                DropdownMenuItem(
                                    text = { Text("Notu Sil", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isPreviewMode) {
                val isAppDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                val dockSurfaceColor = if (isAppDark) Color(0xFF22252A) else Color(0xFFFFFFFF)
                val dockBorderColor = if (isAppDark) Color(0x30FFFFFF) else Color(0x18000000)
                val panelContentColor = if (isAppDark) Color(0xFFF0F1F5) else Color(0xFF191C20)
                val panelSubtleColor = if (isAppDark) Color(0xFF9EABB8) else Color(0xFF5E6573)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Kategoriye Göre Açılan Yüzen Araç Kutusu (Floating Card)
                    AnimatedVisibility(
                        visible = activeToolCategory != NoteToolCategory.NONE,
                        enter = slideInVertically { it / 2 } + fadeIn(),
                        exit = slideOutVertically { it / 2 } + fadeOut()
                    ) {
                        Surface(
                            tonalElevation = 6.dp,
                            shadowElevation = 10.dp,
                            color = dockSurfaceColor,
                            contentColor = panelContentColor,
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, dockBorderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                // Panel Başlığı ve Kapatma Butonu
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when (activeToolCategory) {
                                            NoteToolCategory.MEDIA -> "Medya ve Eklentiler"
                                            NoteToolCategory.FORMAT -> "Metin Biçimlendirme"
                                            NoteToolCategory.COLOR -> "Sayfa Arka Plan Rengi"
                                            NoteToolCategory.CATEGORY -> "Not Kategorisi"
                                            else -> ""
                                        },
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    IconButton(
                                        onClick = { activeToolCategory = NoteToolCategory.NONE },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(18.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                when (activeToolCategory) {
                                    NoteToolCategory.MEDIA -> {
                                        // Medya Butonları
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceAround,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 1. Görsel Ekle
                                            OutlinedButton(
                                                onClick = { showImageSourceDialog = true }
                                            ) {
                                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Resim", style = MaterialTheme.typography.labelMedium)
                                            }

                                            // 2. Çizim Yap
                                            OutlinedButton(onClick = onAddDrawingClick) {
                                                Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Çizim", style = MaterialTheme.typography.labelMedium)
                                            }

                                            // 3. Ses Kaydet
                                            Button(
                                                onClick = {
                                                    audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (state.isRecordingAudio) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Icon(
                                                    if (state.isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (state.isRecordingAudio) "Durdur" else "Ses", style = MaterialTheme.typography.labelMedium)
                                            }

                                            // 4. Zaman Damgası
                                            IconButton(
                                                onClick = {
                                                    val now = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
                                                    insertTextAtCursor(state.contentValue, "[$now] ", onContentValueChange) { runCatching { focusRequester.requestFocus() } }
                                                }
                                            ) {
                                                Icon(Icons.Default.AccessTime, contentDescription = "Zaman Damgası")
                                            }
                                        }
                                    }

                                    NoteToolCategory.FORMAT -> {
                                        // Metin Biçimlendirme ve Yazı Boyutu Araçları
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // 1. Satır: Yazı Boyutu Kontrolü (A- / [16 sp] / A+)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.FormatSize,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = "Yazı Boyutu",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = panelContentColor
                                                    )
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    FilledTonalIconButton(
                                                        onClick = {
                                                            val next = (state.noteFontSize - 1f).coerceIn(12f, 24f)
                                                            onFontSizeChange(next)
                                                        },
                                                        enabled = state.noteFontSize > 12f,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Text("A-", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                    }

                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (isAppDark) Color(0x33FFFFFF) else Color(0x18000000)
                                                    ) {
                                                        Text(
                                                            text = "${state.noteFontSize.toInt()} sp",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = panelContentColor,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }

                                                    FilledTonalIconButton(
                                                        onClick = {
                                                            val next = (state.noteFontSize + 1f).coerceIn(12f, 24f)
                                                            onFontSizeChange(next)
                                                        },
                                                        enabled = state.noteFontSize < 24f,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Text("A+", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            // 2. Satır: Biçimlendirme Butonları (Undo/Redo ve mükerrer checklist kaldırıldı)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Kalın
                                                IconButton(onClick = { applyWrap("**", "**") }) {
                                                    Icon(Icons.Default.FormatBold, contentDescription = "Kalın")
                                                }
                                                // İtalik
                                                IconButton(onClick = { applyWrap("*", "*") }) {
                                                    Icon(Icons.Default.FormatItalic, contentDescription = "İtalik")
                                                }
                                                // Üstü Çizili
                                                IconButton(onClick = { applyWrap("~~", "~~") }) {
                                                    Icon(Icons.Default.FormatStrikethrough, contentDescription = "Üstü Çizili")
                                                }
                                                // Fosforlu Vurgu
                                                IconButton(onClick = { applyWrap("==", "==") }) {
                                                    Icon(Icons.Default.BorderColor, contentDescription = "Vurgu", tint = Color(0xFFFBC02D))
                                                }
                                                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                                                // Başlık (H3)
                                                IconButton(onClick = { applyPrefix("### ") }) {
                                                    Icon(Icons.Default.Title, contentDescription = "Başlık")
                                                }
                                                // Madde İşareti
                                                IconButton(onClick = { applyPrefix("• ") }) {
                                                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Madde")
                                                }
                                                // Numaralı Liste
                                                IconButton(onClick = { applyPrefix("1. ") }) {
                                                    Icon(Icons.Default.FormatListNumbered, contentDescription = "Numaralı")
                                                }
                                                // Alıntı
                                                IconButton(onClick = { applyPrefix("> ") }) {
                                                    Icon(Icons.Default.FormatQuote, contentDescription = "Alıntı")
                                                }
                                                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                                                // Çift Yönlü Bağlantı ([[ ]])
                                                IconButton(onClick = { applyWrap("[[", "]]") }) {
                                                    Icon(Icons.Default.Link, contentDescription = "Bağlantı [[ ]]", tint = MaterialTheme.colorScheme.primary)
                                                }
                                                // Bilgi Kutusu (> [!NOTE])
                                                IconButton(onClick = { applyPrefix("> [!NOTE] ") }) {
                                                    Icon(Icons.Default.Lightbulb, contentDescription = "Bilgi Kutusu", tint = MaterialTheme.colorScheme.secondary)
                                                }
                                                // Kod Bloku (```)
                                                IconButton(onClick = { applyWrap("```\n", "\n```") }) {
                                                    Icon(Icons.Default.Code, contentDescription = "Kod Bloku")
                                                }
                                            }
                                        }
                                    }

                                    NoteToolCategory.COLOR -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            // Renk Paleti Listesi
                                            LazyRow(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                items(NoteColors) { color ->
                                                    val argb = color.toArgb()
                                                    val isSelected = (state.color == argb) || (state.color == 0 && color == Color.Transparent)

                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(CircleShape)
                                                            .background(if (color == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant else color)
                                                            .border(
                                                                width = if (isSelected) 3.dp else 1.dp,
                                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                                                shape = CircleShape
                                                            )
                                                            .clickable { onColorChange(argb) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (color == Color.Transparent) {
                                                            Icon(
                                                                Icons.Default.FormatColorReset,
                                                                contentDescription = "Varsayılan",
                                                                modifier = Modifier.size(16.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        } else if (isSelected) {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp),
                                                                tint = if (color.luminance() < 0.5f) Color.White else Color.Black
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Arka Plan Görseli Seçimi ve Kaldırma
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            backgroundImagePickerLauncher.launch(
                                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                            )
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(if (state.backgroundImage != null) "Arka Planı Değiştir" else "Arka Plan Resmi Seç", style = MaterialTheme.typography.labelMedium)
                                                    }

                                                    if (!state.backgroundImage.isNullOrEmpty()) {
                                                        AsyncImage(
                                                            model = File(state.backgroundImage),
                                                            contentDescription = "Arka Plan Önizleme",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                                        )
                                                    }
                                                }

                                                if (!state.backgroundImage.isNullOrEmpty()) {
                                                    TextButton(
                                                        onClick = { onBackgroundImageChange(null) },
                                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Resmi Kaldır", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    NoteToolCategory.CATEGORY -> {
                                        // Kategori Seçim Listesi
                                        LazyRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            item {
                                                ElevatedAssistChip(
                                                    onClick = { showAddCategoryDialog = true },
                                                    label = { Text("Yeni") },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    }
                                                )
                                            }

                                            item {
                                                FilterChip(
                                                    selected = state.categoryId == null,
                                                    onClick = { onCategoryChange(null) },
                                                    label = { Text("Kategorisiz") },
                                                    leadingIcon = if (state.categoryId == null) {
                                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                    } else null
                                                )
                                            }

                                            items(categories) { category ->
                                                val isSelected = state.categoryId == category.id
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { onCategoryChange(if (isSelected) null else category.id) },
                                                    label = { Text(category.name) },
                                                    leadingIcon = if (isSelected) {
                                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                    } else null
                                                )
                                            }
                                        }
                                    }

                                    NoteToolCategory.NONE -> {}
                                }
                            }
                        }
                    }

                    // Alt Yüzen Kapsül Dok Barı (Minimalist, Şık, Tüm Ekran Boyutlarına Duyarlı)
                    Surface(
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp,
                        color = dockSurfaceColor,
                        shape = RoundedCornerShape(32.dp),
                        border = BorderStroke(1.dp, dockBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Düğmeler
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 0. Onay Kutusu (Checklist)
                                IconButton(
                                    onClick = {
                                        val text = state.contentValue.text
                                        if (hasChecklistItems) {
                                            val stripped = stripChecklist(text)
                                            onContentValueChange(TextFieldValue(stripped, selection = TextRange(stripped.length)))
                                        } else {
                                            val converted = convertToChecklist(text)
                                            onContentValueChange(TextFieldValue(converted, selection = TextRange(converted.length)))
                                        }
                                    },
                                    modifier = Modifier.size(38.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (hasChecklistItems) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (hasChecklistItems) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                        contentDescription = if (hasChecklistItems) "Metne Dönüştür" else "Onay Kutusuna Dönüştür",
                                        tint = if (hasChecklistItems) MaterialTheme.colorScheme.primary else dockIconColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 1. Ekle / Medya
                                IconButton(
                                    onClick = {
                                        activeToolCategory = if (activeToolCategory == NoteToolCategory.MEDIA) NoteToolCategory.NONE else NoteToolCategory.MEDIA
                                    },
                                    modifier = Modifier.size(38.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (activeToolCategory == NoteToolCategory.MEDIA) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.AddCircleOutline,
                                        contentDescription = "Ekle",
                                        tint = if (activeToolCategory == NoteToolCategory.MEDIA) MaterialTheme.colorScheme.primary else dockIconColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 2. Metin Biçimlendirme
                                IconButton(
                                    onClick = {
                                        activeToolCategory = if (activeToolCategory == NoteToolCategory.FORMAT) NoteToolCategory.NONE else NoteToolCategory.FORMAT
                                    },
                                    modifier = Modifier.size(38.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (activeToolCategory == NoteToolCategory.FORMAT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.FormatSize,
                                        contentDescription = "Biçimlendir",
                                        tint = if (activeToolCategory == NoteToolCategory.FORMAT) MaterialTheme.colorScheme.primary else dockIconColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 3. Renk & Tema
                                IconButton(
                                    onClick = {
                                        activeToolCategory = if (activeToolCategory == NoteToolCategory.COLOR) NoteToolCategory.NONE else NoteToolCategory.COLOR
                                    },
                                    modifier = Modifier.size(38.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (activeToolCategory == NoteToolCategory.COLOR) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = "Renk",
                                        tint = if (activeToolCategory == NoteToolCategory.COLOR) MaterialTheme.colorScheme.primary else dockIconColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 4. Kategori / Klasör
                                IconButton(
                                    onClick = {
                                        activeToolCategory = if (activeToolCategory == NoteToolCategory.CATEGORY) NoteToolCategory.NONE else NoteToolCategory.CATEGORY
                                    },
                                    modifier = Modifier.size(38.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (activeToolCategory == NoteToolCategory.CATEGORY) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = "Kategori",
                                        tint = if (activeToolCategory == NoteToolCategory.CATEGORY) MaterialTheme.colorScheme.primary else dockIconColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Sağ Bölüm: Aktif ses kaydı bildirimi veya Tıklanabilir İstatistik Hapı
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                if (state.isRecordingAudio) {
                                    AssistChip(
                                        onClick = onToggleAudioRecording,
                                        label = { Text("Durdur", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                        },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                    )
                                } else {
                                    Surface(
                                        onClick = { showStatsDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isAppDark) Color(0x22FFFFFF) else Color(0x12000000),
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                    ) {
                                        Text(
                                            text = "$wordCount kelime",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = dockIconColor.copy(alpha = 0.75f),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(animatedBgColor)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (!isPreviewMode) {
                        try {
                            if (hasChecklistItems) {
                                trailingFocusRequester.requestFocus()
                            } else {
                                focusRequester.requestFocus()
                            }
                        } catch (_: Exception) {
                            // Çökme önlendi
                        }
                    }
                }
        ) {
            if (!state.backgroundImage.isNullOrEmpty()) {
                AsyncImage(
                    model = File(state.backgroundImage),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.25f)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Notun Atandığı Kategori Etiketi (Varsa)
                val currentCategory = categories.firstOrNull { it.id == state.categoryId }
                if (currentCategory != null) {
                    SuggestionChip(
                        onClick = {
                            activeToolCategory = if (activeToolCategory == NoteToolCategory.CATEGORY) NoteToolCategory.NONE else NoteToolCategory.CATEGORY
                        },
                        label = { Text(currentCategory.name, style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isDarkBackground) Color(0x33FFFFFF) else Color(0x1A000000),
                            labelColor = contentColor,
                            iconContentColor = contentColor
                        ),
                        border = BorderStroke(1.dp, if (isDarkBackground) Color(0x33FFFFFF) else Color(0x22000000)),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Hatırlatıcı Bilgisi Varsa Göster
                state.reminderTime?.let { reminderTime ->
                    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    InputChip(
                        selected = true,
                        onClick = { onSetReminder(null) },
                        label = { Text("Hatırlatıcı: ${formatter.format(Date(reminderTime))}") },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Kaldır") },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Ekler (Görseller, Çizimler ve Ses Kayıtları)
                if (state.attachments.isNotEmpty()) {
                    AttachmentList(
                        attachments = state.attachments,
                        onDeleteAttachment = onDeleteAttachment,
                        onPlayAudio = onToggleAudioPlayback,
                        isPlayingAudio = state.isPlayingAudio,
                        currentPlayingPath = state.currentPlayingPath,
                        onImageClick = onImageClick,
                        onRevertImage = { revertConfirmPath = it },
                        canRevertImage = canRevertImage,
                        onExtractText = onExtractText,
                        isExtractingText = isExtractingText,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Başlık Alanı
                if (isPreviewMode) {
                    if (state.title.isNotBlank()) {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                } else {
                    TextField(
                        value = state.title,
                        onValueChange = onTitleChange,
                        placeholder = {
                            Text(
                                "Başlık",
                                style = MaterialTheme.typography.headlineSmall,
                                color = hintColor
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = contentColor,
                            unfocusedTextColor = contentColor,
                            focusedPlaceholderColor = hintColor,
                            unfocusedPlaceholderColor = hintColor,
                            cursorColor = if (isDarkBackground) Color.White else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // İçerik Alanı: Önizleme veya Canlı Düzenleme
                if (isPreviewMode) {
                    MarkdownPreview(
                        content = state.contentValue.text,
                        onContentChange = { updated ->
                            onContentValueChange(TextFieldValue(updated))
                        },
                        contentColor = contentColor,
                        onLinkClick = onLinkClick,
                        fontSize = state.noteFontSize,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 48.dp)
                    )
                } else {
                    val rawText = state.contentValue.text
                    val lines = rawText.lines()

                    val markdownTransformation = remember(hideMarkdownTokens, isSearchActive, searchQuery, activeMatchStartIndex, state.noteFontSize) {
                        MarkdownVisualTransformation(
                            hideSyntaxTokens = hideMarkdownTokens && !isSearchActive,
                            searchQuery = if (isSearchActive) searchQuery else "",
                            activeMatchStartIndex = if (isSearchActive) activeMatchStartIndex else null,
                            baseFontSize = state.noteFontSize
                        )
                    }

                    if (hasChecklistItems) {
                        // GOOGLE KEEP TARZI İNTERAKTİF ONAY KUTULARI (CHECKLIST)
                        val lastChecklistIdx = remember(rawText) { lines.indexOfLast { isChecklistLine(it) } }
                        val trailingLines = remember(rawText, lastChecklistIdx) {
                            if (lastChecklistIdx in lines.indices && lastChecklistIdx < lines.size - 1) {
                                lines.drop(lastChecklistIdx + 1)
                            } else {
                                emptyList()
                            }
                        }
                        val trailingText = remember(trailingLines) {
                            if (trailingLines.isNotEmpty() && trailingLines.first().isEmpty()) {
                                trailingLines.drop(1).joinToString("\n")
                            } else {
                                trailingLines.joinToString("\n")
                            }
                        }

                        val prefix = remember(rawText, lastChecklistIdx) {
                            if (lastChecklistIdx >= 0) lines.take(lastChecklistIdx + 1).joinToString("\n") else ""
                        }
                        val trailingStart = remember(prefix) { if (prefix.isEmpty()) 0 else prefix.length + 2 }

                        var trailingTfv by remember {
                            mutableStateOf(TextFieldValue(trailingText, TextRange(trailingText.length)))
                        }

                        LaunchedEffect(trailingText) {
                            if (trailingTfv.text != trailingText) {
                                val sel = if (isTrailingActive && state.contentValue.selection.start >= trailingStart) {
                                    val relStart = (state.contentValue.selection.start - trailingStart).coerceIn(0, trailingText.length)
                                    val relEnd = (state.contentValue.selection.end - trailingStart).coerceIn(0, trailingText.length)
                                    TextRange(relStart, relEnd)
                                } else {
                                    TextRange(trailingText.length.coerceAtMost(trailingTfv.selection.end))
                                }
                                trailingTfv = TextFieldValue(text = trailingText, selection = sel)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            val renderChecklistRow: @Composable (Int) -> Unit = { lineIdx ->
                                val line = lines[lineIdx]
                                val isChecked = isCheckedLine(line)
                                val itemText = extractItemText(line)
                                val prefixLen = getChecklistPrefixLength(line)
                                val lineStartOffset = remember(rawText, lineIdx) { getLineStartOffset(rawText, lineIdx) }
                                val itemStartInRaw = lineStartOffset + prefixLen

                                val lineFocusRequester = lineFocusRequesters.getOrPut(lineIdx) { FocusRequester() }
                                val currentLineTfv = lineTfvs[lineIdx]
                                val lineTfv = if (currentLineTfv == null || currentLineTfv.text != itemText) {
                                    val sel = if (activeChecklistLineIdx == lineIdx && state.contentValue.selection.start >= itemStartInRaw) {
                                        val relStart = (state.contentValue.selection.start - itemStartInRaw).coerceIn(0, itemText.length)
                                        val relEnd = (state.contentValue.selection.end - itemStartInRaw).coerceIn(0, itemText.length)
                                        TextRange(relStart, relEnd)
                                    } else {
                                        TextRange(itemText.length)
                                    }
                                    TextFieldValue(itemText, sel).also { lineTfvs[lineIdx] = it }
                                } else {
                                    currentLineTfv
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    KeepCheckboxIcon(
                                        isChecked = isChecked,
                                        fontSize = state.noteFontSize.sp,
                                        contentColor = contentColor,
                                        onToggle = {
                                            val updated = toggleChecklistLine(rawText, lineIdx)
                                            onContentValueChange(TextFieldValue(updated))
                                        }
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    BasicTextField(
                                        value = lineTfv,
                                        onValueChange = { newTfv ->
                                            lineTfvs[lineIdx] = newTfv
                                            activeChecklistLineIdx = lineIdx
                                            isTrailingActive = false

                                            if (newTfv.text != itemText) {
                                                if (newTfv.text.contains('\n')) {
                                                    val parts = newTfv.text.split('\n')
                                                    val currentPart = parts.first()
                                                    val nextPart = parts.drop(1).joinToString("\n")
                                                    val updated = splitChecklistLine(rawText, lineIdx, currentPart, nextPart)
                                                    activeChecklistLineIdx = lineIdx + 1
                                                    onContentValueChange(TextFieldValue(updated))
                                                } else {
                                                    val updated = updateChecklistItemText(rawText, lineIdx, newTfv.text)
                                                    val rawSelStart = (itemStartInRaw + newTfv.selection.start).coerceIn(0, updated.length)
                                                    val rawSelEnd = (itemStartInRaw + newTfv.selection.end).coerceIn(0, updated.length)
                                                    onContentValueChange(TextFieldValue(updated, TextRange(rawSelStart, rawSelEnd)))
                                                }
                                            } else {
                                                val rawSelStart = (itemStartInRaw + newTfv.selection.start).coerceIn(0, rawText.length)
                                                val rawSelEnd = (itemStartInRaw + newTfv.selection.end).coerceIn(0, rawText.length)
                                                onContentValueChange(state.contentValue.copy(selection = TextRange(rawSelStart, rawSelEnd)))
                                            }
                                        },
                                        visualTransformation = markdownTransformation,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = state.noteFontSize.sp,
                                            lineHeight = (state.noteFontSize * 1.35f).sp,
                                            textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                            color = if (isChecked) contentColor.copy(alpha = 0.5f) else contentColor
                                        ),
                                        cursorBrush = SolidColor(if (isDarkBackground) Color.White else MaterialTheme.colorScheme.primary),
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Sentences,
                                            imeAction = ImeAction.Next
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onNext = {
                                                val updated = addChecklistLine(rawText, lineIdx)
                                                activeChecklistLineIdx = lineIdx + 1
                                                onContentValueChange(TextFieldValue(updated))
                                            }
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(lineFocusRequester)
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused) {
                                                    activeChecklistLineIdx = lineIdx
                                                    isTrailingActive = false
                                                    val rawSelStart = (itemStartInRaw + lineTfv.selection.start).coerceIn(0, rawText.length)
                                                    val rawSelEnd = (itemStartInRaw + lineTfv.selection.end).coerceIn(0, rawText.length)
                                                    onContentValueChange(state.contentValue.copy(selection = TextRange(rawSelStart, rawSelEnd)))
                                                }
                                            }
                                    )

                                    IconButton(
                                        onClick = {
                                            val updated = deleteChecklistLine(rawText, lineIdx)
                                            lineTfvs.remove(lineIdx)
                                            lineFocusRequesters.remove(lineIdx)
                                            if (activeChecklistLineIdx == lineIdx) activeChecklistLineIdx = null
                                            onContentValueChange(TextFieldValue(updated))
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Maddeyi Sil",
                                            tint = contentColor.copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            val renderNormalRow: @Composable (Int) -> Unit = { lineIdx ->
                                val line = lines[lineIdx]
                                if (line.isNotBlank()) {
                                    val lineStartOffset = remember(rawText, lineIdx) { getLineStartOffset(rawText, lineIdx) }
                                    val lineFocusRequester = lineFocusRequesters.getOrPut(lineIdx) { FocusRequester() }
                                    val currentLineTfv = lineTfvs[lineIdx]
                                    val lineTfv = if (currentLineTfv == null || currentLineTfv.text != line) {
                                        val sel = if (activeChecklistLineIdx == lineIdx && state.contentValue.selection.start >= lineStartOffset) {
                                            val relStart = (state.contentValue.selection.start - lineStartOffset).coerceIn(0, line.length)
                                            val relEnd = (state.contentValue.selection.end - lineStartOffset).coerceIn(0, line.length)
                                            TextRange(relStart, relEnd)
                                        } else {
                                            TextRange(line.length)
                                        }
                                        TextFieldValue(line, sel).also { lineTfvs[lineIdx] = it }
                                    } else {
                                        currentLineTfv
                                    }

                                    BasicTextField(
                                        value = lineTfv,
                                        onValueChange = { newTfv ->
                                            lineTfvs[lineIdx] = newTfv
                                            activeChecklistLineIdx = lineIdx
                                            isTrailingActive = false

                                            if (newTfv.text != line) {
                                                val linesCopy = lines.toMutableList()
                                                linesCopy[lineIdx] = newTfv.text
                                                val updated = linesCopy.joinToString("\n")
                                                val rawSelStart = (lineStartOffset + newTfv.selection.start).coerceIn(0, updated.length)
                                                val rawSelEnd = (lineStartOffset + newTfv.selection.end).coerceIn(0, updated.length)
                                                onContentValueChange(TextFieldValue(updated, TextRange(rawSelStart, rawSelEnd)))
                                            } else {
                                                val rawSelStart = (lineStartOffset + newTfv.selection.start).coerceIn(0, rawText.length)
                                                val rawSelEnd = (lineStartOffset + newTfv.selection.end).coerceIn(0, rawText.length)
                                                onContentValueChange(state.contentValue.copy(selection = TextRange(rawSelStart, rawSelEnd)))
                                            }
                                        },
                                        visualTransformation = markdownTransformation,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = state.noteFontSize.sp,
                                            lineHeight = (state.noteFontSize * 1.35f).sp,
                                            color = contentColor
                                        ),
                                        cursorBrush = SolidColor(if (isDarkBackground) Color.White else MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .focusRequester(lineFocusRequester)
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused) {
                                                    activeChecklistLineIdx = lineIdx
                                                    isTrailingActive = false
                                                    val rawSelStart = (lineStartOffset + lineTfv.selection.start).coerceIn(0, rawText.length)
                                                    val rawSelEnd = (lineStartOffset + lineTfv.selection.end).coerceIn(0, rawText.length)
                                                    onContentValueChange(state.contentValue.copy(selection = TextRange(rawSelStart, rawSelEnd)))
                                                }
                                            }
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height((state.noteFontSize * 0.7f).dp))
                                }
                            }

                            // 1. Checklist maddeleri ve aradaki satırlar
                            if (lastChecklistIdx >= 0) {
                                if (moveCheckedToBottom) {
                                    val uncheckedIndices = (0..lastChecklistIdx).filter { lineIdx ->
                                        !isChecklistLine(lines[lineIdx]) || !isCheckedLine(lines[lineIdx])
                                    }
                                    val checkedIndices = (0..lastChecklistIdx).filter { lineIdx ->
                                        isChecklistLine(lines[lineIdx]) && isCheckedLine(lines[lineIdx])
                                    }

                                    // Tamamlanmamış maddeler ve önceki metinler
                                    uncheckedIndices.forEach { lineIdx ->
                                        if (isChecklistLine(lines[lineIdx])) {
                                            renderChecklistRow(lineIdx)
                                        } else {
                                            renderNormalRow(lineIdx)
                                        }
                                    }

                                    // "+ Liste öğesi ekle" butonu
                                    Surface(
                                        onClick = {
                                            val updated = addChecklistLine(rawText)
                                            val newLastIdx = updated.lines().indexOfLast { isChecklistLine(it) }
                                            activeChecklistLineIdx = newLastIdx
                                            onContentValueChange(TextFieldValue(updated))
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isDarkBackground) Color(0x25FFFFFF) else Color(0x14000000),
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                tint = contentColor.copy(alpha = 0.9f),
                                                modifier = Modifier.size((state.noteFontSize * 1.15f).dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Liste öğesi ekle",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = state.noteFontSize.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = contentColor.copy(alpha = 0.9f)
                                            )
                                        }
                                    }

                                    // Tamamlanan Öğeler Akordiyonu (Google Keep Tarzı)
                                    if (checkedIndices.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            onClick = { isCompletedSectionExpanded = !isCompletedSectionExpanded },
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.Transparent,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isCompletedSectionExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = contentColor.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "${checkedIndices.size} tamamlanan öğe",
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                                    color = contentColor.copy(alpha = 0.6f)
                                                )
                                            }
                                        }

                                        AnimatedVisibility(
                                            visible = isCompletedSectionExpanded,
                                            enter = fadeIn() + expandVertically(),
                                            exit = fadeOut() + shrinkVertically()
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                checkedIndices.forEach { lineIdx ->
                                                    renderChecklistRow(lineIdx)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Standart (Belge Sırasında) Gösterim
                                    for (lineIdx in 0..lastChecklistIdx) {
                                        val line = lines[lineIdx]
                                        if (isChecklistLine(line)) {
                                            renderChecklistRow(lineIdx)
                                        } else {
                                            renderNormalRow(lineIdx)
                                        }
                                    }

                                    // "+ Liste öğesi ekle" butonu
                                    Surface(
                                        onClick = {
                                            val updated = addChecklistLine(rawText)
                                            val newLastIdx = updated.lines().indexOfLast { isChecklistLine(it) }
                                            activeChecklistLineIdx = newLastIdx
                                            onContentValueChange(TextFieldValue(updated))
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isDarkBackground) Color(0x25FFFFFF) else Color(0x14000000),
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                tint = contentColor.copy(alpha = 0.9f),
                                                modifier = Modifier.size((state.noteFontSize * 1.15f).dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Liste öğesi ekle",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = state.noteFontSize.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = contentColor.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 3. Checklist altındaki serbest metin alanı (notun devamı)
                            BasicTextField(
                                value = trailingTfv,
                                onValueChange = { newTfv ->
                                    trailingTfv = newTfv
                                    isTrailingActive = true
                                    activeChecklistLineIdx = null

                                    val updated = if (newTfv.text.isEmpty()) {
                                        prefix
                                    } else if (prefix.isEmpty()) {
                                        newTfv.text
                                    } else {
                                        "$prefix\n\n${newTfv.text}"
                                    }
                                    val rawSelStart = (trailingStart + newTfv.selection.start).coerceIn(0, updated.length)
                                    val rawSelEnd = (trailingStart + newTfv.selection.end).coerceIn(0, updated.length)
                                    onContentValueChange(TextFieldValue(updated, TextRange(rawSelStart, rawSelEnd)))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 350.dp)
                                    .focusRequester(trailingFocusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            isTrailingActive = true
                                            activeChecklistLineIdx = null
                                            val rawSelStart = (trailingStart + trailingTfv.selection.start).coerceIn(0, rawText.length)
                                            val rawSelEnd = (trailingStart + trailingTfv.selection.end).coerceIn(0, rawText.length)
                                            onContentValueChange(state.contentValue.copy(selection = TextRange(rawSelStart, rawSelEnd)))
                                        }
                                    },
                                visualTransformation = markdownTransformation,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = state.noteFontSize.sp,
                                    lineHeight = (state.noteFontSize * 1.35f).sp,
                                    color = contentColor
                                ),
                                cursorBrush = SolidColor(if (isDarkBackground) Color.White else MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                decorationBox = { innerTextField ->
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (trailingTfv.text.isEmpty()) {
                                            Text(
                                                "Notunuza metin olarak devam edin...",
                                                fontSize = state.noteFontSize.sp,
                                                color = hintColor
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    } else {
                        // STANDART METİN DÜZENLEYİCİSİ
                        TextField(
                            value = state.contentValue,
                            onValueChange = onContentValueChange,
                            placeholder = {
                                Text(
                                    "Notunuzu yazmaya başlayın...\n(Kalın, İtalik, Vurgu, Liste ve Çizim ekleyebilirsiniz)",
                                    fontSize = state.noteFontSize.sp,
                                    color = hintColor
                                )
                            },
                            visualTransformation = markdownTransformation,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = state.noteFontSize.sp,
                                lineHeight = (state.noteFontSize * 1.35f).sp,
                                color = contentColor
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = contentColor,
                                unfocusedTextColor = contentColor,
                                focusedPlaceholderColor = hintColor,
                                unfocusedPlaceholderColor = hintColor,
                                cursorColor = if (isDarkBackground) Color.White else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 400.dp)
                                .focusRequester(focusRequester)
                        )
                    }
                }

                // 1. Bear & Obsidian Stili Metin İçi `#etiket` ve İç İçe Etiketler
                if (state.extractedTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.extractedTags) { tag ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text("#$tag") },
                                icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }

                // 2. Obsidian & Logseq Stili Çift Yönlü Bağlantılar (`[[Wikilinks]]`)
                if (state.extractedWikilinks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.extractedWikilinks) { linkTitle ->
                            AssistChip(
                                onClick = { onLinkClick(linkTitle) },
                                label = { Text("[[$linkTitle]]") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Link,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                    }
                }

                // 3. Obsidian & Roam Stili Yenilenmiş Zarif Geri Bağlantılar (Backlinks & Linked References)
                if (state.backlinks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Katlanabilir Başlık Çubuğu
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isBacklinksExpanded = !isBacklinksExpanded }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.DeviceHub,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    text = "Geri Bağlantılar",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "${state.backlinks.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isBacklinksExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isBacklinksExpanded) "Daralt" else "Genişlet",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Bağlantılı Notlar Listesi
                    AnimatedVisibility(
                        visible = isBacklinksExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.backlinks.forEach { linkedNote ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    tonalElevation = 1.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onNavigateToNote(linkedNote.id) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Description,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = linkedNote.title.ifBlank { "Başlıksız Not" },
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (linkedNote.content.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = linkedNote.content.take(75).replace("\n", " "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Alt kısımda dokunup yazmaya devam etmek için boş alan
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    // Yeni Kategori Ekleme Dialogu
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddCategoryDialog = false
                newCategoryName = ""
            },
            title = { Text("Yeni Kategori Oluştur") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder = { Text("Kategori adı (örn: Projeler)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        onAddCategory(newCategoryName)
                        newCategoryName = ""
                        showAddCategoryDialog = false
                    }
                }) {
                    Text("Ekle")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddCategoryDialog = false
                    newCategoryName = ""
                }) {
                    Text("İptal")
                }
            }
        )
    }

    // Not İstatistikleri Dialogu (Bear & Obsidian Seviyesi Detaylı Analiz)
    if (showStatsDialog) {
        val text = state.contentValue.text
        val words = if (text.isBlank()) emptyList() else text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val wordCnt = words.size
        val charCnt = text.length
        val charWithoutSpaces = text.count { !it.isWhitespace() }
        val lineCnt = if (text.isEmpty()) 0 else text.lines().size
        val readingTimeMin = kotlin.math.max(1, (wordCnt / 200.0).roundToInt())
        val checklistLines = text.lines().filter { isChecklistLine(it) }
        val checkedLinesCount = checklistLines.count { isCheckedLine(it) }

        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            icon = { Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Not İstatistikleri", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tahmini Okuma Süresi Kartı
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Tahmini Okuma: ~$readingTimeMin dakika",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Kelime Sayısı:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$wordCnt kelime", fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Karakter (Boşluklu):", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$charCnt", fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Karakter (Boşluksuz):", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$charWithoutSpaces", fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Satır Sayısı:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$lineCnt satır", fontWeight = FontWeight.Bold)
                    }

                    if (checklistLines.isNotEmpty()) {
                        val pct = if (checklistLines.isNotEmpty()) (checkedLinesCount * 100 / checklistLines.size) else 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Onay Kutusu:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$checkedLinesCount / ${checklistLines.size} (%$pct)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (state.attachments.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Medya & Ekler:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${state.attachments.size} adet", fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("tr")) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Oluşturulma:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dateFormat.format(Date(state.createdAt)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Son Düzenleme:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dateFormat.format(Date(state.updatedAt)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatsDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }



    // İçindekiler (Outline / Başlıklar) Dialogu
    if (showOutlineDialog) {
        val headingsList = remember(state.contentValue.text) {
            state.contentValue.text.lines().mapIndexedNotNull { index, line ->
                val trimmed = line.trimStart()
                if (trimmed.startsWith("#")) {
                    val level = trimmed.takeWhile { it == '#' }.length
                    val title = trimmed.drop(level).trim()
                    if (title.isNotEmpty()) Triple(index, level, title) else null
                } else null
            }
        }

        AlertDialog(
            onDismissRequest = { showOutlineDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("İçindekiler Tablosu") },
            text = {
                if (headingsList.isEmpty()) {
                    Text(
                        "Notta henüz herhangi bir başlık (# Başlık) bulunmuyor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        headingsList.forEach { (lineIndex, level, title) ->
                            val indent = ((level - 1).coerceAtLeast(0) * 16).dp
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = indent)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        showOutlineDialog = false
                                        val lineOffsets = state.contentValue.text.lines().runningFold(0) { acc, l -> acc + l.length + 1 }
                                        val targetOffset = lineOffsets.getOrNull(lineIndex) ?: 0
                                        onContentValueChange(
                                            state.contentValue.copy(
                                                selection = TextRange(targetOffset.coerceAtMost(state.contentValue.text.length))
                                            )
                                        )
                                        runCatching { focusRequester.requestFocus() }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "H$level",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (level == 1) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOutlineDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }
}
