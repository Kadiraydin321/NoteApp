package com.example.noteapp.presentation.detail

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.noteapp.presentation.components.AttachmentList
import com.example.noteapp.presentation.components.MarkdownPreview
import com.example.noteapp.presentation.components.MarkdownVisualTransformation
import com.example.noteapp.presentation.components.RichTextToolbar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    state: NoteDetailState,
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
    onAddDrawingClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    autoAction: String? = null
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var isPreviewMode by remember { mutableStateOf(false) }
    var hideMarkdownTokens by remember { mutableStateOf(true) }

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

    Scaffold(
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
                    // Biçimlendirme İşaretlerini Gizle / Göster (Acil durum ve anlık kontrol)
                    if (!isPreviewMode) {
                        IconButton(onClick = { hideMarkdownTokens = !hideMarkdownTokens }) {
                            Icon(
                                imageVector = if (hideMarkdownTokens) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (hideMarkdownTokens) "İşaretler Gizli (Temiz Görünüm)" else "İşaretler Görünür",
                                tint = if (!hideMarkdownTokens) MaterialTheme.colorScheme.tertiary else contentColor
                            )
                        }
                    }

                    // Düzenle / Önizle Modu Değiştirici
                    IconButton(onClick = { isPreviewMode = !isPreviewMode }) {
                        Icon(
                            imageVector = if (isPreviewMode) Icons.Default.Edit else Icons.Default.RemoveRedEye,
                            contentDescription = if (isPreviewMode) "Düzenle" else "Önizle"
                        )
                    }

                    // Kopyala
                    IconButton(onClick = { copyNoteToClipboard() }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Kopyala")
                    }

                    // Paylaş
                    IconButton(onClick = { shareNote() }) {
                        Icon(Icons.Default.Share, contentDescription = "Paylaş")
                    }

                    // Hatırlatıcı Butonu
                    IconButton(onClick = { showDateTimePicker() }) {
                        Icon(
                            imageVector = if (state.reminderTime != null) Icons.Default.AlarmOn else Icons.Default.AddAlert,
                            contentDescription = "Hatırlatıcı",
                            tint = if (state.reminderTime != null) MaterialTheme.colorScheme.primary else contentColor
                        )
                    }

                    // Sabitle Butonu
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (state.isPinned) Icons.Default.PushPin else Icons.Default.OutlinedFlag,
                            contentDescription = "Sabitle",
                            tint = if (state.isPinned) MaterialTheme.colorScheme.primary else contentColor
                        )
                    }

                    // Kilit Butonu
                    IconButton(onClick = onToggleLock) {
                        Icon(
                            imageVector = if (state.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Kilit",
                            tint = if (state.isLocked) MaterialTheme.colorScheme.primary else contentColor
                        )
                    }

                    // Kaydet
                    IconButton(onClick = onSaveClick) {
                        Icon(Icons.Default.Done, contentDescription = "Kaydet")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                // Zengin Metin Araç Çubuğu (Sadece Düzenleme Modunda)
                if (!isPreviewMode) {
                    RichTextToolbar(
                        textFieldValue = state.contentValue,
                        onValueChange = onContentValueChange,
                        onFocusRequest = { focusRequester.requestFocus() },
                        hideMarkdownTokens = hideMarkdownTokens,
                        onToggleHideMarkdownTokens = { hideMarkdownTokens = !hideMarkdownTokens },
                        onAddImage = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onAddDrawing = onAddDrawingClick,
                        onRecordAudio = {
                            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        },
                        isRecording = state.isRecordingAudio
                    )
                }

                // Sayfa Rengi Seçim Paleti ve İstatistik Çubuğu
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                    color = if (isDarkBackground) Color(0xFF242424) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Not İstatistikleri (Kelime, Karakter, Okuma Süresi)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$wordCount kelime  •  $charCount karakter",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "~$readingTime dk okuma",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Renk Paleti Listesi
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
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
}
