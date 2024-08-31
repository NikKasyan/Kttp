package kttp.protocol.transfer

import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.transfer.*
import kttp.mock.RepeatableInputStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

//@Timeout(5, unit = TimeUnit.SECONDS)
class ChunkedInputstreamTest {
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

    @Test
    fun chunkedInputStream_withDifferentLengths_shouldBeUnchunkedCorrectly() {

        val stream = RepeatableInputStream("Wiki\r\npedia in\r\n\r\nchunks.".toByteArray(), 0L)
        var current = 0
        try {
            for(bufferSize in bufferSizes) {
                for (i in 1..10000) {
                    current = i
                    stream.length = i.toLong()
                    stream.reset()
                    val chunkings = createChunkings(i)
                    val input = chunkString(stream.readAllBytes().toString(Charsets.US_ASCII), chunkings)
                    val headers = HttpHeaders()
                    val chunkedInputStream = ChunkedInputStream(input.byteInputStream(),headers )

                    stream.reset()
                    val expectedValue = stream.readAllBytes()
                    val actualValue = readAll(chunkedInputStream, 8)
                    assertEquals(expectedValue.size, actualValue.size, "Failed at $i with buffer size $bufferSize")
                    assertContentEquals(expectedValue, actualValue, "Failed at $i with buffer size $bufferSize")
                    assertEquals(expectedValue.toString(Charsets.US_ASCII), actualValue.toString(Charsets.US_ASCII), "Failed at $i with buffer size $bufferSize")
                    assertEquals(i.toLong(), headers.contentLengthLong())
                }
            }
        } catch (e: Exception) {
            println("Failed at $current")
            throw e
        }
    }

    @Test
    fun invalidChunkSize_shouldThrow() {
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

        assertThrows<InvalidChunkSize> {
            chunkedInputStream.readAllBytes()
        }

    }

    @Test
    fun invaliTrailer_shouldThrow() {
        val stream = """
            4\r
            Wiki\r
            5\r
            pedia\r
            E\r
             in\r
            \r
            chunks.\r
            0\r
            Expires Wed, 21 Oc\r
            """.trimIndent().replace("\\r","\r").byteInputStream()

        val headers = HttpHeaders()
        val chunkedInputStream = ChunkedInputStream(stream, headers)

        assertThrows<InvalidTrailer> {
            chunkedInputStream.readAllBytes()
        }
    }

    @Test
    fun emptyStream_shouldReturnEmpty() {
        val stream = "".byteInputStream()

        val chunkedInputStream = ChunkedInputStream(stream)

        val bytes = chunkedInputStream.readAllBytes()

        assertEquals("", bytes.toString(Charsets.US_ASCII))

    }

}