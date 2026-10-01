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

        val body = readBody(io, headers)

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


    // Request message framing is independent of method semantics https://www.rfc-editor.org/rfc/rfc9112#section-6-4
    private fun readBody(io: IOStream, headers: HttpHeaders): HttpBody {

        // A request without Transfer-Encoding and Content-Length has no content https://www.rfc-editor.org/rfc/rfc9112#section-6.3-2.7
        if (!headers.hasTransferEncoding() && !headers.hasContentLength())
            return HttpBody.empty()

        // Without chunked as the final coding the length of a request can't be known https://www.rfc-editor.org/rfc/rfc9112#section-6.3-2.4
        if (headers.hasTransferEncoding() && headers.transferEncodings().last() != TransferEncoding.CHUNKED)
            throw InvalidTransferEncoding("chunked must be the final transfer coding of a request")

        val body = HttpBody.withDecoding(io, headers)

        log.debug { "Body: $body" }
        return body
    }
}

class MissingHostHeader : InvalidHttpRequest("Missing Host Header")