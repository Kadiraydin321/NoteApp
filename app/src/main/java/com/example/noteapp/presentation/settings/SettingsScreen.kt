package com.example.noteapp.presentation.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.noteapp.biometric.BiometricPromptManager
import com.example.noteapp.biometric.BiometricResult
import com.example.noteapp.biometric.findFragmentActivity
import com.example.noteapp.data.settings.*
import com.example.noteapp.data.updater.UpdateStatus
import com.example.noteapp.presentation.detail.NoteColors
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
    maintenanceUiState: MaintenanceUiState = MaintenanceUiState(),
    onWidgetFilterChange: (WidgetFilterMode) -> Unit,
    onWidgetShowLockedChange: (Boolean) -> Unit,
    onWidgetShowContentChange: (Boolean) -> Unit = {},
    onDefaultColorChange: (Int) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onColorPaletteChange: (AppColorPalette) -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onAmoledDarkChange: (Boolean) -> Unit = {},
    onDailyWordGoalChange: (Int) -> Unit = {},
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
    onNavigateToStatistics: () -> Unit = {},
    onOptimizeDatabase: () -> Unit = {},
    onDismissMaintenanceMessage: () -> Unit = {},
    onTrashRetentionPolicyChange: (TrashRetentionPolicy) -> Unit = {},
    onLockTimeoutPolicyChange: (LockTimeoutPolicy) -> Unit = {},
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var isSharePending by remember { mutableStateOf(false) }
    var vaultPasswordForExport by remember { mutableStateOf("") }
    var exportPasswordVisible by remember { mutableStateOf(false) }
    var importPasswordInput by remember { mutableStateOf("") }
    var importPasswordVisible by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var setPinVisible by remember { mutableStateOf(false) }
    var showUpdateDetailsDialog by remember { mutableStateOf(false) }

    // Bildirim İzni Kontrolü
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    var notificationPermissionGranted by remember { mutableStateOf(hasNotificationPermission) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationPermissionGranted = isGranted
    }

    // Biyometrik Donanım Durumu
    val activity = context.findFragmentActivity()
    val promptManager = remember(activity) { activity?.let { BiometricPromptManager(it) } }
    val isBiometricAvailable = promptManager?.canAuthenticate() == true

    // Ayarlara girişte güncelleme denetimi
    LaunchedEffect(Unit) {
        if (!updateUiState.isDownloading &&
            updateUiState.status !is UpdateStatus.Downloading &&
            updateUiState.status !is UpdateStatus.ReadyToInstall
        ) {
            onCheckForUpdates()
        }
    }

    // Otomatik güncelleme popup'ı
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

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            onExportToUri(uri, vaultPasswordForExport.ifBlank { null })
        }
    }

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

    val isDarkActive = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Ayarlar",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // =========================================================================
            // GRUP 1: GÖRÜNÜM & ARAYÜZ (APPEARANCE)
            // =========================================================================
            SettingsGroupCard(
                icon = Icons.Default.Palette,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Görünüm",
                subtitle = "Tema, renk paleti, tipografi ve arayüz düzeni",
                badgeText = settings.colorPalette.title
            ) {
                // 1. Tasarım Stüdyosu Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
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
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
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
                                "Kart kenarları, gölge stilleri, buton konumu ve canlı simülasyon",
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 2. Uygulama Teması (Sistem / Açık / Koyu)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Uygulama Teması",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
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
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                            label = { Text("Açık") },
                            leadingIcon = if (settings.themeMode == ThemeMode.LIGHT) {
                                { Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) },
                            label = { Text("Koyu") },
                            leadingIcon = if (settings.themeMode == ThemeMode.DARK) {
                                { Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        )
                    }
                }

                // 3. AMOLED Saf Siyah vs Koyu Gri Seçeneği
                AnimatedVisibility(
                    visible = isDarkActive,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(if (settings.amoledDark) Color.Black else Color(0xFF1E2228), CircleShape)
                                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (settings.amoledDark) "🖤" else "🌑",
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        if (settings.amoledDark) "Saf Siyah AMOLED Modu (#000000)" else "Koyu Grafit Gri Modu",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (settings.amoledDark) "Pikseller tamamen söner, maksimum pil tasarrufu & kontrast"
                                        else "Yumuşak gece tonları, gözü yormayan derin gri zemin",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = settings.amoledDark,
                                onCheckedChange = onAmoledDarkChange
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 4. Dinamik Renkler (Material You)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Dinamik Renkler (Material You)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
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

                // 5. Renk & Atmosfer Paletleri (Canlı Renk Önizlemeleri)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Özel Renk & Atmosfer Paletleri",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (settings.dynamicColor) {
                            Text(
                                "(Dinamik Renk Açıkken Devre Dışı)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppColorPalette.entries.forEach { palette ->
                            val isSelected = (!settings.dynamicColor && settings.colorPalette == palette) ||
                                    (settings.dynamicColor && settings.colorPalette == palette)

                            val previewColors = when (palette) {
                                AppColorPalette.WARM_AMBER -> listOf(Color(0xFFFFB74D), Color(0xFFFFAB91), Color(0xFFE6BE8A), Color(0xFF2E241E))
                                AppColorPalette.MIDNIGHT_ROSE -> listOf(Color(0xFFFFB0CD), Color(0xFFE4BDC8), Color(0xFFF2B7A8), Color(0xFF2E2026))
                                AppColorPalette.FOREST_EMERALD -> listOf(Color(0xFF81D89D), Color(0xFFB7CCB7), Color(0xFFA2CED9), Color(0xFF202A22))
                                AppColorPalette.DEEP_OCEAN -> listOf(Color(0xFF97CCF8), Color(0xFFB9C8DA), Color(0xFFD4BEE6), Color(0xFF202731))
                                AppColorPalette.SLATE_MINIMAL -> listOf(Color(0xFFD0D3D9), Color(0xFFC4C7CC), Color(0xFFD8D2C9), Color(0xFF26292E))
                                AppColorPalette.GLACIAL_ICE -> listOf(Color(0xFF38BDF8), Color(0xFF7DD3FC), Color(0xFF99F6E4), Color(0xFF0B131B))
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
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
                                    // 4 renkli önizleme noktacıkları
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy((-4).dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        previewColors.forEach { dotColor ->
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(dotColor)
                                                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(14.dp))
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
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 6. Ana Sayfa Not Düzeni
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ana Sayfa Not Düzeni", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        )
                        FilterChip(
                            selected = settings.layoutMode == NotesLayoutMode.LIST,
                            onClick = { onLayoutModeChange(NotesLayoutMode.LIST) },
                            label = { Text("Geniş Liste") },
                            leadingIcon = if (settings.layoutMode == NotesLayoutMode.LIST) {
                                { Icon(Icons.Default.ViewAgenda, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        )
                        FilterChip(
                            selected = settings.layoutMode == NotesLayoutMode.COMPACT_LIST,
                            onClick = { onLayoutModeChange(NotesLayoutMode.COMPACT_LIST) },
                            label = { Text("Kompakt") },
                            leadingIcon = if (settings.layoutMode == NotesLayoutMode.COMPACT_LIST) {
                                { Icon(Icons.Default.ViewHeadline, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        )
                    }
                }

                // 7. Sol Menü Kategori ve Etiket Düzeni
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Sol Menü Kategori & Etiket Görünümü",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    DrawerCollapseMode.entries.forEach { mode ->
                        val isSelected = settings.drawerCollapseMode == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 8. Negatif & Yüksek Kontrast Metin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Negatif & Yüksek Kontrast Metin",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Koyu temada ve renkli notlarda yazı rengini otomatik tersine çevirerek okunabilirliği garantiler",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.highContrastNegative,
                        onCheckedChange = onHighContrastChange
                    )
                }

                // 9. Yeni Notlar İçin Varsayılan Renk
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Yeni Notlar İçin Varsayılan Renk",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(NoteColors) { color ->
                            val argb = color.toArgb()
                            val isSelected = (settings.defaultNoteColor == argb) || (settings.defaultNoteColor == 0 && color == Color.Transparent)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
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

                // 10. Ana Ekran Widget Ayarları Sub-Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            widgetSectionOffsetY.intValue = coordinates.positionInParent().y.toInt()
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Widgets,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Ana Ekran Widget Yapılandırması",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Widget Stüdyosu Hızlı Butonu
                        FilledTonalButton(
                            onClick = onNavigateToDesignStudio,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                        ) {
                            Text(settings.widgetDesignPreset.iconEmoji)
                            Spacer(Modifier.width(8.dp))
                            Text("Widget Tasarım Stüdyosu (${settings.widgetDesignPreset.title})")
                        }

                        // Filtre
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = settings.widgetFilterMode == WidgetFilterMode.ALL,
                                onClick = { onWidgetFilterChange(WidgetFilterMode.ALL) },
                                label = { Text("Tüm Notlar") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = settings.widgetFilterMode == WidgetFilterMode.FAVORITES,
                                onClick = { onWidgetFilterChange(WidgetFilterMode.FAVORITES) },
                                label = { Text("★ Favoriler") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Kilitli Notları Göster", style = MaterialTheme.typography.bodySmall)
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
                            Text("Not İçerik Önizlemesi", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = settings.widgetShowContent,
                                onCheckedChange = onWidgetShowContentChange
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // GRUP 2: GÜVENLİK & GİZLİLİK (SECURITY & PRIVACY)
            // =========================================================================
            SettingsGroupCard(
                icon = Icons.Default.Security,
                iconTint = MaterialTheme.colorScheme.tertiary,
                title = "Güvenlik & Gizlilik",
                subtitle = "Biyometrik kilit, Master PIN ve oturum koruması",
                badgeText = if (settings.masterPin != null) "Kilit Aktif" else "Kilit Yok"
            ) {
                // 1. Biyometrik Donanım Durumu
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isBiometricAvailable) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = if (isBiometricAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                if (isBiometricAvailable) "Biyometrik Kimlik Doğrulama Aktif" else "Biyometrik Donanım Kullanılamıyor",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isBiometricAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (isBiometricAvailable) "Cihazınızın parmak izi / yüz tanıma sensörü ile notlarınızı açabilirsiniz"
                                else "Cihaz biyometrik desteklemediğinde Master PIN kodu kullanılır",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 2. Master PIN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Yedek Güvenlik PIN'i (Master PIN)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (settings.masterPin != null) "PIN belirlendi (Parmak izi çalışmadığında veya yedekte kullanılır)"
                            else "Biyometrik çalışmadığında kullanılacak 4-6 haneli PIN belirleyin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showSetPinDialog = true },
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text(if (settings.masterPin != null) "Değiştir" else "Ayarla")
                    }
                }

                if (settings.masterPin != null) {
                    TextButton(
                        onClick = { onMasterPinChange(null) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Güvenlik PIN'ini Kaldır")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 3. Arka Plana Geçince Otomatik Kilitle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Uygulamadan Ayrılınca Otomatik Kilitle",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
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

                // 4. Yeniden Kilitleme Süresi (Grace Period)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Otomatik Yeniden Kilitleme Süresi",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LockTimeoutPolicy.values().forEach { policy ->
                            val isSelected = settings.lockTimeoutPolicy == policy
                            FilterChip(
                                selected = isSelected,
                                onClick = { onLockTimeoutPolicyChange(policy) },
                                label = {
                                    Text(
                                        when (policy) {
                                            LockTimeoutPolicy.IMMEDIATE -> "Anında"
                                            LockTimeoutPolicy.ONE_MINUTE -> "1 Dk"
                                            LockTimeoutPolicy.FIVE_MINUTES -> "5 Dk"
                                            LockTimeoutPolicy.FIFTEEN_MINUTES -> "15 Dk"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                            )
                        }
                    }
                    Text(
                        text = settings.lockTimeoutPolicy.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 5. Çöp Kutusu Saklama Politikası
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Çöp Kutusu Otomatik Temizleme",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TrashRetentionPolicy.values().forEach { policy ->
                            val isSelected = settings.trashRetentionPolicy == policy
                            FilterChip(
                                selected = isSelected,
                                onClick = { onTrashRetentionPolicyChange(policy) },
                                label = {
                                    Text(
                                        when (policy) {
                                            TrashRetentionPolicy.DAYS_30 -> "30 Gün"
                                            TrashRetentionPolicy.DAYS_14 -> "14 Gün"
                                            TrashRetentionPolicy.DAYS_7 -> "7 Gün"
                                            TrashRetentionPolicy.NEVER -> "Asla"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                            )
                        }
                    }
                    Text(
                        text = settings.trashRetentionPolicy.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = {
                            com.example.noteapp.worker.TrashCleanupScheduler.runImmediateCleanup(context)
                            android.widget.Toast.makeText(context, "🗑️ Çöp kutusu temizleme görevi arka planda başlatıldı", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = settings.trashRetentionPolicy != TrashRetentionPolicy.NEVER
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Süresi Dolan Çöpleri Şimdi Temizle", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // 6. FLAG_SECURE ve Donanım Koruması Bilgisi
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            "Donanım Düzeyinde Gizlilik: Kilitli notlar açıkken ekran görüntüsü alınması ve son uygulamalar (Recents) ekranında içeriklerin sızması Android işletim sistemi düzeyinde engellenir.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // =========================================================================
            // GRUP 3: YEDEKLEME & DIŞA AKTARMA (BACKUP, SYNC & STORAGE)
            // =========================================================================
            SettingsGroupCard(
                icon = Icons.Default.CloudSync,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Yedekleme & Dışa Aktarma",
                subtitle = "Yerel yedekler, masaüstü senkronizasyonu ve veritabanı",
                badgeText = settings.lastBackupTimestamp?.let { "Yedek Var" } ?: "Yedek Yok"
            ) {
                // Son Yedekleme Bilgisi
                val lastBackupText = settings.lastBackupTimestamp?.let {
                    "Son yedekleme: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(it))
                } ?: "Henüz yedek alınmadı"
                Text(
                    text = lastBackupText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    "Notlarınızı, çizimlerinizi, ses kayıtlarınızı ve tüm görsellerinizi evrensel bir .ZIP arşiv paketi olarak dışa aktarabilir ya da cihazınıza geri yükleyebilirsiniz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Buton 1: Cihaza / Dosyalara Kaydet
                Button(
                    onClick = {
                        isSharePending = false
                        if (isBiometricAvailable) {
                            promptManager?.showBiometricPrompt(
                                title = "Yedeklemeyi Onayla",
                                description = "Notlarınızı dışa aktarmak için parmak izinizi kullanın",
                                onResult = { result ->
                                    if (result is BiometricResult.AuthenticationSuccess) {
                                        vaultPasswordForExport = settings.masterPin ?: ""
                                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                        val ext = if (vaultPasswordForExport.isNotBlank()) "notevault" else "zip"
                                        createDocumentLauncher.launch("NoteApp_Yedek_$dateStr.$ext")
                                    } else if (result !is BiometricResult.AuthenticationFailed) {
                                        vaultPasswordForExport = settings.masterPin ?: ""
                                        showExportPasswordDialog = true
                                    }
                                }
                            )
                        } else {
                            vaultPasswordForExport = settings.masterPin ?: ""
                            showExportPasswordDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Kasa Kilidi ile Yedekle (.notevault / .zip)")
                }

                // Buton 2: Paylaş Menüsü ile Dışa Aktar
                OutlinedButton(
                    onClick = {
                        isSharePending = true
                        if (isBiometricAvailable) {
                            promptManager?.showBiometricPrompt(
                                title = "Yedeklemeyi Onayla",
                                description = "Notlarınızı paylaşmak için parmak izinizi kullanın",
                                onResult = { result ->
                                    if (result is BiometricResult.AuthenticationSuccess) {
                                        vaultPasswordForExport = settings.masterPin ?: ""
                                        onExportAndShare(context, vaultPasswordForExport.ifBlank { null })
                                    } else if (result !is BiometricResult.AuthenticationFailed) {
                                        vaultPasswordForExport = settings.masterPin ?: ""
                                        showExportPasswordDialog = true
                                    }
                                }
                            )
                        } else {
                            vaultPasswordForExport = settings.masterPin ?: ""
                            showExportPasswordDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Kasa Kilidi ile Yedeği Paylaş / Gönder")
                }

                // Buton 3: Yedeği İçe Aktar
                FilledTonalButton(
                    onClick = {
                        openDocumentLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Yedekten Geri Yükle (İçe Aktar)")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // Masaüstü Wi-Fi Senkronizasyonu Sub-Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Masaüstü Wi-Fi Senkronizasyonu",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            "Bilgisayarınızdaki 'Not Defterim Masaüstü' uygulaması ile yerel ağ üzerinden kablosuz eşitleme yapın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = syncHostInput,
                                onValueChange = { syncHostInput = it },
                                label = { Text("Bilgisayar IP") },
                                placeholder = { Text("192.168.1.X") },
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onAutoDiscoverServer(syncHostInput) },
                                enabled = !syncUiState.isDiscovering && !syncUiState.isOperating,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
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
                                onClick = { onCheckSyncConnection(syncHostInput) },
                                enabled = syncHostInput.isNotBlank() && !syncUiState.statusChecking && !syncUiState.isOperating,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
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
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mevcut Notların Üzerine Yaz", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = syncClearExisting,
                                onCheckedChange = { syncClearExisting = it }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSendNotesToDesktop(syncHostInput, syncPinInput, syncClearExisting, settings.masterPin) },
                                enabled = syncHostInput.isNotBlank() && syncPinInput.length == 4 && !syncUiState.isOperating,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Gönder 📤")
                            }

                            FilledTonalButton(
                                onClick = { onPullNotesFromDesktop(syncHostInput, syncPinInput, syncClearExisting, settings.masterPin) },
                                enabled = syncHostInput.isNotBlank() && syncPinInput.length == 4 && !syncUiState.isOperating,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Çek 📥")
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // Veritabanı & Depolama Bakımı Sub-Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoFixNormal, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Veritabanı & Depolama Bakımı", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            "SQLite WAL Checkpoint, VACUUM ve ANALYZE motoru ile veritabanı defragmente edilir, disk alanı geri kazanılır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Veritabanı: ${if (maintenanceUiState.dbSizeKb > 0) "${maintenanceUiState.dbSizeKb} KB" else "Hesaplanıyor..."}", style = MaterialTheme.typography.bodySmall)
                            Text("Önbellek: ${maintenanceUiState.cacheSizeKb} KB", style = MaterialTheme.typography.bodySmall)
                        }

                        FilledTonalButton(
                            onClick = onOptimizeDatabase,
                            enabled = !maintenanceUiState.isOperating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                        ) {
                            if (maintenanceUiState.isOperating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Optimize Ediliyor...")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Veritabanını ve Önbelleği Optimize Et")
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // GRUP 4: BİLDİRİMLER & HEDEFLER (NOTIFICATIONS & HABITS)
            // =========================================================================
            SettingsGroupCard(
                icon = Icons.Default.NotificationsActive,
                iconTint = MaterialTheme.colorScheme.secondary,
                title = "Bildirimler & Alışkanlıklar",
                subtitle = "Not alarmları, sabit bildirimler ve yazma hedefi",
                badgeText = if (notificationPermissionGranted) "İzin Aktif" else "İzin Gerekli"
            ) {
                // 1. Sistem Bildirim İzni
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (notificationPermissionGranted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (notificationPermissionGranted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = if (notificationPermissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    if (notificationPermissionGranted) "Bildirim İzni Verildi" else "Bildirim İzni Gerekli",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (notificationPermissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    if (notificationPermissionGranted) "Alarmlar ve hatırlatıcılar zamanında bildirim gönderebilir"
                                    else "Hatırlatıcıların çalışması için bildirim izni vermelisiniz",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (!notificationPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            FilledTonalButton(
                                onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) {
                                Text("İzin Ver")
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 2. Sabit Not Bildirimleri Bilgisi
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                "Sabit Not Bildirimleri",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Not detay ekranındaki iğne butonu ile istediğiniz notu bildirim paneline sabitleyebilir, hızlıca göz atabilirsiniz.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 3. Günlük Kelime Hedefi (Word Goal)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Günlük Yazma Hedefi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${settings.dailyWordGoal} Kelime",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "Düzenli yazma alışkanlığı kazanmak için her gün ulaşmak istediğiniz hedefi belirleyin:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(100, 300, 500, 1000).forEach { goal ->
                            val isSelected = settings.dailyWordGoal == goal
                            FilterChip(
                                selected = isSelected,
                                onClick = { onDailyWordGoalChange(goal) },
                                label = {
                                    Text(
                                        if (goal == 300) "300 (Önerilen)" else "$goal Kelime",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // GRUP 5: HAKKINDA & GÜNCELLEMELER (ABOUT & UPDATES)
            // =========================================================================
            SettingsGroupCard(
                icon = Icons.Default.Info,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Hakkında & Güncellemeler",
                subtitle = "Sürüm detayları, güncelleme motoru ve yazma analitiği",
                badgeText = "v1.0.1"
            ) {
                // 1. Güncelleme Durumu Kutusu
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = when (updateUiState.status) {
                        is UpdateStatus.UpdateAvailable -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        is UpdateStatus.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (val status = updateUiState.status) {
                            is UpdateStatus.Checking -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                    Text("Güncellemeler denetleniyor...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            is UpdateStatus.Downloading -> {
                                val availableInfo = updateUiState.availableUpdateInfo
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                    Text(
                                        if (availableInfo != null) "v${availableInfo.remoteVersionName} İndiriliyor..." else "Güncelleme İndiriliyor...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
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
                                        Text("$downloadedMb MB / $totalMb MB", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }

                            is UpdateStatus.ReadyToInstall -> {
                                val availableInfo = updateUiState.availableUpdateInfo
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                                    Text(
                                        "Güncelleme Paketi Hazır! 🎉",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                                Text(
                                    if (availableInfo != null) "v${availableInfo.remoteVersionName} paketi başarıyla indirildi."
                                    else "Güncelleme paketi başarıyla indirildi.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Button(
                                    onClick = onInstallReadyApk,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White)
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
                                    Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                    Text(
                                        "Güncelleme Var! 🚀",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    "Mevcut: v${status.currentVersionName}  →  Yeni: v${status.remoteVersionName}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showUpdateDetailsDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                    ) {
                                        Text("Detay")
                                    }
                                    Button(
                                        onClick = onDownloadAndInstallUpdate,
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .heightIn(min = 44.dp)
                                    ) {
                                        Text("Güncelle")
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
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Text(status.message.ifBlank { "Güncelleme kontrol edilemedi." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                    IconButton(onClick = onCheckForUpdates) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Yeniden Dene", tint = MaterialTheme.colorScheme.error)
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
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column {
                                            Text("Uygulamanız güncel", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text("Sürüm: v${status.currentVersionName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(onClick = onCheckForUpdates) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Denetle")
                                    }
                                }
                            }

                            else -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Güncellemeler kontrol ediliyor...", style = MaterialTheme.typography.bodySmall)
                                    IconButton(onClick = onCheckForUpdates) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Denetle")
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. İstatistikler & Analitik Kartı
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToStatistics)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📊", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "İstatistikler & Yazma Analitiği",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                "Yazma serisi, kelime sayısı, etiketler ve medya envanteri",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 3. Uygulama Bilgi Rozeti
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Not Defterim · v1.0.1 (En Güncel)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Tamamen Çevrimdışı, Açık Kaynak Ruhu, Sıfır İzleme, Güvenli & Optimize",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text("AES-256-GCM", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text("SQLite Room", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text("Material 3", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // İLETİŞİM KUTULARI VE MODERN DİALOGLAR (M3 TASARIM & 48DP BUTONLAR)
    // =========================================================================

    // 1. Kasa Kilidi ile Dışa Aktarma Penceresi
    if (showExportPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showExportPasswordDialog = false
                exportPasswordVisible = false
            },
            icon = {
                Icon(
                    Icons.Default.EnhancedEncryption,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Kasa Kilidi ile Şifreleme",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Yedek dosyanızı AES-256-GCM ve PBKDF2 hash koruması ile şifrelemek için bir Kasa Kilidi (şifre/PIN) girin:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isBiometricAvailable) {
                        FilledTonalButton(
                            onClick = {
                                promptManager?.showBiometricPrompt(
                                    title = "Yedeklemeyi Onayla",
                                    description = "Notlarınızı dışa aktarmak için parmak izinizi kullanın",
                                    onResult = { result ->
                                        if (result is BiometricResult.AuthenticationSuccess) {
                                            showExportPasswordDialog = false
                                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                            if (isSharePending) {
                                                onExportAndShare(context, vaultPasswordForExport.ifBlank { null })
                                            } else {
                                                val ext = if (vaultPasswordForExport.isNotBlank()) "notevault" else "zip"
                                                createDocumentLauncher.launch("NoteApp_Yedek_$dateStr.$ext")
                                            }
                                        }
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Parmak İzi ile Doğrula")
                        }
                    }

                    OutlinedTextField(
                        value = vaultPasswordForExport,
                        onValueChange = { vaultPasswordForExport = it },
                        label = { Text("Kasa Şifresi / PIN") },
                        placeholder = { Text("Şifresiz için boş bırakın") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { exportPasswordVisible = !exportPasswordVisible }) {
                                Icon(
                                    if (exportPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (exportPasswordVisible) "Şifreyi gizle" else "Şifreyi göster"
                                )
                            }
                        },
                        visualTransformation = if (exportPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "💡 Bu şifre yedeği başka bir cihaza taşırken veya geri yüklerken istenecektir.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportPasswordDialog = false
                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        if (isSharePending) {
                            onExportAndShare(context, vaultPasswordForExport.ifBlank { null })
                        } else {
                            val ext = if (vaultPasswordForExport.isNotBlank()) "notevault" else "zip"
                            createDocumentLauncher.launch("NoteApp_Yedek_$dateStr.$ext")
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(if (vaultPasswordForExport.isNotBlank()) "Şifreli Yedekle" else "Şifresiz Yedekle")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showExportPasswordDialog = false
                        exportPasswordVisible = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("İptal")
                }
            }
        )
    }

    // 2. İçe Aktarma Onay Penceresi (Hiyerarşik Dikey Eylemler, Buton Çakışması Yok)
    if (showImportConfirmDialog && pendingImportUri != null) {
        val uri = pendingImportUri!!
        AlertDialog(
            onDismissRequest = {
                showImportConfirmDialog = false
                pendingImportUri = null
                importPasswordInput = ""
                importPasswordVisible = false
            },
            icon = {
                Icon(
                    Icons.Default.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Yedekten Geri Yükle",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Seçtiğiniz yedek paketindeki tüm notlar, çizimler, etiketler ve medya dosyaları geri yüklenecektir.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = importPasswordInput,
                        onValueChange = { importPasswordInput = it },
                        label = { Text("Kasa Kilidi Şifresi (Varsa)") },
                        placeholder = { Text("Yedek şifresi") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { importPasswordVisible = !importPasswordVisible }) {
                                Icon(
                                    if (importPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (importPasswordVisible) "Şifreyi gizle" else "Şifreyi göster"
                                )
                            }
                        },
                        visualTransformation = if (importPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Text(
                        "Geri yükleme yöntemini seçin:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Seçenek 1: Mevcut Notları Koru & Ekle (Önerilen)
                    Button(
                        onClick = {
                            showImportConfirmDialog = false
                            val pass = importPasswordInput.ifBlank { null }
                            pendingImportUri = null
                            importPasswordInput = ""
                            onImportFromUri(uri, false, pass)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddModerator, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mevcut Notları Koru & Ekle")
                    }

                    // Seçenek 2: Tümünü Sil & Sıfırdan Yükle
                    FilledTonalButton(
                        onClick = {
                            showImportConfirmDialog = false
                            val pass = importPasswordInput.ifBlank { null }
                            pendingImportUri = null
                            importPasswordInput = ""
                            onImportFromUri(uri, true, pass)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tümünü Sil & Sıfırdan Yükle")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showImportConfirmDialog = false
                        pendingImportUri = null
                        importPasswordInput = ""
                        importPasswordVisible = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Text("İptal")
                }
            }
        )
    }

    // 3. İşlem Sürüyor Göstergesi
    if (backupUiState.isOperating) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                    Text(
                        text = backupUiState.operationTitle ?: "Lütfen bekleyin...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // 4. Sonuç / Bilgilendirme İletişim Kutusu
    backupUiState.message?.let { msg ->
        AlertDialog(
            onDismissRequest = onDismissBackupMessage,
            icon = {
                Icon(
                    imageVector = if (backupUiState.isSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (backupUiState.isSuccess == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    if (backupUiState.isSuccess == true) "İşlem Başarılı" else "Bilgilendirme",
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(msg) },
            confirmButton = {
                Button(
                    onClick = onDismissBackupMessage,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Tamam")
                }
            }
        )
    }

    // 5. PIN Belirleme / Değiştirme Penceresi
    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showSetPinDialog = false
                pinInput = ""
                setPinVisible = false
            },
            icon = {
                Icon(
                    Icons.Default.Pin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Güvenlik PIN'i Belirle",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Kilitli notlara erişim için 4-6 haneli bir PIN kodu girin:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text("PIN Kodu") },
                        placeholder = { Text("Örn: 1234") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { setPinVisible = !setPinVisible }) {
                                Icon(
                                    if (setPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (setPinVisible) "Gizle" else "Göster"
                                )
                            }
                        },
                        visualTransformation = if (setPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = if (pinInput.length < 4) "En az 4 hane girmelisiniz (${pinInput.length}/4)" else "PIN hazır (${pinInput.length} hane)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pinInput.length >= 4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length >= 4) {
                            onMasterPinChange(pinInput)
                            showSetPinDialog = false
                            pinInput = ""
                            setPinVisible = false
                        }
                    },
                    enabled = pinInput.length >= 4,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showSetPinDialog = false
                        pinInput = ""
                        setPinVisible = false
                    },
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("İptal")
                }
            }
        )
    }

    // 6. Senkronizasyon İşlemi Sürüyor Göstergesi
    if (syncUiState.isOperating) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                    Text(
                        text = syncUiState.operationTitle ?: "Masaüstü ile senkronize ediliyor...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // 7. Senkronizasyon Bilgilendirme / Sonuç İletişim Kutusu
    syncUiState.message?.let { msg ->
        AlertDialog(
            onDismissRequest = onDismissSyncMessage,
            icon = {
                Icon(
                    imageVector = if (syncUiState.isSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (syncUiState.isSuccess == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    if (syncUiState.isSuccess == true) "Senkronizasyon Başarılı" else "Senkronizasyon Bilgisi",
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(msg) },
            confirmButton = {
                Button(
                    onClick = onDismissSyncMessage,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Tamam")
                }
            }
        )
    }

    // 8. Güncelleme Detay Popup Penceresi
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
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Güncelleme Var! 🚀", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
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
                                Text("Mevcut Sürüm:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("v${status.currentVersionName} (Kod: ${status.currentVersionCode})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Yeni Sürüm:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Text("v${status.remoteVersionName} (Kod: ${status.remoteVersionCode})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Text("Son Güncellemede Yapılan Değişiklikler:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        status.message.ifBlank { "Hata düzeltmeleri, Material 3 arayüz iyileştirmeleri ve performans artışı." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateDetailsDialog = false
                        onDownloadAndInstallUpdate()
                    },
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("🚀 Güncelle")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showUpdateDetailsDialog = false },
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Daha Sonra")
                }
            }
        )
    }

    // 9. Veritabanı Bakım Sonuç Penceresi
    maintenanceUiState.message?.let { msg ->
        AlertDialog(
            onDismissRequest = onDismissMaintenanceMessage,
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Veritabanı Bakımı", fontWeight = FontWeight.Bold) },
            text = { Text(msg) },
            confirmButton = {
                Button(
                    onClick = onDismissMaintenanceMessage,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Tamam")
                }
            }
        )
    }
}

/**
 * Modern Material 3 Kart Grubu Konteyneri.
 * İkon, başlık, alt başlık ve opsiyonel rozet ile tutarlı görsel hiyerarşi sağlar.
 */
@Composable
private fun SettingsGroupCard(
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Başlık Satırı
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconTint.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = iconTint.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = iconTint,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // İçerik
            content()
        }
    }
}
