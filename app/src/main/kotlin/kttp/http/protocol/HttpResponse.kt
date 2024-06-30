package kttp.http.protocol

//TODO: body should not be string but Inputstream
class HttpResponse(private val statusLine: StatusLine, val headers: HttpHeaders, private val body: HttpBody) {

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
            return HttpResponse(statusLine, headers, HttpBody.fromString(body))
        }
    }

    override fun toString(): String {
        if (body.hasContentLength() && !headers.hasContentLength())
            headers.withContentLength(body.contentLength!!)
        // Todo: handle Transfer-Encoding https://www.rfc-editor.org/rfc/rfc9112#name-transfer-encoding
        TODO("Read body")
        return "$statusLine\r\n$headers\r\n\r\n$body"
    }



}