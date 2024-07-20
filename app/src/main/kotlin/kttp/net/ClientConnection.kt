package kttp.net

import java.net.Socket
import java.nio.charset.Charset
import java.time.Duration

val DEFAULT_OPTIONS = ConnectionOptions()
class ClientConnection(private val socket: Socket, options: ConnectionOptions = DEFAULT_OPTIONS) {

    private var wasClosedManually: Boolean = false
    val io = IOStream(socket.getInputStream(), socket.getOutputStream(), options.charset, options.maxLineLengthInBytes)
    init {
        socket.soTimeout = options.timeout.toMillis().toInt()
    }

    private val isClosed
        get() = wasClosedManually || !socket.isConnected || socket.isClosed ||
                socket.isInputShutdown || socket.isOutputShutdown

    fun close() {
        if (!isClosed) {
            socket.close()
            wasClosedManually = true
        }
        io.close()
    }


}


data class ConnectionOptions(val timeout: Duration = Duration.ofSeconds(30), val charset: Charset = Charsets.UTF_8, val maxLineLengthInBytes: Int = 8192)