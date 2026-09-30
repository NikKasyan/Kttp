package kttp.mock

import kttp.io.IOStream
import kttp.websocket.WebsocketFrame
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

fun ioStreamOf(bytes: ByteArray): IOStream {
    return IOStream(bytes.inputStream(), OutputStream.nullOutputStream())
}

fun frameOf(opcode: Int, payload: ByteArray, masked: Boolean = false, isFinal: Boolean = true): WebsocketFrame {
    return WebsocketFrame(isFinal, opcode, masked, payload = payload, offset = 0, length = payload.size)
}

fun framesToBytes(vararg frames: WebsocketFrame): ByteArray {
    val output = ByteArrayOutputStream()
    val ioStream = IOStream(InputStream.nullInputStream(), output)
    frames.forEach { it.writeTo(ioStream) }
    return output.toByteArray()
}
