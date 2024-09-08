package kttp.http

import kttp.http.protocol.*
import kttp.log.Logger
import kttp.io.IOStream
import kttp.io.LineTooLongException


private val startsWithWhiteSpace = Regex("^\\s+")

private val log: Logger = Logger(HttpRequestHandler::class.java)

class HttpRequestHandler {


    //Todo: Should probably loop here because https://www.rfc-editor.org/rfc/rfc7230#section-6.3
    // suggests that the connection may send more than one request
    fun handle(io: IOStream): HttpRequest {

        val requestLine = readRequestLine(io)

        val headers = readHeaders(io)

        checkHeaders(headers)

        val body = readBody(io, requestLine, headers)

        return HttpRequest(requestLine, headers, body)
    }

    private fun readRequestLine(io: IOStream): RequestLine {

        var requestLineString: String
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

    private fun checkHeaders(headers: HttpHeaders) {
        if (!headers.hasHost())
            throw MissingHostHeader()
        // https://www.rfc-editor.org/rfc/rfc9112#section-6.3-2.3
        if(headers.hasContentLength() && headers.hasTransferEncoding())
            throw InvalidHeaderStructure("Cannot have both Content-Length and Transfer-Encoding")
        if(headers.hasTransferEncoding()
            && headers.transferEncodingAsList().contains(TransferEncoding.CHUNKED)
            && headers.transferEncodingAsList().last() != TransferEncoding.CHUNKED)
            throw InvalidHeaderStructure("Transfer-Encoding must end with chunked if present")
        if(headers.hasContentLength() && headers.contentLengthLong() < 0)
            throw InvalidHeaderStructure("Content-Length must be a number")
    }

    private fun readHeaders(io: IOStream): HttpHeaders {
        val headers = HttpHeaders()

        var isFirstLine = true
        while (true) {
            val header = io.readLine()
            if (header.isEmpty())
                break
            try {
                headers.add(header)
                isFirstLine = false
            } catch (e: InvalidHeaderName) {
                //Ignore headers with invalid Header Name
                //Except if it is the first one
                // https://www.rfc-editor.org/rfc/rfc9112#section-2.2-8
                if (header.matches(startsWithWhiteSpace) && isFirstLine)
                    throw HeaderStartsWithWhiteSpace()
            }
        }
        log.debug { "Headers: $headers" }
        return headers
    }

    private fun readBody(io: IOStream, requestLine: RequestLine, headers: HttpHeaders): HttpBody {

        val body = if (requestLine.method.allowsBody())
            if(headers.hasTransferEncoding()) HttpBody.withTransferEncodingRequest(io, headers)
            else HttpBody(io, headers.contentLengthLong())
        else HttpBody.empty()

        log.debug { "Body: $body" }
        return body
    }
}

class MissingHostHeader : InvalidHttpRequest("Missing Host Header")