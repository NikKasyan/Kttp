package kttp.http.websocket

import kttp.http.protocol.HttpRequest
import kttp.http.protocol.HttpResponse
import kttp.net.ClientConnection
import java.io.Closeable

class Websocket(private val clientConnection: ClientConnection): Closeable {

    var onMessage: (Websocket.(String) -> Unit)? = null
    var onClose: (Websocket.() -> Unit)? = null
    var onUpgrade: ((httpResponse: HttpResponse, httpRequest: HttpRequest)-> Unit)? = null

    fun send(message: String) {
        clientConnection.io.write(message)
    }

    override fun close() {
        clientConnection.close()
    }

}