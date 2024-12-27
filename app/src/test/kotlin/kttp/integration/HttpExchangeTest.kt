package kttp.integration

import kttp.http.HttpClient
import kttp.http.HttpServer
import kttp.http.protocol.HttpResponse
import kttp.http.protocol.HttpStatus
import kttp.http.protocol.HttpVersion
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
        Thread.sleep(200)
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


    @AfterEach
    fun tearDown() {
        server.clearHandlers()
        server.stop()
    }
}