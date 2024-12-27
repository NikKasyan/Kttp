package kttp.http


object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val httpServer = HttpServer()
        httpServer.onGet("/test/**", FileHost("."))
        httpServer.start()
    }
}

object Client {
    @JvmStatic
    fun main(args: Array<String>) {
        val client = HttpClient("localhost")
        client.get("/test")
    }
}
