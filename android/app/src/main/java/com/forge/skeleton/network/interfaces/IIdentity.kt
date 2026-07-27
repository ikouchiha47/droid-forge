package com.forge.skeleton.network.interfaces

interface IIdentity {
    fun publicKey(): ByteArray
    fun sign(data: ByteArray): ByteArray
    fun verify(data: ByteArray, sig: ByteArray, theirPublicKey: ByteArray): Boolean
}
