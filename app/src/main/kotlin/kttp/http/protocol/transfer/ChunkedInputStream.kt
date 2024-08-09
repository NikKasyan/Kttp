package kttp.http.protocol.transfer

import kttp.http.protocol.HttpHeaders
import kttp.io.*
import java.io.InputStream
import java.lang.RuntimeException

/**
 * A stream that reads chunked data from an input stream.
 * @param inputStream The input stream to read from.
 * @param httpHeaders The headers to populate with the content length.
 */
class ChunkedInputStream(
    private val inputStream: InputStream,
    private val httpHeaders: HttpHeaders = HttpHeaders()
) : TransformingInputStream(inputStream) {


    private var chunkSize = 0

    private var state = ChunkingState.CHUNK_SIZE

    private var contentLength = 0L


    private var chunkExtensionPosition = 0

    private val headerBytes = ByteBuffer(2050)
    override fun canTransform(): Boolean {
        return state != ChunkingState.DONE
                && transformedData.hasCapacity()
    }

    override fun transform() {

        while ((!buffer.hasBeenRead() || isStreamFinished) && canTransform()) {
            when (state) {
                ChunkingState.CHUNK_SIZE -> parseChunkSize()
                ChunkingState.CHUNK_SIZE_EXT -> parseChunkSizeExt()
                ChunkingState.CHUNK_SIZE_EOL -> parseChunkSizeEol()
                ChunkingState.CHUNK_DATA -> parseChunkData()
                ChunkingState.CHUNK_DATA_EOL -> parseChunkEnd()
                ChunkingState.TRAILERS -> parseTrailers()
                ChunkingState.TRAILERS_EOL -> parseTrailersEol()
                else -> break
            }
        }
        if (state == ChunkingState.DONE) {
            httpHeaders.contentLength = contentLength
        }

    }


    private fun parseChunkSize() {
        chunkExtensionPosition = 0
        while (!buffer.hasBeenRead()) {
            val byte = buffer.readByte()
            if (byte == CARRIAGE_RETURN) {
                state = ChunkingState.CHUNK_SIZE_EOL
                break
            }
            if (byte == SEMICOLON) {
                state = ChunkingState.CHUNK_SIZE_EXT
                break
            }
            val prevChunkSize = chunkSize
            chunkSize = chunkSize * 16 + parseSingleHexDigit(byte)

            if (prevChunkSize > chunkSize) {
                throw InvalidChunkSize("Chunk size too big")
            }
        }
        contentLength += chunkSize
    }

    private inline fun parseSingleHexDigit(byte: Byte): Int {
        return when (val hexChar = byte.toInt().toChar()) {
            in '0'..'9' -> hexChar - '0'
            in 'A'..'F' -> 10 + (hexChar - 'A')
            in 'a'..'f' -> 10 + (hexChar - 'a')
            else -> throw InvalidChunkSize("Invalid hexadecimal character: ${hexChar.code}")
        }
    }

    private fun parseChunkSizeExt() {
        var pos = 0
        while (!buffer.hasBeenRead()) {
            val byte = buffer.readByte()
            if (byte == CARRIAGE_RETURN) {
                state = ChunkingState.CHUNK_SIZE_EOL
                break
            }
            pos++
        }

        skipChunkExtensions(pos)

    }

    private fun skipChunkExtensions(pos: Int) {
        if (chunkExtensionPosition + pos > 2048) {
            throw ChunkExtensionTooLong("Chunk extension too long")
        }
        // Skip chunk extensions
        chunkExtensionPosition += pos
    }

    private fun parseChunkSizeEol() {
        val byte = buffer.readByte()
        if (byte == NEW_LINE) {
            state = if (chunkSize == 0) {
                ChunkingState.TRAILERS
            } else {
                ChunkingState.CHUNK_DATA
            }

        } else
            throw InvalidChunkSize("Expected new line after chunk size, but got $byte")
    }

    private fun parseChunkData() {
        if (chunkSize == 0) {
            val byte = buffer.readByte()
            if (byte == CARRIAGE_RETURN) {
                state = ChunkingState.CHUNK_DATA_EOL
                return
            } else {
                throw InvalidChunkSize("Expected carriage return after chunk data, but got $byte. Chunk size might be off by one.")
            }
        }

        val bytesCopied = transformedData.moveFrom(buffer, chunkSize)
        chunkSize -= bytesCopied
    }


    private fun parseChunkEnd() {
        val byte = buffer.readByte()
        if (byte == NEW_LINE) {
            state = ChunkingState.CHUNK_SIZE
        } else {
            throw InvalidChunkSize("Expected new line after chunk data, but got $byte")
        }
    }

    private fun parseTrailers() {
        while (!buffer.hasBeenRead()) {
            val byte = buffer.readByte()
            if (byte == CARRIAGE_RETURN) {
                state = ChunkingState.TRAILERS_EOL
                return
            }
            if (headerBytes.length >= 2048 + 1)
                throw InvalidTrailer("Trailer headers too long")
            headerBytes += byte
        }
    }

    private fun parseTrailersEol() {
        val byte = buffer.readByte()
        if (byte == NEW_LINE) {
            if(headerBytes.isEmpty()){
                state = ChunkingState.DONE
                return
            }
            state = ChunkingState.TRAILERS
            httpHeaders.add(headerBytes.toString())
            headerBytes.clear()
        } else {
            throw InvalidTrailer("Expected new line after trailers, but got $byte")
        }
    }

}


class InvalidChunkSize(msg: String) : RuntimeException(msg)

class InvalidTrailer(msg: String) : RuntimeException(msg)

class InvalidState(msg: String) : RuntimeException(msg)

class ChunkExtensionTooLong(msg: String) : RuntimeException(msg)

