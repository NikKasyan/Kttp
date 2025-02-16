package kttp.integration

import kttp.http.HttpClient
import kttp.http.server.HttpServer
import kttp.http.protocol.*
import kttp.http.server.onGet
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.concurrent.thread
import kotlin.test.Test

class HttpExchangeTest {

    private val server = HttpServer(8080)
    private val client = HttpClient(server.getBaseUri())

    init {
        thread {
            server.start()
        }
        server.waitUntilStarted()
    }


    @Test
    fun serverSendsHelloWorld() {
        val message = "Hello, World!"

        server.onGet("/") {
            respond(message)
        }

        val response = client.get()

        assertEquals(HttpStatus.OK, response.statusLine.status)
        assertEquals(HttpVersion.DEFAULT_VERSION, response.statusLine.httpVersion)
        assertTrue(response.headers.hasServer())
        assertTrue(response.headers.hasDate())
        assertEquals(response.headers.contentLength, message.length.toLong())
        assertEquals(message, response.body.readAsString())

    }

    @Test
    fun serverSendsBadRequest() {
        server.onGet("/") {
            respond(HttpResponse.badRequest())
        }

        val response = client.get()

        assertEquals(HttpStatus.BAD_REQUEST, response.statusLine.status)
        assertEquals(HttpVersion.DEFAULT_VERSION, response.statusLine.httpVersion)
        assertTrue(response.headers.hasServer())
        assertTrue(response.headers.hasDate())
    }

    @Test
    fun serverSendsInternalServerError() {
        server.onGet("/") {
            throw Exception("Internal Server Error")
        }

        val response = client.get()

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusLine.status)
        assertEquals(HttpVersion.DEFAULT_VERSION, response.statusLine.httpVersion)
        assertTrue(response.headers.hasServer())
        assertTrue(response.headers.hasDate())
    }

    @Test
    fun serverEncodesBodyWithGzip() {
        server.onGet("/") {
            respond(HttpResponse.ok(body = "Hello, World!", headers = HttpHeaders().withContentEncoding(ContentEncoding.GZIP)))
        }

        val response = client.get()

        assertEquals(HttpStatus.OK, response.statusLine.status)
        assertEquals(HttpVersion.DEFAULT_VERSION, response.statusLine.httpVersion)
        assertTrue(response.headers.hasServer())
        assertTrue(response.headers.hasDate())
        assertEquals(ContentEncoding.GZIP, response.headers.contentEncoding)
        assertEquals("Hello, World!", response.body.readAsString())
    }


    @AfterEach
    fun tearDown() {
        server.clearHandlers()
        server.stop()
    }
}