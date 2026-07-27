package com.forge.skeleton.network.discovery

import com.forge.skeleton.network.interfaces.IDiscovery
import com.forge.skeleton.network.model.PeerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class NostrDiscovery(
    private val relayUrl: String,
    private val topic: String,
    private val client: OkHttpClient = OkHttpClient(),
) : IDiscovery {

    private val subId = UUID.randomUUID().toString()
    private val _peers = MutableSharedFlow<PeerInfo>(replay = 16)
    override val peers: Flow<PeerInfo> = _peers.asSharedFlow()

    private var webSocket: WebSocket? = null

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val filter = JSONObject()
                .put("kinds", JSONArray().put(30000))
                .put("#t", JSONArray().put(topic))
            val req = JSONArray().put("REQ").put(subId).put(filter)
            webSocket.send(req.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = runCatching { JSONArray(text) }.getOrNull() ?: return
            if (msg.length() < 3 || msg.optString(0) != "EVENT") return
            if (msg.optString(1) != subId) return
            val event = msg.optJSONObject(2) ?: return
            val content = runCatching { JSONObject(event.optString("content")) }.getOrNull() ?: return
            val id = content.optString("id").ifEmpty { return }
            val host = content.optString("host").ifEmpty { return }
            val port = content.optInt("port", -1).takeIf { it >= 0 } ?: return
            _peers.tryEmit(PeerInfo(id = id, host = host, port = port))
        }
    }

    override fun start() {
        val request = Request.Builder().url(relayUrl).build()
        webSocket = client.newWebSocket(request, listener)
    }

    override fun stop() {
        webSocket?.send(JSONArray().put("CLOSE").put(subId).toString())
        webSocket?.close(1000, null)
        webSocket = null
    }
}
