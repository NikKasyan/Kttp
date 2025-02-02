package kttp.websocket

import kttp.http.HttpClientConnection
import kttp.io.IOStream
import kttp.security.SSL
import java.io.Closeable
import java.io.InputStream
import java.net.Socket
import java.net.URI

typealias InitialWebSocketEventsSetter = WebsocketEvents.() -> Unit

// Todo: This is only for client side, server side will be implemented later
class Websocket(private val connection: HttpClientConnection, initializeEvents: InitialWebSocketEventsSetter = {}) : Closeable {

    var state = WebsocketState.CONNECTING
        private set
    private var masked = false
    private val events = WebsocketEvents()
    private var lastOpCode = -1

    companion object {
        fun connect(uri: String, verifyCertificates: Boolean = true, initializeEvents: InitialWebSocketEventsSetter = {}): Websocket {
            return connect(URI.create(uri), verifyCertificates, initializeEvents)
        }
        fun connect(uri: URI, verifyCertificates: Boolean = true, initializeEvents: InitialWebSocketEventsSetter = {}): Websocket {
            val secure = uri.scheme == "wss"
            val port = uri.port.takeIf { it != -1 } ?: if (secure) 443 else 80
            val socket = if (secure) {
                SSL.getSecureSocketFactory(verifyCertificates).createSocket(uri.host, port)
            } else {
                Socket(uri.host, port)
            }
            val connection = HttpClientConnection(socket)
            val webSocket = Websocket(connection, initializeEvents)
            webSocket.masked = true // Client side must always mask

            webSocket.doHandshake(uri)

            webSocket.state = WebsocketState.OPEN
            webSocket.onOpen(webSocket)
            return webSocket
        }

        fun fromConnection(ioStream: IOStream, initializeEvents: InitialWebSocketEventsSetter = {}): Websocket {
            return fromConnection(HttpClientConnection(ioStream), initializeEvents)
        }
        fun fromConnection(connection: HttpClientConnection, initializeEvents: InitialWebSocketEventsSetter = {}): Websocket {
            return Websocket(connection, initializeEvents).also{
                it.state = WebsocketState.OPEN
                it.onOpen(it)
            }
        }


    }
    init {
        initializeEvents(events)
    }



    private fun doHandshake(uri: URI) {
        val request = WebsocketConnectionUpgrade.createUpgradeRequest(uri)

        connection.request(request).runCatching {
            WebsocketConnectionUpgrade.checkIsValidUpgradeResponse(request, this)
        }.onFailure {
            connection.close()
            throw it
        }
    }

    var onMessage: (Websocket.(String) -> Unit)
        get() = events.onMessage
        set(value) { events.onMessage = value }

    var onClose: (Websocket.() -> Unit)
        get() = events.onClose
        set(value) { events.onClose = value }

    var onOpen: (Websocket.() -> Unit)
        get() = events.onOpen
        private set(value) { events.onOpen = value }

    var onBinaryMessage: (Websocket.(ByteArray) -> Unit)
        get() = events.onBinaryMessage ?: {
            onMessage(it.toString(Charsets.UTF_8))
        }
        set(value) { events.onBinaryMessage = value }

    var onLongMessage: (Websocket.(InputStream) -> Unit)
        get() = events.onLongMessage
        set(value) { events.onLongMessage = value }


    fun send(message: String) {
        send(message.toByteArray(), false)
    }

    fun send(message: ByteArray, isBinary: Boolean = true, isFinal: Boolean = true) {
        send(message, 0, message.size, isBinary, isFinal)
    }

    fun send(message: ByteArray, offset: Int, length: Int, isBinary: Boolean, isFinal: Boolean = true) {
        val opcode = if (!isFinal) WebsocketOpCodes.CONTINUATION else if (isBinary) WebsocketOpCodes.BINARY else WebsocketOpCodes.TEXT
        val frame = WebsocketFrame(isFinal, opcode, masked, payload = message, offset = offset, length = length)
        send(frame)

    }

    fun send(inputStream: InputStream, isBinary: Boolean = true) {
        val buffer = ByteArray(1024)
        var read = 0

        while (inputStream.read(buffer).also { read = it } != -1) {
            // Todo: Handle continuation frames
            //https://www.rfc-editor.org/rfc/rfc6455.html#page-34
            send(buffer, 0, read, isBinary, isFinal = false)
        }
        send(buffer, 0, 0, isBinary, isFinal = true)
    }

    fun send(frame: WebsocketFrame) {
        connection.runCatching {
            frame.writeTo(io)
        }.onFailure {
            close()
            throw it
        }
    }
    fun ping() {
        send(WebsocketFrame(true, WebsocketOpCodes.PING, masked))
    }

    fun pong() {
        send(WebsocketFrame(true, WebsocketOpCodes.PONG, masked))
    }

    private fun sendClose() {
        send(WebsocketFrame(true, WebsocketOpCodes.CLOSE, masked))
    }


    fun handleEvents() {
        if(state == WebsocketState.CLOSED) return
        connection.runCatching {
            while (true) {
                val frame = readFrame()
                if(!frame.isFinalFrame)
                    lastOpCode = frame.opcode
                if(frame.payload.length > Int.MAX_VALUE) {
                    onLongMessage(frame.payload)
                    return
                }
                when (frame.opcode) {
                    WebsocketOpCodes.TEXT -> onMessage(frame.payload.toString(Charsets.UTF_8))
                    WebsocketOpCodes.BINARY -> onBinaryMessage(frame.payload.readAllBytes())
                    WebsocketOpCodes.CONTINUATION -> {
                        when (lastOpCode) {
                            WebsocketOpCodes.TEXT -> onMessage(frame.payload.toString(Charsets.UTF_8))
                            WebsocketOpCodes.BINARY -> onBinaryMessage(frame.payload.readAllBytes())
                            else -> throw IllegalArgumentException("Invalid continuation frame")
                        }
                    }
                    WebsocketOpCodes.CLOSE -> close()
                    WebsocketOpCodes.PING -> send(WebsocketFrame(true, WebsocketOpCodes.PONG, masked, payload = frame.payload))
                    WebsocketOpCodes.PONG -> { }
                    else -> throw IllegalArgumentException("Invalid opcode")
                }
            }
        }.onFailure {
            close()
            throw it
        }
    }

    fun readFrame(): WebsocketFrame {
        return WebsocketFrame.readFrom(connection.io)
    }

    override fun close() {
        if(state == WebsocketState.CLOSED) return
        state = WebsocketState.CLOSING
        sendClose()
        onClose()

        connection.runCatching { close() }
        state = WebsocketState.CLOSED
    }


}

class WebsocketEvents {
    var onMessage: (Websocket.(String) -> Unit) = { }
    var onBinaryMessage: (Websocket.(ByteArray) -> Unit)? = null
    var onLongMessage: (Websocket.(InputStream) -> Unit) = { }

    var onClose: (Websocket.() -> Unit) = { }
    var onOpen: (Websocket.() -> Unit) = { }
}

enum class WebsocketState {
    CONNECTING,
    OPEN,
    CLOSING,
    CLOSED
}