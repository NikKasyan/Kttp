package kttp

import kttp.http.server.HttpServer
import kttp.http.protocol.Connection
import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.HttpResponse
import kttp.http.protocol.HttpStatus
import kttp.http.protocol.HttpVersion
import kttp.http.protocol.StatusLine
import kttp.http.protocol.readHeaders
import kttp.http.server.onGet
import kttp.http.server.onPost
import kttp.net.ClientConnection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpServerResponseTest {
    private val port = 8080

    private var server: HttpServer = HttpServer(port)
    private lateinit var client: ClientConnection

    init {
        server.onGet("/partial") {
            write("partial")
            throw IllegalStateException("Failed after the response was started")
        }.onPost("/echo") {
            respond(request.body.readAsString())
        }.onGet("/close") {
            respond(HttpResponse.ok(HttpHeaders().withConnection(Connection.CLOSE), "closing"))
        }.onGet("/upgrade") {
            respond(HttpResponse.fromStatus(HttpStatus.SWITCHING_PROTOCOLS, HttpHeaders().withConnection(Connection.UPGRADE).withUpgrade("test")))
        }
        thread {
            server.start()
        }
        server.waitUntilStarted()
    }
    @BeforeEach
    fun setup() {
        client = ClientConnection(Socket("localhost", port))
    }

    @Test
    fun unknownMethod_getsNotImplemented() {
        client.io.writeln("INVALID / HTTP/1.1")

        val statusLineString = client.io.readLine()
        val statusLine = StatusLine(statusLineString)

        assertEquals(HttpVersion.DEFAULT_VERSION, statusLine.httpVersion)
        assertEquals(HttpStatus.NOT_IMPLEMENTED, statusLine.status)
        assertEquals(HttpStatus.NOT_IMPLEMENTED.message, statusLine.message)
    }

    @Test
    fun tooLongUri_shouldRespondWith414UriTooLong() {
        val uri = "/".repeat(10000)
        client.io.writeln("GET $uri HTTP/1.1")

        val statusLineString = client.io.readLine()
        val statusLine = StatusLine(statusLineString)

        assertEquals(HttpVersion.DEFAULT_VERSION, statusLine.httpVersion)
        assertEquals(HttpStatus.REQUEST_URI_TOO_LARGE, statusLine.status)
        assertEquals(HttpStatus.REQUEST_URI_TOO_LARGE.message, statusLine.message)

    }

    @Test
    fun serverSends400ResponseOnMissingHost(){
        client.io.writeln("GET / HTTP/1.1")
        client.io.writeln("User-Agent: TestClient/7.68.0")
        client.io.writeln("Accept: */*")
        client.io.writeln()

        val statusLineString = client.io.readLine()
        val statusLine = StatusLine(statusLineString)

        assertEquals(HttpVersion.DEFAULT_VERSION, statusLine.httpVersion)
        assertEquals(HttpStatus.BAD_REQUEST, statusLine.status)

    }

    @Test
    fun serverSends501OnUnknownTransferEncoding(){
        client.io.writeln("GET / HTTP/1.1")
        client.io.writeln("User-Agent: TestClient/7.68.0")
        client.io.writeln("Accept: */*")
        client.io.writeln("Host: localhost:8080")
        client.io.writeln("Transfer-Encoding: unknown")
        client.io.writeln()

        val statusLineString = client.io.readLine()
        val statusLine = StatusLine(statusLineString)

        assertEquals(HttpVersion.DEFAULT_VERSION, statusLine.httpVersion)
        assertEquals(HttpStatus.NOT_IMPLEMENTED, statusLine.status)


    }

    @Test
    fun requestWithoutConnectionHeaderToUnknownPath_getsNotFound(){
        client.io.writeln("GET /unknown HTTP/1.1")
        client.io.writeln("Host: localhost:8080")
        client.io.writeln()

        val statusLine = StatusLine(client.io.readLine())

        assertEquals(HttpStatus.NOT_FOUND, statusLine.status)
    }

    @Test
    fun errorResponse_endsAfterContentLength(){
        client.io.writeln("GET / HTTP/1.1")
        client.io.writeln()

        val statusLine = StatusLine(client.io.readLine())
        val headers = readHeaders(client.io)
        val body = client.io.readNBytes(headers.contentLength!!.toInt())

        assertEquals(HttpStatus.BAD_REQUEST, statusLine.status)
        assertEquals(headers.contentLength, body.size.toLong())
        // The server closes the connection after an error, so nothing may follow the body
        assertEquals(0, client.io.readAllBytes().size)
    }

    private val hiddenRequest = "GET /hidden HTTP/1.1\r\nHost: localhost:8080\r\n\r\n"

    private fun sendSecondRequestAndReadAllResponses(): String {
        client.io.write("GET /second HTTP/1.1\r\nHost: localhost:8080\r\nConnection: close\r\n\r\n")
        return client.io.readAllBytes().toString(Charsets.US_ASCII)
    }

    @Test
    fun unreadPostBody_isNotParsedAsRequest(){
        client.io.write("POST /unknown HTTP/1.1\r\nHost: localhost:8080\r\nContent-Length: ${hiddenRequest.length}\r\n\r\n$hiddenRequest")

        val responses = sendSecondRequestAndReadAllResponses()

        assertEquals(2, Regex("HTTP/1.1 404").findAll(responses).count())
        assertFalse(responses.contains("/hidden"))
    }

    @Test
    fun getBody_isNotParsedAsRequest(){
        client.io.write("GET /unknown HTTP/1.1\r\nHost: localhost:8080\r\nContent-Length: ${hiddenRequest.length}\r\n\r\n$hiddenRequest")

        val responses = sendSecondRequestAndReadAllResponses()

        assertEquals(2, Regex("HTTP/1.1 404").findAll(responses).count())
        assertFalse(responses.contains("/hidden"))
    }

    @Test
    fun differentContentLengths_getBadRequest(){
        client.io.write("POST / HTTP/1.1\r\nHost: localhost:8080\r\nContent-Length: 5\r\nContent-Length: 10\r\n\r\nhello")

        val statusLine = StatusLine(client.io.readLine())

        assertEquals(HttpStatus.BAD_REQUEST, statusLine.status)
    }

    private fun countResponses(responses: String) = Regex("HTTP/1.1 \\d{3}").findAll(responses).count()

    @Test
    fun handlerErrorAfterWrite_closesConnectionWithoutSecondResponse(){
        client.io.write("GET /partial HTTP/1.1\r\nHost: localhost:8080\r\n\r\n")

        val responses = client.io.readAllBytes().toString(Charsets.US_ASCII)

        assertEquals(1, countResponses(responses))
        // Without the last chunk the client can tell that the response is incomplete
        assertFalse(responses.endsWith("0\r\n\r\n"))
    }

    @Test
    fun lineFolding_getsBadRequest(){
        client.io.write("GET / HTTP/1.1\r\nHost: localhost:8080\r\nX-Folded: a\r\n b\r\n\r\n")

        assertEquals(HttpStatus.BAD_REQUEST, StatusLine(client.io.readLine()).status)
    }

    @Test
    fun hostWithInvalidPort_getsBadRequest(){
        client.io.write("GET / HTTP/1.1\r\nHost: localhost:abc\r\n\r\n")

        assertEquals(HttpStatus.BAD_REQUEST, StatusLine(client.io.readLine()).status)
    }

    @Test
    fun ipv6Host_isAccepted(){
        client.io.write("GET /unknown HTTP/1.1\r\nHost: [::1]:8080\r\n\r\n")

        assertEquals(HttpStatus.NOT_FOUND, StatusLine(client.io.readLine()).status)
    }

    @Test
    fun queryParameterWithoutValue_isAccepted(){
        client.io.write("GET /unknown?flag HTTP/1.1\r\nHost: localhost:8080\r\n\r\n")

        assertEquals(HttpStatus.NOT_FOUND, StatusLine(client.io.readLine()).status)
    }

    @Test
    fun http2Request_getsHttpVersionNotSupported(){
        client.io.writeln("GET / HTTP/2.0")

        assertEquals(HttpStatus.HTTP_VERSION_NOT_SUPPORTED, StatusLine(client.io.readLine()).status)
    }

    @Test
    fun unsupportedContentEncoding_getsUnsupportedMediaType(){
        client.io.write("POST /echo HTTP/1.1\r\nHost: localhost:8080\r\nContent-Encoding: br\r\nContent-Length: 0\r\n\r\n")

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, StatusLine(client.io.readLine()).status)
    }

    @Test
    fun invalidChunkSize_getsBadRequest(){
        client.io.write("POST /echo HTTP/1.1\r\nHost: localhost:8080\r\nTransfer-Encoding: chunked\r\n\r\nzz\r\nhello\r\n0\r\n\r\n")

        assertEquals(HttpStatus.BAD_REQUEST, StatusLine(client.io.readLine()).status)
    }

    @Test
    fun http10RequestWithoutKeepAlive_closesConnection(){
        client.io.write("GET /unknown HTTP/1.0\r\nHost: localhost:8080\r\n\r\n")

        assertEquals(HttpStatus.NOT_FOUND, StatusLine(client.io.readLine()).status)
        assertTrue(readHeaders(client.io).hasConnection(Connection.CLOSE))
        // Returns only because the server closed the connection
        client.io.readAllBytes()
    }

    @Test
    fun http10RequestWithKeepAlive_keepsConnectionOpen(){
        client.io.write("GET /unknown HTTP/1.0\r\nHost: localhost:8080\r\nConnection: keep-alive\r\n\r\n")

        assertEquals(HttpStatus.NOT_FOUND, StatusLine(client.io.readLine()).status)
        val headers = readHeaders(client.io)
        assertTrue(headers.hasConnection(Connection.KEEP_ALIVE))
        client.io.readNBytes(headers.contentLength!!.toInt())

        assertEquals(1, countResponses(sendSecondRequestAndReadAllResponses()))
    }

    @Test
    fun responseWithConnectionClose_closesConnection(){
        client.io.write("GET /close HTTP/1.1\r\nHost: localhost:8080\r\n\r\n")

        // Returns only because the server closed the connection
        val responses = client.io.readAllBytes().toString(Charsets.US_ASCII)

        assertEquals(1, countResponses(responses))
    }

    @Test
    fun switchingProtocols_endsHttpOnTheConnection(){
        client.io.write("GET /upgrade HTTP/1.1\r\nHost: localhost:8080\r\n\r\n")

        // Returns only because the server stopped reading HTTP and closed the connection
        val responses = client.io.readAllBytes().toString(Charsets.US_ASCII)

        assertEquals(1, countResponses(responses))
        assertTrue(responses.startsWith("HTTP/1.1 101"))
    }

    @AfterEach
    fun tearDown() {
        server.stop()
        try {
            client.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}