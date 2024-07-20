package kttp.mock

import kttp.net.Connection
import kttp.net.ConnectionOptions
import java.net.ServerSocket
import java.time.Duration

class SimpleTestServer(port: Int, private val timeout: Int = 0) {

    private val serverSocket = ServerSocket(port)
    private lateinit var connection: Connection
        get


    //Just accept 1 client
    fun acceptSocket(): Connection {
        val socket = serverSocket.accept()

        val connectionOptions = ConnectionOptions(timeout = Duration.ofSeconds(timeout.toLong()))
        connection = Connection(socket, connectionOptions)
        return connection
    }

    fun stop() {
        serverSocket.close()
        if (::connection.isInitialized)
            connection.close()
    }

    fun readLine(): String {
        return connection.io.readLine()
    }

    fun write(msg: String) {
        connection.io.writeln(msg)
    }


}