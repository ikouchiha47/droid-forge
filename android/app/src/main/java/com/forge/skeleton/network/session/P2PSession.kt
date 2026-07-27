package com.forge.skeleton.network.session

import com.forge.skeleton.network.interfaces.ICipher
import com.forge.skeleton.network.interfaces.IDiscovery
import com.forge.skeleton.network.interfaces.IFraming
import com.forge.skeleton.network.interfaces.ISignaling
import com.forge.skeleton.network.interfaces.ITransport
import com.forge.skeleton.network.model.PeerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class P2PSession(
    private val discovery: IDiscovery,
    private val signaling: ISignaling,
    private val transport: ITransport,
    private val framing: IFraming,
    private val cipher: ICipher,
) {
    val messages: Flow<ByteArray> =
        framing.decode(transport.incoming).map { cipher.decrypt(it) }

    suspend fun send(payload: ByteArray) {
        transport.send(framing.encode(cipher.encrypt(payload)))
    }

    fun startDiscovery() = discovery.start()

    suspend fun connectTo(peer: PeerInfo) = transport.connect(peer)

    suspend fun close() {
        discovery.stop()
        transport.disconnect()
        signaling.close()
    }
}
