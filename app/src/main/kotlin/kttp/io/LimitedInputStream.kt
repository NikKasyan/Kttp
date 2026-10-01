package kttp.io

import java.io.InputStream

/**
 * Reads at most [limit] bytes from [inputStream] and then reports the end of the stream,
 * so that a message body can't be read into the next message on the same connection.
 * Closing this stream doesn't close [inputStream].
 */
class LimitedInputStream(private val inputStream: InputStream, limit: Long) : DefaultInputStream() {

    private var remaining = limit

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L)
            return -1
        if (length == 0)
            return 0
        val read = inputStream.read(bytes, offset, minOf(length.toLong(), remaining).toInt())
        // A message that ends before its Content-Length is incomplete https://www.rfc-editor.org/rfc/rfc9112#section-6.3-2.6
        if (read == -1)
            throw EndOfStream()
        remaining -= read
        return read
    }

    override fun available(): Int {
        return minOf(inputStream.available().toLong(), remaining).toInt()
    }
}
