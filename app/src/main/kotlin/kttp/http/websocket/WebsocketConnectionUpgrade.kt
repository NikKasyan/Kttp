package kttp.http.websocket

import kttp.http.protocol.*
import kttp.http.server.HttpExchange
import kttp.http.server.UpgradeException
import kttp.security.Sha1

const val WEBSOCKET_VERSION = "13"


fun upgradeConnectionToWebsocket(httpExchange: HttpExchange, httpHeaders: HttpHeaders = createUpgradeHeader(httpExchange.request.headers)) {
    if(!isUpgradeRequest(httpExchange))
        throw UpgradeException()
    val headers = httpExchange.request.headers
    if (!headers.hasWebsocketUpgrade())
        throw UpgradeException()
    // Todo: Also check for origin header
    val response = HttpResponse.fromStatus(HttpStatus.SWITCHING_PROTOCOLS, httpHeaders)
    httpExchange.respond(response)
    // Todo: Handle websocket connection
}

private fun createUpgradeHeader(requestHeaders: HttpHeaders): HttpHeaders {
    return HttpHeaders {
        withUpgrade(requestHeaders.upgrade())
        withConnection(Connection.UPGRADE)
        withWebSocketAccept(createWebSocketKey(requestHeaders.webSocketKey()))
        withWebSocketProtocol(requestHeaders.webSocketProtocolAsList().firstOrNull()?: "")
        withWebSocketVersion(WEBSOCKET_VERSION)
    }
}

private fun isUpgradeRequest(httpExchange: HttpExchange): Boolean {
    return httpExchange.request.method == Method.GET && httpExchange.request.headers.hasConnection(Connection.UPGRADE)
}

private fun createWebSocketKey(key: String): String {
    val newKey = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11" // https://www.rfc-editor.org/rfc/rfc6455.html
    return Sha1.hashToBase64(newKey)
}