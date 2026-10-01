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

        fun forbidden(headers: HttpHeaders = HttpHeaders(), body: String): HttpResponse {
            return fromStatus(HttpStatus.FORBIDDEN, headers, body)
        }

        /**
         * Adds Content-Length or Transfer-Encoding, so the client can tell where the response ends
         * https://www.rfc-editor.org/rfc/rfc9112#section-6.3
         * The given headers are copied and not changed.
         */
        fun fromStatus(
            httpStatus: HttpStatus,
            headers: HttpHeaders = HttpHeaders(),
            body: HttpBody = HttpBody.empty()
        ): HttpResponse {
            val statusLine = StatusLine(HttpVersion.DEFAULT_VERSION, httpStatus)
            val responseHeaders = headers.copy()
            if (!httpStatus.allowsContent || responseHeaders.hasContentLength() || responseHeaders.hasTransferEncoding())
                return HttpResponse(statusLine, responseHeaders, body)
            // A content coding changes the length, so it is only known for a body that is sent as it is
            if (body.hasContentLength() && !responseHeaders.hasContentEncoding())
                responseHeaders.withContentLength(body.contentLength!!)
            else
                responseHeaders.withTransferEncoding(TransferEncoding.CHUNKED)
            return HttpResponse(statusLine, responseHeaders, body)
        }

        fun fromStatus(httpStatus: HttpStatus, headers: HttpHeaders = HttpHeaders(), body: String): HttpResponse {
            return fromStatus(httpStatus, headers, HttpBody.fromString(body))
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
        ioStream.writeFromStream(asStream())
    }

    override fun close() {
        body.close()
    }

}