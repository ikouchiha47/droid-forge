package com.forge.skeleton.network.transport

import com.forge.skeleton.network.interfaces.ITransport
import com.forge.skeleton.network.model.PeerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class WebSocketTransport(
    private val client: OkHttpClient = OkHttpClient(),
) : ITransport {

    private val _incoming = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
    override val incoming: Flow<ByteArray> = _incoming.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var webSocket: WebSocket? = null

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _isConnected.value = true
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            _incoming.tryEmit(bytes.toByteArray())
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _isConnected.value = false
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _isConnected.value = false
        }
    }

    override suspend fun connect(peer: PeerInfo) {
        val request = Request.Builder().url("ws://${peer.host}:${peer.port}").build()
        webSocket = client.newWebSocket(request, listener)
    }

    override suspend fun send(bytes: ByteArray) {
        val ws = webSocket ?: error("Not connected")
        ws.send(ByteString.of(*bytes))
    }

    override suspend fun disconnect() {
        webSocket?.close(1000, null)
        webSocket = null
        _isConnected.value = false
    }
}
