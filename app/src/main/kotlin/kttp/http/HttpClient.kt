package kttp.http

import kttp.http.protocol.*
import kttp.http.server.MissingHostHeader
import kttp.io.IOStream
import kttp.log.Logger
import kttp.security.SSL
import java.net.Socket
import java.net.URI
import javax.net.SocketFactory
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket

// Not Thread Safe
class HttpClient(baseURL: String, verifyCertificate: Boolean = true) {

    private val baseURI: URI
    private val isSecure: Boolean
        get() = baseURI.scheme == "https"
    private val socketFactory: SocketFactory
    private lateinit var connection: HttpClientConnection

    companion object {
        fun get(requestUrl: String, httpHeaders: HttpHeaders = createDefaultHeaders()): HttpResponse {
            return HttpClient(requestUrl).get(httpHeaders = httpHeaders.withConnection(Connection.CLOSE))
        }
    }


    private val log = Logger(javaClass)

    init {

        this.baseURI = fixMissingScheme(URI(baseURL))
        this.socketFactory = if (isSecure) SSL.getSecureSocketFactory(verifyCertificate) else SocketFactory.getDefault()
        if (!this.baseURI.isAbsolute)
            TODO("Throw real exception when baseURI is not absolute")
    }

    private fun fixMissingScheme(uri: URI): URI {
        if (uri.scheme == null)
            if (!uri.scheme.startsWith("https"))
                return URI("https://${uri}")
            else if (!uri.scheme.startsWith("http"))
                return URI("http://${uri}")
        return uri
    }

    fun get(requestUrl: String = "/", httpHeaders: HttpHeaders = createDefaultHeaders()): HttpResponse {
        return request(Method.GET, requestUrl, httpHeaders)
    }


    fun request(method: Method, requestUrl: String, httpHeaders: HttpHeaders = createDefaultHeaders()): HttpResponse {
        val request = HttpRequest.from(method, URI(requestUrl), httpHeaders)
        return request(request)
    }

    fun request(request: HttpRequest): HttpResponse {
        ensureOpenConnection()

        val httpHeaders = request.headers
        if (!httpHeaders.hasHost())
            httpHeaders.withHost(baseURI)
        if (!request.method.allowsBody())
            httpHeaders.removeContentLength()

        return connection.request(request)
    }

    private fun ensureOpenConnection() {
        if (!::connection.isInitialized)
            connection = HttpClientConnection(createSocket())
    }

    private fun createSocket(): Socket {
        return socketFactory.createSocket(baseURI.host, getPort()).also { doHandshake(it) }
    }

    private fun doHandshake(socket: Socket) {

        if (isSecure) {
            try {
                (socket as SSLSocket).startHandshake()
            } catch (e: SSLHandshakeException) {
                log.error { "Handshake failed: ${e.message}" }
                if (e.message?.contains("PKIX path building failed") == true) {
                    throw CertificateException(e)
                }
                socket.close()
            }
        }
    }


    private fun getPort(): Int {
        if (baseURI.port != -1)
            return baseURI.port
        if (baseURI.scheme == "http")
            return 80
        if (baseURI.scheme == "https")
            return 443

        return -1
    }

}


class HttpClientConnection(val io: IOStream): AutoCloseable {

    private var isClosed = false

    constructor(socket: Socket): this(IOStream(socket.getInputStream(), socket.getOutputStream()))


    fun request(request: HttpRequest): HttpResponse {
        if(!request.headers.hasHost())
            throw MissingHostHeader()
        io.writeFromStream(request.asStream())
        return readResponse()
    }

    fun readResponse(): HttpResponse {
        val statusLineString = io.readLine()
        val statusLine = StatusLine(statusLineString)
        val headers = readHeaders(io)
        val body = HttpBody.withDecoding(io, headers)
        return HttpResponse(statusLine, headers, body)
    }



    override fun close() {
        if (isClosed)
            return
        isClosed = true
        try {
            io.close()
        } catch (e: Exception){
            //Ignore
        }
    }

}
private fun createDefaultHeaders() = HttpHeaders {
    withUserAgent("Kttp/1.0")
    withAcceptEncoding(ContentEncoding.GZIP, ContentEncoding.DEFLATE)
    withAccept("*/*")
    withConnection(Connection.KEEP_ALIVE)
}

class CertificateException(cause: Throwable) : RuntimeException(
    "Invalid certificate! You can disable certification by setting verify=false in the HTTPClient. Or fixing your certificate",
    cause
)
