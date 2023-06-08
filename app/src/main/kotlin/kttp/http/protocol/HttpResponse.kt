package kttp.http.protocol

class HttpResponse(val statusLine: StatusLine, val headers: HttpHeaders, val body: String) {


    override fun toString(): String {
        if (body.isNotEmpty() && !headers.hasContentLength())
            headers.contentLength(body.length)
        return "$statusLine\r\n$headers\r\n\r\n$body"
    }

}