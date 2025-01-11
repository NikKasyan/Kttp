package kttp.http

import kttp.http.protocol.*
import kttp.http.server.MissingHostHeader
import kttp.io.IOStream
import kttp.log.Logger
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

class HttpClient(baseURL: String, verifyCertificate: Boolean = true) {

    private val baseURI: URI
    private val isSecure: Boolean
        get() = baseURI.scheme == "https"
    private val socketFactory: SocketFactory


    companion object {
        fun get(requestUrl: String, httpHeaders: HttpHeaders = createDefaultHeaders()): HttpResponse {
            return HttpClient(requestUrl).get(httpHeaders = httpHeaders)
        }
    }

    private val log = Logger(javaClass)

    init {
        this.baseURI = fixMissingScheme(URI(baseURL))
        this.socketFactory = if (isSecure) SSL.getSocketFactory(verifyCertificate) else SocketFactory.getDefault()
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
    fun connect(): HttpClientConnection {
        return HttpClientConnection(createSocket())
    }

    fun get(requestUrl: String = "/", httpHeaders: HttpHeaders = createDefaultHeaders()): HttpResponse {
        return request(Method.GET, requestUrl, httpHeaders)
    }


    fun request(method: Method, requestUrl: String, httpHeaders: HttpHeaders = createDefaultHeaders()): HttpResponse {
        val connection = connect()

        if (!httpHeaders.hasHost())
            httpHeaders.withHost(baseURI)
        if (!method.allowsBody())
            httpHeaders.removeContentLength()

        val request = HttpRequest.from(method, URI(requestUrl), httpHeaders)
        return connection.request(request)
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


class HttpClientConnection(private val socket: Socket): AutoCloseable {
    val io = IOStream(socket.getInputStream(), socket.getOutputStream())

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
        try{
            socket.close()
        } catch (e: Exception){
            //Ignore
        }
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
    withConnection("close")
}


private object SSL {
    private val insecureContext = SSLContext.getInstance("TLS")

    init {
        insecureContext.init(null, arrayOf(AllTrustManager()), SecureRandom())
    }

    fun getSocketFactory(verify: Boolean): SSLSocketFactory {
        return if (verify) SSLContext.getDefault().socketFactory else insecureContext.socketFactory
    }

    class AllTrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {
        }

        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {
        }

        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
            return arrayOf()
        }
    }
}

class CertificateException(cause: Throwable) : RuntimeException(
    "Invalid certificate! You can disable certification by setting verify=false in the HTTPClient. Or fixing your certificate",
    cause
)