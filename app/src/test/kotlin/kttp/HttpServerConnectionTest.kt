package kttp

import kttp.http.server.HttpServer
import kttp.http.server.HttpServerOptions
import kttp.net.ClientConnection
import kttp.net.ConnectionOptions
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.util.concurrent.Executors
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

    @Test
    fun stop_doesNotShutDownGivenExecutor() {
        val executor = Executors.newSingleThreadExecutor()
        HttpServer(executorService = executor).stop()

        assertFalse(executor.isShutdown)
        executor.shutdown()
    }

    @Test
    fun unlimitedConnections_canBeConfigured() {
        HttpServer(HttpServerOptions(maxConcurrentConnections = -1)).stop()
    }

    @Test
    fun negativeSocketTimeout_meansNoTimeout() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { serverSocket ->
            Socket(InetAddress.getLoopbackAddress(), serverSocket.localPort).use { socket ->
                ClientConnection(socket, ConnectionOptions(timeout = Duration.ofSeconds(-1)))

                assertEquals(0, socket.soTimeout)
            }
        }
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
