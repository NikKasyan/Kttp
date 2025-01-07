package kttp.http

import kttp.http.protocol.*
import kttp.io.EndOfStream
import kttp.log.Logger
import kttp.net.ClientConnection
import kttp.net.ConnectionOptions
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger


typealias Handler = HttpExchange.() -> Unit

//Todo: Find a better name for this class
data class HttpReqHandler(val path: String, val methods: EnumSet<Method>, val handler: Handler) {
    constructor(path: String, method: Method, handler: Handler) : this(path, EnumSet.of(method), handler)
    constructor(path: String, methods: List<Method>, handler: Handler) : this(
        path,
        EnumSet.noneOf(Method::class.java).also { it.addAll(methods) },
        handler
    )
}

class HttpServer(
    private val httpServerOptions: HttpServerOptions = HttpServerOptions.DEFAULT
) {

    constructor(port: Int) : this(HttpServerOptions(port = port))

    constructor(port: Int, hostName: String) : this(HttpServerOptions(port = port, hostName = hostName))

    constructor(port: Int, hostName: String, maxConcurrentConnections: Int) : this(
        HttpServerOptions(
            port = port,
            hostName = hostName,
            maxConcurrentConnections = maxConcurrentConnections
        )
    )


    private lateinit var serverSocket: ServerSocket

    private var isRunning = false

    private val start = Semaphore(0)

    private var hasStarted = false


    private val executorService = Executors.newFixedThreadPool(httpServerOptions.maxConcurrentConnections)

    private val openConnections = ArrayList<ClientConnection>(httpServerOptions.maxConcurrentConnections)

    private val numberOfConnections: AtomicInteger = AtomicInteger(0)

    private val log: Logger = Logger(javaClass)

    val activeConnections: Int
        get() = numberOfConnections.get()

    private val handlerRegistry = HttpContextRegistry(ArrayList())

    private val connectionOptions = ConnectionOptions(httpServerOptions.socketTimeout)

    fun onGet(path: String, handler: Handler) {
        addRequestHandler(HttpReqHandler(path, EnumSet.of(Method.GET), handler))
    }

    fun onPost(path: String, handler: Handler) {
        addRequestHandler(HttpReqHandler(path, EnumSet.of(Method.POST), handler)).apply { }
    }

    fun onPut(path: String, handler: Handler) {
        addRequestHandler(HttpReqHandler(path, EnumSet.of(Method.PUT), handler))
    }

    fun onDelete(path: String, handler: Handler) {
        addRequestHandler(HttpReqHandler(path, EnumSet.of(Method.DELETE), handler))
    }

    fun onPatch(path: String, handler: Handler) {
        addRequestHandler(HttpReqHandler(path, EnumSet.of(Method.PATCH), handler))
    }

    fun onHead(path: String, handler: Handler) {
        addRequestHandler(HttpReqHandler(path, EnumSet.of(Method.HEAD), handler))
    }

    fun onConnect(path: String, handler: Handler) {
        addRequestHandler(HttpReqHandler(path, EnumSet.of(Method.CONNECT), handler))
    }

    fun onOptions(path: String, handler: Handler) {
        addRequestHandler(HttpReqHandler(path, EnumSet.of(Method.OPTIONS), handler))
    }

    fun onTrace(path: String, handler: Handler) {
        addRequestHandler(HttpReqHandler(path, EnumSet.of(Method.TRACE), handler))
    }

    fun on(method: Method, path: String, handler: Handler) {
        addRequestHandler(HttpReqHandler(path, method, handler))
    }

    fun on(methods: List<Method>, path: String, handler: Handler) {
        addRequestHandler(HttpReqHandler(path, methods, handler))
    }

    fun on(methods: EnumSet<Method>, path: String, handler: Handler) {
        addRequestHandler(HttpReqHandler(path, methods, handler))
    }

    fun on(path: String, handler: Handler) {
        addRequestHandler(HttpReqHandler(path, EnumSet.allOf(Method::class.java), handler))
    }

    fun removeHandler(path: String) {
        handlerRegistry.removeHandler(path)
    }

    fun clearHandlers() {
        handlerRegistry.clear()
    }

    fun start() {

        if (isRunning)
            throw IllegalStateException("Server can only be started once")
        isRunning = true
        val hostName = httpServerOptions.hostName
        val port = httpServerOptions.port
        log.info { "Starting server on $hostName:$port" }
        log.info { getBaseUri() }
        val socketFactory = httpServerOptions.socketFactory
        this.serverSocket = socketFactory.createServerSocket()
        serverSocket.bind(InetSocketAddress(hostName, port))
        acceptNewSockets()

    }

    fun waitUntilStarted() {
        if(hasStarted)
            return
        start.acquire()
    }

    private fun acceptNewSockets() {
        while (isRunning) {
            if (!hasStarted) {
                start.release(start.queueLength)
                hasStarted = true
            }
            try {
                val socket = serverSocket.accept()
                log.debug { "Got new Connection" }
                handleNewSocket(socket)
            } catch (e: SocketException) {
                log.debug { "Connection closed" }
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
        val clientConnection = ClientConnection(socket, connectionOptions)
        openConnections.add(clientConnection)
        try {
            var connectionOpen = true;
            while (connectionOpen) {
                //Todo: Handle any errors that might occur during requests
                val httpRequest = HttpRequestHandler().handleRequest(clientConnection.io)
                connectionOpen = !httpRequest.headers.hasConnection(Connection.CLOSE)
                respond(httpRequest, clientConnection)

            }
        } catch (e: EndOfStream) {
            log.debug { "End of Stream" }
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
            createDefaultResponseHeaders(headers = it.headers)
        }
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
        val httpResponse = HttpResponse.ok(createDefaultResponseHeaders(httpRequest))
        val httpExchange = HttpExchange(httpRequest, httpResponse, clientConnection.io)

        httpExchange.use {
            try {
                val handler = handlerRegistry.getHandlerForRequest(httpRequest)
                handler.handler(httpExchange)
            } catch (e: Exception) {
                respondWithError(clientConnection, e)
            }
        }
    }

    fun addRequestHandler(httpReqHandler: HttpReqHandler) {
        handlerRegistry.addHandler(httpReqHandler)
    }


    fun stop() {
        isRunning = false
        hasStarted = false
        start.release(start.queueLength)
        start.acquire()
        executorService.shutdown()
        openConnections.toList().forEach { it.close() }
        openConnections.clear()
        numberOfConnections.set(0)
        if (::serverSocket.isInitialized)
            serverSocket.close()

    }

    private fun createDefaultResponseHeaders(
        request: HttpRequest? = null,
        headers: HttpHeaders = HttpHeaders()
    ): HttpHeaders {
        return headers.also {
            it.withServer("Kttp")
            it.withDate()
            if (httpServerOptions.transferOptions.shouldAlwaysCompress && request != null) {
                if (request.headers.acceptsEncoding(ContentEncoding.GZIP))
                    it.withContentEncoding(ContentEncoding.GZIP)
                else if (request.headers.acceptsEncoding(ContentEncoding.DEFLATE))
                    it.withContentEncoding(ContentEncoding.DEFLATE)
            }
        }
    }

    fun getBaseUri(): String {
        return if (httpServerOptions.secure)
            "https://${getHost()}"
        else
            "http://${getHost()}"
    }

    fun getHost(): String {
        if (httpServerOptions.port == 80 || httpServerOptions.port == 443)
            return httpServerOptions.hostName
        return "${httpServerOptions.hostName}:${httpServerOptions.port}"
    }

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
        } ?: getHandlerByPath(httpRequest.uri.path)
        ?: NOT_FOUND_HANDLER()

    }

    fun getHandlerByPath(path: String): HttpReqHandler? {
        return httpRequestHandlers.find {
            it.path == path
        } ?: getHandlerForFuzzyPath(path)
    }

    private fun getHandlerForFuzzyPath(path: String): HttpReqHandler? {
        val list = mutableListOf<Pair<HttpReqHandler, Int>>()
        for (handler in httpRequestHandlers) {
            if (handler.path.contains("*")) {
                val handlerPath = handler.path.split("/").filter { it.isNotEmpty() }
                val requestPath = path.split("/").filter { it.isNotEmpty() }
                var pathIndex = 0
                var requestIndex = 0
                while (pathIndex < handlerPath.size && requestIndex < requestPath.size) {
                    if (handlerPath[pathIndex] != requestPath[requestIndex] && !handlerPath[pathIndex].contains("*"))
                        break
                    if (handlerPath[pathIndex] == "**") {
                        list.add(handler to requestIndex)
                        break
                    }
                    pathIndex++
                    requestIndex++
                }
                if (requestPath.size + 1 == handlerPath.size && handlerPath.last().contains("*"))
                    list.add(handler to requestIndex)
                else if (pathIndex == handlerPath.size && requestIndex == requestPath.size)
                    list.add(handler to requestIndex)

            }
        }
        list.sortWith { o1, o2 ->
            val first = o1.second.compareTo(o2.second)

            if (first == 0)
                if (o1.first.path.contains("**") && !o2.first.path.contains("**"))
                    -1
                else if (!o1.first.path.contains("**") && o2.first.path.contains("**"))
                    1
                else
                    -o1.first.path.length.compareTo(o2.first.path.length)
            else
                -first
        }
        return list.firstOrNull()?.first
    }

    fun clear() {
        httpRequestHandlers.clear()
    }

}