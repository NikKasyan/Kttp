package kttp.http.protocol

class HttpResponse(val statusLine: StatusLine, val headers: HttpHeaders, val body: String) {

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
            return HttpResponse(statusLine, headers, body)
        }
    }

    override fun toString(): String {
        if (body.isNotEmpty() && !headers.hasContentLength())
            headers.contentLength(body.length)
        return "$statusLine\r\n$headers\r\n\r\n$body"
    }

}