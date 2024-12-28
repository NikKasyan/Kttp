package kttp.http


object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val httpServer = HttpServer(HttpServerOptions(secure = true))
        httpServer.onGet("/test/**", FileHost("."))
        httpServer.start()
    }
}

object Client {
    @JvmStatic
    fun main(args: Array<String>) {
        val client = HttpClient("https://localhost", verifyCertificate = false)
        val response = client.get("/test")
    }
}
