package kttp.http

import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.HttpInitialRequest
import kttp.http.protocol.HttpRequest
import kttp.net.IOStream
import java.lang.Exception

class HttpHandler {


    fun handle(io: IOStream): HttpRequest {
        try {
            val httpVersionString = io.readLine()
            val httpInitialRequest = HttpInitialRequest(httpVersionString)

            val headers = readHeaders(io)

            val body = readBody(io, headers)

            return HttpRequest(httpInitialRequest, headers, body)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }

    }

    //Todo: https://www.rfc-editor.org/rfc/rfc2616#section-7.2 should also handle if the body is compressed etc.
    //https://www.rfc-editor.org/rfc/rfc2616#section-4.3
    private fun readBody(io: IOStream, headers: HttpHeaders): String {
        if (headers.hasContentLength()) {
            val contentLength = headers.getContentLength()
            return io.readBytes(contentLength)
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