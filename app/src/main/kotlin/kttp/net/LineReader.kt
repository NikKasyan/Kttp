package kttp.net

import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.math.min

class LineReader(inputStream: InputStream, private val maxLineLengthInBytes: Int = 8192) : InputStream() {
    private val bufferedInputStream = BufferedInputStream(inputStream)
    private val buffer = ByteArray(4096)
    private var bytesRead: Int = 0
    private var position: Int = 0


    companion object {
        private const val CARRIAGE_RETURN: Byte = 0x0d // \r
        private const val NEW_LINE: Byte = 0x0a // \n
    }

    override fun read(): Int {
        val buffer = ByteArray(1)
        val readBytes = read(buffer, 0, 1)
        if (readBytes == -1)
            return -1
        return buffer[0].toInt()

    }

    fun readLine(): String {

        val byteBuffer = ByteBuffer(maxLineLengthInBytes)

        while (true) {
            if (isBufferEmpty()) {
                bytesRead = bufferedInputStream.read(buffer)
                position = 0

                if (bytesRead <= 0) {
                    return byteBuffer.toString()
                }

            }
            val startPosition = position

            var readCr = 0
            while(!isBufferEmpty()) {
                val currentByte: Byte = buffer[position++]
                val bytesToAdd = position - startPosition
                if (byteBuffer.exceedsMaxCapacity(bytesToAdd)) {
                    throw LineTooLongException("Line exceeds max length of $maxLineLengthInBytes", byteBuffer.toString())
                }
                readCr = when(currentByte) {
                    CARRIAGE_RETURN -> 1
                    NEW_LINE -> {
                        byteBuffer.tryAppend(buffer, startPosition, bytesToAdd - (1 + readCr))
                        return byteBuffer.toString()
                    }
                    else ->
                        0
                }
            }

            byteBuffer.tryAppend(buffer, startPosition, position - startPosition)

        }
    }

    fun readBytesAsString(contentLength: Int, charset: Charset = StandardCharsets.UTF_8): String {
        val byteArray = readBytes(contentLength)
        return String(byteArray, charset)
    }

    private fun readBytes(contentLength: Int): ByteArray {
        val byteArray = ByteArray(contentLength)
        read(byteArray, contentLength)
        return byteArray
    }

    private fun read(buffer: ByteArray, contentLength: Int): Int {
        return read(buffer, 0, contentLength)
    }

    override fun read(buffer: ByteArray, offset: Int, contentLength: Int): Int {
        val bufferedBytes = readFromInternalBuffer(buffer, offset, contentLength)
        if (bufferedBytes == contentLength)
            return bufferedBytes
        return bufferedInputStream.read(buffer, offset + bufferedBytes, contentLength - bufferedBytes)
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

class LineTooLongException(msg: String, val line: String) : RuntimeException(msg) {

}

private class ByteBuffer(val maxCapacity: Int) {
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
    override fun toString(): String {
        return String(buffer, 0, size)
    }
}