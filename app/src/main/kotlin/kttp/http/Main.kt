package kttp.http

import kttp.http.protocol.HttpResponse
import kttp.http.server.HttpServer
import kttp.http.server.HttpServerOptions
import kttp.http.server.onGet
import kttp.websocket.Websocket
import kttp.websocket.WebsocketConnectionUpgrade
import kttp.websocket.WebsocketState
import java.time.Duration
import kotlin.concurrent.thread


object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        HttpServer(HttpServerOptions(port = 8080, socketTimeout = Duration.ofSeconds(300)))
            .onGet("/test") {
                respond(HttpResponse.ok(body = "Test"))
            }.onGet("/") {
                respond(WebsocketConnectionUpgrade.createUpgradeResponse(request))
                val websocket = Websocket.fromConnection(io) {
                    onOpen = {
                        println("Got new connection")
                    }
                    onMessage = {
                        println("Message: $it")
                    }
                }
                websocket.handleEvents()

            }
            .start()
    }
}


object Client {
    @JvmStatic
    fun main(args: Array<String>) {
        val client = HttpClient("https://localhost", verifyCertificate = false)
        val response = client.get("/test")
        println(response.statusLine)
        println(response.body.readAsString())

    }
}

object WebsocketClient {
    @JvmStatic
    fun main(args: Array<String>) {
        val websocket = Websocket.connect("ws://localhost:8080/") {
            onOpen = {
                println("Connected")
            }
            onMessage = {
                println(it)
            }
            onClose = {
                println("Closed")
            }
        }
        websocket.use {
            thread {
                websocket.handleEvents()
            }
            while (websocket.state == WebsocketState.OPEN) {
                val line = readlnOrNull()
                if (line == "exit" || line == null) {
                    websocket.close()
                    break
                }
                websocket.send(line)
            }
        }
    }
}