package kttp.http.protocol.transfer

import kttp.http.protocol.HttpHeaders
import kttp.io.*
import java.io.InputStream

/**
 * Converts a stream of bytes into a chunked stream.
 * @param inputStream The output stream to write the chunked data to.
 */
class ChunkingInputStream(
    private val inputStream: InputStream,
    headers: HttpHeaders = HttpHeaders(),
    private val chunkings: List<Chunking> = emptyList()
) : TransformingInputStream(inputStream) {

    constructor(inputStream: InputStream, vararg list: Chunking) : this(inputStream, HttpHeaders(), list.toList())

    constructor(inputStream: InputStream, headers: HttpHeaders, vararg list: Chunking) : this(
        inputStream,
        headers,
        list.toList()
    )

    constructor(inputStream: InputStream, chunks: List<Int>, headers: HttpHeaders) : this(
        inputStream,
        headers,
        chunks.map { it to "" })

    init {

        chunkings.forEach {
            if (it.first <= 0)
                throw IllegalArgumentException("Chunk size must be greater than 0")
            if (it.second.length > 2048)
                throw IllegalArgumentException("Chunk extension must be less than 2048 characters")
        }
    }

    private var state = ChunkingState.CHUNK_SIZE
    private var chunkIndex = 0
    private var chunkSize = 0

    private val chunkExtension = ByteBuffer(2051) // 2048 + 3 for \r\n;

    private val headerIterator = headers.iterator()

    private val headerBytes = ByteBuffer(2050)

    override fun canTransform(): Boolean {
        if(state == ChunkingState.DONE || !transformedData.hasCapacity())
            return false
        if(state == ChunkingState.TRAILERS)
            return transformedData.hasCapacityFor(2)
        if(state == ChunkingState.CHUNK_SIZE)
            return transformedData.hasCapacityFor(3)
        return true

    }

    override fun transform() {
        while (canTransform() && (!buffer.isFullyRead() || isStreamFinished)) {
            when (state) {
                ChunkingState.CHUNK_SIZE -> writeChunkSize()
                ChunkingState.CHUNK_SIZE_EXT -> writeChunkSizeExt()
                ChunkingState.CHUNK_SIZE_EOL -> writeChunkSizeEnd()
                ChunkingState.CHUNK_DATA -> writeChunkData()
                ChunkingState.CHUNK_DATA_EOL -> writeChunkEnd()
                else -> break
            }
        }

        if (state == ChunkingState.TRAILERS) {
            writeTrailers()
        }
    }

    private fun writeChunkSize(): Int {
        if(buffer.isFullyRead()){
            if(transformedData.hasCapacityFor(3)) {
                transformedData += '0'.code.toByte()
                transformedData += CARRIAGE_RETURN
                transformedData += NEW_LINE
                state = ChunkingState.TRAILERS
            }
            return 0
        }
        if (chunkSize == 0) {
            chunkSize = currentChunkSize()
        }

        val chunkSizeString = chunkSize.toString(16)
        var writtenBytes = 0
        while (chunkSize > 0 && transformedData.hasCapacity()) {
            val byte = chunkSizeString[writtenBytes++].code.toByte()
            transformedData += byte
            chunkSize /= 16
        }
        if (chunkSize == 0) {
            state = ChunkingState.CHUNK_SIZE_EXT
        }
        return 0
    }

    private fun currentChunkSize(): Int {
        if(chunkings.isNotEmpty())
            return chunkings[chunkIndex].first
        return buffer.length
    }

    private fun writeChunkSizeEnd(): Int {
        if (transformedData.hasCapacityFor(2)) {
            transformedData += CARRIAGE_RETURN
            transformedData += NEW_LINE
            state = ChunkingState.CHUNK_DATA

        }
        return 0
    }

    private fun writeChunkSizeExt(): Int {
        if (chunkExtension.isEmpty()) {
            chunkExtension.clear()
            val newChunkExtension = currentChunkExtension().toByteArray()
            if (newChunkExtension.isNotEmpty())
                chunkExtension  += SEMICOLON
            chunkExtension.moveFrom(newChunkExtension)
            chunkExtension += CARRIAGE_RETURN
            chunkExtension += NEW_LINE
        }

        transformedData.moveFrom(chunkExtension)
        if (chunkExtension.isEmpty()) {
            chunkSize = currentChunkSize()
            state = ChunkingState.CHUNK_DATA
        }
        return 0
    }

    private fun currentChunkExtension(): String {
        if(chunkings.isNotEmpty())
            return chunkings[chunkIndex].second
        return ""
    }

    private fun writeChunkData(): Int {
        if (chunkSize == 0) {
            transformedData += CARRIAGE_RETURN
            state = ChunkingState.CHUNK_DATA_EOL
            return 0
        }

        val bytesCopied = transformedData.moveFrom(buffer, chunkSize)

        chunkSize -= bytesCopied
        return bytesCopied
    }

    private fun writeChunkEnd(): Int {
        if (transformedData.hasCapacity()) {
            transformedData += NEW_LINE
            chunkIndex++
            if (chunkIndex == chunkings.size)
                chunkIndex = 0
            state = ChunkingState.CHUNK_SIZE
        }

        return 0
    }

    private fun writeTrailers() {
        while (headerIterator.hasNext()) {

            if (headerBytes.isFullyRead()) {
                headerBytes.clear()
                val header = headerIterator.next()
                val newHeaderBytes = header.toString().toByteArray()
                if (newHeaderBytes.size > headerBytes.maxCapacity()) {
                    throw InvalidTrailer("Trailer header too long")
                }
                headerBytes.moveFrom(newHeaderBytes)
                headerBytes  += CARRIAGE_RETURN
                headerBytes += NEW_LINE

            }
            transformedData.moveFrom(headerBytes)

        }
        if (transformedData.hasCapacityFor(2)) {
            transformedData += CARRIAGE_RETURN
            transformedData += NEW_LINE
            state = ChunkingState.DONE
        }
    }

}
