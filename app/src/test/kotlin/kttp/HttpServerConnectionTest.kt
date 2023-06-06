package kttp

import kttp.http.HttpServer
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException
import java.net.ConnectException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.test.*


@Timeout(value = 5, unit = TimeUnit.SECONDS)
class HttpServerConnectionTest {


    private lateinit var httpServer: HttpServer
    private val defaultPort = 80

    @BeforeTest
    fun setUp() {
        this.httpServer = HttpServer(defaultPort)
    }

    @Test
    fun createHttpServerWithInvalidPorts_shouldThrowException(){
        assertThrows<IllegalArgumentException> { HttpServer(0x10000) }
        assertThrows<IllegalArgumentException> { HttpServer(-1) }
    }
    @Test
    fun startHttpServer_acceptsNewSockets() {
        this.httpServer.start()
        val socket = createSocket()

        assertTrue(socket.isConnected, "Client should connect after start")
    }

    @Test
    fun startHttpServer_thenStopServer_acceptNoSockets() {
        this.httpServer.start()
        this.httpServer.stop()
        assertThrows<ConnectException> { createSocket() }
    }

    @Test
    fun startHttpServer_thenConnect_AndStopServer_socketShouldDisconnect() {
        this.httpServer.start()
        createSocket()
        this.httpServer.stop()
    }

    @Test
    fun connectWithClientBeforeStart_shouldNotAcceptSocket(){
        assertThrows<ConnectException> { createSocket() }
    }

    @AfterTest
    fun teardown() {
        httpServer.stop()
    }

    private fun createSocket(): Socket {
        return Socket(InetAddress.getLocalHost(), defaultPort);
    }
}
