package kttp.net

import java.io.IOException
import java.io.PrintWriter
import java.net.Socket
import java.nio.charset.Charset
import java.time.Duration

class IOStream(private val socket: Socket, timeout: Duration = Duration.ofSeconds(0), charset: Charset = Charsets.UTF_8) : AutoCloseable {

    private val input = socket.getInputStream().bufferedReader(charset)
    private val output = PrintWriter(socket.getOutputStream().bufferedWriter(charset), true)

    private var wasClosedManually: Boolean = false

    init {
        socket.soTimeout = timeout.toMillis().toInt()
    }

    private val isClosed
        get() = wasClosedManually || !socket.isConnected || socket.isClosed ||
                socket.isInputShutdown || socket.isOutputShutdown

    fun println(string: String) {
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
        try {
            return input.readLine() ?: throw EndOfStream()
        } catch (ioException: IOException) {
            throw EndOfStream()
        }
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
