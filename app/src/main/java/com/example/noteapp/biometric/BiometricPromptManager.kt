package com.example.noteapp.biometric

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

sealed interface BiometricResult {
    data object HardwareUnavailable : BiometricResult
    data object FeatureUnavailable : BiometricResult
    data object NoneEnrolled : BiometricResult
    data object AuthenticationSuccess : BiometricResult
    data object AuthenticationFailed : BiometricResult
    data class AuthenticationError(val error: String, val errorCode: Int = -1) : BiometricResult
    data class AuthenticationLockout(val isPermanent: Boolean, val message: String) : BiometricResult
}

class BiometricPromptManager(
    private val activity: FragmentActivity
) {
    fun canAuthenticate(): Boolean {
        val manager = BiometricManager.from(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            return true
        }
        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceSecure == true
    }

    fun showBiometricPrompt(
        title: String = "Kilitli Not",
        description: String = "Bu nota erişmek için kimliğinizi doğrulayın",
        onResult: (BiometricResult) -> Unit
    ) {
        val manager = BiometricManager.from(activity)
        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = keyguardManager?.isDeviceSecure == true

        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val canAuthResult = manager.canAuthenticate(authenticators)
        val hasDeviceLockFallback = isDeviceSecure

        // Donanımda parmak izi yoksa veya tanımlı değilse, ancak cihaz kilitliyse (PIN/Desen/Şifre)
        // sistem kilit doğrulayıcısına izin ver, hemen hata ile dönme
        if (canAuthResult != BiometricManager.BIOMETRIC_SUCCESS && !hasDeviceLockFallback) {
            when (canAuthResult) {
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                    onResult(BiometricResult.HardwareUnavailable)
                    return
                }
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                    onResult(BiometricResult.FeatureUnavailable)
                    return
                }
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                    onResult(BiometricResult.NoneEnrolled)
                    return
                }
                else -> Unit
            }
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_LOCKOUT -> {
                            onResult(BiometricResult.AuthenticationLockout(isPermanent = false, message = errString.toString()))
                        }
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            onResult(BiometricResult.AuthenticationLockout(isPermanent = true, message = errString.toString()))
                        }
                        else -> {
                            onResult(BiometricResult.AuthenticationError(errString.toString(), errorCode))
                        }
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onResult(BiometricResult.AuthenticationSuccess)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onResult(BiometricResult.AuthenticationFailed)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setDescription(description)
            .setAllowedAuthenticators(authenticators)
            .build()

        try {
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onResult(BiometricResult.AuthenticationError(e.localizedMessage ?: "Kimlik doğrulama başlatılamadı"))
        }
    }
}
