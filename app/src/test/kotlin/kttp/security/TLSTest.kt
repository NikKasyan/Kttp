package kttp.security

import kttp.http.HttpClient
import kttp.http.server.HttpServer
import kttp.http.server.HttpServerOptions
import kttp.http.server.TLSOptions
import kttp.http.protocol.HttpStatus
import kttp.http.server.onGet
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.test.assertEquals

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
        server.onGet("/") {
            respond("Hello World")
        }
        server.waitUntilStarted()

        val client = HttpClient(server.getBaseUri(), verifyCertificate = false)
        val response = client.get()
        assertEquals(HttpStatus.OK, response.statusLine.status)
        assertEquals("Hello World", response.body.readAsString())

    }

}