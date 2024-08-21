package kttp.mock

import kttp.io.DefaultInputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RepeatableInputStream(private val byteArray: ByteArray, var length: Long = byteArray.size.toLong()) :
    DefaultInputStream() {

    private var bytesRead: Long = 0

    private var closed = false

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        val bytesToRead = minOf(length.toLong(), this.length - bytesRead)
        if (this.closed || bytesRead == this.length)
            return -1
        val readBytes = readFromBuffer(bytes, offset, bytesToRead.toInt())
        bytesRead += readBytes
        return readBytes
    }

    private fun readFromBuffer(bytes: ByteArray, offset: Int, length: Int): Int {
        var bytesLeft = length
        var readBytes = 0
        var currentPosition = bytesRead.toInt() % byteArray.size
        while (bytesLeft > 0) {
            if (currentPosition >= byteArray.size)
                currentPosition %= byteArray.size
            val bytesToRead = minOf(bytesLeft, byteArray.size - currentPosition)
            System.arraycopy(byteArray, currentPosition, bytes, offset + readBytes, bytesToRead)
            readBytes += bytesToRead
            bytesLeft -= bytesToRead
            currentPosition += bytesToRead
        }
        return readBytes
    }

    override fun close() {
        closed = true
    }

    override fun available(): Int {
        return (length - bytesRead).toInt()
    }

    override fun reset() {
        bytesRead = 0
    }

}

class RepeatableInputStreamTest {

    @Test
    fun testRepeatableInputStream() {
        val byteArray = byteArrayOf(1, 2, 3, 4, 5)
        val repeatableInputStream = RepeatableInputStream(byteArray)
        val bytes = ByteArray(5)
        repeatableInputStream.read(bytes)
        assertArrayEquals(byteArray, bytes)
        repeatableInputStream.reset()
        repeatableInputStream.read(bytes)
        assertArrayEquals(byteArray, bytes)
    }

    @Test
    fun testRepeatableInputStreamWithLength() {
        val byteArray = byteArrayOf(1, 2, 3, 4, 5)
        val repeatableInputStream = RepeatableInputStream(byteArray, 10)
        val bytes = ByteArray(5)
        repeatableInputStream.read(bytes)
        assertArrayEquals(byteArray, bytes)
        assertArrayEquals(byteArray, bytes)
    }

    @Test
    fun testRepeatableInputStreamWithLongLength() {
        val byteArray = byteArrayOf(1, 2, 3, 4, 5)
        val length = 1000L
        val repeatableInputStream = RepeatableInputStream(byteArray, length * byteArray.size)
        val bytes = ByteArray(5)
        repeatableInputStream.read(bytes)
        var i = 0
        while (i < length) {
            repeatableInputStream.read(bytes)
            assertArrayEquals(byteArray, bytes)
            i++
        }
        assertEquals(-1, repeatableInputStream.read())
    }
}