package kttp

import kttp.http.HttpServer
import kttp.net.ClientConnection
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

    init{

        thread {
            this.httpServer = HttpServer(defaultPort)
            httpServer.start()
        }
        Thread.sleep(100)
    }

    @Test
    fun createHttpServerWithInvalidPorts_shouldThrowException() {
        assertThrows<IllegalArgumentException> { HttpServer(0x10000) }
        assertThrows<IllegalArgumentException> { HttpServer(-1) }
    }

    @Test
    fun startServerTwice_shouldThrowIllegalStateException() {
        assertThrows<IllegalStateException> {
            httpServer.start()
        }
    }

    @Test
    fun startHttpServer_acceptsNewSockets() {
        val socket = createSocket()

        assertTrue(socket.isConnected, "Client should connect after start")
    }

    @Test
    fun startHttpServer_thenStopServer_acceptNoSockets() {
        this.httpServer.stop()
        assertThrows<ConnectException> { createSocket() }
    }

    @Test
    fun startHttpServer_thenConnect_AndStopServer_socketShouldDisconnect() {
        createSocket()
        this.httpServer.stop()
    }

    @Test
    fun connectWithClient_ThenDisconnect_Server_shouldNotFail() {
        val socket = createSocket()
        val clientConnection = ClientConnection(socket)
        clientConnection.close()
        Thread.sleep(500)
        assertEquals(0, httpServer.activeConnections)

    }

    @Test
    fun connectWith21Client_LastClientShouldNotConnect() {
        for (i in 0 until 20)
            createSocket()
        Thread.sleep(100)
        assertEquals(20, httpServer.activeConnections)

    }

    @AfterEach
    fun teardown() {
        httpServer.stop()
    }


    private fun createSocket(): Socket {
        return Socket(InetAddress.getLocalHost(), defaultPort);
    }
}
