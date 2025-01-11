package kttp.websocket

import kttp.http.HttpClient
import kttp.http.protocol.Connection
import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.HttpRequest
import kttp.http.protocol.HttpStatus
import kttp.http.websocket.InvalidUpgrade
import kttp.http.websocket.WebsocketConnectionUpgrade
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class WebSocketUpgradeTest {


    @Test
    fun testUpgradeToWebsocketResponse() {
        val headers = HttpHeaders() {
            withConnection(Connection.UPGRADE)
            withUpgrade("websocket")
            withWebSocketKey("dGhlIHNhbXBsZSBub25jZQ==")
            withWebSocketVersion("13")
        }
        val request = HttpRequest.get("/ws", headers)
        val response = WebsocketConnectionUpgrade.createUpgradeResponse(request)

        assertEquals(HttpStatus.SWITCHING_PROTOCOLS, response.statusLine.status)
        assertEquals("websocket", response.headers.upgrade)
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", response.headers.webSocketAccept)
        assertEquals("13", response.headers.webSocketVersion)
    }

    @Test
    fun testUpgradeToWebsocketRequest() {
        val request = WebsocketConnectionUpgrade.createUpgradeRequest("/ws")
        val response = WebsocketConnectionUpgrade.createUpgradeResponse(request)

        assertEquals(HttpStatus.SWITCHING_PROTOCOLS, response.statusLine.status)
        assertEquals("websocket", response.headers.upgrade)
        assertEquals("13", response.headers.webSocketVersion)
    }

    @Test()
    fun testUpgradeToWebsocketRequestWithInvalidHeaders() {
        assertThrows<InvalidUpgrade> {
            val headers = HttpHeaders() {
                withConnection(Connection.UPGRADE)
                withUpgrade("websocket")
            }
            WebsocketConnectionUpgrade.createUpgradeRequest("", headers)
        }
    }

}