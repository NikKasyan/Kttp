package kttp.net

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CombinedStreamTest {

    @Test
    fun testCombinedStream() {
        val stream1 = "Hello, ".byteInputStream()
        val stream2 = "World!".byteInputStream()
        val combinedStream = stream1.combineWith(stream2)

        val bytes = combinedStream.readAllBytes()
        val content = bytes.toString(Charsets.US_ASCII)

        assertEquals(content, "Hello, World!")
    }

    @Test
    fun testCombinedStreamWithEmptyStream() {
        val stream1 = "".byteInputStream()
        val stream2 = "World!".byteInputStream()
        val combinedStream = CombinedInputStream(stream1, stream2)

        val bytes = combinedStream.readAllBytes()
        val content = bytes.toString(Charsets.US_ASCII)

        assertEquals(content, "World!")
    }

    @Test
    fun testCombinedStreamWithEmptyStreams() {
        val stream1 = "".byteInputStream()
        val stream2 = "".byteInputStream()
        val combinedStream = CombinedInputStream(stream1, stream2)

        val bytes = combinedStream.readAllBytes()
        val content = bytes.toString(Charsets.US_ASCII)

        assertEquals(content, "")
    }

    @Test
    fun testCombinedStreamWithEmptyStreamAtEnd() {
        val stream1 = "Hello, ".byteInputStream()
        val stream2 = "".byteInputStream()
        val combinedStream = CombinedInputStream(stream1, stream2)

        val bytes = combinedStream.readAllBytes()
        val content = bytes.toString(Charsets.US_ASCII)

        assertEquals(content, "Hello, ")
    }

}