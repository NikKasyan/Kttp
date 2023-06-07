package kttp.net

import java.net.Socket
import java.nio.charset.Charset

class IOStream(private val socket: Socket, charset: Charset = Charsets.UTF_8) : AutoCloseable {

    private val input = socket.getInputStream().bufferedReader(charset)
    private val output = socket.getOutputStream().bufferedWriter(charset)

    private var wasClosedManually: Boolean = false

    private val isClosed
        get() = wasClosedManually || !socket.isConnected || socket.isClosed ||
                socket.isInputShutdown || socket.isOutputShutdown

    fun writeln(string: String) {
        write("${string}\n")
    }

    fun write(string: String) {
        if (isClosed)
            throw StreamAlreadyClosed()
        output.write(string)
        output.flush()
    }

    fun readLine(): String {
        if (isClosed)
            throw StreamAlreadyClosed()
        return input.readLine() ?: throw EndOfStream()
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
