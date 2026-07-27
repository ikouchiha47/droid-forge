package com.forge.skeleton.network

import com.forge.skeleton.network.framing.LengthPrefixFraming
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class LengthPrefixFramingTest {

    private val framing = LengthPrefixFraming()

    @Test
    fun `encode then decode round trips single frame`() = runTest {
        val payload = "framing".toByteArray()
        val frames = framing.decode(flowOf(framing.encode(payload))).toList()
        assertEquals(1, frames.size)
        assertContentEquals(payload, frames[0])
    }

    @Test
    fun `decodes multiple frames from a single stream chunk`() = runTest {
        val a = "one".toByteArray()
        val b = "second".toByteArray()
        val combined = framing.encode(a) + framing.encode(b)
        val frames = framing.decode(flowOf(combined)).toList()
        assertEquals(2, frames.size)
        assertContentEquals(a, frames[0])
        assertContentEquals(b, frames[1])
    }

    @Test
    fun `reassembles a frame split across chunks`() = runTest {
        val payload = "reassemble".toByteArray()
        val encoded = framing.encode(payload)
        val chunk1 = encoded.copyOfRange(0, 6)
        val chunk2 = encoded.copyOfRange(6, encoded.size)
        val frames = framing.decode(flowOf(chunk1, chunk2)).toList()
        assertEquals(1, frames.size)
        assertContentEquals(payload, frames[0])
    }
}
