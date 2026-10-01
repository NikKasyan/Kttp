package kttp.io

import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.math.min

class LineReader(inputStream: InputStream, private val maxLineLengthInBytes: Int = 8192) : DefaultInputStream() {
    private val bufferedInputStream = BufferedInputStream(inputStream)
    private val buffer = ByteArray(4096)
    private var bytesRead: Int = 0
    private var position: Int = 0

    /**
     * Returns the next line without its line ending, or null at the end of the stream.
     */
    fun readLine(): String? {

        val cappedByteBuffer = CappedByteBuffer(maxLineLengthInBytes)

        while (true) {
            if (isBufferEmpty()) {
                bytesRead = bufferedInputStream.read(buffer)
                position = 0

                if (bytesRead <= 0) {
                    return if (cappedByteBuffer.isEmpty()) null else cappedByteBuffer.toString()
                }

            }
            val startPosition = position

            while(!isBufferEmpty()) {
                val currentByte: Byte = buffer[position++]
                val bytesToAdd = position - startPosition
                if (cappedByteBuffer.exceedsMaxCapacity(bytesToAdd)) {
                    throw LineTooLongException("Line exceeds max length of $maxLineLengthInBytes", cappedByteBuffer.toString())
                }
                if (currentByte == NEW_LINE) {
                    cappedByteBuffer.tryAppend(buffer, startPosition, bytesToAdd - 1)
                    // The CR of a CRLF can arrive in an earlier read than the LF, so it is removed from the line, not from this read
                    cappedByteBuffer.removeTrailingCarriageReturn()
                    return cappedByteBuffer.toString()
                }
            }

            cappedByteBuffer.tryAppend(buffer, startPosition, position - startPosition)

        }
    }

    fun readBytesAsString(contentLength: Int, charset: Charset = StandardCharsets.UTF_8): String {
        val byteArray = readNBytes(contentLength)
        return String(byteArray, charset)
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        val bufferedBytes = readFromInternalBuffer(bytes, offset, length)
        if (bufferedBytes == length)
            return bufferedBytes
        val readBytesFromStream = bufferedInputStream.read(bytes, offset + bufferedBytes, length - bufferedBytes)
        return if (readBytesFromStream == -1) {
            if (bufferedBytes == 0)
                -1
            else
                bufferedBytes
        } else {
            bufferedBytes + readBytesFromStream
        }
    }

    private fun readFromInternalBuffer(buffer: ByteArray, offset: Int, contentLength: Int): Int {
        if (isBufferEmpty())
            return 0

        val numberOfBufferedBytes = bytesRead - position

        //We only want to read the minimum amount of bytes
        val readBytes = min(numberOfBufferedBytes, contentLength)
        System.arraycopy(this.buffer, position, buffer, offset, readBytes)
        position += readBytes

        return readBytes
    }


    private fun isBufferEmpty(): Boolean {
        return position >= bytesRead
    }

    override fun close() {
        bufferedInputStream.close()
    }

}

class LineTooLongException(msg: String, val line: String) : RuntimeException(msg)

private class CappedByteBuffer(val maxCapacity: Int) {
    private val buffer = ByteArray(maxCapacity)
    private var size: Int = 0

    fun tryAppend(bytes: ByteArray, offset: Int, length: Int): Boolean {
        val maxBytesToAppend = buffer.size - size
        val bytesToAppend = min(length, maxBytesToAppend)
        System.arraycopy(bytes, offset, buffer, size, bytesToAppend)
        size += bytesToAppend
        return bytesToAppend < length
    }
    fun tryAppend(bytes: ByteArray): Boolean {
        return tryAppend(bytes, 0, bytes.size)
    }
    fun exceedsMaxCapacity(bytesToAdd: Int): Boolean {
        return size + bytesToAdd > maxCapacity
    }
    fun isEmpty(): Boolean {
        return size == 0
    }
    fun removeTrailingCarriageReturn() {
        if (size > 0 && buffer[size - 1] == CARRIAGE_RETURN)
            size--
    }
    override fun toString(): String {
        return String(buffer, 0, size, Charsets.US_ASCII)
    }
}