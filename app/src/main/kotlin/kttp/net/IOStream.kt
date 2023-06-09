package kttp.net

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.Socket
import java.nio.charset.Charset
import java.time.Duration

//Todo: Have to rewrite using bytes instead of strings and not using UTF-8 as standard charset
// https://www.rfc-editor.org/rfc/rfc7230#page-19
class IOStream(private val socket: Socket, timeout: Duration = Duration.ofSeconds(0), charset: Charset = Charsets.UTF_8) : AutoCloseable {

    private val input = BufferedInputStream(socket.getInputStream())
    private val output = BufferedOutputStream(socket.getOutputStream())

    private var wasClosedManually: Boolean = false

    init {
        socket.soTimeout = timeout.toMillis().toInt()
    }

    private val isClosed
        get() = wasClosedManually || !socket.isConnected || socket.isClosed ||
                socket.isInputShutdown || socket.isOutputShutdown

    fun writeln(string: String) {
        write("${string}\r\n")
    }

    fun write(string: String, charset: Charset = Charsets.UTF_8) {
        if (isClosed)
            throw StreamAlreadyClosed()
        output.write(string.toByteArray(charset))
        output.flush()
    }

    fun readLine(): String {
        if (isClosed)
            throw StreamAlreadyClosed()
        try {
            return input.bufferedReader().readLine() ?: throw EndOfStream()
        } catch (ioException: IOException) {
            throw EndOfStream()
        }
    }

    fun readBytes(contentLength: Int): ByteArray {
        if (isClosed)
            throw StreamAlreadyClosed()
        val byteArray = ByteArray(contentLength)
        input.read(byteArray, 0, contentLength)
        return byteArray
    }

    fun readBytesAsString(contentLength: Int, charset: Charset = Charsets.UTF_8): String {
        return String(readBytes(contentLength), charset)
    }

    override fun close() {
        wasClosedManually = true
        socket.close()
        input.close()
        output.close()
    }

}

class EndOfStream : RuntimeException()

class StreamAlreadyClosed : RuntimeException()
