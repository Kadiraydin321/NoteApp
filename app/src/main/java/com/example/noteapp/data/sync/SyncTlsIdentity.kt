package com.example.noteapp.data.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import javax.inject.Inject
import javax.inject.Singleton
import javax.security.auth.x500.X500Principal

data class SyncTlsIdentityInfo(
    val certificateFingerprint: String,
    val serverSocketFactory: SSLServerSocketFactory
)

@Singleton
class SyncTlsIdentity @Inject constructor() {
    @Synchronized
    fun getOrCreate(): SyncTlsIdentityInfo {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKeyPair()
        }

        val certificate = keyStore.getCertificate(KEY_ALIAS) as? X509Certificate
            ?: throw KeyStoreException("Sync TLS certificate is unavailable")
        certificate.checkValidity()

        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, null)
        }.keyManagers
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(keyManagers, null, SecureRandom())
        }
        return SyncTlsIdentityInfo(
            certificateFingerprint = certificate.encoded.sha256Hex(),
            serverSocketFactory = sslContext.serverSocketFactory
        )
    }

    private fun generateKeyPair() {
        val now = System.currentTimeMillis()
        val serialNumber = ByteArray(16).also(SecureRandom()::nextBytes)
            .let { BigInteger(1, it) }
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEY_STORE
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setCertificateSubject(X500Principal("CN=Not Defterim Local Sync"))
                .setCertificateSerialNumber(serialNumber)
                .setCertificateNotBefore(Date(now - CERTIFICATE_CLOCK_SKEW_MS))
                .setCertificateNotAfter(Date(now + CERTIFICATE_VALIDITY_MS))
                .setUserAuthenticationRequired(false)
                .build()
        )
        generator.generateKeyPair()
    }

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "notdefterim.sync.tls.v1"
        const val CERTIFICATE_CLOCK_SKEW_MS = 60_000L
        const val CERTIFICATE_VALIDITY_MS = 10L * 365 * 24 * 60 * 60 * 1000
    }
}
