package kttp.integration

import kttp.http.HttpClient
import kttp.http.protocol.HttpStatus
import kttp.http.server.HttpServer
import kttp.http.server.onGet
import kttp.http.websocket.Websocket
import kttp.http.websocket.WebsocketConnectionUpgrade
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.concurrent.thread
import kotlin.test.Test

class WebSocketUpgradeTest {
    private val server = HttpServer(8080)
    private val client = HttpClient(server.getBaseUri())

    init {
        thread {
            server.onGet("/") {
                respond(WebsocketConnectionUpgrade.createUpgradeResponse(request))
            }
            server.start()
        }
        server.waitUntilStarted()
    }

    @Test
    fun testUpgradeToWebsocketResponse() {
        val headers = WebsocketConnectionUpgrade.createDefaultUpgradeRequestHeader()
            .withWebSocketKey("dGhlIHNhbXBsZSBub25jZQ==")
            .withHost("localhost")

        val request = WebsocketConnectionUpgrade.createUpgradeRequest("/", headers)
        val response = client.request(request)

        assertEquals(HttpStatus.SWITCHING_PROTOCOLS, response.statusLine.status)
        assertEquals("websocket", response.headers.upgrade)
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", response.headers.webSocketAccept)
        assertEquals("13", response.headers.webSocketVersion)

    }

    @Test
    fun testUpgradeToWebsocket() {
        Websocket("ws://${server.getHost()}").close()

    }

    // Close the server after all tests
    @AfterEach
    fun closeServer() {
        server.stop()
    }
}