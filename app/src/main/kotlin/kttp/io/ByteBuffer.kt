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
        return this.capacity >= neededBytes
    }
    fun hasCapacity() = hasCapacityFor(1)

    private val capacity
        get() = buffer.size - length

    private val availableToRead
        get() = length - readPosition
    fun clear() {
        length = 0
        readPosition = 0
    }

    fun moveFrom(src: ByteBuffer, maxLength: Int = Int.MAX_VALUE): Int {
        val bytesToCopy = minOf(
            src.availableToRead,
            this.capacity,
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
            this.capacity,
            maxLength
        )
        System.arraycopy(src, offset, buffer, destOffset, bytesToCopy)
        this.length += bytesToCopy
    }

    fun moveInto(dest: ByteArray, offset: Int, length: Int): Int {
        val bytesToCopy = minOf(length, this.availableToRead)
        System.arraycopy(buffer, readPosition, dest, offset, bytesToCopy)
        this.readPosition += bytesToCopy
        return bytesToCopy
    }

    fun fillWith(inputStream: InputStream) {
        val bytesToRead = this.capacity
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
        if(length == -1) return ""
        val bytes = buffer.copyOfRange(readPosition, length)
        return String(bytes, charset)
    }

    override fun toString(): String {
        return toString(Charsets.US_ASCII)
    }
}
