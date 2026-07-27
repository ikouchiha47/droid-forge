// Add io.getstream:webrtc-android to build.gradle before using this transport
package com.forge.skeleton.network.transport

import com.forge.skeleton.network.interfaces.ITransport
import com.forge.skeleton.network.model.PeerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class WebRtcTransport : ITransport {

    private val _incoming = MutableSharedFlow<ByteArray>()
    override val incoming: Flow<ByteArray> = _incoming.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    override suspend fun connect(peer: PeerInfo) {
        throw IllegalStateException("Add io.getstream:webrtc-android to build.gradle")
    }

    override suspend fun send(bytes: ByteArray) {
        throw IllegalStateException("Add io.getstream:webrtc-android to build.gradle")
    }

    override suspend fun disconnect() {
        _isConnected.value = false
    }
}
