package com.example.noteapp.presentation.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.noteapp.data.sync.OpticalImportResult
import com.example.noteapp.data.sync.OpticalImportSummary
import com.example.noteapp.data.sync.OpticalStreamProgress
import com.example.noteapp.data.sync.OpticalTransferManager
import com.example.noteapp.data.sync.QrSyncManager
import com.example.noteapp.data.sync.SyncQrPayload
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

enum class ScannerMode {
    WIFI_SYNC_PAIRING, // Masaüstü Wi-Fi Eşleşme QR Kodu
    OPTICAL_STREAM     // Animasyonlu Optik Veri Akışı (Air-Gapped)
}

private class ScannerResources {
    @Volatile var released = false
    var executor: java.util.concurrent.ExecutorService? = null
    var scanner: com.google.mlkit.vision.barcode.BarcodeScanner? = null
    var provider: ProcessCameraProvider? = null

    fun release() {
        released = true
        runCatching { provider?.unbindAll() }
        runCatching { scanner?.close() }
        executor?.shutdown()
    }
}

enum class OpticalReceivePhase {
    SCANNING, NEED_PASSWORD, DECRYPTING, CONFIRM, IMPORTING, DONE
}

@Composable
fun OpticalQrScannerDialog(
    mode: ScannerMode,
    qrSyncManager: QrSyncManager,
    opticalTransferManager: OpticalTransferManager,
    onDismiss: () -> Unit,
    onWifiSyncScanned: (SyncQrPayload) -> Unit = {},
    onOpticalTransferComplete: (OpticalImportResult) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        if (mode == ScannerMode.OPTICAL_STREAM) {
            opticalTransferManager.reset()
        }
    }

    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    val scannerResources = remember { ScannerResources() }
    var wifiPayloadHandled by remember { mutableStateOf(false) }
    var wifiScanError by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { scannerResources.release() }
    }
    var isTorchOn by remember { mutableStateOf(false) }

    // Optik Akış İlerleme Durumu
    var opticalProgress by remember { mutableStateOf(OpticalStreamProgress()) }
    var phase by remember { mutableStateOf(OpticalReceivePhase.SCANNING) }
    var importSuccessResult by remember { mutableStateOf<OpticalImportResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingEnvelope by remember { mutableStateOf<String?>(null) }
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var pendingSummary by remember { mutableStateOf<OpticalImportSummary?>(null) }
    var pendingIsLegacyPlain by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    val isImporting = phase == OpticalReceivePhase.IMPORTING || phase == OpticalReceivePhase.DECRYPTING

    fun restartScanning(message: String? = null) {
        opticalTransferManager.reset()
        opticalProgress = OpticalStreamProgress()
        pendingEnvelope = null
        pendingJson = null
        pendingSummary = null
        pendingIsLegacyPlain = false
        passwordInput = ""
        passwordError = null
        errorMessage = message
        phase = OpticalReceivePhase.SCANNING
    }

    // Titreşim Yardımcısı
    fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                // CameraX Preview + Barcode Scanning
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        val cameraExecutor = Executors.newSingleThreadExecutor()
                        scannerResources.executor = cameraExecutor

                        val barcodeScannerOptions = BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                        val barcodeScanner = BarcodeScanning.getClient(barcodeScannerOptions)
                        scannerResources.scanner = barcodeScanner

                        cameraProviderFuture.addListener({
                            val cameraProvider = try {
                                cameraProviderFuture.get()
                            } catch (e: Exception) {
                                cameraError = "Kamera başlatılamadı. Uygulamayı yeniden açıp tekrar deneyin."
                                return@addListener
                            }
                            if (scannerResources.released) return@addListener
                            scannerResources.provider = cameraProvider
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && phase == OpticalReceivePhase.SCANNING && importSuccessResult == null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    barcodeScanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                val rawValue = barcode.rawValue ?: continue

                                                when (mode) {
                                                    ScannerMode.WIFI_SYNC_PAIRING -> {
                                                        val payload = qrSyncManager.parseSyncPayload(rawValue)
                                                        if (payload != null) {
                                                            if (wifiPayloadHandled) return@addOnSuccessListener
                                                            wifiPayloadHandled = true
                                                            triggerVibration()
                                                            coroutineScope.launch(Dispatchers.Main) {
                                                                onWifiSyncScanned(payload)
                                                                onDismiss()
                                                            }
                                                            return@addOnSuccessListener
                                                        } else {
                                                            wifiScanError = "Bu QR kodu geçerli bir masaüstü eşleşme kodu değil veya süresi dolmuş. Masaüstündeki güncel kodu okutun."
                                                        }
                                                    }

                                                    ScannerMode.OPTICAL_STREAM -> {
                                                        val progress = opticalTransferManager.processChunk(rawValue)
                                                        coroutineScope.launch(Dispatchers.Main) {
                                                            opticalProgress = progress
                                                            progress.errorMessage?.let { errorMessage = it }

                                                            // Tüm kareler geldiğinde birleştirilir; içe aktarma yalnızca kullanıcı onayıyla yapılır
                                                            if (progress.isComplete && phase == OpticalReceivePhase.SCANNING) {
                                                                triggerVibration()
                                                                opticalTransferManager.assembleAndVerify()
                                                                    .onSuccess { assembled ->
                                                                        errorMessage = null
                                                                        if (opticalTransferManager.isEncryptedStream) {
                                                                            pendingEnvelope = assembled
                                                                            phase = OpticalReceivePhase.NEED_PASSWORD
                                                                        } else {
                                                                            opticalTransferManager.summarize(assembled)
                                                                                .onSuccess { summary ->
                                                                                    pendingJson = assembled
                                                                                    pendingSummary = summary
                                                                                    pendingIsLegacyPlain = true
                                                                                    phase = OpticalReceivePhase.CONFIRM
                                                                                }
                                                                                .onFailure { err ->
                                                                                    restartScanning(err.localizedMessage ?: "Veri okunamadı")
                                                                                }
                                                                        }
                                                                    }
                                                                    .onFailure { err ->
                                                                        restartScanning(err.localizedMessage ?: "Doğrulama hatası")
                                                                    }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            try {
                                cameraProvider.unbindAll()
                                val cam = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                                cameraInstance = cam
                            } catch (e: Exception) {
                                cameraError = "Kamera açılamadı: ${e.localizedMessage ?: "bilinmeyen hata"}"
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Hedefleme Kare Çerçevesi (Scanning Reticle Overlay)
                ScanningReticleOverlay(
                    isComplete = opticalProgress.isComplete || importSuccessResult != null,
                    modifier = Modifier.align(Alignment.Center)
                )

            } else {
                // Kamera İzni Yok Ekranı
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "Kamera İzni Gerekli",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Masaüstü QR kodunu veya optik veri akışını okumak için kamera iznine ihtiyaç vardır.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        ) {
                            Text("Kamera İzni Ver")
                        }
                    }
                }
            }

            // Üst Başlık ve Flaş / Kapat Butonları
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 36.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                }

                Text(
                    text = if (mode == ScannerMode.WIFI_SYNC_PAIRING) "Masaüstü QR Eşleşmesi" else "Kamera ile Not Al",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = {
                        val newTorchState = !isTorchOn
                        cameraInstance?.cameraControl?.enableTorch(newTorchState)
                        isTorchOn = newTorchState
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flaş",
                        tint = if (isTorchOn) Color.Yellow else Color.White
                    )
                }
            }

            // Alt Durum ve İlerleme Kartı
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (mode) {
                        ScannerMode.WIFI_SYNC_PAIRING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Masaüstündeki QR Kodu Okutun",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                "Masaüstü uygulamasında Wi-Fi Senkronizasyon penceresinde yer alan QR kodu vizöre ortalayın.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            (cameraError ?: wifiScanError)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        ScannerMode.OPTICAL_STREAM -> {
                            if (importSuccessResult != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Optik Aktarım Tamamlandı!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                                Text(
                                    importSuccessResult?.message ?: "Notlar başarıyla telefona aktarıldı.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Tamam")
                                }
                            } else if (isImporting) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        if (phase == OpticalReceivePhase.DECRYPTING) "Şifre çözülüyor..." else "Notlar içe aktarılıyor...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else if (phase != OpticalReceivePhase.SCANNING) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Tüm kareler alındı. Devam etmek için onay verin.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (opticalProgress.totalChunks > 0)
                                            "Alınan: ${opticalProgress.receivedCount} / ${opticalProgress.totalChunks} kare"
                                        else
                                            "Optik Akış Bekleniyor...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "%${opticalProgress.progressPercent}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { opticalProgress.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                if (opticalProgress.missingChunks.isNotEmpty() && opticalProgress.missingChunks.size <= 8) {
                                    Text(
                                        "Eksik Kareler: ${opticalProgress.missingChunks.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text(
                                        "Kamerayı telefonun veya masaüstünün animasyonlu QR koduna sabit tutun. Kareler sırayla okunur; kaçırılanlar döngüde tekrar gelir.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (errorMessage != null) {
                                    Text(
                                        errorMessage ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (mode == ScannerMode.OPTICAL_STREAM && phase == OpticalReceivePhase.NEED_PASSWORD) {
        AlertDialog(
            onDismissRequest = { restartScanning() },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Aktarım Parolası") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Bu akış şifreli. Gönderen cihazda belirlenen parolayı girin. Notlar henüz içe aktarılmadı.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            passwordError = null
                        },
                        label = { Text("Parola") },
                        singleLine = true,
                        isError = passwordError != null,
                        supportingText = passwordError?.let { { Text(it) } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = passwordInput.isNotEmpty(),
                    onClick = {
                        val envelope = pendingEnvelope ?: return@Button
                        val chars = passwordInput.toCharArray()
                        phase = OpticalReceivePhase.DECRYPTING
                        coroutineScope.launch {
                            val decrypted = opticalTransferManager.decryptEnvelope(envelope, chars)
                            chars.fill('\u0000')
                            decrypted
                                .onSuccess { json ->
                                    opticalTransferManager.summarize(json)
                                        .onSuccess { summary ->
                                            pendingJson = json
                                            pendingSummary = summary
                                            pendingIsLegacyPlain = false
                                            passwordInput = ""
                                            phase = OpticalReceivePhase.CONFIRM
                                        }
                                        .onFailure { err -> restartScanning(err.localizedMessage ?: "Veri okunamadı") }
                                }
                                .onFailure { err ->
                                    passwordError = err.localizedMessage ?: "Şifre çözülemedi"
                                    phase = OpticalReceivePhase.NEED_PASSWORD
                                }
                        }
                    }
                ) { Text("Çöz") }
            },
            dismissButton = {
                TextButton(onClick = { restartScanning() }) { Text("İptal") }
            }
        )
    }

    val summaryToConfirm = pendingSummary
    if (mode == ScannerMode.OPTICAL_STREAM && phase == OpticalReceivePhase.CONFIRM && summaryToConfirm != null) {
        AlertDialog(
            onDismissRequest = { restartScanning() },
            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Notlar İçe Aktarılsın mı?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${summaryToConfirm.notesCount} not, ${summaryToConfirm.categoriesCount} kategori (${formatPayloadSize(summaryToConfirm.payloadBytes)}). " +
                            "Notlar mevcut notlarınızın yanına eklenir; hiçbir not silinmez.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (pendingIsLegacyPlain) {
                        Text(
                            "⚠ Bu akış eski, şifresiz formatta üretilmiş; içeriği ekranı gören herkes okuyabilir. Yalnızca kendi cihazınızdan geldiğinden eminseniz içe aktarın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val json = pendingJson ?: return@Button
                    phase = OpticalReceivePhase.IMPORTING
                    coroutineScope.launch {
                        opticalTransferManager.importAssembledNotes(json)
                            .onSuccess { res ->
                                importSuccessResult = res
                                phase = OpticalReceivePhase.DONE
                                triggerVibration()
                                onOpticalTransferComplete(res)
                            }
                            .onFailure { err ->
                                restartScanning(err.localizedMessage ?: "Aktarım başarısız oldu")
                            }
                    }
                }) { Text("İçe Aktar") }
            },
            dismissButton = {
                TextButton(onClick = { restartScanning() }) { Text("Vazgeç") }
            }
        )
    }
}

internal fun formatPayloadSize(bytes: Int): String =
    if (bytes < 1024) "$bytes B" else "%.1f KB".format(java.util.Locale.US, bytes / 1024.0)

@Composable
fun ScanningReticleOverlay(
    isComplete: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isComplete) Color(0xFF10B981) else Color(0xFF38BDF8),
        label = "reticle_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "laser_anim")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = -120f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Box(
        modifier = modifier
            .size(260.dp)
            .border(2.dp, borderColor, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Lazer Çizgisi Animasyonu
        if (!isComplete) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(2.dp)
                    .offset(y = laserOffset.dp)
                    .background(Color(0xFF38BDF8).copy(alpha = 0.85f))
            )
        }
    }
}
