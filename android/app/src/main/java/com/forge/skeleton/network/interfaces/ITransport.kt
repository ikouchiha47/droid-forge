package com.forge.skeleton.network.interfaces

import com.forge.skeleton.network.model.PeerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ITransport {
    suspend fun connect(peer: PeerInfo)
    suspend fun disconnect()
    suspend fun send(bytes: ByteArray)
    val incoming: Flow<ByteArray>
    val isConnected: StateFlow<Boolean>
}
