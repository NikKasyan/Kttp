package kttp.http.protocol.transfer

import kttp.http.protocol.HttpHeaders
import kttp.net.DefaultInputStream
import java.io.InputStream
import java.lang.RuntimeException

private const val CARRIAGE_RETURN: Byte = 0x0d // \r
private const val NEW_LINE: Byte = 0x0a // \n
private const val SEMICOLON: Byte = 0x3b // ;

/**
 * A stream that reads chunked data from an input stream.
 * @param inputStream The input stream to read from.
 * @param httpHeaders The headers to populate with the content length.
 */
class ChunkedInputStream(
    private val inputStream: InputStream,
    private val httpHeaders: HttpHeaders = HttpHeaders()
) : DefaultInputStream() {


    private var chunkSize = 0

    private var state = ChunkedInputStreamState.CHUNK_SIZE

    private var contentLength = 0L


    private var chunkExtension = ByteArray(2048)
    private var chunkExtensionPosition = 0

    private val transformedData = ByteArray(4096)
    private var transformedDataSize = 0
    private var transformedDataPosition = 0


    private var headerBytes = ByteArray(2048)
    private var headerBytesPosition = 0


    private val buffer = ByteArray(4096)
    private var readBytes = 0
    private var bufferPosition = 0

    override fun close() {
        inputStream.close()
    }

    override fun read(): Int {
        val byteArray = ByteArray(1)
        val read = read(byteArray)
        return if (read == -1) -1 else byteArray[0].toInt()
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        val readBytesFromBuffer = readFromInternalBuffer(bytes, offset, length)
        if (readBytesFromBuffer == length) {
            return readBytesFromBuffer
        }
        val newReadBytes = readIntoInternalBuffer(length - readBytesFromBuffer)
        if (newReadBytes == -1) {
            return if (readBytesFromBuffer == 0) -1 else readBytesFromBuffer
        }
        return readBytesFromBuffer + read(bytes, offset + readBytesFromBuffer, length - readBytesFromBuffer)
    }

    private fun readFromInternalBuffer(b: ByteArray, off: Int, len: Int): Int {
        if (isTransformedDataEmpty()) {
            return 0
        }
        val bytesToRead = minOf(len, transformedDataSize)
        System.arraycopy(transformedData, transformedDataPosition, b, off, bytesToRead)
        transformedDataPosition += bytesToRead
        transformedDataSize -= bytesToRead
        return bytesToRead
    }

    private fun readIntoInternalBuffer(neededBytes: Int): Int {
        var totalReadBytes = 0
        while (
            neededBytes > availableTransformedData
            && canReadMoreData() && readBytes != -1
        ) {
            if (bufferPosition == readBytes) {
                bufferPosition = 0
                readBytes = inputStream.read(buffer, 0, buffer.size)
                if (readBytes == -1)
                    break

                totalReadBytes += readBytes
            }
            parseChunkedData()

        }
        if (totalReadBytes == 0 && isTransformedDataEmpty()) {
            return -1
        }
        return transformedDataSize

    }

    private fun canReadMoreData(): Boolean {
        return !isTransformedDataFull()
                && state != ChunkedInputStreamState.DONE
    }

    private fun parseChunkedData() {

        while (bufferPosition < readBytes && canReadMoreData()) {
            val bytesParsed = when (state) {
                ChunkedInputStreamState.CHUNK_SIZE -> parseChunkSize()
                ChunkedInputStreamState.CHUNK_SIZE_EXT -> parseChunkSizeExt()
                ChunkedInputStreamState.CHUNK_SIZE_EOL -> parseChunkSizeEol()
                ChunkedInputStreamState.CHUNK_DATA -> parseChunkData()
                ChunkedInputStreamState.CHUNK_DATA_EOL -> parseChunkEnd()
                ChunkedInputStreamState.TRAILERS -> parseTrailers()
                else -> 0
            }
            assert(bytesParsed > 0) { "Bytes parsed should be greater than 0" }
            bufferPosition += bytesParsed
        }
        if (state == ChunkedInputStreamState.DONE) {
            httpHeaders.contentLength = contentLength
        }
    }


    private fun parseChunkSize(): Int {
        chunkExtensionPosition = 0
        var pos = 0
        while (bufferPosition + pos < readBytes) {
            val byte = buffer[bufferPosition + pos]
            pos++
            if (byte == CARRIAGE_RETURN) {
                state = ChunkedInputStreamState.CHUNK_SIZE_EOL
                break
            }
            if (byte == SEMICOLON) {
                state = ChunkedInputStreamState.CHUNK_SIZE_EXT
                break
            }
            val prevChunkSize = chunkSize
            chunkSize = chunkSize * 16 + parseSingleHexDigit(byte)

            if (prevChunkSize > chunkSize) {
                throw InvalidChunkSize("Chunk size too big")
            }
        }
        contentLength += chunkSize
        return pos
    }

    private inline fun parseSingleHexDigit(byte: Byte): Int {
        return when (val hexChar = byte.toInt().toChar()) {
            in '0'..'9' -> hexChar - '0'
            in 'A'..'F' -> 10 + (hexChar - 'A')
            in 'a'..'f' -> 10 + (hexChar - 'a')
            else -> throw InvalidChunkSize("Invalid hexadecimal character: ${hexChar.code}")
        }
    }

    private fun parseChunkSizeExt(): Int {
        var pos = 0
        while (bufferPosition + pos < readBytes) {
            val byte = buffer[bufferPosition + pos]
            pos++
            if (byte == CARRIAGE_RETURN) {
                state = ChunkedInputStreamState.CHUNK_SIZE_EOL
                break
            }
        }

        copyChunkExtension(pos)

        return pos
    }

    private fun copyChunkExtension(pos: Int) {
        if (chunkExtensionPosition + pos > chunkExtension.size) {
            throw ChunkExtensionTooLong("Chunk extension too long")
        }
        System.arraycopy(buffer, bufferPosition, chunkExtension, chunkExtensionPosition, pos)
        chunkExtensionPosition += pos
    }

    private fun parseChunkSizeEol(): Int {
        if (buffer[bufferPosition] == NEW_LINE) {
            state = if (chunkSize == 0) {
                ChunkedInputStreamState.TRAILERS
            } else {
                ChunkedInputStreamState.CHUNK_DATA
            }
            return 1
        }
        throw InvalidChunkSize("Expected new line after chunk size, but got ${buffer[bufferPosition]}")
    }

    private fun parseChunkData(): Int {
        if (chunkSize == 0) {
            if (buffer[bufferPosition] == CARRIAGE_RETURN) {
                state = ChunkedInputStreamState.CHUNK_DATA_EOL
                return 1
            } else {
                throw InvalidChunkSize("Expected carriage return after chunk data, but got ${buffer[bufferPosition]}. Chunk size might be off by one.")
            }
        }
        if (allTransformedDataWasRead()) {
            transformedDataSize = 0
            transformedDataPosition = 0
        }
        val bytesToCopy = minOf(chunkSize, readBytes - bufferPosition, transformedDataCapacity())
        System.arraycopy(buffer, bufferPosition, transformedData, transformedDataSize, bytesToCopy)
        transformedDataSize += bytesToCopy
        chunkSize -= bytesToCopy
        return bytesToCopy
    }

    private fun allTransformedDataWasRead(): Boolean {
        return transformedData.size == transformedDataPosition
    }

    private fun transformedDataCapacity() = transformedData.size - transformedDataSize

    private fun parseChunkEnd(): Int {
        if (buffer[bufferPosition] == NEW_LINE) {
            state = ChunkedInputStreamState.CHUNK_SIZE
        } else {
            throw InvalidChunkSize("Expected new line after chunk data, but got ${buffer[bufferPosition]}")
        }
        return 1
    }

    private fun parseTrailers(): Int {
        var pos = 0
        while (bufferPosition + pos < readBytes) {
            if (buffer[bufferPosition + pos] == NEW_LINE) {
                if (headerBytesPosition > 0 && headerBytes[headerBytesPosition - 1] != CARRIAGE_RETURN)
                    throw InvalidTrailer("Expected carriage return before new line in trailers")
                if (headerBytesPosition == 1)
                    state = ChunkedInputStreamState.DONE
                else {
                    val header = String(headerBytes, 0, headerBytesPosition - 1)
                    httpHeaders.add(header)
                    headerBytesPosition = 0
                }
                return pos + 1
            }
            if (headerBytesPosition == headerBytes.size)
                throw InvalidTrailer("Trailer headers too long")
            headerBytes[headerBytesPosition++] = buffer[bufferPosition + pos]
            pos++
        }
        return pos
    }

    private fun isTransformedDataEmpty(): Boolean {
        return availableTransformedData <= 0
    }

    private fun isTransformedDataFull(): Boolean {
        return availableTransformedData == transformedData.size
    }

    private val availableTransformedData: Int
        get() = transformedDataSize


    override fun available(): Int {
        return availableTransformedData
    }

}

