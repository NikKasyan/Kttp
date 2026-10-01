package kttp

import kttp.http.protocol.*
import kttp.http.server.HttpExchange
import kttp.io.IOStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HttpExchangeResponseTest {

    private val output = ByteArrayOutputStream()
    private val io = IOStream(InputStream.nullInputStream(), output)

    private fun exchange(method: Method = Method.GET, defaultHeaders: HttpHeaders = HttpHeaders()): HttpExchange {
        val request = HttpRequest.from(method, URI("http://localhost/"), HttpHeaders())
        return HttpExchange(request, defaultHeaders, io)
    }

    private fun writtenResponse(): IOStream {
        return IOStream(output.toByteArray().inputStream(), ByteArrayOutputStream())
    }

    @Test
    fun responseCompressedByDefault_hasTransferEncodingButNoContentLength() {
        val exchange = exchange(defaultHeaders = HttpHeaders().withContentEncoding(ContentEncoding.GZIP))

        exchange.respond(HttpResponse.ok(body = "Hello"))

        val written = writtenResponse()
        written.readLine()
        val headers = readHeaders(written)
        assertEquals(TransferEncoding.CHUNKED, headers.transferEncoding)
        assertFalse(headers.hasContentLength())
        assertEquals("Hello", HttpBody.withDecoding(written, headers).readAsString())
    }

    @Test
    fun streamedWrites_areSentChunked() {
        val exchange = exchange()

        exchange.write("Hello, ")
        exchange.write("World!")
        exchange.close()

        val written = writtenResponse()
        assertEquals(HttpStatus.OK, StatusLine(written.readLine()).status)
        val headers = readHeaders(written)
        assertEquals(TransferEncoding.CHUNKED, headers.transferEncoding)
        assertFalse(headers.hasContentLength())
        assertEquals("Hello, World!", HttpBody.withDecoding(written, headers).readAsString())
        assertEquals(0, written.readAllBytes().size)
    }

    @Test
    fun headResponse_hasHeadersButNoContent() {
        val exchange = exchange(Method.HEAD)

        exchange.respond(HttpResponse.ok(body = "Hello"))

        val written = writtenResponse()
        written.readLine()
        val headers = readHeaders(written)
        assertEquals(5, headers.contentLength)
        assertEquals(0, written.readAllBytes().size)
    }

    @Test
    fun handlerThatSendsNothing_getsEmptyOk() {
        val exchange = exchange()

        exchange.close()

        val written = writtenResponse()
        assertEquals(HttpStatus.OK, StatusLine(written.readLine()).status)
        assertEquals(0, readHeaders(written).contentLength)
        assertEquals(0, written.readAllBytes().size)
    }

    @Test
    fun respondTwice_sendsOneResponse() {
        val exchange = exchange()

        exchange.respond(HttpResponse.ok(body = "first"))
        exchange.respond(HttpResponse.ok(body = "second"))
        exchange.close()

        val written = writtenResponse()
        written.readLine()
        val headers = readHeaders(written)
        assertEquals("first", HttpBody.withDecoding(written, headers).readAsString())
        assertEquals(0, written.readAllBytes().size)
    }
}
