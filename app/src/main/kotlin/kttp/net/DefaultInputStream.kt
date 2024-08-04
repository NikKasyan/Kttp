package kttp.net

import java.io.InputStream

abstract class DefaultInputStream: InputStream() {
    override fun read(): Int {
        val bytes = ByteArray(1)
        val read = read(bytes)
        return if (read == -1) -1 else bytes[0].toInt()
    }

    abstract override fun read(bytes: ByteArray, offset: Int, length: Int): Int

}