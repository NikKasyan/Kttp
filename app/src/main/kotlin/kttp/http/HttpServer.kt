package kttp.http

import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

class HttpServer(private val port: Int, private val maxConcurrentConnections: Int = 20) {


    private lateinit var serverSocket: ServerSocket

    private var isRunning = false

    private val connectionQueue = Semaphore(maxConcurrentConnections)

    private val executorService = Executors.newFixedThreadPool(maxConcurrentConnections)

    private val openConnections = ArrayList<Socket>(maxConcurrentConnections)


    val currentNumberOfConnections: Int
        get() = openConnections.size

    init {
        if (port < 0 || port > 0xFFFF)
            throw IllegalArgumentException("port must be between 0 and ${0xFFFF}")
    }


    fun start() {
        if (isRunning)
            throw IllegalStateException("Server can only be started once")
        isRunning = true
        this.serverSocket = ServerSocket(port)
        acceptNewSockets()

    }

    private fun acceptNewSockets() {
        while (isRunning) {
            var socket: Socket? = null
            try {
                connectionQueue.acquire()
                socket = serverSocket.accept()
                openConnections.add(socket)

                handleNewSocket(socket)
            } catch (e: SocketException) {
                println("Client closed connection")
                connectionQueue.release()
                if (socket != null)
                    openConnections.remove(socket)
            }
        }
    }

    private fun handleNewSocket(socket: Socket) {

        executorService.submit {
            HttpHandler().handle(socket)
            connectionQueue.release()
            openConnections.remove(socket)
        }
    }

    fun stop() {
        isRunning = false
        openConnections.forEach { it.close() }
        openConnections.clear()
        if (::serverSocket.isInitialized)
            serverSocket.close()

    }

}