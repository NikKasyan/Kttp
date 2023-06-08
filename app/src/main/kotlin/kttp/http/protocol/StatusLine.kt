package kttp.http.protocol

data class StatusLine(val httpVersion: HttpVersion, val status: HttpStatus) {

    override fun toString(): String {
        return "$httpVersion ${status.code} ${status.message}"
    }
}