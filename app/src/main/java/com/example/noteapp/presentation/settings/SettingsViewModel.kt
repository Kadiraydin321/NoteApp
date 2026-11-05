package com.example.noteapp.presentation.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import com.example.noteapp.data.sync.AndroidSyncServer
import com.example.noteapp.data.sync.DiscoveredSyncServer
import com.example.noteapp.data.sync.IncomingSyncTransfer
import com.example.noteapp.data.sync.OpticalImportResult
import com.example.noteapp.data.sync.OpticalTransferManager
import com.example.noteapp.data.sync.QrSyncManager
import com.example.noteapp.data.sync.SyncUploadProgress
import com.example.noteapp.data.sync.SyncHostTransferResult
import com.example.noteapp.data.sync.SyncPairingSession
import com.example.noteapp.data.sync.SyncServiceDiscovery
import com.example.noteapp.data.sync.SyncQrPayload
import com.example.noteapp.data.sync.SyncResult
import com.example.noteapp.data.sync.SyncServerStatus
import com.example.noteapp.data.updater.AppUpdateManager
import com.example.noteapp.data.updater.UpdateStatus
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.widget.NotesWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.Inet4Address
import javax.inject.Inject

data class BackupUiState(
    val isOperating: Boolean = false,
    val operationTitle: String? = null,
    val message: String? = null,
    val isSuccess: Boolean? = null,
    val needsPasswordForUri: Uri? = null,
    val isPasswordWrong: Boolean = false
)

