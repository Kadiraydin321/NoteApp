package com.example.noteapp.desktop.presentation.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.noteapp.desktop.data.security.DesktopCryptoManager
import com.example.noteapp.desktop.data.security.DesktopSecurityManager
import com.example.noteapp.desktop.data.sync.DesktopSyncServer
import com.example.noteapp.desktop.data.sync.SyncServerState
import com.example.noteapp.desktop.domain.model.Category
import com.example.noteapp.desktop.presentation.components.QRCodeView
import com.example.noteapp.desktop.presentation.theme.NotePresets
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun VaultPasswordDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val hasMaster = DesktopCryptoManager.hasMasterPassword()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(if (hasMaster) "Kasa Kilidini Aç" else "Master Kasa Şifresi Oluştur")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (hasMaster)
                        "Kilitli notları görüntülemek ve düzenlemek için kasa şifrenizi girin."
                    else
                        "Kilitli notlarınızı askeri düzeyde (AES-256-GCM) şifrelemek için bir ana şifre belirleyin.",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Şifre") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (!hasMaster) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMessage = null },
                        label = { Text("Şifreyi Tekrar Girin") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (hasMaster) {
                        if (DesktopCryptoManager.unlockVault(password)) {
                            onSuccess()
                            onDismiss()
                        } else {
                            errorMessage = "Hatalı şifre!"
                        }
                    } else {
                        if (password.length < 4) {
                            errorMessage = "Şifre en az 4 karakter olmalıdır."
                        } else if (password != confirmPassword) {
                            errorMessage = "Şifreler uyuşmuyor!"
                        } else {
                            if (DesktopCryptoManager.setupMasterPassword(password)) {
                                onSuccess()
                                onDismiss()
                            } else {
                                errorMessage = "Şifre oluşturulamadı."
                            }
                        }
                    }
                }
            ) {
                Text(if (hasMaster) "Kilidi Aç" else "Şifreyi Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

@Composable
fun CategoryManageDialog(
    categories: List<Category>,
    onAddCategory: (String, Int) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var newCatName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Kategorileri Yönet")
        },
        text = {
            Column(
                modifier = Modifier.width(420.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Yeni Kategori Ekleme Alanı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newCatName,
                        onValueChange = { newCatName = it },
                        label = { Text("Yeni Kategori") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newCatName.isNotBlank()) {
                                onAddCategory(newCatName.trim(), selectedColor)
                                newCatName = ""
                            }
                        },
                        enabled = newCatName.isNotBlank()
                    ) {
                        Text("Ekle")
                    }
                }

                // Renk Seçenekleri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NotePresets.take(7).forEach { (colorInt, _) ->
                        val color = if (colorInt != 0) Color(colorInt) else Color(0xFF64748B)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = colorInt }
                                .border(
                                    width = if (selectedColor == colorInt) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                HorizontalDivider()

                // Mevcut Kategoriler Listesi
                Text("Mevcut Kategoriler (${categories.size}):", style = MaterialTheme.typography.titleSmall)

                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories) { cat ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (cat.color != 0) Color(cat.color) else Color.Gray)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDeleteCategory(cat.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Sil",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Tamam")
            }
        }
    )
}

@Composable
fun SyncDialog(
    serverState: SyncServerState,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onRefreshPin: () -> Unit,
    onDismiss: () -> Unit
) {
    val syncUrl = "http://${serverState.localIp}:${serverState.port}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Wi-Fi ile Telefon Senkronizasyonu")
            }
        },
        text = {
            Column(
                modifier = Modifier.width(440.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (serverState.isRunning) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        QRCodeView(content = syncUrl, size = 180)
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Eşleşme PIN Kodu",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = serverState.pairingPin,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 4.sp
                            )
                            Text(
                                text = syncUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "1. Telefonunuzun kamerasıyla QR kodu taratın veya tarayıcınızda yukarıdaki adresi açın.\n" +
                               "2. Web sayfasından 'Notları Telefona İndir' veya 'Bilgisayara Yükle' butonunu kullanarak saniyeler içinde eşitleyin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (serverState.lastSyncMessage != null) {
                        Text(
                            text = serverState.lastSyncMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    Text(
                        text = "Wi-Fi senkronizasyon sunucusu şu an kapalı. Telefonunuzla kablosuz not alışverişi yapmak için sunucuyu başlatın.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (serverState.isRunning) {
                    OutlinedButton(onClick = onRefreshPin) {
                        Text("Yeni PIN")
                    }
                    Button(onClick = onStopServer, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Sunucuyu Durdur")
                    }
                } else {
                    Button(onClick = onStartServer) {
                        Text("Sunucuyu Başlat")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}

@Composable
fun SecuritySettingsDialog(
    onDismiss: () -> Unit
) {
    var isVaultSet by remember { mutableStateOf(DesktopCryptoManager.hasMasterPassword()) }
    val isAntiScreenshot = DesktopSecurityManager.isScreenshotProtectionActive

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Güvenlik ve Gizlilik")
            }
        },
        text = {
            Column(
                modifier = Modifier.width(420.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Ekran Görüntüsü Koruması
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (isAntiScreenshot) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Anti-Screenshot Koruması",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isAntiScreenshot)
                                "Aktif: Windows Win32 SetWindowDisplayAffinity devrede. Snipping Tool, OBS, PrintScreen ve ekran kayıtları bu pencereyi simsiyah yakalar."
                            else
                                "Yerel sistem ekran koruması etkin değil (Linux veya desteklenmeyen ortamda Gizlilik Perdesi ve Kasa Kilidi devrededir).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 2. Kasa Kilidi Durumu
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Kasa Şifreleme (AES-256-GCM)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isVaultSet)
                                "Master şifre tanımlı. Kilitli notlar veritabanında AES-256-GCM ile şifrelenir."
                            else
                                "Henüz master kasa şifresi tanımlanmadı.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(8.dp))
                        if (DesktopCryptoManager.isVaultUnlocked) {
                            Button(
                                onClick = {
                                    DesktopCryptoManager.lockVault()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Kasayı Şimdi Kilitle")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}
