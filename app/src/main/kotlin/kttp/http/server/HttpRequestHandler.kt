package kttp.http.server

import kttp.http.protocol.*
import kttp.log.Logger
import kttp.io.IOStream
import kttp.io.LineTooLongException


private val log: Logger = Logger(HttpRequestHandler::class.java)

class HttpRequestHandler {

    fun handleRequest(io: IOStream): HttpRequest {

        val requestLine = readRequestLine(io)

        val headers = readHeaders(io)

        if (!headers.hasHost())
            throw MissingHostHeader()

        val body = readBody(io, requestLine, headers)

        return HttpRequest(requestLine, headers, body)
    }

    private fun readRequestLine(io: IOStream): RequestLine {

        var requestLineString = ""
        try {
            requestLineString = io.readLine()
            if (requestLineString.isEmpty()) // Got empty line try next line https://www.rfc-editor.org/rfc/rfc9112#section-2.2-6
                requestLineString = io.readLine()
        } catch (e: LineTooLongException) {
            val line = e.line
            val parts = line.split(" ")
            if (parts.size == 2) {
                throw UriTooLong()
            } else {
                throw RequestLineTooLong()
            }
        }

        val requestLine = RequestLine(requestLineString)
        log.debug { "Request Line: $requestLineString" }
        return requestLine
    }


    private fun readBody(io: IOStream, requestLine: RequestLine, headers: HttpHeaders): HttpBody {

        val body = if (requestLine.method.allowsBody())
            HttpBody.withDecoding(io, headers)
        else HttpBody.empty()

        log.debug { "Body: $body" }
        return body
    }
}

class MissingHostHeader : InvalidHttpRequest("Missing Host Header")