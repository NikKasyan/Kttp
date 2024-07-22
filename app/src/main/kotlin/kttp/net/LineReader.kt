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

    fun readLine(): String? {

        val builder = StringBuilder()

        var hasReadBytes = false

        while (true) {
            if (isBufferEmpty()) {
                bytesRead = bufferedInputStream.read(buffer)
                position = 0

                if (bytesRead <= 0) {
                    return if (hasReadBytes)
                        builder.toString()
                    else
                        null
                }
                hasReadBytes = true
            }
            checkLineIsNotTooLong(builder.length, maxLineLengthInBytes)

            val currentByte: Byte = buffer[position++]

            if (currentByte == CARRIAGE_RETURN) {
                continue
            } else if (currentByte == NEW_LINE) {
                return builder.toString()
            }

            builder.append(currentByte.toInt().toChar())
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

private fun checkLineIsNotTooLong(readBytes: Int, maxLineLengthInBytes: Int) {
    if (readBytes > maxLineLengthInBytes)
        throw LineTooLongException("Line is longer than $maxLineLengthInBytes bytes")
}

class LineTooLongException(msg: String) : RuntimeException(msg)