enum class ChunkedInputStreamState {
    CHUNK_SIZE,
    CHUNK_SIZE_EXT,
    CHUNK_SIZE_EOL,
    CHUNK_DATA,
    CHUNK_DATA_EOL,
    TRAILERS,
    DONE
}

class InvalidChunkSize(msg: String) : RuntimeException(msg)

class InvalidTrailer(msg: String) : RuntimeException(msg)

class InvalidState(msg: String) : RuntimeException(msg)

class ChunkExtensionTooLong(msg: String) : RuntimeException(msg)


typealias Chunking = Pair<Int, String>

fun chunkString(payload: String, chunkSize: Int = 100): String {
    val chunks = mutableListOf<String>()
    var index = 0
    while (index < payload.length) {
        val chunk = payload.substring(index, minOf(index + chunkSize, payload.length))
        chunks.add("${chunk.length.toString(16)}\r\n$chunk\r\n")
        index += chunkSize
    }
    chunks.add("0\r\n")
    return chunks.joinToString("")
}

fun chunkStringWithChunkSize(payload: String, chunkSizes: List<Int>, headers: HttpHeaders = HttpHeaders()): String {
    return chunkString(payload, chunkSizes.map { it to "" }, headers)
}

fun chunkString(payload: String, chunkings: List<Chunking>, headers: HttpHeaders = HttpHeaders()): String {
    val chunkingLength = chunkings.sumOf { it.first }
    if (chunkingLength != payload.length)
        throw IllegalArgumentException("Chunking sizes must sum up to payload length. Expected $chunkingLength, got ${payload.length}")
    val chunks = mutableListOf<String>()
    var index = 0
    for (chunking in chunkings) {
        val chunk = payload.substring(index, index + chunking.first)
        chunks.add("${chunking.first.toString(16)}${chunkExt(chunking)}\r\n$chunk\r\n")
        index += chunking.first
    }
    chunks.add("0\r\n")
    chunks.add(headersToString(headers) + "\r\n")
    return chunks.joinToString("")
}

private fun headersToString(headers: HttpHeaders): String {
    var headersString = ""
    for (header in headers) {
        headersString += "${header.key}: ${header.value}\r\n"
    }
    return headersString
}

private fun chunkExt(chunking: Chunking): String {
    return if (chunking.second.isEmpty()) "" else ";${chunking.second}"
}