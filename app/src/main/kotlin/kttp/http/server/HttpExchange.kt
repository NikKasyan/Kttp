package kttp.http.server

import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.HttpRequest
import kttp.http.protocol.HttpResponse
import kttp.http.protocol.HttpStatus
import kttp.http.protocol.HttpVersion
import kttp.http.protocol.Method
import kttp.http.protocol.StatusLine
import kttp.http.protocol.TransferEncoding
import kttp.io.IOStream
import java.io.InputStream

class HttpExchange(
    val request: HttpRequest,
    private val defaultHeaders: HttpHeaders = HttpHeaders(),
    val io: IOStream
) : AutoCloseable {

    private var headerWritten = false
    private var closed = false
    // The body is written with write(...) as it comes, in chunks
    private var isStreaming = false
    var response: HttpResponse = HttpResponse.ok(defaultHeaders)

    // A response to HEAD has the header section of a GET response, but no content https://www.rfc-editor.org/rfc/rfc9110#section-9.3.2
    private val sendsContent: Boolean
        get() = request.method != Method.HEAD

    private fun writeHeaders(response: HttpResponse) {
        if (headerWritten)
            return
        if (closed)
            return

        addFraming(response)

        io.write(response.statusLine.toString())
        io.write("\r\n")
        io.write(response.headers.toString())
        io.write("\r\n\r\n")
        headerWritten = true
    }

    /**
     * Makes sure the client can tell where the response ends https://www.rfc-editor.org/rfc/rfc9112#section-6.3
     */
    private fun addFraming(response: HttpResponse) {
        val headers = response.headers
        if (!response.statusLine.status.allowsContent)
            return
        // The body is encoded while it is written, so a Content-Length counted before encoding is wrong
        if (headers.hasContentEncoding() && headers.hasContentLength())
            headers.removeContentLength()
        // A sender must not send both https://www.rfc-editor.org/rfc/rfc9112#section-6.2-2
        if (!headers.hasContentLength() && !headers.hasTransferEncoding())
            headers.withTransferEncoding(TransferEncoding.CHUNKED)
    }

    private fun writeBody(response: HttpResponse) {
        response.headers.addMissingHeaders(defaultHeaders)
        writeHeaders(response)
        if (sendsContent)
            io.writeFromStream(response.body.toEncodedBody(response.headers))
    }

    private fun startStreaming() {
        if (headerWritten)
            return
        // The length isn't known in advance, so the body is sent in chunks https://www.rfc-editor.org/rfc/rfc9112#section-7.1
        val headers = defaultHeaders.copy().withTransferEncoding(TransferEncoding.CHUNKED)
        // Written bytes are sent as they are, without a content coding
        headers.contentEncoding = null
        writeHeaders(HttpResponse(StatusLine(HttpVersion.DEFAULT_VERSION, HttpStatus.OK), headers))
        isStreaming = true
    }

    private fun writeChunk(bytes: ByteArray, length: Int = bytes.size) {
        // An empty chunk would end the body https://www.rfc-editor.org/rfc/rfc9112#section-7.1
        if (closed || length == 0 || !sendsContent)
            return
        io.write("${length.toString(16)}\r\n")
        io.writeBytesToBuffer(bytes, 0, length)
        io.write("\r\n")
    }

    fun write(inputStream: InputStream) {
        startStreaming()
        val buffer = ByteArray(4096)
        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1)
                break
            writeChunk(buffer, read)
        }
    }

    fun write(bytes: ByteArray) {
        startStreaming()
        writeChunk(bytes)
    }

    fun writeln(string: String = "") {
        write("${string}\r\n")
    }

    fun write(string: String) {
        write(string.toByteArray())
    }

    fun respond(response: HttpResponse) {
        if (closed)
            return
        if (isStreaming)
            throw IllegalStateException("Can't respond after the body was started with write")
        writeBody(response)
        response.close()
        closed = true
    }

    fun respond(body: String) {
        respond(HttpResponse.ok(body = body))
    }

    /**
     * Ends the response. If nothing was written, an empty 200 OK is sent.
     */
    override fun close() {
        if (closed)
            return
        if (isStreaming) {
            // The last chunk and the end of the (empty) trailer section https://www.rfc-editor.org/rfc/rfc9112#section-7.1
            if (sendsContent)
                io.write("0\r\n\r\n")
        } else
            writeBody(HttpResponse.ok(defaultHeaders))
        closed = true
    }

}
