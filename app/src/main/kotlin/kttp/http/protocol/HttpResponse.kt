package kttp.http.protocol

import java.io.ByteArrayInputStream
import java.io.InputStream


class HttpResponse(val statusLine: StatusLine, val headers: HttpHeaders, val body: HttpBody) {

    companion object {
        fun ok(headers: HttpHeaders = HttpHeaders(), body: String = ""): HttpResponse {
            return fromStatus(HttpStatus.OK, headers, body)
        }

        fun badRequest(headers: HttpHeaders = HttpHeaders(), body: String = ""): HttpResponse {
            return fromStatus(HttpStatus.BAD_REQUEST, headers, body)
        }

        fun internalError(headers: HttpHeaders = HttpHeaders(), body: String = ""): HttpResponse {
            return fromStatus(HttpStatus.INTERNAL_SERVER_ERROR, headers, body)
        }

        fun fromStatus(httpStatus: HttpStatus, headers: HttpHeaders, body: String = ""): HttpResponse {
            val statusLine = StatusLine(HttpVersion.DEFAULT_VERSION, httpStatus)
            if(!headers.hasContentLength())
                headers.withContentLength(body.length)
            return HttpResponse(statusLine, headers, HttpBody.fromString(body))
        }

        fun ok(headers: HttpHeaders, body: HttpBody = HttpBody()): HttpResponse {
            return fromStatus(HttpStatus.OK, headers, body)
        }

        fun badRequest(headers: HttpHeaders, body: HttpBody= HttpBody()): HttpResponse {
            return fromStatus(HttpStatus.BAD_REQUEST, headers, body)
        }

        fun internalError(headers: HttpHeaders, body: HttpBody = HttpBody()): HttpResponse {
            return fromStatus(HttpStatus.INTERNAL_SERVER_ERROR, headers, body)
        }

        fun fromStatus(httpStatus: HttpStatus, headers: HttpHeaders, body: HttpBody = HttpBody()): HttpResponse {
            val statusLine = StatusLine(HttpVersion.DEFAULT_VERSION, httpStatus)
            if(!headers.hasContentLength())
                headers.withContentLength(body.contentLength!!)
            return HttpResponse(statusLine, headers, body)
        }

    }

    override fun toString(): String {
        if (body.hasContentLength() && !headers.hasContentLength())
            headers.withContentLength(body.contentLength!!)
        // Todo: handle Transfer-Encoding https://www.rfc-editor.org/rfc/rfc9112#name-transfer-encoding
        val bodyString = body.readAllBytes().toString(Charsets.UTF_8)
        return "$statusLine\r\n$headers\r\n\r\n$bodyString"
    }

    fun asStream(): InputStream {
        return HttpResponseStream(this)
    }



}

class HttpResponseStream(private val httpResponse: HttpResponse) : InputStream() {

    private var closed = false;

    private val statusLineAndHeaders: ByteArrayInputStream

    init {
        val statusLineBytes = "${httpResponse.statusLine}\r\n".toByteArray(Charsets.UTF_8)
        val headersBytes = "${httpResponse.headers}\r\n\r\n".toByteArray(Charsets.UTF_8)
        statusLineAndHeaders = ByteArrayInputStream(statusLineBytes + headersBytes)
    }
    override fun read(): Int {

        val byteArray = ByteArray(1)
        val read = read(byteArray, 0, 1)
        return if (read == -1) -1 else byteArray[0].toInt()
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (closed)
            return -1

        val statusLineAndHeadersRead = statusLineAndHeaders.read(b, off, len)
        if (statusLineAndHeadersRead == -1) { // statusLineAndHeaders is empty
            val bodyRead = httpResponse.body.read(b, off, len)
            if (bodyRead == -1) {
                closed = true
                return -1
            }
            return bodyRead
        } else if (statusLineAndHeadersRead < len) {
            val bodyRead = httpResponse.body.read(b, off + statusLineAndHeadersRead, len - statusLineAndHeadersRead)
            if (bodyRead == -1) {
                closed = true
                return -1
            }
            return statusLineAndHeadersRead + bodyRead
        }
        return statusLineAndHeadersRead
    }

    override fun close() {
        closed = true
        statusLineAndHeaders.close()
        httpResponse.body.close()
    }

    override fun readAllBytes(): ByteArray {
        val statusLineAndHeadersBytes = statusLineAndHeaders.readAllBytes()
        val bodyBytes = httpResponse.body.readAllBytes()
        return statusLineAndHeadersBytes + bodyBytes
    }

    override fun readNBytes(len: Int): ByteArray {
        val statusLineAndHeadersBytes = statusLineAndHeaders.readNBytes(len)
        val bodyBytes = httpResponse.body.readNBytes(len - statusLineAndHeadersBytes.size)
        return statusLineAndHeadersBytes + bodyBytes
    }

    override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int {
        val statusLineAndHeadersBytes = statusLineAndHeaders.readNBytes(b, off, len)
        val bodyBytes = httpResponse.body.readNBytes(b, off + statusLineAndHeadersBytes, len - statusLineAndHeadersBytes)
        return statusLineAndHeadersBytes + bodyBytes
    }
}