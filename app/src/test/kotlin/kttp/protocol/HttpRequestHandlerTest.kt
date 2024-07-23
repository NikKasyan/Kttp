package kttp.protocol

import kttp.http.HttpRequestHandler
import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.HttpVersion
import kttp.http.protocol.Method
import kttp.http.protocol.RequestLine
import kttp.net.IOStream
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.net.URI
import kotlin.test.assertEquals

class HttpRequestHandlerTest {

    @Test
    fun testRequest() {
        val stream = """
            GET / HTTP/1.1
            Host: localhost:8080
            User-Agent: TestClient/7.68.0
            Accept: */*\r\n\r\n
        """.trimIndent().byteInputStream()

        val outputStream = ByteArrayOutputStream()
        val ioStream = IOStream(stream, outputStream)

        val request = HttpRequestHandler().handle(ioStream)

        println(request)



    }
    @Test
    fun testRequestIsParsedCorrectly(){
        val path = "/"
        val host = "localhost"
        val port = 8080
        val protocol = "http"
        val requestLine = RequestLine(Method.GET, path, HttpVersion.DEFAULT_VERSION)
        val headers = HttpHeaders()
                      .withHost("$host:$port")
                      .withUserAgent("TestClient/7.68.0")
                      .withAccept("*/*")
        val request = "$requestLine$headers\r\n"
        val stream = request.byteInputStream()

        val outputStream = ByteArrayOutputStream()
        val ioStream = IOStream(stream, outputStream)

        val parsedRequest = HttpRequestHandler().handle(ioStream)

        assertEquals(requestLine.method, parsedRequest.method)
        assertEquals(URI(protocol, null, host, 8080, path, null, null), parsedRequest.requestUri)
        assertEquals(requestLine.httpVersion, parsedRequest.httpVersion)
        assertEquals(headers, parsedRequest.httpHeaders)

    }

}
