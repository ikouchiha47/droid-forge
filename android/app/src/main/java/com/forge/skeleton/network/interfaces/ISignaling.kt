package com.forge.skeleton.network.interfaces

import com.forge.skeleton.network.model.SignalEnvelope
import kotlinx.coroutines.flow.Flow

interface ISignaling {
    suspend fun publish(peerId: String, offer: SignalEnvelope)
    fun incoming(peerId: String): Flow<SignalEnvelope>
    suspend fun close()
}
