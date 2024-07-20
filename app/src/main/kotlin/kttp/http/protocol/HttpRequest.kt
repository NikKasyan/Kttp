package kttp.http.protocol

import kttp.http.MissingHostHeader
import java.io.ByteArrayInputStream
import java.net.URI

class HttpRequest(
    private val requestLine: RequestLine,
    val httpHeaders: HttpHeaders = HttpHeaders(),
    val body: HttpBody
) {

    init {
        if(body.hasContentLength() && !httpHeaders.hasContentLength())
            httpHeaders.withContentLength(body.contentLength!!)
    }
    companion object {
        fun from(method: Method, uri: URI, httpHeaders: HttpHeaders = HttpHeaders(), body: String = ""): HttpRequest {
            if(uri.isAbsolute && uri.host == null && !httpHeaders.hasHost())
                throw MissingHostHeader()
            if(!httpHeaders.hasHost())
                httpHeaders.withHost(uri.host)
            val bodyArray = body.toByteArray()

            return HttpRequest(RequestLine(method, uri), httpHeaders, HttpBody(ByteArrayInputStream(bodyArray), bodyArray.size))
        }
    }

    val httpVersion
        get() = requestLine.httpVersion
    val requestUri: URI
    val method
        get() = requestLine.method

    init {
        if (!httpHeaders.hasHost())
            throw MissingHostHeader()
        requestUri = combineToRequestUri(httpHeaders.host(), requestLine.requestTarget)
    }

    private fun combineToRequestUri(host: String, requestTarget: URI): URI {
        return URI(
            "http",
            null,
            host,
            -1,
            requestTarget.path,
            requestTarget.query,
            requestTarget.fragment
        )
    }

    override fun toString(): String {
        return "$requestLine$httpHeaders\r\n\r\n$body"
    }
}

fun hasBareCR(string: String): Boolean {
    for (i in string.indices) {
        if (string[i] == '\r') {
            if (i + 1 >= string.length || string[i + 1] != '\n') {
                return true
            }
        }
    }
    return false
}

object GetRequest {
    fun from(uri: URI, httpHeaders: HttpHeaders = HttpHeaders()): HttpRequest {
        return HttpRequest.from(Method.GET, uri, httpHeaders)
    }
}

