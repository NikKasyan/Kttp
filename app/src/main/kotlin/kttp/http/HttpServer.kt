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



typealias Handler = (HttpExchange) -> Unit

//Todo: Find a better name for this class
data class HttpReqHandler(val path:String, val methods: EnumSet<Method>, val handler: Handler) {
    constructor(path: String, method: Method, handler: Handler): this(path, EnumSet.of(method), handler)
    constructor(path: String, methods: List<Method>, handler: Handler): this(path, EnumSet.noneOf(Method::class.java).also { it.addAll(methods) }, handler)
}

class HttpServer(private val port: Int = 80, maxConcurrentConnections: Int = 20) {


    private lateinit var serverSocket: ServerSocket

    private var isRunning = false


    private val executorService = Executors.newFixedThreadPool(maxConcurrentConnections)

    private val openConnections = ArrayList<ClientConnection>(maxConcurrentConnections)

    private val numberOfConnections: AtomicInteger = AtomicInteger(0)

    private val log: Logger = Logger(javaClass)

    val activeConnections: Int
        get() = numberOfConnections.get()

    private val handlerRegistry = HttpContextRegistry(ArrayList())


    init {
        if (port < 0 || port > 0xFFFF)
            throw IllegalArgumentException("port must be between 0 and ${0xFFFF}")
    }

    fun onGet(path: String, handler: Handler) {
        addHandler(HttpReqHandler(path, EnumSet.of(Method.GET), handler))
    }

    fun onPost(path: String, handler: Handler) {
        addHandler(HttpReqHandler(path, EnumSet.of(Method.POST), handler))
    }

    fun onPut(path: String, handler: Handler) {
        addHandler(HttpReqHandler(path, EnumSet.of(Method.PUT), handler))
    }

    fun onDelete(path: String, handler: Handler) {
        addHandler(HttpReqHandler(path, EnumSet.of(Method.DELETE), handler))
    }

    fun onPatch(path: String, handler: Handler) {
        addHandler(HttpReqHandler(path, EnumSet.of(Method.PATCH), handler))
    }

    fun onHead(path: String, handler: Handler) {
        addHandler(HttpReqHandler(path, EnumSet.of(Method.HEAD), handler))
    }

    fun onConnect(path: String, handler: Handler) {
        addHandler(HttpReqHandler(path, EnumSet.of(Method.CONNECT), handler))
    }

    fun onOptions(path: String, handler: Handler) {
        addHandler(HttpReqHandler(path, EnumSet.of(Method.OPTIONS), handler))
    }

    fun onTrace(path: String, handler: Handler) {
        addHandler(HttpReqHandler(path, EnumSet.of(Method.TRACE), handler))
    }

    fun on(method: Method, path: String, handler: Handler) {
        addHandler(HttpReqHandler(path, method, handler))
    }

    fun on(methods: List<Method>, path: String, handler: Handler) {
        addHandler(HttpReqHandler(path, methods, handler))
    }

    fun on(methods: EnumSet<Method>, path: String, handler: Handler) {
        addHandler(HttpReqHandler(path, methods, handler))
    }

    fun on(path: String, handler: Handler) {
        addHandler(HttpReqHandler(path, EnumSet.allOf(Method::class.java), handler))
    }

    private fun addHandler(httpReqHandler: HttpReqHandler) {
        handlerRegistry.addHandler(httpReqHandler)
    }

    fun removeHandler(path: String) {
        handlerRegistry.removeHandler(path)
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
        addMandatoryHeadersIfMissing(httpResponse)
        httpResponse.writeTo(clientConnection.io)
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
        val httpExchange = HttpExchange(httpRequest, httpResponse, clientConnection.io)
        httpExchange.use {
            val handler = handlerRegistry.getHandlerForRequest(httpRequest)
            handler.handler(httpExchange)
        }
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

private val NOT_FOUND_HANDLER = HttpReqHandler("", EnumSet.allOf(Method::class.java)) {
    val notFound = "Not Found ${it.request.uri.path}"
    it.respond(HttpResponse.notFound(body = notFound))
}
class HttpContextRegistry(private val httpRequestHandlers: MutableList<HttpReqHandler> = mutableListOf()) {

    fun addHandler(httpReqHandler: HttpReqHandler) {
        httpRequestHandlers.add(httpReqHandler)
    }

    fun removeHandler(path: String) {
        httpRequestHandlers.removeIf { it.path == path }
    }

    fun getHandlerForRequest(httpRequest: HttpRequest): HttpReqHandler {
        return httpRequestHandlers.find {
            it.path == httpRequest.uri.path && it.methods.contains(httpRequest.method)
        } ?: getHandlerForRequestByPath(httpRequest)
            ?: getHandlerForFuzzyPath(httpRequest)
            ?: NOT_FOUND_HANDLER

    }

    private fun getHandlerForRequestByPath(httpRequest: HttpRequest): HttpReqHandler? {
        return httpRequestHandlers.find {
            it.path == httpRequest.uri.path
        }
    }

    private fun getHandlerForFuzzyPath(httpRequest: HttpRequest): HttpReqHandler? {
        for (handler in httpRequestHandlers) {
            if(handler.path.contains("*")) {
                val path = handler.path.split("/")
                val requestPath = httpRequest.uri.path.split("/")
                if(path.size == requestPath.size) {
                    var match = true
                    for (i in path.indices) {
                        if(path[i] != "*" && path[i] != requestPath[i]) {
                            match = false
                            break
                        }
                    }
                    if(match)
                        return handler
                }
            }
        }
        return httpRequestHandlers.find {
            it.path.contains("{") && it.path.contains("}")
        }
    }

}