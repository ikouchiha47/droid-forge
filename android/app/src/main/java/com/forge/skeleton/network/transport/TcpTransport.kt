package com.forge.skeleton.network.transport

import com.forge.skeleton.network.interfaces.ITransport
import com.forge.skeleton.network.model.PeerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class TcpTransport : ITransport {

    private val _incoming = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
    override val incoming: Flow<ByteArray> = _incoming.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var socket: Socket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readJob: Job? = null

    override suspend fun connect(peer: PeerInfo) = withContext(Dispatchers.IO) {
        val s = Socket()
        s.connect(InetSocketAddress(peer.host, peer.port))
        socket = s
        _isConnected.value = true
        readJob = scope.launch {
            val input = s.getInputStream()
            val buffer = ByteArray(8192)
            while (isActive) {
                val read = runCatching { input.read(buffer) }.getOrDefault(-1)
                if (read <= 0) break
                _incoming.emit(buffer.copyOf(read))
            }
            _isConnected.value = false
        }
    }

    override suspend fun send(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val out = socket?.getOutputStream() ?: error("Not connected")
        out.write(bytes)
        out.flush()
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        readJob?.cancel()
        readJob = null
        runCatching { socket?.close() }
        socket = null
        _isConnected.value = false
    }
}
