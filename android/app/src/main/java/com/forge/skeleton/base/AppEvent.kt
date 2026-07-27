package com.forge.skeleton.base

import com.forge.skeleton.network.model.PeerInfo

sealed class AppEvent {
    data class PeerConnected(val peer: PeerInfo) : AppEvent()
    data class PeerDisconnected(val peerId: String) : AppEvent()
    data class MessageReceived(val from: String, val payload: ByteArray) : AppEvent()
    data class ErrorOccurred(val tag: String, val cause: Throwable) : AppEvent()
}
