package kttp.http

import java.lang.IllegalArgumentException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class HttpServer(private val port: Int) {


    private lateinit var serverSocket: ServerSocket

    private var isRunning = false

    init {
        if (port < 0 || port > 0xFFFF)
            throw IllegalArgumentException("port must be between 0 and ${0xFFFF}")
    }


    fun start() {
        isRunning = true
        this.serverSocket = ServerSocket(port)
        thread {
            acceptNewSockets()
        }
    }

    private fun acceptNewSockets(){
        while (isRunning)
            acceptNewSocket(serverSocket.accept())
    }

    private fun acceptNewSocket(socket: Socket){

    }

    fun stop() {
        isRunning = false
        if (::serverSocket.isInitialized)
            serverSocket.close()
    }

}