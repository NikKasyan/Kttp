package kttp.http

import kotlinx.coroutines.delay
import kttp.http.protocol.HttpResponse
import kttp.http.server.*
import kttp.http.websocket.upgradeConnectionToWebsocket


object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        HttpServer()
            .onGet("/test") {
                respond(HttpResponse.ok(body = "Test"))
            }.onGet("/ws") {
                upgradeConnectionToWebsocket(this)
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

suspend fun test() {
    delay(1000)
}