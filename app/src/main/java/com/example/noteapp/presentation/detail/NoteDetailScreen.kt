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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.noteapp.domain.model.Category
import com.example.noteapp.presentation.components.AttachmentList
import com.example.noteapp.presentation.components.MarkdownPreview
import com.example.noteapp.presentation.components.MarkdownVisualTransformation
import com.example.noteapp.presentation.components.applyMarkdownWrap
import com.example.noteapp.presentation.components.applyPrefixToLine
import com.example.noteapp.presentation.components.insertTextAtCursor
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
    onClearJustEditedImage: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isPreviewMode by remember { mutableStateOf(false) }
    var hideMarkdownTokens by remember { mutableStateOf(true) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showStatsDialog by remember { mutableStateOf(false) }
    var revertConfirmPath by remember { mutableStateOf<String?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var activeToolCategory by remember { mutableStateOf(NoteToolCategory.NONE) }

    // Donanımsal veya jest ile geri tuşuna basıldığında otomatik kaydet
    BackHandler {
        onSaveClick()
        onBackClick()
    }

    // Not detay ekranından herhangi bir şekilde ayrılındığında otomatik kaydet
    DisposableEffect(Unit) {
        onDispose {
            onSaveClick()
        }
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

    // Açık veya koyu arkaplana göre metin ve ikon renkleri
    val isDarkBackground = animatedBgColor.luminance() < 0.45f
    val contentColor = if (isDarkBackground) Color(0xFFF5F5F5) else MaterialTheme.colorScheme.onSurface
    val hintColor = contentColor.copy(alpha = 0.55f)

    // Medya ve Ses Seçicileri
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAddImageUri(uri)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onToggleAudioRecording()
        }
    }

    // Widget'tan otomatik aksiyonla açıldıysa
    LaunchedEffect(autoAction) {
        when (autoAction) {
            "image" -> {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            "voice" -> {
                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
            "draw" -> {
                onAddDrawingClick()
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

    // Notu Paylaşma
    fun shareNote() {
        val fullText = buildString {
            if (state.title.isNotBlank()) {
                appendLine(state.title)
                appendLine()
            }
            append(state.contentValue.text)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, state.title)
            putExtra(Intent.EXTRA_TEXT, fullText)
        }
        context.startActivity(Intent.createChooser(intent, "Notu Paylaş"))
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = animatedBgColor,
        contentColor = contentColor,
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = animatedBgColor,
                    titleContentColor = contentColor,
                    actionIconContentColor = contentColor,
                    navigationIconContentColor = contentColor
                ),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    // Geri Al (Undo)
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Geri Al",
                            tint = if (canUndo) contentColor else contentColor.copy(alpha = 0.35f)
                        )
                    }

                    // İleri Al (Redo)
                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "İleri Al",
                            tint = if (canRedo) contentColor else contentColor.copy(alpha = 0.35f)
                        )
                    }

                    // 1. Sabitleme (Pin) Butonu - Ana Aksiyon
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (state.isPinned) Icons.Default.PushPin else Icons.Default.OutlinedFlag,
                            contentDescription = "Sabitle",
                            tint = if (state.isPinned) MaterialTheme.colorScheme.primary else contentColor
                        )
                    }

                    // 2. Notu Sil Butonu - Ana Aksiyon
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Notu Sil",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        )
                    }

                    // 3. Kaydet Butonu - Ana Aksiyon
                    IconButton(onClick = onSaveClick) {
                        Icon(Icons.Default.Done, contentDescription = "Kaydet", tint = contentColor)
                    }

                    // 4. Üç Nokta Menüsü - Harici Tüm Özellikler
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Daha Fazla Seçenek", tint = contentColor)
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

                            // Notu Paylaş
                            DropdownMenuItem(
                                text = { Text("Notu Paylaş") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    shareNote()
                                    showMoreMenu = false
                                }
                            )

                            // Kopyala
                            DropdownMenuItem(
                                text = { Text("Panoya Kopyala") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    copyNoteToClipboard()
                                    showMoreMenu = false
                                }
                            )

                            HorizontalDivider()

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

                            HorizontalDivider()

                            // Not İstatistikleri
                            DropdownMenuItem(
                                text = { Text("Not İstatistikleri") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showStatsDialog = true
                                    showMoreMenu = false
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isPreviewMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    // Kategoriye Göre Genişleyen Araç Kutusu (Drawer / Expansion Panel)
                    AnimatedVisibility(
                        visible = activeToolCategory != NoteToolCategory.NONE,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            color = if (isDarkBackground) Color(0xFF232323) else MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                                                onClick = {
                                                    imagePickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }
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
                                                    insertTextAtCursor(state.contentValue, "[$now] ", onContentValueChange) { focusRequester.requestFocus() }
                                                }
                                            ) {
                                                Icon(Icons.Default.AccessTime, contentDescription = "Zaman Damgası")
                                            }
                                        }
                                    }

                                    NoteToolCategory.FORMAT -> {
                                        // Metin Biçimlendirme Araçları
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Geri Al (Undo)
                                            IconButton(onClick = onUndo, enabled = canUndo) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Undo,
                                                    contentDescription = "Geri Al",
                                                    tint = if (canUndo) contentColor else contentColor.copy(alpha = 0.35f)
                                                )
                                            }
                                            // İleri Al (Redo)
                                            IconButton(onClick = onRedo, enabled = canRedo) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Redo,
                                                    contentDescription = "İleri Al",
                                                    tint = if (canRedo) contentColor else contentColor.copy(alpha = 0.35f)
                                                )
                                            }
                                            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                                            // Kalın
                                            IconButton(onClick = {
                                                applyMarkdownWrap(state.contentValue, "**", "**", onContentValueChange) { focusRequester.requestFocus() }
                                            }) {
                                                Icon(Icons.Default.FormatBold, contentDescription = "Kalın")
                                            }
                                            // İtalik
                                            IconButton(onClick = {
                                                applyMarkdownWrap(state.contentValue, "*", "*", onContentValueChange) { focusRequester.requestFocus() }
                                            }) {
                                                Icon(Icons.Default.FormatItalic, contentDescription = "İtalik")
                                            }
                                            // Üstü Çizili
                                            IconButton(onClick = {
                                                applyMarkdownWrap(state.contentValue, "~~", "~~", onContentValueChange) { focusRequester.requestFocus() }
                                            }) {
                                                Icon(Icons.Default.FormatStrikethrough, contentDescription = "Üstü Çizili")
                                            }
                                            // Fosforlu Vurgu
                                            IconButton(onClick = {
                                                applyMarkdownWrap(state.contentValue, "==", "==", onContentValueChange) { focusRequester.requestFocus() }
                                            }) {
                                                Icon(Icons.Default.BorderColor, contentDescription = "Vurgu", tint = Color(0xFFFBC02D))
                                            }
                                            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                                            // Başlık (H3)
                                            IconButton(onClick = {
                                                applyPrefixToLine(state.contentValue, "### ", onContentValueChange) { focusRequester.requestFocus() }
                                            }) {
                                                Icon(Icons.Default.Title, contentDescription = "Başlık")
                                            }
                                            // Kontrol Kutusu
                                            IconButton(onClick = {
                                                applyPrefixToLine(state.contentValue, "- [ ] ", onContentValueChange) { focusRequester.requestFocus() }
                                            }) {
                                                Icon(Icons.Default.Checklist, contentDescription = "Yapılacak")
                                            }
                                            // Madde İşareti
                                            IconButton(onClick = {
                                                applyPrefixToLine(state.contentValue, "• ", onContentValueChange) { focusRequester.requestFocus() }
                                            }) {
                                                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Madde")
                                            }
                                            // Numaralı Liste
                                            IconButton(onClick = {
                                                applyPrefixToLine(state.contentValue, "1. ", onContentValueChange) { focusRequester.requestFocus() }
                                            }) {
                                                Icon(Icons.Default.FormatListNumbered, contentDescription = "Numaralı")
                                            }
                                            // Alıntı
                                            IconButton(onClick = {
                                                applyPrefixToLine(state.contentValue, "> ", onContentValueChange) { focusRequester.requestFocus() }
                                            }) {
                                                Icon(Icons.Default.FormatQuote, contentDescription = "Alıntı")
                                            }
                                        }
                                    }

                                    NoteToolCategory.COLOR -> {
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

                    // Alt Sabit Kategori Dok Barı (Minimalist ve Şık)
                    Surface(
                        tonalElevation = 3.dp,
                        shadowElevation = 6.dp,
                        color = if (isDarkBackground) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 4 Kategori Düğmesi
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // 1. Ekle / Medya
                                IconButton(
                                    onClick = {
                                        activeToolCategory = if (activeToolCategory == NoteToolCategory.MEDIA) NoteToolCategory.NONE else NoteToolCategory.MEDIA
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (activeToolCategory == NoteToolCategory.MEDIA) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.AddCircleOutline,
                                        contentDescription = "Ekle",
                                        tint = if (activeToolCategory == NoteToolCategory.MEDIA) MaterialTheme.colorScheme.primary else contentColor
                                    )
                                }

                                // 2. Metin Biçimlendirme
                                IconButton(
                                    onClick = {
                                        activeToolCategory = if (activeToolCategory == NoteToolCategory.FORMAT) NoteToolCategory.NONE else NoteToolCategory.FORMAT
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (activeToolCategory == NoteToolCategory.FORMAT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.FormatSize,
                                        contentDescription = "Biçimlendir",
                                        tint = if (activeToolCategory == NoteToolCategory.FORMAT) MaterialTheme.colorScheme.primary else contentColor
                                    )
                                }

                                // 3. Renk & Tema
                                IconButton(
                                    onClick = {
                                        activeToolCategory = if (activeToolCategory == NoteToolCategory.COLOR) NoteToolCategory.NONE else NoteToolCategory.COLOR
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (activeToolCategory == NoteToolCategory.COLOR) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = "Renk",
                                        tint = if (activeToolCategory == NoteToolCategory.COLOR) MaterialTheme.colorScheme.primary else contentColor
                                    )
                                }

                                // 4. Kategori / Klasör
                                IconButton(
                                    onClick = {
                                        activeToolCategory = if (activeToolCategory == NoteToolCategory.CATEGORY) NoteToolCategory.NONE else NoteToolCategory.CATEGORY
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (activeToolCategory == NoteToolCategory.CATEGORY) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = "Kategori",
                                        tint = if (activeToolCategory == NoteToolCategory.CATEGORY) MaterialTheme.colorScheme.primary else contentColor
                                    )
                                }
                            }

                            // Sağ Bölüm: Aktif ses kaydı bildirimi veya Kelime sayısı
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.isRecordingAudio) {
                                    AssistChip(
                                        onClick = onToggleAudioRecording,
                                        label = { Text("Kayıt Durdur", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                    )
                                } else {
                                    Text(
                                        text = "$wordCount kelime",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = contentColor.copy(alpha = 0.65f),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        val scrollState = rememberScrollState()

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
                        focusRequester.requestFocus()
                    }
                }
        ) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 48.dp)
                    )
                } else {
                    TextField(
                        value = state.contentValue,
                        onValueChange = onContentValueChange,
                        placeholder = {
                            Text(
                                "Notunuzu yazmaya başlayın...\n(Kalın, İtalik, Vurgu, Liste ve Çizim ekleyebilirsiniz)",
                                color = hintColor
                            )
                        },
                        visualTransformation = remember(hideMarkdownTokens) { MarkdownVisualTransformation(hideSyntaxTokens = hideMarkdownTokens) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = contentColor,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.25f
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = if (isDarkBackground) Color.White else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 400.dp)
                            .focusRequester(focusRequester)
                    )
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

    // Not İstatistikleri Dialogu
    if (showStatsDialog) {
        val text = state.contentValue.text
        val wordCount = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
        val charCount = text.length
        val readingTimeMin = kotlin.math.max(1, (wordCount / 200.0).roundToInt())

        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            icon = { Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Not İstatistikleri") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Kelime Sayısı:", fontWeight = FontWeight.Medium)
                        Text("$wordCount kelime", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Karakter Sayısı:", fontWeight = FontWeight.Medium)
                        Text("$charCount karakter", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tahmini Okuma Süresi:", fontWeight = FontWeight.Medium)
                        Text("~$readingTimeMin dakika", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    if (state.attachments.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Medya & Ekler:", fontWeight = FontWeight.Medium)
                            Text("${state.attachments.size} adet", fontWeight = FontWeight.Bold)
                        }
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
}
