package kttp.websocket

import kttp.io.EndOfStream
import kttp.mock.frameOf
import kttp.mock.framesToBytes
import kttp.mock.ioStreamOf
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WebsocketFrameTest {

    @Test
    fun readingFrameFromEmptyStreamThrowsEndOfStream() {
        assertFailsWith<EndOfStream> { WebsocketFrame.readFrom(ioStreamOf(byteArrayOf())) }
    }

    @Test
    fun frameWithCutOffMaskingKeyThrowsEndOfStream() {
        // Final text frame with a masked payload of 5 bytes, but only 2 of the 4 masking key bytes
        val bytes = byteArrayOf(0x81.toByte(), 0x85.toByte(), 1, 2)
        assertFailsWith<EndOfStream> { WebsocketFrame.readFrom(ioStreamOf(bytes)) }
    }

    @Test
    fun payloadLengthOver32767IsReadAsUnsigned() {
        val payload = ByteArray(40000) { it.toByte() }

        val frame = WebsocketFrame.readFrom(ioStreamOf(framesToBytes(frameOf(WebsocketOpCodes.BINARY, payload))))

        assertEquals(40000L, frame.payload.length)
        assertContentEquals(payload, frame.payload.readAllBytes())
    }

    @Test
    fun payloadLengthOver65535IsWrittenAsEightBytes() {
        val payload = ByteArray(70000) { it.toByte() }

        val bytes = framesToBytes(frameOf(WebsocketOpCodes.BINARY, payload))

        // 127 announces a 64-bit length, 70000 is 0x11170
        assertContentEquals(byteArrayOf(127, 0, 0, 0, 0, 0, 1, 0x11, 0x70), bytes.copyOfRange(1, 10))
        val frame = WebsocketFrame.readFrom(ioStreamOf(bytes))
        assertEquals(70000L, frame.payload.length)
        assertContentEquals(payload, frame.payload.readAllBytes())
    }

    @Test
    fun payloadLengthWithMostSignificantBitSetIsInvalid() {
        val bytes = byteArrayOf(0x82.toByte(), 127, 0x80.toByte(), 0, 0, 0, 0, 0, 0, 1)
        assertFailsWith<IllegalArgumentException> { WebsocketFrame.readFrom(ioStreamOf(bytes)) }
    }
}
