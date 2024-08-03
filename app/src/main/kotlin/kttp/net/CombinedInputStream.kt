package kttp.net

import java.io.InputStream
import java.io.OutputStream

class CombinedInputStream(vararg streams: InputStream) : InputStream() {

    private val streams = mutableListOf<InputStream>()

    private var closed = false

    init {
        this.streams.addAll(streams)
    }

    fun append(stream: InputStream) {
        streams.add(stream)
    }

    override fun read(): Int {
        val byteArray = ByteArray(1)
        val read = read(byteArray, 0, 1)
        return if (read == -1) -1 else byteArray[0].toInt()
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (closed || streams.isEmpty())
            return -1
        return readFromStreams(b, off, len)

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

    override fun readAllBytes(): ByteArray {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val byteArray = ByteArray(1024)
            val read = read(byteArray)
            if (read == -1)
                break
            bytes.addAll(byteArray.toList().subList(0, read))
        }
        return bytes.toByteArray()
    }

    override fun readNBytes(len: Int): ByteArray {
        val bytes = ByteArray(len)
        var remaining = len
        var read = 0
        while (remaining > 0) {
            val readNow = read(bytes, read, remaining)
            if (readNow == -1)
                break
            read += readNow
            remaining -= readNow
        }
        return bytes.copyOf(read)
    }

    override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int {
        var remaining = len
        var read = 0
        while (remaining > 0) {
            val readNow = read(b!!, off + read, remaining)
            if (readNow == -1)
                break
            read += readNow
            remaining -= readNow
        }
        return read
    }

    override fun skip(n: Long): Long {
        var skipped = 0L
        while (skipped < n) {
            val byteArray = ByteArray(1024)
            val read = read(byteArray)
            if (read == -1)
                break
            skipped += read
        }
        return skipped
    }

    override fun skipNBytes(n: Long) {
        skip(n)
    }

    override fun available(): Int {
        return streams.sumOf { it.available() }
    }

    override fun transferTo(out: OutputStream?): Long {
        var transferred = 0L
        while (true) {
            val byteArray = ByteArray(1024)
            val read = read(byteArray)
            if (read == -1)
                break
            out?.write(byteArray, 0, read)
            transferred += read
        }
        return transferred
    }
}

fun InputStream.combineWith(vararg streams: InputStream): InputStream {
    return CombinedInputStream(this, *streams)
}