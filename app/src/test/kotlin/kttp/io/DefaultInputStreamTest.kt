package kttp.io

import kttp.io.DefaultInputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class DefaultInputStreamTest {

    @Test
    fun testDefaultInputStream_SingleByteRead_ShouldReturnUnsignedByte() {
        val inputStream = SimpleInputStream(ByteArrayInputStream(byteArrayOf(0xFF.toByte())))

        val readByte = inputStream.read()

        assertEquals(0xFF, readByte)
    }


}

class SimpleInputStream(private val inputStream: InputStream): DefaultInputStream() {


    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        return inputStream.read(bytes, offset, length)
    }
}