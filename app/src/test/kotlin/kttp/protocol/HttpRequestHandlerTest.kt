package kttp.protocol

import kttp.http.HttpClient
import kttp.http.HttpRequestHandler
import kttp.http.protocol.HttpHeaders
import kttp.mock.SimpleTestServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.Socket
import kotlin.concurrent.thread

class HttpRequestHandlerTest {
    private lateinit var simpleTestServer: SimpleTestServer
    private val defaultPort = 80

    @BeforeEach
    fun setUp() {
        simpleTestServer = SimpleTestServer(defaultPort)
    }

    @Test
    fun createHttpServerWithInvalidPorts_shouldThrowException() {
        thread {
            Thread.sleep(500)
            //HttpClient.get("http://google.com/", HttpHeaders(
            //        "host" to "localhost"
            //))
            HttpClient.get("http://localhost/", HttpHeaders(
                    "Host" to "localhost"
            ))
        }

        val acceptedClient = simpleTestServer.acceptSocket()
        val request = HttpRequestHandler().handle(acceptedClient)


    }

    @AfterEach
    fun teardown() {
        simpleTestServer.stop()
    }


    private fun createSocket(): Socket {
        return Socket(InetAddress.getLocalHost(), defaultPort);
    }
}