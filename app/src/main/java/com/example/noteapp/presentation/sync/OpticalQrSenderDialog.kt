package com.example.noteapp.presentation.sync

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.noteapp.data.sync.OpticalFormat
import com.example.noteapp.data.sync.OpticalTransferManager
import com.example.noteapp.data.sync.OpticalTransferPackage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class FrameSize(val label: String, val chunkChars: Int) {
    SMALL("Küçük", 250),
    MEDIUM("Orta", 500),
    LARGE("Büyük", 900)
}

private enum class FrameSpeed(val label: String, val fps: Int) {
    SLOW("Yavaş", 2),
    NORMAL("Normal", 4),
    FAST("Hızlı", 7)
}

/**
 * Notları parola ile şifreleyip kare kare dönen (animasyonlu) QR akışı olarak gösterir.
 * Alıcı cihaz "Kamera ile Not Al" ile okur; ağ veya kablo gerekmez.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpticalQrSenderDialog(
    opticalTransferManager: OpticalTransferManager,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var passwordRepeat by remember { mutableStateOf("") }
    var isPreparing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var transfer by remember { mutableStateOf<OpticalTransferPackage?>(null) }

    var frameSize by remember { mutableStateOf(FrameSize.MEDIUM) }
    var frameSpeed by remember { mutableStateOf(FrameSpeed.NORMAL) }
    var isPlaying by remember { mutableStateOf(true) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val activeTransfer = transfer
    val frames = remember(activeTransfer, frameSize) {
        activeTransfer?.frames(frameSize.chunkChars).orEmpty()
    }

    // Boyut değiştiğinde kare dizisi yeniden bölündüğü için başa dön
    LaunchedEffect(frames) { frameIndex = 0 }

    // Kareleri döngüyle ilerlet
    LaunchedEffect(frames, isPlaying, frameSpeed) {
        if (frames.size > 1 && isPlaying) {
            while (isActive) {
                delay(1000L / frameSpeed.fps)
                frameIndex = (frameIndex + 1) % frames.size
            }
        }
    }

    LaunchedEffect(frames, frameIndex) {
        val frame = frames.getOrNull(frameIndex) ?: return@LaunchedEffect
        qrBitmap = withContext(Dispatchers.Default) { renderOpticalQr(frame) }
    }

    // QR akışı gösterilirken ekran kapanmasın
    val view = LocalView.current
    DisposableEffect(activeTransfer != null) {
        val previous = view.keepScreenOn
        if (activeTransfer != null) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Animasyonlu QR ile Gönder",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                if (activeTransfer == null) {
                    Text(
                        "Notlarınız parolayla şifrelenir (AES-256-GCM) ve kare kare dönen QR akışı olarak gösterilir. " +
                            "Diğer cihazda 'Kamera ile Not Al' seçeneğini açıp kamerayı bu ekrana tutun, ardından aynı parolayı girin. " +
                            "İnternet veya kablo gerekmez. Kilitli notlar aktarıma dahil edilmez.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Aktarım parolası (en az ${OpticalFormat.MIN_PASSWORD_LENGTH} karakter)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = passwordRepeat,
                        onValueChange = { passwordRepeat = it; errorMessage = null },
                        label = { Text("Parolayı tekrar girin") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            when {
                                password.length < OpticalFormat.MIN_PASSWORD_LENGTH ->
                                    errorMessage = "Parola en az ${OpticalFormat.MIN_PASSWORD_LENGTH} karakter olmalıdır."
                                password != passwordRepeat ->
                                    errorMessage = "Parolalar eşleşmiyor."
                                else -> {
                                    val chars = password.toCharArray()
                                    isPreparing = true
                                    scope.launch {
                                        val result = opticalTransferManager.prepareTransfer(chars)
                                        chars.fill('\u0000')
                                        isPreparing = false
                                        result
                                            .onSuccess {
                                                password = ""
                                                passwordRepeat = ""
                                                transfer = it
                                            }
                                            .onFailure { errorMessage = it.localizedMessage ?: "Hazırlanamadı" }
                                    }
                                }
                            }
                        },
                        enabled = !isPreparing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (isPreparing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Şifreleniyor...")
                        } else {
                            Text("Şifrele ve QR Akışını Başlat", fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Text(
                        "${activeTransfer.noteCount} not, ${activeTransfer.categoryCount} kategori şifreli olarak yayınlanıyor " +
                            "(${formatPayloadSize(activeTransfer.payloadBytes)}).",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (activeTransfer.skippedLockedCount > 0) {
                        Text(
                            "${activeTransfer.skippedLockedCount} kilitli not güvenlik nedeniyle aktarıma dahil edilmedi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val bitmap = qrBitmap
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Animasyonlu QR karesi ${frameIndex + 1} / ${frames.size}",
                                filterQuality = FilterQuality.None,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Kare ${frameIndex + 1} / ${frames.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${frameSpeed.fps} kare/sn · döngü",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (frames.isEmpty()) 0f else (frameIndex + 1f) / frames.size },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            isPlaying = false
                            if (frames.isNotEmpty()) frameIndex = (frameIndex - 1 + frames.size) % frames.size
                        }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Önceki kare")
                        }
                        FilledIconButton(onClick = { isPlaying = !isPlaying }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Duraklat" else "Oynat"
                            )
                        }
                        IconButton(onClick = {
                            isPlaying = false
                            if (frames.isNotEmpty()) frameIndex = (frameIndex + 1) % frames.size
                        }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Sonraki kare")
                        }
                    }

                    Text("Kare boyutu (küçük = daha kolay okunur, daha çok kare)", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FrameSize.entries.forEach { size ->
                            FilterChip(
                                selected = frameSize == size,
                                onClick = { frameSize = size },
                                label = { Text(size.label) }
                            )
                        }
                    }
                    Text("Oynatma hızı (okuyamazsa yavaşlatın)", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FrameSpeed.entries.forEach { speed ->
                            FilterChip(
                                selected = frameSpeed == speed,
                                onClick = { frameSpeed = speed },
                                label = { Text("${speed.label} (${speed.fps}/sn)") }
                            )
                        }
                    }

                    Text(
                        "Akış sonsuz döner; alıcı kaçırdığı kareleri sonraki turda alır. Parola QR akışında yer almaz; alıcıya ayrıca iletin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start
                    )

                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Gönderimi Bitir")
                    }
                }
            }
        }
    }
}

internal fun renderOpticalQr(contents: String, sizePx: Int = 720): Bitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to 2
    )
    val matrix = MultiFormatWriter().encode(contents, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        val x = index % matrix.width
        val y = index / matrix.width
        if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    }
}.getOrNull()
