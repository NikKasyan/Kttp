package kttp.mock

import kttp.net.ClientConnection
import kttp.net.ConnectionOptions
import java.net.ServerSocket
import java.time.Duration

class SimpleTestServer(port: Int, private val timeout: Int = 0) {

    private val serverSocket = ServerSocket(port)
    private lateinit var clientConnection: ClientConnection
        get


    //Just accept 1 client
    fun acceptSocket(): ClientConnection {
        val socket = serverSocket.accept()

        val connectionOptions = ConnectionOptions(timeout = Duration.ofSeconds(timeout.toLong()))
        clientConnection = ClientConnection(socket, connectionOptions)
        return clientConnection
    }

    fun stop() {
        serverSocket.close()
        if (::clientConnection.isInitialized)
            clientConnection.close()
    }

    fun readLine(): String {
        return clientConnection.io.readLine()
    }

    fun write(msg: String) {
        clientConnection.io.writeln(msg)
    }


}