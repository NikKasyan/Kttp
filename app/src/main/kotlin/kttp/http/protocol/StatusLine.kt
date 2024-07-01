package kttp.http.protocol

data class StatusLine(val httpVersion: HttpVersion, val status: HttpStatus, val message: String = status.message) {
    override fun toString(): String {
        return "$httpVersion ${status.code} $message"
    }
}