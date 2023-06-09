package kttp.net

import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.math.min

class LineReader(private val inputStream: InputStream) {
    private val bufferedInputStream = BufferedInputStream(inputStream)
    private val byteArray = ByteArray(4096)
    private var bytesRead: Int = 0
    private var position: Int = 0

    fun readLine(): String? {
        var line: String? = null

        try {
            val builder = StringBuilder()

            while (true) {
                if (position >= bytesRead) {
                    bytesRead = bufferedInputStream.read(byteArray)
                    position = 0

                    if (bytesRead <= 0) {
                        if (builder.isNotEmpty()) {
                            line = builder.toString()
                        }
                        break
                    }
                }

                val currentByte = byteArray[position++].toInt().toChar()

                if (currentByte == '\r') {
                    continue
                } else if (currentByte == '\n') {
                    line = builder.toString()
                    break
                }

                builder.append(currentByte)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return line
    }

    fun close() {
        bufferedInputStream.close()
    }


    fun read(buffer: ByteArray, offset: Int, contentLength: Int) {
        val internalReadBytes = readFromInternalBuffer(buffer, offset, contentLength)
        if (internalReadBytes == contentLength)
            return

        bufferedInputStream.read(buffer, offset + internalReadBytes, contentLength - internalReadBytes)
    }

    private fun readFromInternalBuffer(buffer: ByteArray, offset: Int, contentLength: Int): Int {
        if (position >= bytesRead) {
            return 0
        }

        val numberOfBufferedBytes = bytesRead - position

        //We only want to read the minimum amount of bytes
        val readBytes = min(numberOfBufferedBytes, contentLength)

        System.arraycopy(byteArray, position, buffer, offset, readBytes)

        position += readBytes

        return readBytes
    }

    fun readBytesAsString(contentLength: Int, charset: Charset = Charsets.UTF_8): String {
        val byteArray = ByteArray(contentLength)
        read(byteArray, 0, contentLength)
        return String(byteArray, charset)
    }
}
