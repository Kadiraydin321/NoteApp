package com.example.noteapp.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.noteapp.data.settings.AppSettings
import com.example.noteapp.data.settings.NotesLayoutMode
import com.example.noteapp.data.settings.ThemeMode
import com.example.noteapp.data.settings.WidgetFilterMode
import com.example.noteapp.presentation.detail.NoteColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    backupUiState: BackupUiState,
    onWidgetFilterChange: (WidgetFilterMode) -> Unit,
    onWidgetShowLockedChange: (Boolean) -> Unit,
    onWidgetShowContentChange: (Boolean) -> Unit = {},
    onDefaultColorChange: (Int) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onLayoutModeChange: (NotesLayoutMode) -> Unit = {},
    onMasterPinChange: (String?) -> Unit = {},
    onAutoLockChange: (Boolean) -> Unit = {},
    onHighContrastChange: (Boolean) -> Unit = {},
    onRefreshWidget: () -> Unit,
    onExportToUri: (Uri, String?) -> Unit,
    onExportAndShare: (Context, String?) -> Unit,
    onImportFromUri: (Uri, Boolean, String?) -> Unit,
    onDismissBackupMessage: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var isSharePending by remember { mutableStateOf(false) }
    var vaultPasswordForExport by remember { mutableStateOf("") }
    var importPasswordInput by remember { mutableStateOf("") }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    // Yeni dosya oluşturarak dışa aktarma (SAF CreateDocument)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            onExportToUri(uri, vaultPasswordForExport.ifBlank { null })
        }
    }

    // Dosyadan içe aktarma seçicisi (SAF OpenDocument)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            importPasswordInput = settings.masterPin ?: ""
            showImportConfirmDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ==========================================
            // BÖLÜM 1: YEDEKLEME & DIŞA / İÇE AKTARMA
            // ==========================================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Yedekleme ve Dışa Aktarma",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val lastBackupText = settings.lastBackupTimestamp?.let {
                                "Son yedek: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(it))
                            } ?: "Henüz yedek alınmadı"
                            Text(
                                text = lastBackupText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        "Notlarınızı, çizimlerinizi, ses kayıtlarınızı ve tüm görsellerinizi evrensel bir .ZIP arşiv paketi olarak dışa aktarabilir ya da cihazınıza geri yükleyebilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    // Buton 1: Cihaza / Dosyalara Kaydet
                    Button(
                        onClick = {
                            isSharePending = false
                            vaultPasswordForExport = settings.masterPin ?: ""
                            showExportPasswordDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Kasa Kilidi ile Yedekle (.notevault / .zip)")
                    }

                    // Buton 2: Paylaş Menüsü ile Dışa Aktar (WhatsApp, Drive, vb.)
                    OutlinedButton(
                        onClick = {
                            isSharePending = true
                            vaultPasswordForExport = settings.masterPin ?: ""
                            showExportPasswordDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Kasa Kilidi ile Yedeği Paylaş / Gönder")
                    }

                    // Buton 3: Yedeği İçe Aktar (Geri Yükle)
                    FilledTonalButton(
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Yedekten Geri Yükle (İçe Aktar)")
                    }

                }
            }

            // ==========================================
            // BÖLÜM 2: ANA EKRAN WİDGET'I
            // ==========================================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Ana Ekran Widget Ayarları",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    // Filtreleme Seçimi
                    Text("Widget'ta Görüntülenecek Notlar:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = settings.widgetFilterMode == WidgetFilterMode.ALL,
                            onClick = { onWidgetFilterChange(WidgetFilterMode.ALL) },
                            label = { Text("Tüm Notlar") },
                            leadingIcon = if (settings.widgetFilterMode == WidgetFilterMode.ALL) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = settings.widgetFilterMode == WidgetFilterMode.FAVORITES,
                            onClick = { onWidgetFilterChange(WidgetFilterMode.FAVORITES) },
                            label = { Text("Sadece Favoriler ★") },
                            leadingIcon = if (settings.widgetFilterMode == WidgetFilterMode.FAVORITES) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Kilitli Notlar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kilitli Notları Göster", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "Kapalıyken şifrelenmiş notlar widget listesinde hiç görünmez",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.widgetShowLockedNotes,
                            onCheckedChange = onWidgetShowLockedChange
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Widget'ta içerik önizlemesi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "Kapalıyken not başlıkları gösterilir, içerik gizlenir",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.widgetShowContent,
                            onCheckedChange = onWidgetShowContentChange
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Widget listesini kaydırarak tüm notlarına ulaşabilirsin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Otomatik Senkronizasyon Durum Kartı
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Otomatik Senkronizasyon Aktif",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Not eklendiğinde, silindiğinde veya içeriği değiştiğinde widget anlık olarak otomatik güncellenir.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // BÖLÜM 3: GÖRÜNÜM & TEMA
            // ==========================================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Görünüm ve Tema",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    // Tema Seçimi: Sistem, Açık, Koyu
                    Text("Uygulama Teması:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                            label = { Text("Sistem") },
                            leadingIcon = if (settings.themeMode == ThemeMode.SYSTEM) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                            label = { Text("Açık") },
                            leadingIcon = if (settings.themeMode == ThemeMode.LIGHT) {
                                { Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) },
                            label = { Text("Koyu") },
                            leadingIcon = if (settings.themeMode == ThemeMode.DARK) {
                                { Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Varsayılan Not Listeleme Düzeni
                    Text("Ana Sayfa Not Düzeni:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.layoutMode == NotesLayoutMode.STAGGERED_GRID,
                            onClick = { onLayoutModeChange(NotesLayoutMode.STAGGERED_GRID) },
                            label = { Text("Izgara") },
                            leadingIcon = if (settings.layoutMode == NotesLayoutMode.STAGGERED_GRID) {
                                { Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = settings.layoutMode == NotesLayoutMode.LIST,
                            onClick = { onLayoutModeChange(NotesLayoutMode.LIST) },
                            label = { Text("Geniş Liste") },
                            leadingIcon = if (settings.layoutMode == NotesLayoutMode.LIST) {
                                { Icon(Icons.Default.ViewAgenda, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = settings.layoutMode == NotesLayoutMode.COMPACT_LIST,
                            onClick = { onLayoutModeChange(NotesLayoutMode.COMPACT_LIST) },
                            label = { Text("Kompakt") },
                            leadingIcon = if (settings.layoutMode == NotesLayoutMode.COMPACT_LIST) {
                                { Icon(Icons.Default.ViewHeadline, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Negatif / Yüksek Kontrastlı Metinler
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Negatif & Yüksek Kontrast Metin", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "Koyu temada ve renkli notlarda yazı rengini otomatik olarak tersine çevirerek her zaman net okunabilir kılar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.highContrastNegative,
                            onCheckedChange = onHighContrastChange
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dinamik Renkler (Material You)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "Android 12+ sistem duvar kağıdı renklerine göre arayüzü şekillendirir",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }

                    // Varsayılan Not Rengi
                    Text("Yeni Notlar İçin Varsayılan Renk:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(NoteColors) { color ->
                            val argb = color.toArgb()
                            val isSelected = (settings.defaultNoteColor == argb) || (settings.defaultNoteColor == 0 && color == Color.Transparent)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (color == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant else color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { onDefaultColorChange(argb) }
                            )
                        }
                    }
                }
            }

            // ==========================================
            // BÖLÜM 4: GÜVENLİK & BİYOMETRİK KORUMA
            // ==========================================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Güvenlik & Biyometrik Koruma",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    // Master PIN Ayarı
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Yedek Güvenlik PIN'i",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (settings.masterPin != null) "PIN belirlendi (Parmak izi çalışmadığında kullanılır)"
                                else "Biyometrik donanım olmayan cihazlar veya yedek kilit için PIN belirleyin",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = { showSetPinDialog = true }) {
                            Text(if (settings.masterPin != null) "Değiştir" else "Ayarla")
                        }
                    }

                    if (settings.masterPin != null) {
                        TextButton(
                            onClick = { onMasterPinChange(null) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("PIN'i Kaldır")
                        }
                    }

                    HorizontalDivider()

                    // Arka Plana Geçince Kilitle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Uygulama Ayrılınca Otomatik Kilitle", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "Uygulama arka plana geçtiğinde kilitli notların oturumunu anında sonlandırır",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoLockOnExit,
                            onCheckedChange = onAutoLockChange
                        )
                    }

                    HorizontalDivider()

                    // FLAG_SECURE ve Donanım Koruması Bilgisi
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text(
                                "Güçlendirilmiş Güvenlik: Kilitli notlar açıkken ekran görüntüsü alınması ve son uygulamalar (Recents) ekranında içeriklerin sızması Android işletim sistemi düzeyinde engellenir.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Sürüm Bilgisi
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Not Defterim · v1.1.0",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tamamen Çevrimdışı, Güvenli ve Taşınabilir Yedekleme",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // ==========================================
    // İLETİŞİM KUTULARI VE BİLDİRİMLER
    // ==========================================

    // 0. Kasa Kilidi ile Dışa Aktarma Penceresi
    if (showExportPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showExportPasswordDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Kasa Kilidi ile Şifreleme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Yedek dosyanızı AES-256-GCM ve PBKDF2 hash koruması ile şifrelemek için bir Kasa Kilidi (şifre/PIN) girin:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = vaultPasswordForExport,
                        onValueChange = { vaultPasswordForExport = it },
                        placeholder = { Text("Kasa Şifresi / PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Not: Bu şifre yedeği başka cihaza taşırken veya geri yüklerken istenecektir.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportPasswordDialog = false
                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        if (isSharePending) {
                            onExportAndShare(context, vaultPasswordForExport.ifBlank { null })
                        } else {
                            val ext = if (vaultPasswordForExport.isNotBlank()) "notevault" else "zip"
                            createDocumentLauncher.launch("NoteApp_Yedek_$dateStr.$ext")
                        }
                    }
                ) {
                    Text(if (vaultPasswordForExport.isNotBlank()) "Şifreli Yedekle" else "Şifresiz Yedekle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportPasswordDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // 1. İçe Aktarma Onay Penceresi (Birleştir veya Sıfırla & Kasa Kilidi)
    if (showImportConfirmDialog && pendingImportUri != null) {
        val uri = pendingImportUri!!
        AlertDialog(
            onDismissRequest = {
                showImportConfirmDialog = false
                pendingImportUri = null
                importPasswordInput = ""
            },
            icon = { Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Yedekten Geri Yükle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Seçtiğiniz yedek dosyasındaki notlar ve medya dosyaları geri yüklenecektir.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Yedek dosyası Kasa Kilidi ile şifrelenmiş ise lütfen şifrenizi girin:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importPasswordInput,
                        onValueChange = { importPasswordInput = it },
                        placeholder = { Text("Kasa Kilidi Şifresi (Varsa)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        val pass = importPasswordInput.ifBlank { null }
                        pendingImportUri = null
                        importPasswordInput = ""
                        onImportFromUri(uri, false, pass)
                    }
                ) {
                    Text("Mevcut Notları Koru & Ekle")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showImportConfirmDialog = false
                            val pass = importPasswordInput.ifBlank { null }
                            pendingImportUri = null
                            importPasswordInput = ""
                            onImportFromUri(uri, true, pass)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Tümünü Sil & Yükle")
                    }
                    TextButton(
                        onClick = {
                            showImportConfirmDialog = false
                            pendingImportUri = null
                            importPasswordInput = ""
                        }
                    ) {
                        Text("İptal")
                    }
                }
            }
        )
    }

    // 2. İşlem Sürüyor Göstergesi
    if (backupUiState.isOperating) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = backupUiState.operationTitle ?: "Lütfen bekleyin...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // 3. Sonuç / Bilgilendirme İletişim Kutusu
    backupUiState.message?.let { msg ->
        AlertDialog(
            onDismissRequest = onDismissBackupMessage,
            icon = {
                Icon(
                    imageVector = if (backupUiState.isSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (backupUiState.isSuccess == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(if (backupUiState.isSuccess == true) "İşlem Başarılı" else "Bilgilendirme")
            },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = onDismissBackupMessage) {
                    Text("Tamam")
                }
            }
        )
    }

    // 4. PIN Belirleme / Değiştirme Penceresi
    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showSetPinDialog = false
                pinInput = ""
            },
            title = { Text("Güvenlik PIN'i Belirle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Kilitli notlara erişim için 4-6 haneli bir PIN kodu girin:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
                        placeholder = { Text("Örn: 1234") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pinInput.length >= 4) {
                            onMasterPinChange(pinInput)
                            showSetPinDialog = false
                            pinInput = ""
                        }
                    },
                    enabled = pinInput.length >= 4
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSetPinDialog = false
                    pinInput = ""
                }) {
                    Text("İptal")
                }
            }
        )
    }
}
