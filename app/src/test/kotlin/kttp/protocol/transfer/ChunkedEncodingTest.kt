package kttp.protocol.transfer

import kttp.http.protocol.transfer.ChunkedInputStream
import kttp.http.protocol.transfer.Chunking
import kttp.http.protocol.transfer.ChunkingInputStream
import kttp.mock.RepeatableInputStream
import org.junit.jupiter.api.Test
import java.io.InputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ChunkedEncodingTest {

    @Test
    fun chunkingInputStream_shouldBeUnchunkedCorrectly() {
        val stream = RepeatableInputStream("Wiki\r\npedia in\r\n\r\nchunks.".toByteArray(), 14)
        val chunkingInputStream = ChunkingInputStream(stream, 14 to "")
        val chunkedInputStream = ChunkedInputStream(chunkingInputStream)


        val expectedValue = stream.readAllBytes()
        stream.reset()
        val actualValue = chunkedInputStream.readAllBytes()

        assertContentEquals(expectedValue, actualValue)
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
                    val chunkedInputStream = ChunkedInputStream(chunkingInputStream)


                    val expectedValue = stream.readAllBytes()
                    stream.reset()
                    val actualValue = readAll(chunkedInputStream, bufferSize)
                    assertEquals(expectedValue.size, actualValue.size, "Failed at $i with buffer size $bufferSize")
                    assertContentEquals(expectedValue, actualValue, "Failed at $i with buffer size $bufferSize")
                }
            }
        } catch (e: Exception) {
            println("Failed at $current")
            throw e
        }
    }


}

fun createChunkings(i: Int, chunkingSize: Int = 14): List<Chunking> {
    val chunkings = mutableListOf<Chunking>()
    for (j in 1..i / chunkingSize) {
        chunkings.add(chunkingSize to "")
    }
    if (i % chunkingSize == 0 && i >= chunkingSize) return chunkings
    chunkings.add((i % chunkingSize) to "")
    return chunkings
}

fun readAll(inputStream: InputStream, bufferSize: Int): ByteArray {
    val buffer = ByteArray(bufferSize)
    val allBytes = mutableListOf<Byte>()
    var readBytes = 0
    while (readBytes != -1) {
        readBytes = inputStream.read(buffer)
        if (readBytes != -1) {
            allBytes.addAll(buffer.sliceArray(0 until readBytes).toList())
        }
    }

    return allBytes.toByteArray()

}

val bufferSizes = listOf(8, 49, 64, 256, 333, 1024, 4096, 8192)