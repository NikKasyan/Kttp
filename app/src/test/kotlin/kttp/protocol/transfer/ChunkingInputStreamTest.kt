package kttp.protocol.transfer

import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.transfer.ChunkingInputStream
import kttp.http.protocol.transfer.chunkString
import kttp.mock.RepeatableInputStream
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import kotlin.test.assertEquals

class ChunkingInputStreamTest {

    @Test
    fun chunkingInputStream_shouldChunkData() {
        val wikiString = "Wikipedia in\r\n\r\nchunks."

        val chunks = listOf(
            4 to "",
            5 to "",
            14 to ""
        )
        val chunkedString = chunkString(wikiString, chunks)
        val stream = ChunkingInputStream(wikiString.byteInputStream(), chunkings = chunks)

        val string = stream.readAllBytes().toString(Charset.defaultCharset())

        assertEquals(chunkedString, string)

    }

    @Test
    fun chunkingInputStream_shouldHandleExtension() {
        val wikiString = "Wikipedia in\r\n\r\nchunks."

        val chunks = listOf(
            4 to "test=1",
            5 to "test=2",
            14 to "test=3"
        )
        val chunkedString = chunkString(wikiString, chunks)
        val stream = ChunkingInputStream(wikiString.byteInputStream(), HttpHeaders(), chunks)

        val string = stream.readAllBytes().toString(Charset.defaultCharset())

        assertEquals(chunkedString, string)
    }

    @Test
    fun chunkingInputStream_shouldHandleTrailerHeaders() {
        val wikiString = "Wikipedia in\r\n\r\nchunks."

        val chunks = listOf(
            4 to "",
            5 to "",
            14 to ""
        )
        val chunkedString =
            chunkString(wikiString, chunks, HttpHeaders("Expires" to "Wed, 21 Oct 2015 07:28:00 GMT"))
        val stream = ChunkingInputStream(
            wikiString.byteInputStream(),
            headers = HttpHeaders("Expires" to "Wed, 21 Oct 2015 07:28:00 GMT"),
            chunkings = chunks
        )

        val string = stream.readAllBytes().toString(Charset.defaultCharset())

        assertEquals(chunkedString, string)
    }

    @Test
    fun chunkingInputStream_shouldHandleMultipleTrailerHeaders() {
        val wikiString = "Wikipedia in\r\n\r\nchunks."

        val chunks = listOf(
            4 to "",
            5 to "",
            14 to ""
        )
        val chunkedString = chunkString(
            wikiString,
            chunks,
            HttpHeaders("Expires" to "Wed, 21 Oct 2015 07:28:00 GMT", "Content-Type" to "text/plain")
        )
        val stream = ChunkingInputStream(
            wikiString.byteInputStream(),
            headers = HttpHeaders("Expires" to "Wed, 21 Oct 2015 07:28:00 GMT", "Content-Type" to "text/plain"),
            chunkings = chunks
        )

        val string = stream.readAllBytes().toString(Charset.defaultCharset())

        assertEquals(chunkedString, string)
    }

    @Test
    fun chunkingInputStream_shouldHandleLongData() {
        val wikiString = "Wikipedia in\r\n\r\nchunks."
        val longString = wikiString.repeat(1000)

        val chunks = listOf(
            4 to "",
            5 to "",
            14 to ""
        )
        val chunkedString = chunkString(longString, chunks)
        val stream = ChunkingInputStream(longString.byteInputStream(), chunkings = chunks)

        val string = stream.readAllBytes().toString(Charset.defaultCharset())

        assertEquals(chunkedString, string)
    }

    @Test
    fun chunkingInputStream_withDifferentLengths_shouldBeUnchunkedCorrectly() {

        val stream = RepeatableInputStream("Wiki\r\npedia in\r\n\r\nchunks.".toByteArray(), 0L)
        var current = 0
        try {
            for (bufferSize in bufferSizes) {

                for (i in 1..10000) {
                    current = i
                    stream.length = i.toLong()
                    stream.reset()
                    val chunkings = createChunkings(i)
                    val chunkingInputStream = ChunkingInputStream(stream, chunkings = chunkings)

                    val expectedValue =
                        chunkString(stream.readAllBytes().toString(Charsets.US_ASCII), chunkings).toByteArray()
                    stream.reset()
                    val actualValue = readAll(chunkingInputStream, bufferSize)
                    assertEquals(expectedValue.size, actualValue.size, "Failed at $i with buffer size $bufferSize")
                    assertEquals(
                        expectedValue.toString(Charsets.US_ASCII),
                        actualValue.toString(Charsets.US_ASCII),
                        "Failed at $i with buffer size $bufferSize"
                    )
                }
            }
        } catch (e: Exception) {
            println("Failed at $current")
            throw e
        }
    }
}