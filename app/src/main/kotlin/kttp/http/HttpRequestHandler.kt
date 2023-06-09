package kttp.http

import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.RequestLine
import kttp.http.protocol.HttpRequest
import kttp.net.IOStream
import java.lang.Exception

class HttpRequestHandler {


    //Todo: Should probably loop here because https://www.rfc-editor.org/rfc/rfc7230#section-6.3
    // suggests that the connection may send more than one request
    fun handle(io: IOStream): HttpRequest {
        try {
            val httpVersionString = io.readLine()
            val requestLine = RequestLine(httpVersionString)

            val headers = readHeaders(io)

            val body = readBody(io, headers)

            return HttpRequest(requestLine, headers, body)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }

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