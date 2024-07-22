package kttp.http

import java.net.Socket

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
//        val httpServer = HttpServer()
//
//        httpServer.start()
        val socket = Socket("google.com", 80)
        val outputStream = socket.getOutputStream()
        val inputStream = socket.getInputStream()

        outputStream.write("YEEEEEEEEEEEEEEEEEEEEEEEET / HTTP/1.1\r\nHost: google.com\r\n\r\n".toByteArray())
        outputStream.flush()

        val buffer = ByteArray(4096)
        while (true) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead == -1)
                break
            print(String(buffer, 0, bytesRead))

        }
        println()

    }
}
