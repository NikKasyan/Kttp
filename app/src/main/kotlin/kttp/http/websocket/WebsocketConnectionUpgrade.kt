package kttp.http.websocket

import kttp.http.HttpClient
import kttp.http.HttpClientConnection
import kttp.http.protocol.*
import kttp.http.websocket.WebsocketConnectionUpgrade.checkIsValidUpgradeResponse
import kttp.http.websocket.WebsocketConnectionUpgrade.createUpgradeRequest
import kttp.security.Sha1
import java.net.URI
import java.security.SecureRandom
import java.util.*

const val WEBSOCKET_VERSION = "13"
const val WEBSOCKET_PROTOCOL = "websocket"

object WebsocketConnectionUpgrade {
    fun createUpgradeResponse(httpRequest: HttpRequest): HttpResponse {
        checkIsValidUpgradeRequest(httpRequest)
        return createUpgradeResponse(httpRequest, createDefaultUpgradeResponseHeader(httpRequest.headers))
    }

    fun createUpgradeResponse(httpRequest: HttpRequest, httpHeaders: HttpHeaders): HttpResponse {
        checkIsValidUpgradeRequest(httpRequest)
        val response = HttpResponse.fromStatus(HttpStatus.SWITCHING_PROTOCOLS, httpHeaders)
        return response
    }

    fun createUpgradeRequest(uri: String): HttpRequest {
        return createUpgradeRequest(URI.create(uri))
    }

    fun createUpgradeRequest(uri: URI): HttpRequest {
        return createUpgradeRequest(uri, createDefaultUpgradeRequestHeader())
    }

    fun createUpgradeRequest(uri: String, httpHeaders: HttpHeaders): HttpRequest {
        return createUpgradeRequest(URI.create(uri), httpHeaders)
    }

    fun createUpgradeRequest(uri: URI, httpHeaders: HttpHeaders): HttpRequest {
        return HttpRequest.get(uri, httpHeaders).also { checkIsValidUpgradeRequest(it) }
    }

    fun createDefaultUpgradeRequestHeader(): HttpHeaders {
        return HttpHeaders {
            withUpgrade(WEBSOCKET_PROTOCOL)
            withConnection(Connection.UPGRADE)
            withWebSocketKey(createWebSocketAcceptFromRequest())
            withWebSocketVersion(WEBSOCKET_VERSION)
        }
    }

    fun createDefaultUpgradeResponseHeader(requestHeaders: HttpHeaders): HttpHeaders {
        return HttpHeaders {
            withUpgrade(requestHeaders.upgrade())
            withConnection(Connection.UPGRADE)
            withWebSocketAccept(createWebSocketAcceptFromRequest(requestHeaders.webSocketKey()))
            if(requestHeaders.hasWebSocketProtocol())
                withWebSocketProtocol(requestHeaders.webSocketProtocolAsList().firstOrNull()?: "")
            withWebSocketVersion(WEBSOCKET_VERSION)
        }
    }


    fun checkIsValidUpgradeRequest(httpRequest: HttpRequest) {
        val headers = httpRequest.headers

        if(!isUpgradeRequest(httpRequest))
            throw InvalidUpgrade("Invalid upgrade request")

        if (!hasWebsocketUpgrade(headers))
            throw InvalidUpgrade("Unsupported upgrade protocol ${headers.upgrade?: ""}")

        if(!hasValidWebSocketKey(headers))
            throw InvalidUpgrade("Invalid websocket key must be 16 bytes encoded in base64")

        if(hasValidWebSocketVersion(headers))
            throw InvalidUpgrade("Unsupported websocket version ${headers.webSocketVersion}")

        if(!hasValidWebSocketProtocol(headers))
            throw InvalidUpgrade("Invalid websocket protocol")
        // Todo: Also check for origin header https://www.rfc-editor.org/rfc/rfc6455#page-18 number 8

    }

    fun checkIsValidUpgradeResponse(request: HttpRequest, httpResponse: HttpResponse) {
        val requestHeaders = request.headers
        val responseHeaders = httpResponse.headers

        if (!requestHeaders.upgrade().equals(responseHeaders.upgrade, ignoreCase = true))
            throw InvalidUpgradeHeader("Invalid upgrade protocol ${responseHeaders.upgrade}")

        if (!requestHeaders.webSocketVersion.equals(responseHeaders.webSocketVersion))
            throw InvalidUpgradeHeader("Invalid websocket version")

        checkIsValidWebsocketAccept(requestHeaders.webSocketKey(), responseHeaders.webSocketAccept())
    }

    private fun checkIsValidWebsocketAccept(requestKey: String, responseAccept: String) {
        val expectedAccept = createWebSocketAcceptFromRequest(requestKey)
        if (expectedAccept != responseAccept)
            throw InvalidUpgradeHeader("Invalid websocket accept")
    }

    fun isUpgradeRequest(request: HttpRequest): Boolean {
        return request.method == Method.GET
                && request.headers.hasConnection(Connection.UPGRADE)
    }

    private fun hasValidWebSocketVersion(headers: HttpHeaders): Boolean {
        return headers.webSocketVersion?.isNotEmpty() == true
                && headers.webSocketVersion != WEBSOCKET_VERSION
    }


    private fun hasWebsocketUpgrade(headers: HttpHeaders): Boolean {
        return headers.hasUpgrade()
                && headers.upgrade() == WEBSOCKET_PROTOCOL
                && headers.upgrade().equals(WEBSOCKET_PROTOCOL, ignoreCase = true)
    }

    private fun hasValidWebSocketKey(headers: HttpHeaders): Boolean {
        return headers.webSocketKey?.isNotEmpty() == true
                && headers.webSocketKey().length == 24 // Key must be 16 bytes encoded in base64
    }

    private fun hasValidWebSocketProtocol(headers: HttpHeaders): Boolean {
        if(!headers.hasWebSocketProtocol())
            return true
        return headers.webSocketProtocol?.isNotEmpty() == true
    }


    private fun createWebSocketAcceptFromRequest(): String {
        val random = SecureRandom()
        val key = ByteArray(16)
        random.nextBytes(key)

        return Base64.getEncoder().encodeToString(key)
    }
    private fun createWebSocketAcceptFromRequest(key: String): String {
        val newKey = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11" // https://www.rfc-editor.org/rfc/rfc6455#page-8
        return Sha1.hashToBase64(newKey)
    }
}



fun HttpClientConnection.upgradeToWebsocket(uri: String): HttpClientConnection {
    val request = createUpgradeRequest(uri)
    val response = this.request(request)
    checkIsValidUpgradeResponse(request, response)
    return this
}

fun HttpClient.upgradeToWebsocket(uri: String): HttpClientConnection {
    return connect().upgradeToWebsocket(uri)
}

open class InvalidUpgrade(msg: String) : RuntimeException(msg)

class InvalidUpgradeHeader(msg: String) : InvalidUpgrade(msg)