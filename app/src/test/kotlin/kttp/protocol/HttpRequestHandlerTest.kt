package kttp.protocol

import kttp.http.HttpRequestHandler
import kttp.http.MissingHostHeader
import kttp.http.protocol.*
import kttp.http.protocol.transfer.ChunkExtensionTooLong
import kttp.http.protocol.transfer.chunkString
import kttp.http.protocol.transfer.chunkStringWithChunkSize
import kttp.io.IOStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.OutputStream
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
    fun testRequestIsParsedCorrectly() {
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

        val outputStream = OutputStream.nullOutputStream()
        val ioStream = IOStream(stream, outputStream)

        val parsedRequest = HttpRequestHandler().handle(ioStream)

        assertEquals(requestLine.method, parsedRequest.method)
        assertEquals(URI(protocol, null, host, 8080, path, null, null), parsedRequest.requestUri)
        assertEquals(requestLine.httpVersion, parsedRequest.httpVersion)
        assertEquals(headers, parsedRequest.httpHeaders)

    }

    @Test
    fun testRequestParameters() {
        val path = "/test"
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

        val outputStream = OutputStream.nullOutputStream()
        val ioStream = IOStream(stream, outputStream)

        val parsedRequest = HttpRequestHandler().handle(ioStream)

        assertEquals(requestLine.method, parsedRequest.method)
        assertEquals(URI(protocol, null, host, 8080, path, null, null), parsedRequest.requestUri)
        assertEquals(requestLine.httpVersion, parsedRequest.httpVersion)
        assertEquals(headers, parsedRequest.httpHeaders)

    }

    @Test
    fun testRequestShouldHaveAtLeastOneHost() {
        val requestWithNoHost = """
            GET / HTTP/1.1
            User-Agent: TestClient/7.68.0
            Accept: */*\r\n\r\n
        """.trimIndent().byteInputStream()

        val ioStream = IOStream(requestWithNoHost, OutputStream.nullOutputStream())

        assertThrows<MissingHostHeader> {
            HttpRequestHandler().handle(ioStream)
        }

    }

    @Test
    fun testRequestShouldHaveAtMostOneHost() {
        val requestWithNoHost = """
            GET / HTTP/1.1
            User-Agent: TestClient/7.68.0
            Host: localhost:8080
            Host: localhost:8080
            Accept: */*\r\n\r\n
        """.trimIndent().byteInputStream()

        val ioStream = IOStream(requestWithNoHost, OutputStream.nullOutputStream())

        assertThrows<TooManyHostHeaders> {
            HttpRequestHandler().handle(ioStream)
        }

    }

    @Test
    fun requestWithInvalidContentLengthShouldThrowInvalidContentLength() {
        val requestWithInvalidContentLength = """
            GET / HTTP/1.1
            User-Agent: TestClient/7.68.0
            Host: localhost:8080
            Content-Length: invalid
            Accept: */*\r\n\r\n
        """.trimIndent().byteInputStream()

        val ioStream = IOStream(requestWithInvalidContentLength, OutputStream.nullOutputStream())

        assertThrows<InvalidContentLength> {
            HttpRequestHandler().handle(ioStream)
        }
    }

    @Test
    fun requestShouldHaveValidTransferEncoding() {
        val requestWithInvalidTransferEncoding = """
            GET / HTTP/1.1
            User-Agent: TestClient/7.68.0
            Host: localhost:8080
            Transfer-Encoding: invalid
            Accept: */*\r\n\r\n
        """.trimIndent().byteInputStream()

        val ioStream = IOStream(requestWithInvalidTransferEncoding, OutputStream.nullOutputStream())

        assertThrows<UnknownTransferEncoding> {
            HttpRequestHandler().handle(ioStream)
        }
    }

    @Test
    fun requestShouldHaveValidContentLengthIfContentLengthContainsListWithSameValue() {
        val requestWithInvalidContentLength = """
            GET / HTTP/1.1
            User-Agent: TestClient/7.68.0
            Host: localhost:8080
            Content-Length: 0,0,0
            Accept: */*\r\n\r\n
        """.trimIndent().byteInputStream()

        val ioStream = IOStream(requestWithInvalidContentLength, OutputStream.nullOutputStream())


        val request = HttpRequestHandler().handle(ioStream)

        assertEquals(0, request.httpHeaders.contentLength)
    }

    @Test
    fun requestWithInvalidContentLengthShouldThrowInvalidHeader() {
        val requestWithInvalidContentLength = """
            GET / HTTP/1.1
            User-Agent: TestClient/7.68.0
            Host: localhost:8080
            Content-Length: invalid
            Accept: */*\r\n\r\n
        """.trimIndent().byteInputStream()

        val ioStream = IOStream(requestWithInvalidContentLength, OutputStream.nullOutputStream())

        assertThrows<InvalidContentLength> {
            HttpRequestHandler().handle(ioStream)
        }
    }
    @Test
    fun requestWithInvalidContentLengthShouldThrowInvalidHeader2() {
        val requestWithInvalidContentLength = """
            GET / HTTP/1.1
            User-Agent: TestClient/7.68.0
            Host: localhost:8080
            Content-Length: 0,1,0
            Accept: */*\r\n\r\n
        """.trimIndent().byteInputStream()

        val ioStream = IOStream(requestWithInvalidContentLength, OutputStream.nullOutputStream())

        assertThrows<InvalidContentLength> {
            HttpRequestHandler().handle(ioStream)
        }
    }

    @Test
    fun requestWithTransferEncodingChunked_shouldBeHandled() {
        val wikiString = "Wikipedia in\r\n\r\nchunks."
        val chunkedBody = chunkStringWithChunkSize(wikiString, listOf(4, 5, 14))
        val remoteRequest = PostRequest.from( "http://localhost:8080",
            HttpHeaders().withTransferEncoding(TransferEncoding.CHUNKED),
            HttpBody.fromBytes(chunkedBody.toByteArray()))

        val ioStream = IOStream(remoteRequest.asStream(), OutputStream.nullOutputStream())

        val request = HttpRequestHandler().handle(ioStream)

        val body = request.body
        val bytes = body.readAllBytes()
        assertEquals(wikiString, bytes.toString(Charsets.US_ASCII))
    }

    @Test
    fun chunkedInputStream_shouldThrowOnTooLongChunkExtension() {
        val wikiString = "Wikipedia in\r\n\r\nchunks."
        val stream = chunkString(wikiString, listOf(
            4 to "test=1",
            5 to "test=2",
            14 to "test=${"3".repeat(3000)}",
        )).byteInputStream()


        val ioStream = IOStream(
            PostRequest.from(
                "http://localhost:8080",
                HttpHeaders().withTransferEncoding(TransferEncoding.CHUNKED),
                stream)
                .asStream(),
            OutputStream.nullOutputStream())
        val request = HttpRequestHandler().handle(ioStream)
        assertThrows<ChunkExtensionTooLong> {
            request.body.readAllBytes()
        }

    }
}
