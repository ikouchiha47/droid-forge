package com.forge.skeleton.network.interfaces

import com.forge.skeleton.network.model.PeerInfo
import kotlinx.coroutines.flow.Flow

interface IDiscovery {
    fun start()
    fun stop()
    val peers: Flow<PeerInfo>
}
