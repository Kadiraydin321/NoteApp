package com.example.noteapp.presentation.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.noteapp.data.backup.BackupManager
import com.example.noteapp.data.backup.BackupResult
import com.example.noteapp.data.backup.RestoreResult
import com.example.noteapp.data.local.DatabaseMaintenanceManager
import com.example.noteapp.data.local.MaintenanceResult
import com.example.noteapp.data.settings.AppSettings
import com.example.noteapp.data.settings.AppSettingsManager
import com.example.noteapp.data.settings.WidgetFilterMode
import com.example.noteapp.data.sync.DesktopSyncClient
import com.example.noteapp.data.sync.SyncResult
import com.example.noteapp.data.sync.SyncServerStatus
import com.example.noteapp.data.updater.AppUpdateManager
import com.example.noteapp.data.updater.UpdateStatus
import com.example.noteapp.widget.NotesWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BackupUiState(
    val isOperating: Boolean = false,
    val operationTitle: String? = null,
    val message: String? = null,
    val isSuccess: Boolean? = null
)

data class SyncUiState(
    val isOperating: Boolean = false,
    val statusChecking: Boolean = false,
    val isDiscovering: Boolean = false,
    val isConnected: Boolean? = null,
    val serverStatus: SyncServerStatus? = null,
    val discoveredHost: String? = null,
    val operationTitle: String? = null,
    val message: String? = null,
    val isSuccess: Boolean? = null
)

data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.Idle,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val readyApkFile: File? = null,
    val availableUpdateInfo: UpdateStatus.UpdateAvailable? = null,
    val hasShownDialogForVersion: Int? = null,
    val message: String? = null
)

