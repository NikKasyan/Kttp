package kttp

import kttp.http.HttpServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.ConnectException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


@Timeout(value = 5, unit = TimeUnit.SECONDS)
class HttpServerConnectionTest {


    private lateinit var httpServer: HttpServer
    private val defaultPort = 80

    @BeforeEach
    fun setUp() {
        this.httpServer = HttpServer(defaultPort)
    }

    @Test
    fun createHttpServerWithInvalidPorts_shouldThrowException() {
        assertThrows<IllegalArgumentException> { HttpServer(0x10000) }
        assertThrows<IllegalArgumentException> { HttpServer(-1) }
    }

    @Test
    fun startServerTwice_shouldThrowIllegalStateException() {
        assertThrows<IllegalStateException> {
            startHttpServer()
            Thread.sleep(500)
            httpServer.start()
        }
    }

    @Test
    fun startHttpServer_acceptsNewSockets() {
        startHttpServer()
        val socket = createSocket()

        assertTrue(socket.isConnected, "Client should connect after start")
    }

    @Test
    fun startHttpServer_thenStopServer_acceptNoSockets() {
        startHttpServer()
        Thread.sleep(500)
        this.httpServer.stop()
        assertThrows<ConnectException> { createSocket() }
    }

    @Test
    fun startHttpServer_thenConnect_AndStopServer_socketShouldDisconnect() {
        startHttpServer()
        createSocket()
        this.httpServer.stop()
    }

    @Test
    fun connectWithClientBeforeStart_shouldNotAcceptSocket() {
        assertThrows<ConnectException> { createSocket() }
    }

    @Test
    fun connectWithClient_ThenDisconnect_Server_shouldNotFail() {
        startHttpServer()
        createSocket().close()
        Thread.sleep(500)
        assertEquals(0, httpServer.currentNumberOfConnections)

    }

    @Test
    fun connectWith21Client_LastClientShouldNotConnect() {
        startHttpServer()
        for (i in 0..20)
            thread { createSocket() }
        Thread.sleep(500)
        assertEquals(20, httpServer.currentNumberOfConnections)

    }

    @AfterEach
    fun teardown() {
        httpServer.stop()
    }

    private fun startHttpServer() =
        thread {
            httpServer.start()
        }


    private fun createSocket(): Socket {
        return Socket(InetAddress.getLocalHost(), defaultPort);
    }
}
