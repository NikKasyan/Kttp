package kttp.http

import kttp.http.protocol.*
import kttp.log.Logger
import kttp.net.ClientConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class HttpServer(private val port: Int = 80, maxConcurrentConnections: Int = 20) {


    private lateinit var serverSocket: ServerSocket

    private var isRunning = false


    private val executorService = Executors.newFixedThreadPool(maxConcurrentConnections)

    private val openConnections = ArrayList<ClientConnection>(maxConcurrentConnections)

    private val numberOfConnections: AtomicInteger = AtomicInteger(0)

    private val log: Logger = Logger(javaClass)
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
        log.info { "Starting server on port $port" }
        this.serverSocket = ServerSocket(port)
        acceptNewSockets()

    }

    private fun acceptNewSockets() {
        while (isRunning) {
            try {
                val socket = serverSocket.accept()
                log.info { "Got new Connection" }
                handleNewSocket(socket)
            } catch (e: SocketException) {
                log.info { "Connection closed" }
            }

        }
    }

    private fun handleNewSocket(socket: Socket) {
        executorService.submit {
            handleSocket(socket)
        }
    }

    private fun handleSocket(socket: Socket) {

        numberOfConnections.incrementAndGet()
        val clientConnection = ClientConnection(socket)
        openConnections.add(clientConnection)
        try {
            var connectionOpen = true;
            while (connectionOpen) {
                //Todo: Handle any errors that might occur during requests
                val httpRequest = HttpRequestHandler().handleRequest(clientConnection.io)
                connectionOpen = httpRequest.headers.hasConnection(Connection.CLOSE)
                respond(httpRequest, clientConnection)

            }
        } catch (exception: Exception) {
            respondWithError(clientConnection, exception)
        } finally {
            closeConnection(clientConnection)
        }

    }

    private fun closeConnection(clientConnection: ClientConnection) {

        log.info { "Closed connection." }
        clientConnection.close()
        numberOfConnections.decrementAndGet()
        openConnections.remove(clientConnection)
    }

    private fun respondWithError(clientConnection: ClientConnection, exception: Exception) {
        val httpResponse = httpResponseFromException(exception).also {
            if (it.statusLine.status == HttpStatus.INTERNAL_SERVER_ERROR)
                log.error(exception) { "Internal Server Error" }
            else
                log.warn { "Client Error: ${exception.message}" }
        }
        respond(httpResponse, clientConnection)
    }

    private fun httpResponseFromException(exception: Exception) = when (exception) {
        is UnknownTransferEncoding -> HttpResponse.fromStatus(
            HttpStatus.NOT_IMPLEMENTED,
            body = exception.message ?: "No message"
        )

        is UnknownHttpMethod -> HttpResponse.fromStatus(
            HttpStatus.METHOD_NOT_ALLOWED,
            body = exception.message ?: "No message"
        )

        is UriTooLong -> HttpResponse.fromStatus(
            HttpStatus.REQUEST_URI_TOO_LARGE,
            body = exception.message ?: "No message"
        )

        is HeaderNameEndsWithWhiteSpace,
        is InvalidHttpRequestLine,
        is HeaderStartsWithWhiteSpace,
        is InvalidHeaderStructure,
        is MissingHostHeader,
        is TooManyHostHeaders ->
            HttpResponse.badRequest(body = exception.message ?: "No message")

        else ->
            HttpResponse.internalError(body = exception.message ?: "No message")
    }

    private fun respond(httpRequest: HttpRequest, clientConnection: ClientConnection) {


        //Todo: If major version is not supported by this server
        // Answer with A server can send a 505
        //   (HTTP Version Not Supported) response if it wishes, for any reason,
        //   to refuse service of the client's major protocol version.
        // https://www.rfc-editor.org/rfc/rfc7230#page-14
        val status = StatusLine(HttpVersion.DEFAULT_VERSION, HttpStatus.OK)
        val httpResponse = HttpResponse(status, HttpHeaders(), httpRequest.body)
        respond(httpResponse, clientConnection)
    }

    private fun respond(httpResponse: HttpResponse, clientConnection: ClientConnection) {

        addMandatoryHeadersIfMissing(httpResponse)

        httpResponse.writeTo(clientConnection.io)

    }

    private fun addMandatoryHeadersIfMissing(httpResponse: HttpResponse) {
        val headers = httpResponse.headers
        if (!headers.hasDate())
            headers.withDate(Date())

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