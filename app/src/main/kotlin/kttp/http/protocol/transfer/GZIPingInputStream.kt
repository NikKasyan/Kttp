package kttp.http.protocol.transfer

import kttp.io.DefaultInputStream
import java.io.*
import java.util.zip.GZIPOutputStream

/**
 * Converts a stream of data into a GZIP compressed stream
 */
class GZIPingInputStream(private val source: InputStream) : DefaultInputStream() {
    private val buffer = ByteArray(1024) // Buffer to read and compress data
    private var compressedStream: ByteArrayInputStream? = null

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (compressedStream == null || compressedStream?.available() == 0) {
            fillCompressedStream()
        }

        return compressedStream?.read(bytes, offset, length) ?: -1
    }

    private fun fillCompressedStream() {
        val bytesRead = source.read(buffer)
        if (bytesRead == -1) {
            compressedStream = null // End of stream
        } else {
            val compressedData = ByteArrayOutputStream()
            GZIPOutputStream(compressedData).use { gzip ->
                gzip.write(buffer, 0, bytesRead)
            }
            compressedStream = ByteArrayInputStream(compressedData.toByteArray())
        }
    }

    override fun close() {
        super.close()
        source.close()
        compressedStream?.close()
    }
}