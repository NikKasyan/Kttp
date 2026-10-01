package kttp.protocol

import kttp.http.protocol.*
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse

class HttpResponseTest {

    @Test
    fun testOkResponse() {
        val body = "Hello, World!"
        val response = HttpResponse.ok(headers = HttpHeaders("Content-Type" to "text/plain"), body = body)
        val expectedStatusLine = "HTTP/1.1 200 OK\r\n"
        val expectedHeaders = "Content-Type: text/plain\r\nContent-Length: ${body.length}\r\n\r\n"
        val expectedResponse = expectedStatusLine + expectedHeaders + body

        assertEquals(expectedResponse, response.toString())
    }

    @Test
    fun testBadRequestResponse() {
        val response = HttpResponse.badRequest(body = "Bad Request")
        val expectedStatusLine = "HTTP/1.1 400 Bad Request\r\n"
        val expectedHeaders = "Content-Length: 11\r\n\r\n"
        val expectedBody = "Bad Request"
        val expectedResponse = expectedStatusLine + expectedHeaders + expectedBody

        assertEquals(expectedResponse, response.toString())
    }

    @Test
    fun testInternalErrorResponse() {
        val body = "Internal Error"
        val response = HttpResponse.internalError(body = body)
        val expectedStatusLine = "HTTP/1.1 500 Internal Server Error\r\n"
        val expectedHeaders = "Content-Length: ${body.length}\r\n\r\n"
        val expectedResponse = expectedStatusLine + expectedHeaders + body

        assertEquals(expectedResponse, response.toString())
    }

    @Test
    fun testAsStream() {
        val response = HttpResponse.ok(body = "Stream Test")
        val expectedContent = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: 11\r\n\r\n" +
                "Stream Test"
        val inputStream = response.asStream()

        val actualContent = inputStream.bufferedReader().use { it.readText() }
        assertEquals(expectedContent, actualContent)
    }

    @Test
    fun testCustomResponse() {
        val statusLine = StatusLine(HttpVersion.DEFAULT_VERSION, HttpStatus.NOT_FOUND)
        val headers = HttpHeaders(mapOf("Content-Type" to "text/plain"))
        val bodyString = "Not Found"
        val body = HttpBody.fromString(bodyString)
        val response = HttpResponse(statusLine, headers, body)
        val expectedStatusLine = "HTTP/1.1 404 Not Found\r\n"
        val expectedHeaders = "Content-Type: text/plain\r\nContent-Length: ${bodyString.length}\r\n\r\n"
        val expectedResponse = expectedStatusLine + expectedHeaders + bodyString

        assertEquals(expectedResponse, response.toString())
    }

    @Test
    fun nonAsciiBody_hasContentLengthInBytes() {
        val response = HttpResponse.ok(body = "äöü")

        assertEquals(6, response.headers.contentLength)
        assertEquals(6, HttpBody.fromString("äöü").contentLength)
    }

    @Test
    fun bodyWithUnknownLength_isSentChunked() {
        val response = HttpResponse.ok(body = HttpBody("Hello".byteInputStream()))

        assertEquals(TransferEncoding.CHUNKED, response.headers.transferEncoding)
        assertFalse(response.headers.hasContentLength())
    }

    @Test
    fun emptyBody_hasContentLengthZero() {
        val response = HttpResponse.badRequest()

        assertEquals(0, response.headers.contentLength)
    }

    @Test
    fun responseWithoutContent_hasNoFraming() {
        val response = HttpResponse.fromStatus(HttpStatus.SWITCHING_PROTOCOLS)

        assertFalse(response.headers.hasContentLength())
        assertFalse(response.headers.hasTransferEncoding())
    }

    @Test
    fun fromStatus_doesNotChangeTheGivenHeaders() {
        val headers = HttpHeaders().withContentEncoding(ContentEncoding.GZIP)

        HttpResponse.ok(headers, body = "Hello")

        assertFalse(headers.hasTransferEncoding())
        assertFalse(headers.hasContentLength())
    }
}