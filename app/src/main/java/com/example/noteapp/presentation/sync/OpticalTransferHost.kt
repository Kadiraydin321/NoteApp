package com.example.noteapp.presentation.sync

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.example.noteapp.data.sync.OpticalImportResult
import com.example.noteapp.data.sync.OpticalTransferManager
import com.example.noteapp.data.sync.QrSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

enum class OpticalTransferAction { SEND, RECEIVE }

@HiltViewModel
class OpticalTransferViewModel @Inject constructor(
    val opticalTransferManager: OpticalTransferManager,
    val qrSyncManager: QrSyncManager
) : ViewModel()

/**
 * Ana ekran gibi SettingsViewModel'e erişimi olmayan yerlerden animasyonlu QR gönder/al
 * ekranlarını açmak için ortak giriş noktası.
 */
@Composable
fun OpticalTransferHost(
    action: OpticalTransferAction,
    onDismiss: () -> Unit
) {
    val viewModel = hiltViewModel<OpticalTransferViewModel>()
    val context = LocalContext.current
    when (action) {
        OpticalTransferAction.SEND -> OpticalQrSenderDialog(
            opticalTransferManager = viewModel.opticalTransferManager,
            onDismiss = onDismiss
        )
        OpticalTransferAction.RECEIVE -> OpticalQrScannerDialog(
            mode = ScannerMode.OPTICAL_STREAM,
            qrSyncManager = viewModel.qrSyncManager,
            opticalTransferManager = viewModel.opticalTransferManager,
            onDismiss = onDismiss,
            onOpticalTransferComplete = { result: OpticalImportResult ->
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        )
    }
}
