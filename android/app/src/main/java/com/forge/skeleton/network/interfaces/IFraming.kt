package com.forge.skeleton.network.interfaces

import kotlinx.coroutines.flow.Flow

interface IFraming {
    fun encode(payload: ByteArray): ByteArray
    fun decode(stream: Flow<ByteArray>): Flow<ByteArray>
}