data class MaintenanceUiState(
    val isOperating: Boolean = false,
    val dbSizeKb: Long = 0L,
    val cacheSizeKb: Long = 0L,
    val lastResult: MaintenanceResult? = null,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val app: Application,
    private val settingsManager: AppSettingsManager,
    private val backupManager: BackupManager,
    private val desktopSyncClient: DesktopSyncClient,
    private val updateManager: AppUpdateManager,
    private val maintenanceManager: DatabaseMaintenanceManager
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsManager.settings

    private val _backupUiState = MutableStateFlow(BackupUiState())
    val backupUiState: StateFlow<BackupUiState> = _backupUiState.asStateFlow()

    private val _syncUiState = MutableStateFlow(SyncUiState())
    val syncUiState: StateFlow<SyncUiState> = _syncUiState.asStateFlow()

    private val _updateUiState = MutableStateFlow(UpdateUiState())
    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()

    private val _maintenanceUiState = MutableStateFlow(MaintenanceUiState())
    val maintenanceUiState: StateFlow<MaintenanceUiState> = _maintenanceUiState.asStateFlow()

    init {
        loadStorageInfo()
        viewModelScope.launch {
            updateManager.updateStatus.collect { status ->
                _updateUiState.value = _updateUiState.value.copy(
                    status = status,
                    isDownloading = status is UpdateStatus.Downloading,
                    downloadProgress = if (status is UpdateStatus.Downloading) status.progressPercent else _updateUiState.value.downloadProgress,
                    downloadedBytes = if (status is UpdateStatus.Downloading) status.downloadedBytes else _updateUiState.value.downloadedBytes,
                    totalBytes = if (status is UpdateStatus.Downloading) status.totalBytes else _updateUiState.value.totalBytes,
                    readyApkFile = if (status is UpdateStatus.ReadyToInstall) status.apkFile else _updateUiState.value.readyApkFile,
                    availableUpdateInfo = updateManager.lastAvailableUpdate ?: (status as? UpdateStatus.UpdateAvailable) ?: _updateUiState.value.availableUpdateInfo,
                    hasShownDialogForVersion = updateManager.hasShownDialogForVersion
                )
            }
        }
    }

    fun setWidgetFilterMode(mode: WidgetFilterMode) {
        settingsManager.setWidgetFilterMode(mode)
        NotesWidgetProvider.updateAllWidgets(app)
    }

    fun setWidgetShowLocked(show: Boolean) {
        settingsManager.setWidgetShowLockedNotes(show)
        NotesWidgetProvider.updateAllWidgets(app)
    }

    fun setWidgetShowContent(show: Boolean) {
        settingsManager.setWidgetShowContent(show)
        NotesWidgetProvider.updateAllWidgets(app)
    }

    fun setWidgetDesignPreset(preset: com.example.noteapp.data.settings.WidgetDesignPreset) {
        settingsManager.setWidgetDesignPreset(preset)
        NotesWidgetProvider.updateAllWidgets(app)
    }

    fun setWidgetFabBehavior(behavior: com.example.noteapp.data.settings.WidgetFabBehavior) {
        settingsManager.setWidgetFabBehavior(behavior)
        NotesWidgetProvider.updateAllWidgets(app)
    }

    fun setWidgetCornerRadius(radius: com.example.noteapp.data.settings.WidgetCornerRadius) {
        settingsManager.setWidgetCornerRadius(radius)
        NotesWidgetProvider.updateAllWidgets(app)
    }

    fun setDefaultNoteColor(color: Int) {
        settingsManager.setDefaultNoteColor(color)
    }

    fun setDynamicColor(enabled: Boolean) {
        settingsManager.setDynamicColor(enabled)
    }

    fun setColorPalette(palette: com.example.noteapp.data.settings.AppColorPalette) {
        settingsManager.setColorPalette(palette)
    }

    fun setThemeMode(mode: com.example.noteapp.data.settings.ThemeMode) {
        settingsManager.setThemeMode(mode)
    }

    fun setLayoutMode(mode: com.example.noteapp.data.settings.NotesLayoutMode) {
        settingsManager.setLayoutMode(mode)
    }

    fun setDrawerCollapseMode(mode: com.example.noteapp.data.settings.DrawerCollapseMode) {
        settingsManager.setDrawerCollapseMode(mode)
    }

    fun setCardCornerStyle(style: com.example.noteapp.data.settings.CardCornerStyle) {
        settingsManager.setCardCornerStyle(style)
    }

    fun setCardElevationStyle(style: com.example.noteapp.data.settings.CardElevationStyle) {
        settingsManager.setCardElevationStyle(style)
    }

    fun setQuickActionLocation(location: com.example.noteapp.data.settings.QuickActionLocation) {
        settingsManager.setQuickActionLocation(location)
    }

    fun setSearchBarShape(shape: com.example.noteapp.data.settings.SearchBarShape) {
        settingsManager.setSearchBarShape(shape)
    }

    fun applyDesignPreset(preset: com.example.noteapp.data.settings.DesignPreset) {
        settingsManager.applyDesignPreset(preset)
    }

    fun setMasterPin(pin: String?) {
        settingsManager.setMasterPin(pin)
    }

    fun setAutoLockOnExit(enabled: Boolean) {
        settingsManager.setAutoLockOnExit(enabled)
    }

    fun setHighContrastNegative(enabled: Boolean) {
        settingsManager.setHighContrastNegative(enabled)
    }

    fun setTrashRetentionPolicy(policy: com.example.noteapp.data.settings.TrashRetentionPolicy) {
        settingsManager.setTrashRetentionPolicy(policy)
    }

    fun refreshWidget() {
        NotesWidgetProvider.updateAllWidgets(app)
    }

    /**
     * Seçilen URI hedefine ZIP yedeği oluşturup yazar.
     */
    fun exportBackupToUri(uri: Uri, vaultPassword: String? = null) {
        viewModelScope.launch {
            _backupUiState.value = BackupUiState(isOperating = true, operationTitle = "Yedek paketi hazırlanıyor...")
            try {
                app.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val result = backupManager.createBackup(outputStream, vaultPassword)
                    when (result) {
                        is BackupResult.Success -> {
                            val now = System.currentTimeMillis()
                            settingsManager.setLastBackupTimestamp(now)
                            _backupUiState.value = BackupUiState(
                                isOperating = false,
                                message = "Yedekleme başarılı! ${result.notesCount} not ve ${result.attachmentsCount} medya dosyası dışa aktarıldı.",
                                isSuccess = true
                            )
                        }
                        is BackupResult.Error -> {
                            _backupUiState.value = BackupUiState(
                                isOperating = false,
                                message = result.message,
                                isSuccess = false
                            )
                        }
                    }
                } ?: run {
                    _backupUiState.value = BackupUiState(
                        isOperating = false,
                        message = "Hedef dosya açılamadı.",
                        isSuccess = false
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _backupUiState.value = BackupUiState(
                    isOperating = false,
                    message = "Dışa aktarma hatası: ${e.localizedMessage}",
                    isSuccess = false
                )
            }
        }
    }

    /**
     * Yedek oluşturup Android Paylaş menüsü ile doğrudan uygulamalara gönderir.
     */
    fun exportAndShare(context: Context, vaultPassword: String? = null) {
        viewModelScope.launch {
            _backupUiState.value = BackupUiState(isOperating = true, operationTitle = "Yedek dosyası hazırlanıyor...")
            val result = backupManager.createShareableBackupFile(vaultPassword)
            when (result) {
                is BackupResult.Success -> {
                    val now = System.currentTimeMillis()
                    settingsManager.setLastBackupTimestamp(now)
                    _backupUiState.value = BackupUiState(isOperating = false)
                    result.zipFile?.let { file ->
                        val shareIntent = backupManager.createShareIntent(file)
                        val chooser = Intent.createChooser(shareIntent, "Yedek Dosyasını Paylaş").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                    }
                }
                is BackupResult.Error -> {
                    _backupUiState.value = BackupUiState(
                        isOperating = false,
                        message = result.message,
                        isSuccess = false
                    )
                }
            }
        }
    }

    /**
     * Seçilen ZIP yedek dosyasından notları ve medyaları içe aktarır.
     */
    fun importBackupFromUri(uri: Uri, clearExisting: Boolean, vaultPassword: String? = null) {
        viewModelScope.launch {
            _backupUiState.value = BackupUiState(isOperating = true, operationTitle = "Yedek verileri geri yükleniyor...")
            try {
                app.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val result = backupManager.restoreBackup(inputStream, clearExisting, vaultPassword)
                    when (result) {
                        is RestoreResult.Success -> {
                            _backupUiState.value = BackupUiState(
                                isOperating = false,
                                message = "Geri yükleme tamamlandı! ${result.notesCount} not ve ${result.attachmentsCount} medya dosyası başarıyla içeri aktarıldı.",
                                isSuccess = true
                            )
                        }
                        is RestoreResult.NeedsPassword -> {
                            _backupUiState.value = BackupUiState(
                                isOperating = false,
                                message = "Bu yedek dosyası Kasa Kilidi ile şifrelenmiştir. Lütfen kasa kilidini giriniz.",
                                isSuccess = false
                            )
                        }
                        is RestoreResult.Error -> {
                            _backupUiState.value = BackupUiState(
                                isOperating = false,
                                message = result.message,
                                isSuccess = false
                            )
                        }
                    }
                } ?: run {
                    _backupUiState.value = BackupUiState(
                        isOperating = false,
                        message = "Yedek dosyası açılamadı.",
                        isSuccess = false
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _backupUiState.value = BackupUiState(
                    isOperating = false,
                    message = "İçe aktarma hatası: ${e.localizedMessage}",
                    isSuccess = false
                )
            }
        }
    }

    fun dismissMessage() {
        _backupUiState.value = _backupUiState.value.copy(message = null, isSuccess = null)
    }

    /**
     * Masaüstü sunucusu ile bağlantıyı test eder.
     */
    fun checkSyncConnection(host: String) {
        viewModelScope.launch {
            _syncUiState.value = _syncUiState.value.copy(
                statusChecking = true,
                message = null,
                isSuccess = null
            )
            val result = desktopSyncClient.checkConnection(host)
            result.onSuccess { status ->
                settingsManager.setLastSyncHost(host)
                _syncUiState.value = _syncUiState.value.copy(
                    statusChecking = false,
                    isConnected = true,
                    serverStatus = status,
                    message = "Bağlantı başarılı: ${status.appName} (${status.notesCount} not mevcut)",
                    isSuccess = true
                )
            }.onFailure { error ->
                _syncUiState.value = _syncUiState.value.copy(
                    statusChecking = false,
                    isConnected = false,
                    serverStatus = null,
                    message = error.localizedMessage ?: "Bağlantı kurulamadı.",
                    isSuccess = false
                )
            }
        }
    }

    /**
     * Yerel ağdaki bilgisayarı otomatik arar ve bulunursa IP'yi kaydeder.
     */
    fun autoDiscoverServer(currentInput: String? = null) {
        viewModelScope.launch {
            _syncUiState.value = _syncUiState.value.copy(
                isDiscovering = true,
                message = "Yerel ağdaki bilgisayar taranıyor...",
                isSuccess = null
            )
            val preferred = currentInput?.ifBlank { null } ?: settings.value.lastSyncHost.ifBlank { null }
            val result = desktopSyncClient.autoDiscoverServer(preferred)
            result.onSuccess { (host, status) ->
                settingsManager.setLastSyncHost(host)
                _syncUiState.value = _syncUiState.value.copy(
                    isDiscovering = false,
                    isConnected = true,
                    serverStatus = status,
                    discoveredHost = host,
                    message = "Bilgisayar bulundu: $host (${status.appName}, ${status.notesCount} not)",
                    isSuccess = true
                )
            }.onFailure { err ->
                _syncUiState.value = _syncUiState.value.copy(
                    isDiscovering = false,
                    message = err.localizedMessage ?: "Ağda bilgisayar bulunamadı.",
                    isSuccess = false
                )
            }
        }
    }

    /**
     * Notları masaüstüne aktarır.
     */
    fun sendNotesToDesktop(host: String, pin: String, clearExisting: Boolean, vaultPassword: String? = null) {
        viewModelScope.launch {
            _syncUiState.value = _syncUiState.value.copy(
                isOperating = true,
                operationTitle = "Masaüstüne gönderiliyor...",
                message = null,
                isSuccess = null
            )
            val result = desktopSyncClient.sendNotesToDesktop(host, pin, clearExisting, vaultPassword)
            when (result) {
                is SyncResult.Success -> {
                    settingsManager.setLastSyncHost(host)
                    settingsManager.setLastSyncPin(pin)
                    _syncUiState.value = _syncUiState.value.copy(
                        isOperating = false,
                        isConnected = true,
                        message = result.message,
                        isSuccess = true
                    )
                }
                is SyncResult.Error -> {
                    _syncUiState.value = _syncUiState.value.copy(
                        isOperating = false,
                        message = result.message,
                        isSuccess = false
                    )
                }
                is SyncResult.NeedsPassword -> {
                    _syncUiState.value = _syncUiState.value.copy(
                        isOperating = false,
                        message = result.message,
                        isSuccess = false
                    )
                }
            }
        }
    }

    /**
     * Notları masaüstünden çeker ve telefona yükler.
     */
    fun pullNotesFromDesktop(host: String, pin: String, clearExisting: Boolean, vaultPassword: String? = null) {
        viewModelScope.launch {
            _syncUiState.value = _syncUiState.value.copy(
                isOperating = true,
                operationTitle = "Masaüstünden notlar alınıyor...",
                message = null,
                isSuccess = null
            )
            val result = desktopSyncClient.pullNotesFromDesktop(host, pin, clearExisting, vaultPassword)
            when (result) {
                is SyncResult.Success -> {
                    settingsManager.setLastSyncHost(host)
                    settingsManager.setLastSyncPin(pin)
                    NotesWidgetProvider.updateAllWidgets(app)
                    _syncUiState.value = _syncUiState.value.copy(
                        isOperating = false,
                        isConnected = true,
                        message = result.message,
                        isSuccess = true
                    )
                }
                is SyncResult.Error -> {
                    _syncUiState.value = _syncUiState.value.copy(
                        isOperating = false,
                        message = result.message,
                        isSuccess = false
                    )
                }
                is SyncResult.NeedsPassword -> {
                    _syncUiState.value = _syncUiState.value.copy(
                        isOperating = false,
                        message = result.message,
                        isSuccess = false
                    )
                }
            }
        }
    }

    fun dismissSyncMessage() {
        _syncUiState.value = _syncUiState.value.copy(message = null, isSuccess = null)
    }

    // ==========================================
    // UYGULAMA İÇİ GÜNCELLEME İŞLEMLERİ
    // ==========================================

    fun checkForUpdates() {
        viewModelScope.launch {
            updateManager.checkForUpdates()
        }
    }

    /**
     * Güncelleme denetimi yapar.
     * Kullanıcı onay vermeden asla indirme başlatmaz.
     */
    fun checkAndInstallUpdate(force: Boolean = false) {
        if (force) {
            downloadAndInstallUpdate()
        } else {
            checkForUpdates()
        }
    }

    fun downloadAndInstallUpdate() {
        updateManager.downloadAndInstallUpdate()
    }

    fun installReadyApk() {
        updateManager.installReadyApk()
    }

    fun dismissUpdateStatus() {
        updateManager.dismissUpdateStatus()
    }

    fun markDialogShown(versionCode: Int) {
        updateManager.markDialogShown(versionCode)
        _updateUiState.value = _updateUiState.value.copy(hasShownDialogForVersion = versionCode)
    }

    // ==========================================
    // VERİTABANI & DEPOLAMA BAKIMI (MAINTENANCE)
    // ==========================================

    fun loadStorageInfo() {
        viewModelScope.launch {
            val (dbBytes, cacheBytes) = maintenanceManager.getStorageInfo()
            _maintenanceUiState.value = _maintenanceUiState.value.copy(
                dbSizeKb = dbBytes / 1024L,
                cacheSizeKb = cacheBytes / 1024L
            )
        }
    }

    fun optimizeDatabase() {
        viewModelScope.launch {
            _maintenanceUiState.value = _maintenanceUiState.value.copy(isOperating = true)
            val result = maintenanceManager.optimizeDatabase()
            val (newDbBytes, newCacheBytes) = maintenanceManager.getStorageInfo()
            val totalFreedKb = (result.freedBytes + result.freedCacheBytes) / 1024L

            _maintenanceUiState.value = _maintenanceUiState.value.copy(
                isOperating = false,
                dbSizeKb = newDbBytes / 1024L,
                cacheSizeKb = newCacheBytes / 1024L,
                lastResult = result,
                message = if (totalFreedKb > 0) {
                    "Veritabanı optimize edildi! $totalFreedKb KB disk alanı geri kazanıldı ve indeksler yenilendi (${result.executionTimeMs} ms)."
                } else {
                    "Veritabanı zaten en üst düzeyde optimize durumda. İndeksler doğrulandı (${result.executionTimeMs} ms)."
                }
            )
        }
    }

    fun dismissMaintenanceMessage() {
        _maintenanceUiState.value = _maintenanceUiState.value.copy(message = null)
    }
}
