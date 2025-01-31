package kttp.integration

import kttp.http.HttpClient
import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.HttpStatus
import kttp.http.server.HttpServer
import kttp.http.server.onGet
import kttp.websocket.InvalidUpgrade
import kttp.websocket.Websocket
import kttp.websocket.WebsocketConnectionUpgrade
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import kotlin.concurrent.thread
import kotlin.test.Test

@Timeout(5)
class WebSocketUpgradeTest {
    private val server = HttpServer(8080)
    private val client = HttpClient(server.getBaseUri())

    init {
        thread {
            server.onGet("/") {
                respond(WebsocketConnectionUpgrade.createUpgradeResponse(request))
            }.onGet("/invalid") {
                respond(WebsocketConnectionUpgrade.createUpgradeResponse(request, HttpHeaders(){withWebSocketAccept("invalid")}))
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
        Websocket.connect("ws://${server.getHost()}").close()

    }
    @Test
    fun testUpgradeWithInvalidAccept() {

        assertThrows<InvalidUpgrade> {
            Websocket.connect("ws://${server.getHost()}/invalid")
        }

    }

    @Test
    fun testMessageSending() {
        val websocket = Websocket.connect("ws://${server.getHost()}") {
            onOpen = {
                send("Hello")
            }
        }
        server.onGet("/") {
            respond(WebsocketConnectionUpgrade.createUpgradeResponse(request))
            val websocketClient = Websocket.fromConnection(io)
            websocketClient.onMessage = {
                assertEquals("Hello", it)
                websocket.send(it)
            }
        }

        val frame = websocket.readFrame()
        assertEquals("Hello", frame.payload.toString(Charsets.UTF_8))

    }

    // Close the server after all tests
    @AfterEach
    fun closeServer() {
        server.stop()
    }
}