package kttp.http

import kttp.http.protocol.HttpResponse
import kttp.http.server.HttpServer
import kttp.http.server.onGet
import kttp.http.websocket.WebsocketConnectionUpgrade


object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        HttpServer()
            .onGet("/test") {
                respond(HttpResponse.ok(body = "Test"))
            }.onGet("/ws") {
                respond(WebsocketConnectionUpgrade.createUpgradeResponse(request))
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