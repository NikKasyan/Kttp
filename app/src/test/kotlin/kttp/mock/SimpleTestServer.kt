package kttp.mock

import kttp.net.ClientConnection
import kttp.net.ConnectionOptions
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.Semaphore

class SimpleTestServer(port: Int, private val timeout: Int = 0) {

    private val serverSocket = ServerSocket(port)
    private lateinit var clientConnection: ClientConnection

    private val initSemaphore = Semaphore(0)


    //Just accept 1 client
    fun acceptSocket(): ClientConnection {
        val socket = serverSocket.accept()

        val connectionOptions = ConnectionOptions(timeout = Duration.ofSeconds(timeout.toLong()))
        clientConnection = ClientConnection(socket, connectionOptions)
        initSemaphore.release()
        return clientConnection
    }

    fun stop() {
        serverSocket.close()
        waitForInit()
        clientConnection.close()
    }

    fun readLine(): String {
        waitForInit()
        return clientConnection.io.readLine()
    }

    fun write(msg: String) {
        waitForInit()
        clientConnection.io.writeln(msg)
    }

    private fun waitForInit() {
        initSemaphore.acquire()
        initSemaphore.release()
    }


}