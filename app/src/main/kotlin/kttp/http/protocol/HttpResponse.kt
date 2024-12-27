package kttp.http.protocol

import kttp.io.CombinedInputStream
import kttp.io.IOStream
import java.io.InputStream


class HttpResponse(val statusLine: StatusLine, val headers: HttpHeaders, val body: HttpBody = HttpBody.empty()): AutoCloseable {

    companion object {
        fun ok(headers: HttpHeaders = HttpHeaders(), body: String): HttpResponse {
            return fromStatus(HttpStatus.OK, headers, body)
        }

        fun badRequest(headers: HttpHeaders = HttpHeaders(), body: String): HttpResponse {
            return fromStatus(HttpStatus.BAD_REQUEST, headers, body)
        }

        fun internalError(headers: HttpHeaders = HttpHeaders(), body: String): HttpResponse {
            return fromStatus(HttpStatus.INTERNAL_SERVER_ERROR, headers, body)
        }

        fun notFound(headers: HttpHeaders = HttpHeaders(), body: String): HttpResponse {
            return fromStatus(HttpStatus.NOT_FOUND, headers, body)
        }

        fun fromStatus(
            httpStatus: HttpStatus,
            headers: HttpHeaders = HttpHeaders(),
            body: HttpBody = HttpBody()
        ): HttpResponse {
            val statusLine = StatusLine(HttpVersion.DEFAULT_VERSION, httpStatus)
            if (!headers.hasContentLength() && body.hasContentLength())
                headers.withContentLength(body.contentLength!!)
            return HttpResponse(statusLine, headers, body)
        }

        fun fromStatus(httpStatus: HttpStatus, headers: HttpHeaders = HttpHeaders(), body: String = ""): HttpResponse {
            val statusLine = StatusLine(HttpVersion.DEFAULT_VERSION, httpStatus)
            if (!headers.hasContentLength())
                headers.withContentLength(body.length.toLong())
            return HttpResponse(statusLine, headers, HttpBody.fromString(body))
        }

        fun ok(headers: HttpHeaders = HttpHeaders(), body: HttpBody = HttpBody.empty()): HttpResponse {
            return fromStatus(HttpStatus.OK, headers, body)
        }

        fun badRequest(headers: HttpHeaders = HttpHeaders(), body: HttpBody = HttpBody.empty()): HttpResponse {
            return fromStatus(HttpStatus.BAD_REQUEST, headers, body)
        }

        fun internalError(headers: HttpHeaders = HttpHeaders(), body: HttpBody = HttpBody.empty()): HttpResponse {
            return fromStatus(HttpStatus.INTERNAL_SERVER_ERROR, headers, body)
        }

    }

    override fun toString(): String {
        if (body.hasContentLength() && !headers.hasContentLength())
            headers.withContentLength(body.contentLength!!)
        // Todo: handle Transfer-Encoding https://www.rfc-editor.org/rfc/rfc9112#name-transfer-encoding
        return asStream().readAllBytes().toString(Charsets.US_ASCII)
    }

    fun asStream(): InputStream {
        return CombinedInputStream(
            "${statusLine}\r\n".byteInputStream(),
            "${headers}\r\n\r\n".byteInputStream(),
            body
        )
    }

    fun writeTo(ioStream: IOStream) {
        val stream = asStream()

        val buffer = ByteArray(2048)

        while (true) {
            val read = stream.read(buffer)
            if (read == -1)
                break
            ioStream.writeBytes(buffer)
        }

    }

    override fun close() {
        body.close()
    }

}