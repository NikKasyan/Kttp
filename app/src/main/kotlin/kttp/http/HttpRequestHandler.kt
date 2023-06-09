package kttp.http

import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.HttpRequest
import kttp.http.protocol.RequestLine
import kttp.net.IOStream

class HttpRequestHandler {


    //Todo: Should probably loop here because https://www.rfc-editor.org/rfc/rfc7230#section-6.3
    // suggests that the connection may send more than one request
    fun handle(io: IOStream): HttpRequest {
        val requestLineString = io.readLine()
        val requestLine = RequestLine(requestLineString)

        val headers = readHeaders(io)

        val body = readBody(io, headers)

        return HttpRequest(requestLine, headers, body)
    }

    //Todo: https://www.rfc-editor.org/rfc/rfc7230#section-3.3 should also handle if the body is compressed etc.
    private fun readBody(io: IOStream, headers: HttpHeaders): String {
        if (headers.hasContentLength()) {
            val contentLength = headers.getContentLength()
            return io.readBytes(contentLength).toString(Charsets.UTF_8)
        }
        return ""
    }

    private fun readHeaders(io: IOStream): HttpHeaders {
        val headers = HttpHeaders()
        while (true) {
            val string = io.readLine()
            if (string.isEmpty())
                break
            headers.add(string)
        }
        return headers
    }

}