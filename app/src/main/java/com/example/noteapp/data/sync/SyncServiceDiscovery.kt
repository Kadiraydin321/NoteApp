package com.example.noteapp.data.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredSyncServer(
    val name: String,
    val host: String,
    val port: Int
) {
    val endpoint: String
        get() {
            val urlHost = if (host.contains(':')) "[$host]" else host
            return "http://$urlHost:$port"
        }
}

@Singleton
class SyncServiceDiscovery @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun discover(): Flow<DiscoveredSyncServer> = callbackFlow {
        val nsdManager = context.getSystemService<NsdManager>()
        if (nsdManager == null) {
            close(IllegalStateException("Android yerel ağ keşfi kullanılamıyor."))
            return@callbackFlow
        }

        val resolving = mutableSetOf<String>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.trimEnd('.').equals(SERVICE_TYPE.trimEnd('.'), ignoreCase = true)) {
                    return
                }
                if (!resolving.add(serviceInfo.serviceName)) return
                try {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            resolving.remove(serviceInfo.serviceName)
                        }

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            resolving.remove(resolved.serviceName)
                            val host = resolved.host?.hostAddress ?: return
                            if (SyncHostValidator.isAllowedHost(host) &&
                                SyncHostValidator.isValidPort(resolved.port)
                            ) {
                                trySend(
                                    DiscoveredSyncServer(
                                        name = resolved.serviceName,
                                        host = host,
                                        port = resolved.port
                                    )
                                )
                            }
                        }
                    })
                } catch (_: IllegalArgumentException) {
                    resolving.remove(serviceInfo.serviceName)
                } catch (_: SecurityException) {
                    resolving.remove(serviceInfo.serviceName)
                    close(SecurityException("Yerel ağ keşfi için Android izni gerekiyor."))
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                resolving.remove(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsdManager.stopServiceDiscovery(this) }
                close(IllegalStateException("Masaüstü araması başlatılamadı (hata $errorCode)."))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsdManager.stopServiceDiscovery(this) }
                close(IllegalStateException("Masaüstü araması durdurulamadı (hata $errorCode)."))
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: SecurityException) {
            close(SecurityException("Yerel ağ keşfi için Android izni gerekiyor."))
        } catch (error: IllegalArgumentException) {
            close(error)
        }

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_notdefterim-sync._tcp."
    }
}
