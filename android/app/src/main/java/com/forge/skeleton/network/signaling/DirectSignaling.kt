package com.forge.skeleton.network.signaling

import com.forge.skeleton.network.interfaces.ISignaling
import com.forge.skeleton.network.model.SignalEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter

class DirectSignaling : ISignaling {

    private val bus = MutableSharedFlow<SignalEnvelope>(replay = 64)

    override suspend fun publish(peerId: String, offer: SignalEnvelope) {
        bus.emit(offer)
    }

    override fun incoming(peerId: String): Flow<SignalEnvelope> =
        bus.filter { it.to == peerId }

    override suspend fun close() {}
}
