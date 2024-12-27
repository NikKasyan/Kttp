package kttp.http

import kttp.http.protocol.*
import kttp.io.IOStream
import kttp.log.Logger
import java.net.Socket
import java.net.URI

class HttpClient(baseURL: String) {

    private val baseURI: URI

    companion object {
        fun get(requestUrl: String, httpHeaders: HttpHeaders = HttpHeaders()): HttpResponse {
            return HttpClient(requestUrl).get(httpHeaders = httpHeaders)
        }
    }

    private val log = Logger(javaClass)

    init {
        this.baseURI = fixUri(URI(baseURL))

        if (!this.baseURI.isAbsolute)
            TODO("Throw real exception when baseURI is not absolute")
    }

    private fun fixUri(uri: URI): URI {
        if (uri.scheme == null || !uri.scheme.startsWith("http"))
            return URI("http://${uri}")
        return uri
    }

    fun get(requestUrl: String = "/", httpHeaders: HttpHeaders = HttpHeaders()): HttpResponse {
        return request(Method.GET, requestUrl, httpHeaders)
    }

    fun request(method: Method, requestUrl: String, httpHeaders: HttpHeaders = HttpHeaders()): HttpResponse {
        val socket = Socket(baseURI.host, getPort())
        val io = IOStream(socket.getInputStream(), socket.getOutputStream())


        val request = HttpRequest.from(method, URI(requestUrl), httpHeaders)

        io.writeFromStream(request.asStream())


        val statusLineString = io.readLine()
        val statusLine = StatusLine(statusLineString)

        log.debug{"StatusLine: $statusLine"}
        val headers = readHeaders(io)

        log.debug{"Headers: $headers"}

        val body =
            if(headers.hasTransferEncoding())
                HttpBody.withTransferEncodingRequest(io, headers)
            else
                HttpBody(io, headers.contentLengthLong())

        log.debug { "Body: $body" }

        return HttpResponse(statusLine, headers, body)
    }

    private fun readHeaders(input: IOStream): HttpHeaders {
        val headers = HttpHeaders()
        while (true) {
            val line = input.readLine()
            if (line.isEmpty())
                break
            headers.add(line)
        }
        return headers
    }

    private fun getPort(): Int {
        if (baseURI.port != -1)
            return baseURI.port
        if (baseURI.scheme == "http")
            return 80
        if (baseURI.scheme == "https")
            return 443

        return -1
    }

}