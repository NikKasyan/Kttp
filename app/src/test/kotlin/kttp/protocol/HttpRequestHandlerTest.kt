package kttp.protocol

import kttp.http.server.HttpRequestHandler
import kttp.http.server.MissingHostHeader
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
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream
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

        val request = HttpRequestHandler().handleRequest(ioStream)

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

        val parsedRequest = HttpRequestHandler().handleRequest(ioStream)

        assertEquals(requestLine.method, parsedRequest.method)
        assertEquals(URI(protocol, null, host, 8080, path, null, null), parsedRequest.uri)
        assertEquals(requestLine.httpVersion, parsedRequest.httpVersion)
        assertEquals(headers, parsedRequest.headers)

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

        val parsedRequest = HttpRequestHandler().handleRequest(ioStream)

        assertEquals(requestLine.method, parsedRequest.method)
        assertEquals(URI(protocol, null, host, 8080, path, null, null), parsedRequest.uri)
        assertEquals(requestLine.httpVersion, parsedRequest.httpVersion)
        assertEquals(headers, parsedRequest.headers)

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
            HttpRequestHandler().handleRequest(ioStream)
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
            HttpRequestHandler().handleRequest(ioStream)
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
            HttpRequestHandler().handleRequest(ioStream)
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
            HttpRequestHandler().handleRequest(ioStream)
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


        val request = HttpRequestHandler().handleRequest(ioStream)

        assertEquals(0, request.headers.contentLength)
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
            HttpRequestHandler().handleRequest(ioStream)
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
            HttpRequestHandler().handleRequest(ioStream)
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

        val request = HttpRequestHandler().handleRequest(ioStream)

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
        val request = HttpRequestHandler().handleRequest(ioStream)
        assertThrows<ChunkExtensionTooLong> {
            request.body.readAllBytes()
        }
    }
    @Test
    fun compressionDeflate_shouldBeHandled() {
        val wikiString = "Wikipedia in\r\n\r\nchunks."
        val compressedBody = wikiString.toByteArray().let {boa ->
            val outputStream = ByteArrayOutputStream()
            DeflaterOutputStream(outputStream).use { it.write(boa) }
            outputStream.toByteArray()
        }
        val remoteRequest = PostRequest.from( "http://localhost:8080",
            HttpHeaders().withContentEncoding(ContentEncoding.DEFLATE),
            HttpBody.fromBytes(compressedBody))

        val ioStream = IOStream(remoteRequest.asStream(), OutputStream.nullOutputStream())

        val request = HttpRequestHandler().handleRequest(ioStream)

        val body = request.body
        val bytes = body.readAllBytes()
        assertEquals(wikiString, bytes.toString(Charsets.US_ASCII))
    }
    @Test
    fun compressionGzip_shouldBeHandled() {
        val wikiString = "Wikipedia in\r\n\r\nchunks."

        val compressed = gzipCompress(wikiString)
        val remoteRequest = PostRequest.from( "http://localhost:8080",
            HttpHeaders().withContentEncoding(ContentEncoding.GZIP),
            HttpBody.fromBytes(compressed))

        val ioStream = IOStream(remoteRequest.asStream(), OutputStream.nullOutputStream())

        val request = HttpRequestHandler().handleRequest(ioStream)

        val body = request.body
        val bytes = body.readAllBytes()
        assertEquals(wikiString, bytes.toString(Charsets.US_ASCII))
    }
    fun gzipCompress(input: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).use { gzipOutputStream ->
            gzipOutputStream.write(input.toByteArray(Charsets.UTF_8))
        }
        return outputStream.toByteArray()
    }

}
