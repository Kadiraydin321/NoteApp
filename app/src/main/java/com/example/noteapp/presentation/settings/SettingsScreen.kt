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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import com.example.noteapp.data.settings.AppColorPalette
import com.example.noteapp.data.settings.AppSettings
import com.example.noteapp.data.settings.DrawerCollapseMode
import com.example.noteapp.data.settings.NotesLayoutMode
import com.example.noteapp.data.settings.ThemeMode
import com.example.noteapp.data.settings.WidgetFabBehavior
import com.example.noteapp.data.settings.WidgetFilterMode
import com.example.noteapp.data.updater.UpdateStatus
import com.example.noteapp.presentation.detail.NoteColors
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialFocusSection: String? = null,
    settings: AppSettings,
    backupUiState: BackupUiState,
    syncUiState: SyncUiState = SyncUiState(),
    updateUiState: UpdateUiState = UpdateUiState(),
    onWidgetFilterChange: (WidgetFilterMode) -> Unit,
    onWidgetShowLockedChange: (Boolean) -> Unit,
    onWidgetShowContentChange: (Boolean) -> Unit = {},
    onDefaultColorChange: (Int) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onColorPaletteChange: (AppColorPalette) -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onLayoutModeChange: (NotesLayoutMode) -> Unit = {},
    onDrawerCollapseModeChange: (DrawerCollapseMode) -> Unit = {},
    onMasterPinChange: (String?) -> Unit = {},
    onAutoLockChange: (Boolean) -> Unit = {},
    onHighContrastChange: (Boolean) -> Unit = {},
    onRefreshWidget: () -> Unit,
    onExportToUri: (Uri, String?) -> Unit,
    onExportAndShare: (Context, String?) -> Unit,
    onImportFromUri: (Uri, Boolean, String?) -> Unit,
    onDismissBackupMessage: () -> Unit,
    onCheckSyncConnection: (String) -> Unit = {},
    onAutoDiscoverServer: (String?) -> Unit = {},
    onSendNotesToDesktop: (String, String, Boolean, String?) -> Unit = { _, _, _, _ -> },
    onPullNotesFromDesktop: (String, String, Boolean, String?) -> Unit = { _, _, _, _ -> },
    onDismissSyncMessage: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onCheckAndInstallUpdate: (Boolean) -> Unit = {},
    onDownloadAndInstallUpdate: () -> Unit = {},
    onInstallReadyApk: () -> Unit = {},
    onDismissUpdateStatus: () -> Unit = {},
    onMarkDialogShown: (Int) -> Unit = {},
    onNavigateToDesignStudio: () -> Unit = {},
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
    var showUpdateDetailsDialog by remember { mutableStateOf(false) }

    // Ayarlara girişte sadece halihazırda indirme veya hazır paket yoksa denetle
    LaunchedEffect(Unit) {
        if (!updateUiState.isDownloading &&
            updateUiState.status !is UpdateStatus.Downloading &&
            updateUiState.status !is UpdateStatus.ReadyToInstall
        ) {
            onCheckForUpdates()
        }
    }

    // Güncelleme bulunduğunda popup penceresini bu sürüm için bir defa otomatik aç (indirme yapılıyorsa asla açma)
    LaunchedEffect(updateUiState.status) {
        val status = updateUiState.status
        if (status is UpdateStatus.UpdateAvailable &&
            !updateUiState.isDownloading &&
            updateUiState.hasShownDialogForVersion != status.remoteVersionCode
        ) {
            showUpdateDetailsDialog = true
            onMarkDialogShown(status.remoteVersionCode)
        }
    }

    var syncHostInput by remember(settings.lastSyncHost) {
        mutableStateOf(settings.lastSyncHost.ifBlank { "192.168.1." })
    }
    var syncPinInput by remember(settings.lastSyncPin) {
        mutableStateOf(settings.lastSyncPin)
    }
    var syncClearExisting by remember { mutableStateOf(false) }

    LaunchedEffect(syncUiState.discoveredHost) {
        syncUiState.discoveredHost?.let {
            syncHostInput = it
        }
    }

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

    val scrollState = rememberScrollState()
    val widgetSectionOffsetY = remember { mutableIntStateOf(0) }

    LaunchedEffect(initialFocusSection, widgetSectionOffsetY.intValue) {
        if (initialFocusSection == "widget" && widgetSectionOffsetY.intValue > 0) {
            delay(100)
            scrollState.animateScrollTo(widgetSectionOffsetY.intValue)
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
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ==========================================
            // BÖLÜM 1: GÜNCELLEME (OTOMATİK DENETİM & DETAYLAR)
            // ==========================================
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (updateUiState.status) {
                        is UpdateStatus.UpdateAvailable -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        is UpdateStatus.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (val status = updateUiState.status) {
                        is UpdateStatus.Checking -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Güncellemeler denetleniyor...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is UpdateStatus.Downloading -> {
                            val availableInfo = updateUiState.availableUpdateInfo
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    if (availableInfo != null) "v${availableInfo.remoteVersionName} İndiriliyor..." else "Güncelleme İndiriliyor...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { if (status.progressPercent >= 0) status.progressPercent / 100f else 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        if (status.progressPercent >= 0) "%${status.progressPercent}" else "İndiriliyor...",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (status.totalBytes > 0) {
                                        val downloadedMb = String.format(Locale.US, "%.1f", status.downloadedBytes / (1024f * 1024f))
                                        val totalMb = String.format(Locale.US, "%.1f", status.totalBytes / (1024f * 1024f))
                                        Text(
                                            "$downloadedMb MB / $totalMb MB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    "İndirme işlemi arka planda devam eder; ayarlardan çıksanız bile kesilmez.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }

                        is UpdateStatus.ReadyToInstall -> {
                            val availableInfo = updateUiState.availableUpdateInfo
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    "Güncelleme Paketi Hazır! 🎉",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                            }

                            Text(
                                if (availableInfo != null) "v${availableInfo.remoteVersionName} paketi başarıyla indirildi. Kurulumu başlatmak için dokunun."
                                else "Güncelleme paketi başarıyla indirildi. Kurulumu başlatmak için dokunun.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = { onInstallReadyApk() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("🚀 Şimdi Yükle", fontWeight = FontWeight.Bold)
                            }
                        }

                        is UpdateStatus.UpdateAvailable -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    "Güncelleme Var! 🚀",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                "Mevcut: v${status.currentVersionName}  →  Yeni: v${status.remoteVersionName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Butonlar: Detay ve Güncelle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showUpdateDetailsDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Detay", fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        onDownloadAndInstallUpdate()
                                    },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Güncelle", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        is UpdateStatus.Error -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        status.message.ifBlank { "İnternet bağlantısı kurulamadığı için güncelleme kontrol edilemedi." },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                IconButton(
                                    onClick = { onCheckForUpdates() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Tekrar Dene",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        is UpdateStatus.UpToDate -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column {
                                        Text(
                                            "Uygulamanız güncel",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Sürüm: v${status.currentVersionName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onCheckForUpdates() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Yeniden Denetle",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        else -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Update,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        "Güncellemeler kontrol ediliyor...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onCheckForUpdates() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Denetle",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // BÖLÜM 2: YEDEKLEME & DIŞA / İÇE AKTARMA
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
            // BÖLÜM 1.5: MASAÜSTÜ Wİ-Fİ SENKRONİZASYONU (📱 ⇄ 💻)
            // ==========================================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
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
                            Icons.Default.Devices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Masaüstü Wi-Fi Senkronizasyonu",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Bilgisayar ile yerel ağ üzerinden kablosuz eşitleme",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        "Bilgisayarınızda 'Not Defterim Masaüstü' uygulamasını açıp 'Wi-Fi Senkronizasyonu' sekmesindeki IP adresi ve 4 haneli PIN kodunu girin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    // IP / Sunucu Adresi ve PIN Girişi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = syncHostInput,
                            onValueChange = { syncHostInput = it },
                            label = { Text("Bilgisayar IP Adresi") },
                            placeholder = { Text("192.168.1.X veya 10.0.2.2") },
                            singleLine = true,
                            modifier = Modifier.weight(1.8f)
                        )

                        OutlinedTextField(
                            value = syncPinInput,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) syncPinInput = it },
                            label = { Text("PIN") },
                            placeholder = { Text("4 Hane") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                            )
                        )
                    }

                    // Otomatik Bul & Bağlantıyı Test Et Butonları
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onAutoDiscoverServer(syncHostInput)
                            },
                            enabled = !syncUiState.isDiscovering && !syncUiState.isOperating,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (syncUiState.isDiscovering) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Taranıyor...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ağda Bul 🔍")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                onCheckSyncConnection(syncHostInput)
                            },
                            enabled = syncHostInput.isNotBlank() && !syncUiState.statusChecking && !syncUiState.isOperating,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (syncUiState.statusChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Bağlanıyor...")
                            } else {
                                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Test Et")
                            }
                        }
                    }

                    if (syncUiState.isConnected == true) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "✓ Bağlandı: ${syncUiState.serverStatus?.appName ?: "Masaüstü"} (${syncUiState.serverStatus?.notesCount ?: 0} not)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Temizle & Eşitle Seçeneği
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Mevcut Notların Üzerine Yaz",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (syncClearExisting) "Karşı taraftaki eski notlar silinir, sadece yeni liste kalır"
                                else "Mevcut notlar korunur ve eşleşen/yeni notlar eklenir (Tavsiye edilen)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = syncClearExisting,
                            onCheckedChange = { syncClearExisting = it }
                        )
                    }

                    HorizontalDivider()

                    // Gönder ve Çek Butonları
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onSendNotesToDesktop(
                                    syncHostInput,
                                    syncPinInput,
                                    syncClearExisting,
                                    settings.masterPin
                                )
                            },
                            enabled = syncHostInput.isNotBlank() && syncPinInput.length == 4 && !syncUiState.isOperating,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gönder 📤")
                        }

                        FilledTonalButton(
                            onClick = {
                                onPullNotesFromDesktop(
                                    syncHostInput,
                                    syncPinInput,
                                    syncClearExisting,
                                    settings.masterPin
                                )
                            },
                            enabled = syncHostInput.isNotBlank() && syncPinInput.length == 4 && !syncUiState.isOperating,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Çek 📥")
                        }
                    }
                }
            }

            // ==========================================
            // BÖLÜM 2: ANA EKRAN WİDGET'I
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        widgetSectionOffsetY.intValue = coordinates.positionInParent().y.toInt()
                    },
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

                    // Widget Tasarım Stüdyosu Hızlı Geçiş Kartı
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToDesignStudio)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(settings.widgetDesignPreset.iconEmoji, fontSize = 24.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Widget Tasarım Stüdyosu",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Tema: ${settings.widgetDesignPreset.title} · '+' Butonu: ${if (settings.widgetFabBehavior == WidgetFabBehavior.DIRECT_NEW_NOTE) "Anında Açılış" else "Keep Menüsü"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

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

                    // ==========================================
                    // TASARIM STÜDYOSU & HAZIR ŞABLONLAR KARTI
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToDesignStudio)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Tasarım Stüdyosu",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            settings.designPreset.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    "Hazır şablonlar, kart stilleri, buton konumları ve canlı simülasyon",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

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

                    // Sol Menü Kategori ve Etiket Düzeni
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Sol Menü Kategori & Etiket Görünümü:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Sol menüdeki kategori ve etiketlerin listelenme ve katlanma biçimini seçin:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        DrawerCollapseMode.entries.forEach { mode ->
                            val isSelected = settings.drawerCollapseMode == mode
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDrawerCollapseModeChange(mode) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onDrawerCollapseModeChange(mode) }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = mode.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
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
                                "Android 12+ sistem duvar kağıdı renklerine göre arayüzü şekillendirir (Kapalıyken aşağıdaki özel paletler kullanılır)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }

                    // Özel Renk Paletleri Seçimi
                    Text("Özel Renk & Atmosfer Paleti:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppColorPalette.entries.forEach { palette ->
                            val isSelected = (!settings.dynamicColor && settings.colorPalette == palette) ||
                                    (settings.dynamicColor && settings.colorPalette == palette)
                            val previewColors = when (palette) {
                                AppColorPalette.WARM_AMBER -> listOf(Color(0xFFFFB74D), Color(0xFFFFAB91), Color(0xFF2E241E))
                                AppColorPalette.MIDNIGHT_ROSE -> listOf(Color(0xFFFFB0CD), Color(0xFFE4BDC8), Color(0xFF2E2026))
                                AppColorPalette.FOREST_EMERALD -> listOf(Color(0xFF81D89D), Color(0xFFA2CED9), Color(0xFF202A22))
                                AppColorPalette.DEEP_OCEAN -> listOf(Color(0xFF97CCF8), Color(0xFFB9C8DA), Color(0xFF202731))
                                AppColorPalette.SLATE_MINIMAL -> listOf(Color(0xFFD0D3D9), Color(0xFFC4C7CC), Color(0xFF26292E))
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (settings.dynamicColor) {
                                            onDynamicColorChange(false)
                                        }
                                        onColorPaletteChange(palette)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 3 renkli önizleme noktacığı
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        previewColors.forEach { dotColor ->
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(dotColor)
                                                    .border(0.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            palette.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                        )
                                        Text(
                                            palette.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
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
                        "Not Defterim · v1.0.1 (En Güncel)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tamamen Çevrimdışı, Güvenli, Hızlı ve Optimize",
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

    // 5. Senkronizasyon İşlemi Sürüyor Göstergesi
    if (syncUiState.isOperating) {
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
                        text = syncUiState.operationTitle ?: "Masaüstü ile senkronize ediliyor...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // 6. Senkronizasyon Bilgilendirme / Sonuç İletişim Kutusu
    syncUiState.message?.let { msg ->
        AlertDialog(
            onDismissRequest = onDismissSyncMessage,
            icon = {
                Icon(
                    imageVector = if (syncUiState.isSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (syncUiState.isSuccess == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(if (syncUiState.isSuccess == true) "Senkronizasyon Başarılı" else "Senkronizasyon Bilgisi")
            },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = onDismissSyncMessage) {
                    Text("Tamam")
                }
            }
        )
    }

    // 7. Güncelleme Popup Penceresi (Güncelleme bulunduğunda veya Detay butonuna basıldığında açılır)
    val activeUpdateInfo = (updateUiState.status as? UpdateStatus.UpdateAvailable) ?: updateUiState.availableUpdateInfo
    if (showUpdateDetailsDialog && activeUpdateInfo != null) {
        val status = activeUpdateInfo
        AlertDialog(
            onDismissRequest = { showUpdateDetailsDialog = false },
            icon = {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "Güncelleme Var! 🚀",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Sürüm Karşılaştırma Kutusu
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Mevcut Sürüm:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "v${status.currentVersionName} (Kod: ${status.currentVersionCode})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Yeni Sürüm:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "v${status.remoteVersionName} (Kod: ${status.remoteVersionCode})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (status.releaseDate.isNotBlank()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Yayın Tarihi:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        status.releaseDate,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    // Yapılan Değişiklikler
                    Text(
                        "Son Güncellemede Yapılan Değişiklikler:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        status.message.ifBlank { "Hata düzeltmeleri ve kararlılık iyileştirmeleri." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateDetailsDialog = false
                        onDownloadAndInstallUpdate()
                    }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("🚀 Güncelle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDetailsDialog = false }) {
                    Text("Daha Sonra")
                }
            }
        )
    }

    // 8. İndirme Tamamlandı Bildirimi
    if (updateUiState.status is UpdateStatus.ReadyToInstall) {
        AlertDialog(
            onDismissRequest = onDismissUpdateStatus,
            icon = {
                Icon(
                    Icons.Default.InstallMobile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Güncelleme İndirildi") },
            text = {
                Text("APK başarıyla indirildi. Yükleme işlemini başlatmak için Yükle butonuna dokunun.")
            },
            confirmButton = {
                Button(onClick = {
                    onDismissUpdateStatus()
                    onInstallReadyApk()
                }) {
                    Text("Şimdi Yükle")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUpdateStatus) {
                    Text("Kapat")
                }
            }
        )
    }
}
