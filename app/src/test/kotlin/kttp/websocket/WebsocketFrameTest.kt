package kttp.websocket

import kttp.io.EndOfStream
import kttp.mock.ioStreamOf
import org.junit.jupiter.api.Test
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
}
