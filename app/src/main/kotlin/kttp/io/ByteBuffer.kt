package kttp.io

import java.io.InputStream
import java.nio.charset.Charset

class ByteBuffer(private val buffer: ByteArray,
                 var length: Int = 0,
                 private var readPosition: Int = 0) {

    constructor(size: Int) : this(ByteArray(size))

    operator fun plusAssign(byte: Byte) {
        buffer[length++] = byte
    }

    fun hasCapacityFor(neededBytes: Int): Boolean {
        return capacity() >= neededBytes
    }
    fun hasCapacity() = hasCapacityFor(1)

    fun capacity() = buffer.size - length

    fun available() = length - readPosition
    fun clear() {
        length = 0
        readPosition = 0
    }

    fun moveFrom(src: ByteBuffer, maxLength: Int = Int.MAX_VALUE): Int {
        val bytesToCopy = minOf(
            src.available(),
            capacity(),
            maxLength
        )
        System.arraycopy(src.buffer, src.readPosition, buffer, length, bytesToCopy)
        length += bytesToCopy
        src.readPosition += bytesToCopy
        return bytesToCopy
    }
    fun moveFrom(src: ByteArray, offset: Int = 0,
                 length: Int = src.size,
                 destOffset: Int = this.length,
                 maxLength: Int = Int.MAX_VALUE) {
        val bytesToCopy = minOf(
            length,
            capacity(),
            maxLength
        )
        System.arraycopy(src, offset, buffer, destOffset, bytesToCopy)
        this.length += bytesToCopy
    }

    fun moveInto(dest: ByteArray, offset: Int, length: Int): Int {
        val bytesToCopy = minOf(length, this.length)
        System.arraycopy(buffer, readPosition, dest, offset, bytesToCopy)
        this.readPosition += bytesToCopy
        this.length -= bytesToCopy
        return bytesToCopy
    }

    fun fillWith(inputStream: InputStream) {
        val bytesToRead = capacity()
        val readBytes = inputStream.read(buffer, length, bytesToRead)
        length += readBytes
    }

    fun hasBeenRead(): Boolean = readPosition >= length

    fun isEmpty() = length == 0 || hasBeenRead()

    fun maxCapacity() = buffer.size
    fun readByte(): Byte {
        return buffer[readPosition++]
    }

    fun toString(charset: Charset = Charsets.US_ASCII): String {
        val bytes = buffer.copyOfRange(readPosition, readPosition + length)
        return String(bytes, charset)
    }

    override fun toString(): String {
        return toString(Charsets.US_ASCII)
    }
}
