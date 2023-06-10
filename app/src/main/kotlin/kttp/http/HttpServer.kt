package kttp.http

import kttp.http.protocol.*
import kttp.net.IOStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class HttpServer(private val port: Int, private val maxConcurrentConnections: Int = 20) {


    private lateinit var serverSocket: ServerSocket

    private var isRunning = false


    private val executorService = Executors.newFixedThreadPool(maxConcurrentConnections)

    private val openConnections = ArrayList<Socket>(maxConcurrentConnections)

    private val numberOfConnections: AtomicInteger = AtomicInteger(0)

    val activeConnections: Int
        get() = numberOfConnections.get()

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
            try {
                val socket = serverSocket.accept()
                handleNewSocket(socket)
            } catch (e: SocketException) {
                println("Connection closed")
            }

        }
    }

    private fun handleNewSocket(socket: Socket) {
        openConnections.add(socket)
        executorService.submit {
            handleSocket(socket)
        }
    }

    private fun handleSocket(socket: Socket) {

        numberOfConnections.incrementAndGet()
        val io = IOStream(socket)
        try {
            //Todo: Handle any errors that might occur during requests
            val httpRequest = HttpRequestHandler().handle(io)

            respond(httpRequest, io)


            //Todo: If major version is not supported by this server
            // Answer with A server can send a 505
            //   (HTTP Version Not Supported) response if it wishes, for any reason,
            //   to refuse service of the client's major protocol version.
            // https://www.rfc-editor.org/rfc/rfc7230#page-14
        } catch (exception: Exception) {
            responseWithError(io, exception)
        } finally {
            io.close()
            numberOfConnections.decrementAndGet()
            openConnections.remove(socket)
        }

    }

    private fun responseWithError(io: IOStream, exception: Exception) {
        val httpResponse = httpResponseFromException(exception)
        io.writeln(httpResponse.toString())
    }

    private fun httpResponseFromException(exception: Exception) = when (exception) {
        is HeaderNameEndsWithWhiteSpace, is InvalidHttpRequestLine -> {
            HttpResponse.badRequest(body = exception.message ?: "No message")
        }
        else -> {
            HttpResponse.internalError(body = exception.message ?: "No message")
        }
    }

    private fun respond(httpRequest: HttpRequest, io: IOStream) {
        val status = StatusLine(HttpVersion(1, 1), HttpStatus.OK)
        val httpResponse = HttpResponse(status, HttpHeaders(), httpRequest.body)
        io.write(httpResponse.toString())
    }

    fun stop() {
        isRunning = false
        openConnections.toList().forEach { it.close() }
        openConnections.clear()
        numberOfConnections.set(0)
        if (::serverSocket.isInitialized)
            serverSocket.close()

    }

}