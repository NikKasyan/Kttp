package kttp.protocol

import kttp.http.HttpRequestHandler
import kttp.net.IOStream
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.StringWriter

class HttpRequestHandlerTest {

    @Test
    fun testRequest() {
        val stream = """
            GET / HTTP/1.1
            Host: localhost:8080
            User-Agent: TestClient/7.68.0
            Accept: */*\r\n\r
        """.trimIndent().byteInputStream()

        val outputStream = ByteArrayOutputStream()
        val ioStream = IOStream(stream, outputStream)

        val request = HttpRequestHandler().handle(ioStream)

        println(request)



    }

}
