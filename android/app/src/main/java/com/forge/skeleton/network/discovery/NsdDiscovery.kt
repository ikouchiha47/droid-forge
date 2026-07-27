package com.forge.skeleton.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.forge.skeleton.network.interfaces.IDiscovery
import com.forge.skeleton.network.model.PeerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class NsdDiscovery(
    private val context: Context,
    private val serviceType: String,
    private val port: Int = 0,
) : IDiscovery {

    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private val _peers = MutableSharedFlow<PeerInfo>(replay = 16)
    override val peers: Flow<PeerInfo> = _peers.asSharedFlow()

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onDiscoveryStarted(serviceType: String) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceResolved(info: NsdServiceInfo) {
                    _peers.tryEmit(PeerInfo(
                        id = info.serviceName,
                        host = info.host.hostAddress ?: return,
                        port = info.port,
                    ))
                }
            })
        }
        override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        override fun onServiceRegistered(info: NsdServiceInfo) {}
        override fun onServiceUnregistered(info: NsdServiceInfo) {}
    }

    override fun start() {
        if (port > 0) {
            val info = NsdServiceInfo().apply {
                serviceName = "forge-${android.os.Build.MODEL}"
                serviceType = this@NsdDiscovery.serviceType
                port = this@NsdDiscovery.port
            }
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    override fun stop() {
        runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        if (port > 0) runCatching { nsdManager.unregisterService(registrationListener) }
    }
}
