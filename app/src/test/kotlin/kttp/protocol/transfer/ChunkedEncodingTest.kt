package kttp.protocol.transfer

import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.transfer.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

//@Timeout(5, unit = TimeUnit.SECONDS)
class ChunkedEncodingTest {
    private val wikiString = "Wikipedia in\r\n\r\nchunks."
    @Test
    fun testChunkedInputStream() {
        val stream = chunkStringWithChunkSize(wikiString, listOf(
            4,
            5,
            14 ,
        )).byteInputStream()


        val chunkedInputStream = ChunkedInputStream(stream)
        val bytes = chunkedInputStream.readAllBytes()

        assertEquals(wikiString, bytes.toString(Charsets.US_ASCII))

    }

    @Test
    fun chunkedInputStream_shouldSkipHandleExtension() {
        val stream = chunkString(wikiString, listOf(
            4 to "test=1",
            5 to "test=2",
            14 to "test=3",
        )).byteInputStream()

        val chunkedInputStream = ChunkedInputStream(stream)

        val bytes = chunkedInputStream.readAllBytes()

        assertEquals(wikiString, bytes.toString(Charsets.US_ASCII))
    }

    @Test
    fun chunkedInputStream_shouldHandleTrailerHeaders() {
        val stream = chunkStringWithChunkSize(wikiString, listOf(
            4,
            5,
            14,
        ), HttpHeaders("Expires" to "Wed, 21 Oct 2015 07:28:00 GMT")
        ).byteInputStream()

        val httpHeaders = HttpHeaders()
        val chunkedInputStream = ChunkedInputStream(stream, httpHeaders)

        val bytes = chunkedInputStream.readAllBytes()

        assertEquals(wikiString, bytes.toString(Charsets.US_ASCII))
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", httpHeaders["Expires"])
        assertEquals(wikiString.length, httpHeaders.contentLength())
    }

    @Test
    fun chunkedInputStream_shouldHandleMultipleTrailerHeaders() {
        val stream = chunkStringWithChunkSize(wikiString, listOf(
            4,
            5,
            14,
        ),
            HttpHeaders(
                "Expires" to "Wed, 21 Oct 2015 07:28:00 GMT",
                "SHA-256" to "1234567890",
                "Ignore" to "me",
                "Content-Length" to "123"
            )
        ).byteInputStream()

        val httpHeaders = HttpHeaders("Trailers" to "Expires, SHA-256, Content-Length")
        val chunkedInputStream = ChunkedInputStream(stream, httpHeaders)

        val bytes = chunkedInputStream.readAllBytes()

        assertEquals(wikiString, bytes.toString(Charsets.US_ASCII))
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", httpHeaders["Expires"])
        assertEquals("1234567890", httpHeaders["SHA-256"])
        assertEquals("me", httpHeaders["Ignore"])
        assertNotEquals(1234, httpHeaders.contentLength)
    }

    @Test
    fun chunkedInputStream_shouldThrowOnTooBigChunkSize() {
        val stream = """
            4\r
            Wiki\r
            ${(Int.MAX_VALUE.toLong() + 1).toString(16)}\r
            pedia\r
            E\r
             in\r
            \r
            chunks.\r
            0\r
        """.trimIndent().replace("\\r","\r").byteInputStream()

        val chunkedInputStream = ChunkedInputStream(stream)

        assertThrows<Throwable> {
            chunkedInputStream.readAllBytes()
        }

    }

    @Test
    fun chunkedInputStream_shouldThrowOnMismachtingChunkSize() {
        val stream = """
            4\r
            Wiki\r
            5\r
            pedia\r
            F\r
             in\r
            \r
            chunks.\r
            0\r
        """.trimIndent().replace("\\r","\r").byteInputStream()

        val chunkedInputStream = ChunkedInputStream(stream)

        assertThrows<Throwable> {
            chunkedInputStream.readAllBytes()
        }

    }

    @Test
    fun chunkedInputStream_shouldThrowOnTooSmallChunkSize() {
        val stream = """
            4\r
            Wiki\r
            5\r
            pedia\r
            D\r
             in\r
            \r
            chunks.\r
            0\r
        """.trimIndent().replace("\\r","\r").byteInputStream()

        val chunkedInputStream = ChunkedInputStream(stream)

        assertThrows<Throwable> {
            chunkedInputStream.readAllBytes()
        }

    }

    @Test
    fun chunkedInputStream_shouldThrowOnTooLongChunkExtension() {
        val stream = chunkString(wikiString, listOf(
            4 to "test=1",
            5 to "test=2",
            14 to "test=${"3".repeat(3000)}",
        )).byteInputStream()

        val chunkedInputStream = ChunkedInputStream(stream)

        assertThrows<ChunkExtensionTooLong> {  chunkedInputStream.readAllBytes() }

    }

    @Test
    fun chunkedInputStream_shouldHandleLongStreams() {
        val payload = "This is a test".repeat(1000)
        val stream = chunkString(payload).byteInputStream()

        val chunkedInputStream = ChunkedInputStream(stream)

        val bytes = chunkedInputStream.readAllBytes()

        assertEquals(payload.length, bytes.size)
        assertEquals(payload, bytes.toString(Charsets.US_ASCII))

    }

    @Test
    fun chunkedInputStream_shouldHandleLongStreamsWithHeaders() {
        val payload = "This is a test".repeat(1000)
        val stream = chunkString(payload, headers=HttpHeaders().add("Test" to "Test").withAccept("de")).byteInputStream()

        val headers = HttpHeaders()
        val chunkedInputStream = ChunkedInputStream(stream, headers)

        val bytes = chunkedInputStream.readAllBytes()

        assertEquals(payload.length, bytes.size)
        assertEquals(payload, bytes.toString(Charsets.US_ASCII))
        assertEquals("Test", headers["Test"])
        assertEquals("de", headers.accept())


    }

    @Test
    fun chunkedInputStream_shouldHandleLongChunks() {
        val payload = "This is a test".repeat(1000)
        val stream = chunkStringWithChunkSize(payload, listOf(1000)).byteInputStream()

        val chunkedInputStream = ChunkedInputStream(stream)

        val bytes = chunkedInputStream.readAllBytes()

        assertEquals(payload.length, bytes.size)
        assertEquals(payload, bytes.toString(Charsets.US_ASCII))

    }


}