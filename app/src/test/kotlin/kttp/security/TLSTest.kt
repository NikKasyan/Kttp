package kttp.security

import kttp.http.HttpClient
import kttp.http.HttpServer
import kttp.http.HttpServerOptions
import kttp.http.TLSOptions
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.concurrent.thread

class TLSTest {

    @Test
    fun `test tls`() {
        val sslContext = SSL.createDefaultSSLContextFromKeyStore(
            keyStoreOptions = KeyStoreOptions(
                keyStoreFilePath = Paths.get("./keystore.jks"),
                keyStorePassword = "password"
            )
        )
        val httpServerOptions = HttpServerOptions(secure = true, tlsOptions = TLSOptions(sslContext = sslContext) )
        val server = HttpServer(httpServerOptions)
        thread {
            server.start()
        }
        server.waitUntilStarted()

        val client = HttpClient(server.getBaseUri(), verifyCertificate = false)
        val response = client.get()
        println(response.body.readAsString())



    }
}