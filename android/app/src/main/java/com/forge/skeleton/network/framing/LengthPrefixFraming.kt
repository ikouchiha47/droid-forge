package com.forge.skeleton.network.framing

import com.forge.skeleton.network.interfaces.IFraming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream

class LengthPrefixFraming : IFraming {

    override fun encode(payload: ByteArray): ByteArray {
        val length = payload.size
        val out = ByteArray(4 + length)
        out[0] = (length ushr 24 and 0xFF).toByte()
        out[1] = (length ushr 16 and 0xFF).toByte()
        out[2] = (length ushr 8 and 0xFF).toByte()
        out[3] = (length and 0xFF).toByte()
        System.arraycopy(payload, 0, out, 4, length)
        return out
    }

    override fun decode(stream: Flow<ByteArray>): Flow<ByteArray> = flow {
        val buffer = ByteArrayOutputStream()
        stream.collect { chunk ->
            buffer.write(chunk)
            var data = buffer.toByteArray()
            var offset = 0
            while (data.size - offset >= 4) {
                val length = ((data[offset].toInt() and 0xFF) shl 24) or
                    ((data[offset + 1].toInt() and 0xFF) shl 16) or
                    ((data[offset + 2].toInt() and 0xFF) shl 8) or
                    (data[offset + 3].toInt() and 0xFF)
                if (data.size - offset - 4 < length) break
                val frameStart = offset + 4
                emit(data.copyOfRange(frameStart, frameStart + length))
                offset = frameStart + length
            }
            buffer.reset()
            if (offset < data.size) buffer.write(data, offset, data.size - offset)
        }
    }
}
