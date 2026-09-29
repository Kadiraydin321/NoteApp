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
import com.example.noteapp.data.settings.AppSettings
import com.example.noteapp.data.settings.AppSettingsManager
import com.example.noteapp.data.settings.WidgetFilterMode
import com.example.noteapp.widget.NotesWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val isOperating: Boolean = false,
    val operationTitle: String? = null,
    val message: String? = null,
    val isSuccess: Boolean? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val app: Application,
    private val settingsManager: AppSettingsManager,
    private val backupManager: BackupManager,
    private val sampleDataLoader: com.example.noteapp.data.sample.SampleDataLoader
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsManager.settings

    private val _backupUiState = MutableStateFlow(BackupUiState())
    val backupUiState: StateFlow<BackupUiState> = _backupUiState.asStateFlow()

    fun setWidgetFilterMode(mode: WidgetFilterMode) {
        settingsManager.setWidgetFilterMode(mode)
        NotesWidgetProvider.updateAllWidgets(app)
    }

    fun setWidgetShowLocked(show: Boolean) {
        settingsManager.setWidgetShowLockedNotes(show)
        NotesWidgetProvider.updateAllWidgets(app)
    }

    fun setDefaultNoteColor(color: Int) {
        settingsManager.setDefaultNoteColor(color)
    }

    fun setDynamicColor(enabled: Boolean) {
        settingsManager.setDynamicColor(enabled)
    }

    fun refreshWidget() {
        NotesWidgetProvider.updateAllWidgets(app)
    }

    /**
     * Seçilen URI hedefine ZIP yedeği oluşturup yazar.
     */
    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            _backupUiState.value = BackupUiState(isOperating = true, operationTitle = "Yedek paketi hazırlanıyor...")
            try {
                app.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val result = backupManager.createBackup(outputStream)
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
    fun exportAndShare(context: Context) {
        viewModelScope.launch {
            _backupUiState.value = BackupUiState(isOperating = true, operationTitle = "Yedek dosyası hazırlanıyor...")
            val result = backupManager.createShareableBackupFile()
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
    fun importBackupFromUri(uri: Uri, clearExisting: Boolean) {
        viewModelScope.launch {
            _backupUiState.value = BackupUiState(isOperating = true, operationTitle = "Yedek verileri geri yükleniyor...")
            try {
                app.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val result = backupManager.restoreBackup(inputStream, clearExisting)
                    when (result) {
                        is RestoreResult.Success -> {
                            _backupUiState.value = BackupUiState(
                                isOperating = false,
                                message = "Geri yükleme tamamlandı! ${result.notesCount} not ve ${result.attachmentsCount} medya dosyası içeri aktarıldı.",
                                isSuccess = true
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

    /**
     * Kullanıcının isteği üzerine 25 adet zengin özellikli örnek notu yükler.
     */
    fun loadSampleNotes() {
        viewModelScope.launch {
            _backupUiState.value = BackupUiState(isOperating = true, operationTitle = "25 adet örnek not ve kategori yükleniyor...")
            try {
                sampleDataLoader.populateIfEmpty(force = true)
                _backupUiState.value = BackupUiState(
                    isOperating = false,
                    message = "Harika! 25 adet zengin özellikli örnek not (çizimli, görselli, kilitli, renkli ve yapılacak listeleri) başarıyla yüklendi.",
                    isSuccess = true
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _backupUiState.value = BackupUiState(
                    isOperating = false,
                    message = "Örnek notlar yüklenirken hata oluştu: ${e.localizedMessage}",
                    isSuccess = false
                )
            }
        }
    }

    fun dismissMessage() {
        _backupUiState.value = _backupUiState.value.copy(message = null, isSuccess = null)
    }
}
