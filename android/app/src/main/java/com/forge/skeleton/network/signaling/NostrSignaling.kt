package com.forge.skeleton.network.signaling

import com.forge.skeleton.network.interfaces.ISignaling
import com.forge.skeleton.network.model.SignalEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class NostrSignaling(
    private val relayUrl: String,
    private val selfPubKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) : ISignaling {

    private val subId = UUID.randomUUID().toString()
    private val _incoming = MutableSharedFlow<SignalEnvelope>(replay = 32)
    private var webSocket: WebSocket? = null

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val filter = JSONObject()
                .put("kinds", JSONArray().put(25000))
                .put("#p", JSONArray().put(selfPubKey))
            val req = JSONArray().put("REQ").put(subId).put(filter)
            webSocket.send(req.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = runCatching { JSONArray(text) }.getOrNull() ?: return
            if (msg.length() < 3 || msg.optString(0) != "EVENT") return
            if (msg.optString(1) != subId) return
            val event = msg.optJSONObject(2) ?: return
            val body = runCatching { JSONObject(event.optString("content")) }.getOrNull() ?: return
            _incoming.tryEmit(
                SignalEnvelope(
                    from = body.optString("from"),
                    to = body.optString("to"),
                    type = body.optString("type"),
                    payload = body.optString("payload"),
                )
            )
        }
    }

    private fun ensureConnected() {
        if (webSocket != null) return
        val request = Request.Builder().url(relayUrl).build()
        webSocket = client.newWebSocket(request, listener)
    }

    override suspend fun publish(peerId: String, offer: SignalEnvelope) {
        ensureConnected()
        val content = JSONObject()
            .put("from", offer.from)
            .put("to", offer.to)
            .put("type", offer.type)
            .put("payload", offer.payload)
        val event = JSONObject()
            .put("kind", 25000)
            .put("content", content.toString())
            .put("tags", JSONArray().put(JSONArray().put("p").put(peerId)))
        val req = JSONArray().put("EVENT").put(event)
        webSocket?.send(req.toString())
    }

    override fun incoming(peerId: String): Flow<SignalEnvelope> {
        ensureConnected()
        return _incoming.filter { it.from == peerId }
    }

    override suspend fun close() {
        webSocket?.send(JSONArray().put("CLOSE").put(subId).toString())
        webSocket?.close(1000, null)
        webSocket = null
    }
}
