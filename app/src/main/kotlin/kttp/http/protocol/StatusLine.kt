package kttp.http.protocol

class StatusLine {

    val httpVersion: HttpVersion
    val status: HttpStatus
    val message: String

    constructor(statusLine: String) {
        val parts = statusLine.split(" ", limit = 3)
        if (parts.size != 3)
            throw InvalidHttpResponseLine("Status line must have the structure \"HTTP-Version Status-Code Reason-Phrase\". Invalid: $statusLine")
        this.httpVersion = HttpVersion(parts[0])
        this.status = HttpStatus.byCode(parts[1].toInt())
        this.message = parts[2]
    }

    constructor(httpVersion: HttpVersion, status: HttpStatus, message: String = status.message) {
        this.httpVersion = httpVersion
        this.status = status
        this.message = message
    }

    override fun toString(): String {
        return "$httpVersion ${status.code} $message"
    }
}

class InvalidHttpResponseLine(msg: String) : InvalidHttpResponse(msg)

open class InvalidHttpResponse(msg: String) : RuntimeException(msg)