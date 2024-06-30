package kttp.http

import kttp.http.protocol.*
import kttp.log.Logger
import kttp.net.IOStream


private val startsWithWhiteSpace = Regex("^\\s+")

private val log: Logger = Logger(HttpRequestHandler::class.java)

class HttpRequestHandler {


    //Todo: Should probably loop here because https://www.rfc-editor.org/rfc/rfc7230#section-6.3
    // suggests that the connection may send more than one request
    fun handle(io: IOStream): HttpRequest {

        val requestLineString = io.readLine()
        val requestLine = RequestLine(requestLineString)
        log.debug { "Request Line: $requestLineString" }
        val headers = readHeaders(io)

        log.debug { "Headers: $headers" }


        checkHeaders(headers)

        val body = readBody(io, headers)


        log.debug { "Body: $body" }

        return HttpRequest(requestLine, headers, body)
    }

    private fun checkHeaders(headers: HttpHeaders) {
        if (!headers.hasHost())
            throw MissingHostHeader()
    }

    //Todo: Handle also Transfer-Encoding https://www.rfc-editor.org/rfc/rfc9112#name-transfer-encoding
    private fun readBody(io: IOStream, headers: HttpHeaders): String {
        if (headers.hasContentLength()) {
            val contentLength = headers.getContentLength()
            return io.readBytes(contentLength).toString(Charsets.UTF_8)
        }
        return ""
    }

    private fun readHeaders(io: IOStream): HttpHeaders {
        val headers = HttpHeaders()

        var isFirstLine = true
        while (true) {
            val header = io.readLine()
            if (header.isEmpty())
                break
            try {
                // Todo: Handle Line folding https://www.rfc-editor.org/rfc/rfc9112#name-obsolete-line-folding
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
        return headers
    }

}

class MissingHostHeader : InvalidHttpRequest("Missing Host Header")
