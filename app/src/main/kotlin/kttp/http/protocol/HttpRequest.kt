package kttp.http.protocol

import java.net.URI

class HttpRequest(private val requestLine: RequestLine, val httpHeaders: HttpHeaders = HttpHeaders(), val body: String = "") {

    companion object {
        fun from(method: Method, uri: URI, httpHeaders: HttpHeaders = HttpHeaders(), body: String = ""): HttpRequest {
            return HttpRequest(RequestLine(method, uri), httpHeaders, body)
        }
    }

    val httpVersion
        get() = requestLine.httpVersion
    val requestUri
        get() = requestLine.uri
    val method
        get() = requestLine.method


    override fun toString(): String {
        return "$requestLine\r\n$httpHeaders\r\n\r\n$body"
    }
}

object GetRequest {
    fun from(uri: URI, httpHeaders: HttpHeaders = HttpHeaders()): HttpRequest {
        return HttpRequest.from(Method.GET, uri, httpHeaders)
    }
}