package kttp.mock

import kttp.net.IOStream
import java.net.ServerSocket
import java.time.Duration

class SimpleTestServer(port: Int, private val timeout: Int = 0) {

    private val serverSocket = ServerSocket(port)
    private lateinit var ioStream: IOStream
        get


    //Just accept 1 client
    fun acceptSocket(): IOStream {
        val socket = serverSocket.accept()
        ioStream = IOStream(socket, Duration.ofSeconds(timeout.toLong()))
        return ioStream
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