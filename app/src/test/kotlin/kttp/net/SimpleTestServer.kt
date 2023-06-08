package kttp.net

import java.net.ServerSocket
import java.time.Duration

class SimpleTestServer(port: Int, private val timeout: Int) {

    private val serverSocket = ServerSocket(port)
    private lateinit var ioStream: IOStream


    fun start() {
        val socket = serverSocket.accept()
        ioStream = IOStream(socket, Duration.ofSeconds(timeout.toLong()))


    }

    fun stop() {
        serverSocket.close()
        if (::ioStream.isInitialized)
            ioStream.close()
    }

    fun readLine(): String {
        return ioStream.readLine()
    }

    fun write(msg: String) {
        ioStream.writeln(msg)
    }


}