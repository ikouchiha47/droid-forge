package com.forge.skeleton.network.model

data class PeerInfo(
    val id: String,
    val host: String,
    val port: Int,
    val publicKey: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeerInfo) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}
