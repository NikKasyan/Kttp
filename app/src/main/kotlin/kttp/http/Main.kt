package kttp.http

import kttp.http.protocol.HttpHeaders
import kttp.security.SSL
import java.nio.file.Paths


object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val sslContext = SSL.createSSLContextFromCertificateAndKey("test.crt", "test.key")
        val httpServer = HttpServer(HttpServerOptions(secure = true, tlsOptions = TLSOptions(sslContext = sslContext)))
        httpServer.addRequestHandler(HostFilesHandler(".", "/test"))
        httpServer.start()
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
