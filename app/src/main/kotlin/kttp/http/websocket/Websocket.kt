package kttp.http.websocket

import kttp.http.HttpClientConnection
import kttp.security.SSL
import java.io.Closeable
import java.net.Socket
import java.net.URI

class Websocket(private val uri: URI, verifyCertificates: Boolean = true): Closeable {

    private val isSecure: Boolean
        get() = uri.scheme == "wss"
    private val connection: HttpClientConnection
    constructor(uri: String): this(URI.create(uri))

    init {
        val socket = if (isSecure) {
            SSL.getSecureSocketFactory(verifyCertificates).createSocket(uri.host, getPort())
        } else {
            Socket(uri.host, getPort())
        }
        connection = HttpClientConnection(socket)
        val request = WebsocketConnectionUpgrade.createUpgradeRequest(uri)
        connection.request(request).also {
            WebsocketConnectionUpgrade.checkIsValidUpgradeResponse(request, it)
        }
    }

    var onMessage: (Websocket.(String) -> Unit)? = null
    var onClose: (Websocket.() -> Unit)? = null
    var onOpen: (Websocket.() -> Unit)? = null

    fun send(message: String) {

    }

    override fun close() {
        connection.close()
    }

    private fun getPort(): Int {
        return if(uri.port != -1) uri.port else if(isSecure) 443 else 80
    }
}