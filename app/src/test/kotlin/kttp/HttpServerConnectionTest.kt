package kttp

import kttp.http.server.HttpServer
import kttp.net.ClientConnection
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.ConnectException
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


@Timeout(value = 5, unit = TimeUnit.SECONDS)
class HttpServerConnectionTest {


    private var httpServer: HttpServer = HttpServer(port=8080)

    init{
        thread {
            httpServer.start()
        }
        httpServer.waitUntilStarted()
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
        Thread.sleep(200)
        assertEquals(0, httpServer.activeConnections)

    }

    @Test
    fun connectWith21Client_LastClientShouldNotConnect() {
        for (i in 0 .. 20)
            createSocket()
        Thread.sleep(200)
        assertEquals(20, httpServer.activeConnections)

    }

    @AfterEach
    fun teardown() {
        httpServer.stop()
    }


    private fun createSocket(): Socket {
        val host = httpServer.getHost()
        if(host.contains(":"))
            return Socket(host.substringBefore(":"), host.substringAfter(":").toInt())
        return Socket(httpServer.getHost(), 80)
    }
}
