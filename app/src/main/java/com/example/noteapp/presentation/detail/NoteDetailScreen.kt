package com.example.noteapp.presentation.detail

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.noteapp.presentation.components.AttachmentList
import com.example.noteapp.presentation.components.RichTextToolbar
import java.text.SimpleDateFormat
import java.util.*

val NoteColors = listOf(
    Color.Transparent,
    Color(0xFFFFCDD2), // Açık Kırmızı
    Color(0xFFF8BBD0), // Açık Pembe
    Color(0xFFE1BEE7), // Açık Mor
    Color(0xFFC5CAE9), // Açık İndigo
    Color(0xFFBBDEFB), // Açık Mavi
    Color(0xFFB2DFDB), // Açık Teal
    Color(0xFFC8E6C9), // Açık Yeşil
    Color(0xFFFFF9C4), // Açık Sarı
    Color(0xFFFFE0B2)  // Açık Turuncu
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
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
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

    // Tarih / Saat seçimi
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    // Hatırlatıcı Butonu
                    IconButton(onClick = { showDateTimePicker() }) {
                        Icon(
                            imageVector = if (state.reminderTime != null) Icons.Default.AlarmOn else Icons.Default.AddAlert,
                            contentDescription = "Hatırlatıcı",
                            tint = if (state.reminderTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Sabitle Butonu
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (state.isPinned) Icons.Default.PushPin else Icons.Default.OutlinedFlag,
                            contentDescription = "Sabitle",
                            tint = if (state.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Kilit Butonu
                    IconButton(onClick = onToggleLock) {
                        Icon(
                            imageVector = if (state.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Kilit",
                            tint = if (state.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
            Column {
                // Zengin Metin Araç Çubuğu
                RichTextToolbar(
                    textFieldValue = state.contentValue,
                    onValueChange = onContentValueChange,
                    onAddImage = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRecordAudio = {
                        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    isRecording = state.isRecordingAudio
                )

                // Renk Seçim Paleti
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(NoteColors) { color ->
                            val argb = color.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (color == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant else color)
                                    .border(
                                        width = if (state.color == argb) 3.dp else 1.dp,
                                        color = if (state.color == argb) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { onColorChange(argb) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
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

            // Ekler (Görseller ve Ses Kayıtları)
            AttachmentList(
                attachments = state.attachments,
                onDeleteAttachment = onDeleteAttachment,
                onPlayAudio = onToggleAudioPlayback,
                isPlayingAudio = state.isPlayingAudio,
                currentPlayingPath = state.currentPlayingPath,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Başlık
            TextField(
                value = state.title,
                onValueChange = onTitleChange,
                placeholder = { Text("Başlık", style = MaterialTheme.typography.headlineSmall) },
                textStyle = MaterialTheme.typography.headlineSmall,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // İçerik (Zengin Metin / Markdown)
            TextField(
                value = state.contentValue,
                onValueChange = onContentValueChange,
                placeholder = { Text("Notunuzu yazmaya başlayın...") },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 250.dp)
            )
        }
    }
}
