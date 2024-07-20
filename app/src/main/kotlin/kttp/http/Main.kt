package kttp.http

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val httpServer = HttpServer()

        httpServer.start()
    }
}
