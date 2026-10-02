package kttp.http.server

import kttp.http.protocol.*
import kttp.websocket.InvalidUpgrade
import kttp.io.EndOfStream
import kttp.log.Logger
import kttp.net.ClientConnection
import kttp.net.ConnectionOptions
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger


typealias OnReqHandler = HttpExchange.() -> Unit

//Todo: Find a better name for this class
data class ReqHandler(private var _path: String, val methods: EnumSet<Method>, val handle: OnReqHandler) {
    constructor(path: String, method: Method, httpHandler: OnReqHandler) : this(path, EnumSet.of(method), httpHandler)
    constructor(path: String, methods: List<Method>, httpHandler: OnReqHandler) : this(
        path,
        EnumSet.noneOf(Method::class.java).also { it.addAll(methods) },
        httpHandler
    )

    init {
        if (_path.isEmpty())
            throw IllegalArgumentException("Path cannot be empty")
        _path = _path.replace("//", "/").replace(Regex("\\*{2,}"), "**")
    }

    val path: String
        get() = _path
}

/**
 * @param executorService Runs the connections. Without one, the server creates its own from [HttpServerOptions.maxConcurrentConnections].
 */
class HttpServer(
    private val httpServerOptions: HttpServerOptions = HttpServerOptions.DEFAULT,
    executorService: ExecutorService? = null
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

    constructor(secure: Boolean) : this(HttpServerOptions(secure = secure))
    constructor(port: Int, secure: Boolean) : this(HttpServerOptions(port = port, secure = secure))


    // An executor passed in by the caller may still be used elsewhere, so stop() only shuts down its own
    private val ownsExecutor = executorService == null

    private val executorService: ExecutorService = executorService ?: createExecutor(httpServerOptions.maxConcurrentConnections)

    private lateinit var serverSocket: ServerSocket

    @Volatile
    private var isRunning = false

    private val start = CountDownLatch(1)

    @Volatile
    private var hasStarted = false

    // Changed by every connection's thread
    private val openConnections: MutableSet<ClientConnection> = ConcurrentHashMap.newKeySet()

    private val numberOfConnections: AtomicInteger = AtomicInteger(0)

    private val log: Logger = Logger(javaClass)

    val activeConnections: Int
        get() = numberOfConnections.get()

    private val httpHandlers = ReqHandlers()

    private val connectionOptions = ConnectionOptions(httpServerOptions.socketTimeout)

    fun addHttpReqHandler(httpReqHandler: ReqHandler): HttpServer {
        httpHandlers.addHandler(httpReqHandler)
        return this
    }

    fun removeHandler(path: String) {
        httpHandlers.removeHandler(path)
    }

    fun clearHandlers() {
        httpHandlers.clear()
    }

    fun start() {

        if (isRunning)
            throw IllegalStateException("Server can only be started once")
        isRunning = true
        val hostName = httpServerOptions.hostName
        val port = httpServerOptions.port
        log.info { "Starting server on $hostName:$port" }
        log.info { getBaseUri() }

        try {
            val socketFactory = httpServerOptions.socketFactory
            this.serverSocket = socketFactory.createServerSocket()
            serverSocket.bind(InetSocketAddress(hostName, port))
        } catch (e: Exception) {
            start.countDown()
            throw e
        }
        acceptNewSockets()

    }

    fun waitUntilStarted() {
        if (hasStarted)
            return
        start.await()
        if(!hasStarted)
            throw IllegalStateException("Server did not start")
    }

    private fun acceptNewSockets() {
        while (isRunning) {
            if (!hasStarted) {
                hasStarted = true
                start.countDown()
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
        try {
            executorService.submit {
                handleSocket(socket)
            }
        } catch (e: RejectedExecutionException) {
            // The server was stopped after it accepted the socket
            socket.close()
        }
    }

    private fun handleSocket(socket: Socket) {

        numberOfConnections.incrementAndGet()
        val clientConnection = ClientConnection(socket, connectionOptions)
        openConnections.add(clientConnection)
        try {
            while (true) {
                //Todo: Handle any errors that might occur during requests
                val httpRequest = HttpRequestHandler().handleRequest(clientConnection.io, httpServerOptions.secure)

                val connectionOpen = respond(httpRequest, clientConnection)
                if (!connectionOpen)
                    break

                // The next request starts after this request's body, also if the handler didn't read it
                try {
                    httpRequest.body.discardRemaining()
                } catch (e: Exception) {
                    // The response was already sent, so an invalid body can only be answered by closing the connection
                    log.warn { "Could not read the rest of the request body: ${e.message}" }
                    break
                }
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

        // An unrecognized method is answered with 501 https://www.rfc-editor.org/rfc/rfc9110#section-9.1
        is UnknownHttpMethod -> HttpResponse.fromStatus(
            HttpStatus.NOT_IMPLEMENTED,
            body = exception.message ?: "No message"
        )

        is UriTooLong -> HttpResponse.fromStatus(
            HttpStatus.REQUEST_URI_TOO_LARGE,
            body = exception.message ?: "No message"
        )

        is HttpVersionNotSupported -> HttpResponse.fromStatus(
            HttpStatus.HTTP_VERSION_NOT_SUPPORTED,
            body = exception.message ?: "No message"
        )

        // https://www.rfc-editor.org/rfc/rfc9110#section-15.5.16
        is UnknownContentEncoding,
        is UnsupportedContentEncoding -> HttpResponse.fromStatus(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            body = exception.message ?: "No message"
        )

        // Every other malformed request, for example a body length that can't be determined, is answered with 400
        // https://www.rfc-editor.org/rfc/rfc9112#section-6.3
        is InvalidHttpRequest,
        is InvalidUpgrade ->
            HttpResponse.badRequest(body = exception.message ?: "No message")

        // The exception message may contain details the client shouldn't see; it is logged instead in respondWithError
        else ->
            HttpResponse.internalError(body = "Internal Server Error")
    }

    /**
     * Runs the handler for the request.
     * @return Whether the connection stays open for the next request
     */
    private fun respond(httpRequest: HttpRequest, clientConnection: ClientConnection): Boolean {
        val httpRequestHandler = httpHandlers.getHandlerForRequest(httpRequest) ?: NOT_FOUND_HANDLER()
        val httpExchange = HttpExchange(httpRequest, createDefaultResponseHeaders(httpRequest), clientConnection.io)
        try {
            httpRequestHandler.handle(httpExchange)
            // Ends a response the handler started with write, or sends one if the handler sent nothing
            httpExchange.close()
        } catch (e: Exception) {
            // An error response now would be read as part of the started one. Closing the connection
            // leaves that response incomplete, which tells the client that it failed https://www.rfc-editor.org/rfc/rfc9112#section-8
            if (httpExchange.hasStartedResponse) {
                log.error(e) { "Handler failed after its response was started" }
                return false
            }
            respondWithError(clientConnection, e)
        }

        // After a 101 the connection no longer speaks HTTP/1.1 https://www.rfc-editor.org/rfc/rfc9110#section-15.2.2
        if (httpExchange.switchedProtocols)
            return false
        // A server that sends "close" must close the connection after the response https://www.rfc-editor.org/rfc/rfc9112#section-9.6
        return isPersistent(httpRequest) && !httpExchange.closesConnection
    }

    /**
     * Whether the client keeps the connection open after the response https://www.rfc-editor.org/rfc/rfc9112#section-9.3
     */
    private fun isPersistent(request: HttpRequest): Boolean {
        if (request.headers.hasConnection(Connection.CLOSE))
            return false
        // HTTP/1.1 persists by default, HTTP/1.0 only with keep-alive
        if (request.httpVersion.minorVersion >= 1)
            return true
        return request.headers.hasConnection(Connection.KEEP_ALIVE)
    }


    fun stop() {
        isRunning = false
        hasStarted = false
        start.countDown()
        // Closed first, so that no connection is accepted after the executor is shut down
        if (::serverSocket.isInitialized)
            serverSocket.close()
        if (ownsExecutor)
            executorService.shutdown()
        openConnections.toList().forEach { it.close() }
        openConnections.clear()
        numberOfConnections.set(0)
    }

    fun createDefaultResponseHeaders(
        request: HttpRequest? = null,
        headers: HttpHeaders = HttpHeaders()
    ): HttpHeaders {
        return headers.also {
            it.withServer("Kttp")
            it.withDate()
            if (request != null) {
                // https://www.rfc-editor.org/rfc/rfc9112#section-9.6
                if (!isPersistent(request))
                    it.withConnection(Connection.CLOSE)
                // An HTTP/1.0 client only keeps the connection if the response has keep-alive https://www.rfc-editor.org/rfc/rfc9112#appendix-C.2.2
                else if (request.httpVersion.minorVersion == 0)
                    it.withConnection(Connection.KEEP_ALIVE)
            }
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

    private fun createExecutor(maxConcurrentConnections: Int): ExecutorService {
        // -1 stands for unlimited
        if (maxConcurrentConnections == -1)
            return Executors.newCachedThreadPool()
        return Executors.newFixedThreadPool(maxConcurrentConnections)
    }

    fun getHost(): String {
        if (httpServerOptions.port == 80 || httpServerOptions.port == 443)
            return httpServerOptions.hostName
        return "${httpServerOptions.hostName}:${httpServerOptions.port}"
    }

}

class ReqHandlers(private val httpRequestHandlers: MutableList<ReqHandler> = mutableListOf()) {

    fun addHandler(httpReqHandler: ReqHandler) {
        if(httpRequestHandlers.any { it.path == httpReqHandler.path })
            throw IllegalArgumentException("Path: \"${httpReqHandler.path}\" already exists")
        httpRequestHandlers.add(httpReqHandler)
    }

    fun removeHandler(path: String) {
        httpRequestHandlers.removeIf { it.path == path }
    }

    fun getHandlerForRequest(httpRequest: HttpRequest): ReqHandler? {
        return httpRequestHandlers.find {
            it.path == httpRequest.uri.path && it.methods.contains(httpRequest.method)
        } ?: getHandlerByPath(httpRequest.uri.path)

    }

    fun getHandlerByPath(path: String): ReqHandler? {
        return httpRequestHandlers.find {
            it.path == path
        } ?: getHandlerForFuzzyPath(path)
    }

    private fun getHandlerForFuzzyPath(path: String): ReqHandler? {
        val list = mutableListOf<Pair<ReqHandler, Int>>()
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

