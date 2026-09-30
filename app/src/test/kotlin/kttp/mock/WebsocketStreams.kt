package kttp.mock

import kttp.io.IOStream
import java.io.OutputStream

fun ioStreamOf(bytes: ByteArray): IOStream {
    return IOStream(bytes.inputStream(), OutputStream.nullOutputStream())
}
