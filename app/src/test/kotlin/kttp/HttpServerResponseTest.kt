package kttp

import kttp.http.HttpServer
import kttp.http.protocol.HttpStatus
import kttp.http.protocol.HttpVersion
import kttp.http.protocol.StatusLine
import kttp.net.ClientConnection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class HttpServerResponseTest {
    private val port = 8080

    private lateinit var server: HttpServer
    private lateinit var client: ClientConnection

    @BeforeEach
    fun setup() {
        thread {
            server = HttpServer(port)
            server.start()
        }
        Thread.sleep(300)
        client = ClientConnection(Socket("localhost", port))
    }

    @Test
    fun testServerRespondsWith405MethodNotAllowedOnInvalidMethod() {
        client.io.writeln("INVALID / HTTP/1.1")

        val statusLineString = client.io.readLine()
        val statusLine = StatusLine(statusLineString)

        assertEquals(HttpVersion.DEFAULT_VERSION, statusLine.httpVersion)
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, statusLine.status)
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED.message, statusLine.message)
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