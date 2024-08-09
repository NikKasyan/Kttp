package kttp.io

import java.io.InputStream

class CombinedInputStream(vararg streams: InputStream) : DefaultInputStream() {

    private val streams = mutableListOf<InputStream>()

    private var closed = false

    init {
        this.streams.addAll(streams)
    }

    fun append(stream: InputStream) {
        streams.add(stream)
    }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (closed || streams.isEmpty())
            return -1
        return readFromStreams(bytes, offset, length)

    }

    private fun readFromStreams(b: ByteArray, off: Int, len: Int): Int {
        val stream = streams.firstOrNull() ?: return 0
        val read = stream.read(b, off, len)
        return if (read == -1) {
            streams.removeFirst()
            readFromStreams(b, off, len)
        } else if(read < len) {
            read + readFromStreams(b, off + read, len - read)
        } else {
            read
        }
    }

    override fun close() {
        this.closed = true
        for (stream in streams) {
            try {
                stream.close()
            } catch (e: Exception) {
            }
        }
    }

    override fun available(): Int {
        return streams.sumOf { it.available() }
    }

}

fun InputStream.combineWith(vararg streams: InputStream): InputStream {
    return CombinedInputStream(this, *streams)
}