data class SyncUiState(
    val isOperating: Boolean = false,
    val isHosting: Boolean = false,
    val hostQrPayload: SyncQrPayload? = null,
    val incomingTransfer: IncomingSyncTransfer? = null,
    val uploadProgress: SyncUploadProgress = SyncUploadProgress(),
    val localNotesCount: Int = 0,
    val statusChecking: Boolean = false,
    val isDiscovering: Boolean = false,
    val isConnected: Boolean? = null,
    val serverStatus: SyncServerStatus? = null,
    val discoveredHost: String? = null,
    val discoveredServers: List<DiscoveredSyncServer> = emptyList(),
    val discoveryMessage: String? = null,
    val hasSession: Boolean = false,
    val sessionExpiresAt: Long? = null,
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
    private val noteRepository: NoteRepository,
    private val desktopSyncClient: DesktopSyncClient,
    private val androidSyncServer: AndroidSyncServer,
    private val syncServiceDiscovery: SyncServiceDiscovery,
    private val updateManager: AppUpdateManager,
    private val maintenanceManager: DatabaseMaintenanceManager,
    val qrSyncManager: QrSyncManager,
    val opticalTransferManager: OpticalTransferManager
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsManager.settings

    private val _backupUiState = MutableStateFlow(BackupUiState())
    val backupUiState: StateFlow<BackupUiState> = _backupUiState.asStateFlow()

    private val _syncUiState = MutableStateFlow(SyncUiState())
    val syncUiState: StateFlow<SyncUiState> = _syncUiState.asStateFlow()
    private data class ActiveSyncSession(
        val host: String,
        val session: SyncPairingSession,
        val tlsCertSha256: String?
    )
    private var activeSyncSession: ActiveSyncSession? = null
    private var syncDiscoveryJob: Job? = null

    private val _updateUiState = MutableStateFlow(UpdateUiState())
    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()

    private val _maintenanceUiState = MutableStateFlow(MaintenanceUiState())
    val maintenanceUiState: StateFlow<MaintenanceUiState> = _maintenanceUiState.asStateFlow()

    init {
        loadStorageInfo()
        viewModelScope.launch {
            _syncUiState.value = _syncUiState.value.copy(
                localNotesCount = noteRepository.getAllNotes().size
            )
        }
        viewModelScope.launch {
            androidSyncServer.incomingTransfer.collect { transfer ->
                _syncUiState.value = _syncUiState.value.copy(incomingTransfer = transfer)
            }
        }
        viewModelScope.launch {
            androidSyncServer.uploadProgress.collect { progress ->
                _syncUiState.value = _syncUiState.value.copy(uploadProgress = progress)
            }
        }
        viewModelScope.launch {
            androidSyncServer.transferResult.collect { result ->
                if (result != null) {
                    _syncUiState.value = _syncUiState.value.copy(
                        isOperating = false,
                        message = result.message,
                        isSuccess = result.success
                    )
                }
            }
        }
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

    fun startAndroidSyncServer() {
        viewModelScope.launch {
            try {
                val connectivity = app.getSystemService(ConnectivityManager::class.java)
                    ?: throw IllegalStateException("Ağ bağlantısı yöneticisi kullanılamıyor.")
                val network = connectivity.activeNetwork
                    ?: throw IllegalStateException("Önce Wi-Fi ağına bağlanın.")
                val capabilities = connectivity.getNetworkCapabilities(network)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
                    throw IllegalStateException("Eşitleme sunucusu için Wi-Fi bağlantısı gerekiyor.")
                }
                val host = connectivity.getLinkProperties(network)
                    ?.linkAddresses
                    ?.asSequence()
                    ?.map { it.address }
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull { !it.isLoopbackAddress && (it.isSiteLocalAddress || it.isLinkLocalAddress) }
                    ?.hostAddress
                    ?: throw IllegalStateException("Wi-Fi ağında kullanılabilir yerel IPv4 adresi bulunamadı.")
                val payload = androidSyncServer.start(host)
                _syncUiState.value = _syncUiState.value.copy(
                    isHosting = true,
                    hostQrPayload = payload,
                    message = "HTTPS eşitleme sunucusu yerel ağda başlatıldı.",
                    isSuccess = true
                )
            } catch (error: Exception) {
                androidSyncServer.stop()
                _syncUiState.value = _syncUiState.value.copy(
                    isHosting = false,
                    hostQrPayload = null,
                    message = error.localizedMessage ?: "HTTPS eşitleme sunucusu başlatılamadı.",
                    isSuccess = false
                )
            }
        }
    }

    fun stopAndroidSyncServer() {
        androidSyncServer.stop()
        _syncUiState.value = _syncUiState.value.copy(isHosting = false, hostQrPayload = null)
    }

    fun respondToIncomingSync(transferId: String, approved: Boolean, clearExistingConfirmed: Boolean = false) {
        if (approved) {
            androidSyncServer.approveIncoming(transferId, clearExistingConfirmed)
        } else {
            androidSyncServer.rejectIncoming(transferId)
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

    fun setLockTimeoutPolicy(policy: com.example.noteapp.data.settings.LockTimeoutPolicy) {
        settingsManager.setLockTimeoutPolicy(policy)
    }

    fun setAmoledDark(enabled: Boolean) {
        settingsManager.setAmoledDark(enabled)
    }

    fun setDailyWordGoal(goal: Int) {
        settingsManager.setDailyWordGoal(goal)
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
    fun importBackupFromUri(
        uri: Uri,
        clearExisting: Boolean,
        vaultPassword: String? = null,
        candidatePasswords: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            _backupUiState.value = BackupUiState(isOperating = true, operationTitle = "Yedek verileri geri yükleniyor...")
            try {
                app.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val candidates = candidatePasswords.toMutableList()
                    settingsManager.settings.value.masterPin?.let {
                        if (it.isNotBlank() && !candidates.contains(it)) {
                            candidates.add(it)
                        }
                    }
                    val result = backupManager.restoreBackup(inputStream, clearExisting, vaultPassword, candidates)
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
                                needsPasswordForUri = uri,
                                isPasswordWrong = !vaultPassword.isNullOrBlank()
                            )
                        }
                        is RestoreResult.Error -> {
                            if (result.message.contains("Hatalı Kasa", ignoreCase = true) ||
                                result.message.contains("şifre", ignoreCase = true)) {
                                _backupUiState.value = BackupUiState(
                                    isOperating = false,
                                    needsPasswordForUri = uri,
                                    isPasswordWrong = true
                                )
                            } else {
                                _backupUiState.value = BackupUiState(
                                    isOperating = false,
                                    message = result.message,
                                    isSuccess = false
                                )
                            }
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
        _backupUiState.value = _backupUiState.value.copy(message = null, isSuccess = null, needsPasswordForUri = null, isPasswordWrong = false)
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

    fun discoverSyncServers() {
        syncDiscoveryJob?.cancel()
        syncDiscoveryJob = viewModelScope.launch {
            val found = linkedMapOf<String, DiscoveredSyncServer>()
            _syncUiState.value = _syncUiState.value.copy(
                isDiscovering = true,
                discoveredServers = emptyList(),
                discoveryMessage = "Aynı Wi-Fi ağındaki masaüstü cihazları aranıyor…"
            )
            try {
                withTimeoutOrNull(12_000) {
                    syncServiceDiscovery.discover().collect { server ->
                        found[server.endpoint] = server
                        _syncUiState.value = _syncUiState.value.copy(
                            discoveredServers = found.values.toList(),
                            discoveryMessage = null
                        )
                    }
                }
                _syncUiState.value = _syncUiState.value.copy(
                    isDiscovering = false,
                    discoveryMessage = if (found.isEmpty()) {
                        "Masaüstü bulunamadı. İki cihazın aynı Wi-Fi ağında ve masaüstü uygulamasının açık olduğundan emin olun."
                    } else {
                        "${found.size} masaüstü bulundu. Cihazı seçin; eşleşme için QR kodunu okutmanız gerekir."
                    }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _syncUiState.value = _syncUiState.value.copy(
                    isDiscovering = false,
                    discoveryMessage = error.localizedMessage
                        ?: "Yerel ağ keşfi başarısız oldu. Ağ iznini ve Wi-Fi bağlantısını kontrol edin."
                )
            }
        }
    }

    fun selectDiscoveredSyncServer(server: DiscoveredSyncServer) {
        if (activeSyncSession?.host != server.endpoint) activeSyncSession = null
        settingsManager.setLastSyncHost(server.endpoint)
        _syncUiState.value = _syncUiState.value.copy(
            discoveredHost = server.endpoint,
            isConnected = false,
            serverStatus = null,
            hasSession = activeSyncSession != null,
            sessionExpiresAt = activeSyncSession?.session?.expiresAt,
            discoveryMessage = "Masaüstü bulundu. Eşleşme için bu bilgisayardaki QR kodunu okutun.",
            message = null,
            isSuccess = null
        )
    }

    fun onQrSyncScanned(payload: SyncQrPayload) {
        viewModelScope.launch {
            val host = payload.fullUrl
            settingsManager.setLastSyncHost(host)
            _syncUiState.value = _syncUiState.value.copy(
                isOperating = true,
                operationTitle = "QR eşleşmesi doğrulanıyor…",
                discoveredHost = host,
                message = null,
                discoveryMessage = null,
                isSuccess = null
            )

            val pin: String
            val sessionToken: String?
            val tlsCertSha256 = payload.tlsCertSha256
            if (payload.isSessionPairing) {
                val pairing = desktopSyncClient.pairSession(
                    host,
                    payload.pairingToken.orEmpty(),
                    tlsCertSha256
                )
                if (pairing.isFailure) {
                    activeSyncSession = null
                    _syncUiState.value = _syncUiState.value.copy(
                        isOperating = false,
                        isConnected = false,
                        hasSession = false,
                        sessionExpiresAt = null,
                        message = pairing.exceptionOrNull()?.message
                            ?: "QR eşleşmesi başarısız oldu. Masaüstünden yeni QR kodu üretip tekrar okutun.",
                        isSuccess = false
                    )
                    return@launch
                }
                val session = pairing.getOrThrow()
                activeSyncSession = ActiveSyncSession(host, session, tlsCertSha256)
                _syncUiState.value = _syncUiState.value.copy(
                    hasSession = true,
                    sessionExpiresAt = session.expiresAt,
                    operationTitle = "Masaüstü oturumu oluşturuldu. Notlar alınıyor…"
                )
                pin = ""
                sessionToken = session.sessionToken
            } else {
                pin = payload.pin.orEmpty()
                sessionToken = null
            }

            val connRes = desktopSyncClient.checkConnection(host, tlsCertSha256)
            if (connRes.isSuccess) {
                val status = connRes.getOrThrow()
                _syncUiState.value = _syncUiState.value.copy(
                    isOperating = false,
                    isConnected = true,
                    serverStatus = status,
                    message = if (sessionToken != null) {
                        "Güvenli oturum açıldı: ${status.appName} (${status.notesCount} not)."
                    } else {
                        "Eski PIN eşleşmesi doğrulandı: ${status.appName} (${status.notesCount} not)."
                    }
                )
            } else {
                _syncUiState.value = _syncUiState.value.copy(
                    isOperating = false,
                    isConnected = false,
                    message = connRes.exceptionOrNull()?.message ?: "Masaüstüne bağlanılamadı.",
                    isSuccess = false
                )
            }
        }
    }

    /**
     * Optik akıştan not aktarımı tamamlandığında tetiklenir.
     */
    fun onOpticalTransferCompleted(result: OpticalImportResult) {
        _syncUiState.value = _syncUiState.value.copy(
            message = result.message,
            isSuccess = true
        )
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
            val session = validSessionFor(host)
            val result = desktopSyncClient.sendNotesToDesktop(
                host,
                pin,
                clearExisting,
                vaultPassword,
                session?.sessionToken,
                activeTlsPinFor(host)
            ) { sentBytes, totalBytes ->
                _syncUiState.value = _syncUiState.value.copy(
                    uploadProgress = SyncUploadProgress(sentBytes, totalBytes)
                )
            }
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
                    if (result.requiresRePair) activeSyncSession = null
                    _syncUiState.value = _syncUiState.value.copy(
                        isOperating = false,
                        isConnected = if (result.requiresRePair) false else _syncUiState.value.isConnected,
                        hasSession = if (result.requiresRePair) false else _syncUiState.value.hasSession,
                        sessionExpiresAt = if (result.requiresRePair) null else _syncUiState.value.sessionExpiresAt,
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
            val session = validSessionFor(host)
            val result = desktopSyncClient.pullNotesFromDesktop(
                host,
                pin,
                clearExisting,
                vaultPassword,
                session?.sessionToken,
                activeTlsPinFor(host)
            ) { receivedBytes, totalBytes ->
                _syncUiState.value = _syncUiState.value.copy(
                    uploadProgress = SyncUploadProgress(receivedBytes, totalBytes)
                )
            }
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
                    if (result.requiresRePair) activeSyncSession = null
                    _syncUiState.value = _syncUiState.value.copy(
                        isOperating = false,
                        isConnected = if (result.requiresRePair) false else _syncUiState.value.isConnected,
                        hasSession = if (result.requiresRePair) false else _syncUiState.value.hasSession,
                        sessionExpiresAt = if (result.requiresRePair) null else _syncUiState.value.sessionExpiresAt,
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

    private fun validSessionFor(host: String): SyncPairingSession? {
        val active = activeSyncSession ?: return null
        if (active.session.expiresAt <= System.currentTimeMillis() || syncOrigin(active.host) != syncOrigin(host)) {
            activeSyncSession = null
            _syncUiState.value = _syncUiState.value.copy(hasSession = false, sessionExpiresAt = null)
            return null
        }
        return active.session
    }

    private fun activeTlsPinFor(host: String): String? =
        activeSyncSession
            ?.takeIf { syncOrigin(it.host) == syncOrigin(host) }
            ?.tlsCertSha256

    private fun syncOrigin(host: String): String? {
        val candidate = if (host.startsWith("http://", true) || host.startsWith("https://", true)) host else "http://$host"
        return runCatching {
            val uri = java.net.URI(candidate)
            val address = uri.host ?: return null
            val port = if (uri.port == -1) 8765 else uri.port
            "${address.lowercase()}:$port"
        }.getOrNull()
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

    override fun onCleared() {
        androidSyncServer.stop()
        super.onCleared()
    }
}
