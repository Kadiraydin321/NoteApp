package com.example.noteapp.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.noteapp.biometric.BiometricPromptManager
import com.example.noteapp.biometric.BiometricResult
import com.example.noteapp.biometric.findFragmentActivity

/**
 * Not(lar)ı AES-256 ile şifreli .notevault dosyası olarak dışa aktarmak için parola belirleme diyaloğu.
 */
@Composable
fun ExportVaultDialog(
    noteCount: Int = 1,
    masterPin: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val promptManager = remember(activity) { activity?.let { BiometricPromptManager(it) } }
    val isBiometricAvailable = remember(promptManager) { promptManager?.canAuthenticate() == true }

    var password by remember { mutableStateOf(masterPin ?: "") }
    var confirmPassword by remember { mutableStateOf(masterPin ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun triggerBiometric() {
        promptManager?.showBiometricPrompt(
            title = "Not Kasasını Dışa Aktar",
            description = "Şifreli kasayı dışa aktarmak için parmak izinizi kullanın",
            onResult = { result ->
                if (result is BiometricResult.AuthenticationSuccess) {
                    val finalPassword = password.ifBlank { masterPin ?: "" }
                    if (finalPassword.isNotBlank()) {
                        onConfirm(finalPassword)
                    } else {
                        errorMessage = "Diğer cihazlarda açılabilmesi için lütfen en az 4 haneli bir şifre belirleyin."
                    }
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        if (isBiometricAvailable && !masterPin.isNullOrBlank()) {
            triggerBiometric()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Şifreli Not Kasası (.notevault)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "$noteCount not AES-256-GCM askeri düzeyde şifrelenecek. Belirleyeceğiniz parola olmadan dosya asla açılamaz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isBiometricAvailable) {
                    FilledTonalButton(
                        onClick = { triggerBiometric() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Parmak İzi ile Onayla")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Kasa Şifresi") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "Şifreyi gizle" else "Şifreyi göster"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Şifreyi Tekrar Girin") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        password.isBlank() -> {
                            errorMessage = "Lütfen bir şifre belirleyin veya parmak izinizi kullanın."
                        }
                        password.length < 4 -> {
                            errorMessage = "Şifre en az 4 karakter olmalıdır."
                        }
                        password != confirmPassword -> {
                            errorMessage = "Şifreler birbiriyle eşleşmiyor."
                        }
                        else -> {
                            onConfirm(password)
                        }
                    }
                }
            ) {
                Text("Şifrele ve Dışa Aktar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

/**
 * .notevault dosyasını açmak ve içindeki notları içeri aktarmak için parola sorma diyaloğu.
 */
@Composable
fun ImportVaultDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit,
    errorMessage: String? = null
) {
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Kasayı Aç (.notevault)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Bu şifreli not kasasını açmak için dışa aktarırken belirlediğiniz parolayı girin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Kasa Şifresi") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "Şifreyi gizle" else "Şifreyi göster"
                            )
                        }
                    },
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.isNotBlank()) {
                        onConfirm(password)
                    }
                },
                enabled = password.isNotBlank()
            ) {
                Text("Kasayı Aç")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
