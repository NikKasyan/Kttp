package kttp.http.protocol

object CommonHeaders {
    const val CONTENT_LENGTH = "Content-Length"
}

class HttpHeaders(private val headers: HashMap<String, String> = HashMap()) {

    constructor(vararg headers: Pair<String, String>) : this() {
        this.headers.putAll(headers)
    }

    fun add(headerString: String) {
        add(HttpHeader(headerString))
    }

    fun add(header: HttpHeader) {
        headers[header.key] = header.value
    }

    fun add(header: Pair<String, String>) {
        headers[header.first] = header.second
    }


    ///////////////////////////////////////////////////////
    //                  COMMON HEADERS                  //
    //////////////////////////////////////////////////////
    fun contentLength(contentLength: Int) {
        headers[CommonHeaders.CONTENT_LENGTH] = contentLength.toString()
    }

    fun hasContentLength(): Boolean {
        return headers.containsKey(CommonHeaders.CONTENT_LENGTH)
    }

    fun getContentLength(): Int {
        return headers[CommonHeaders.CONTENT_LENGTH]!!.toInt()
    }

    fun list(): List<HttpHeader> {
        return headers.toList().map { HttpHeader(it) }
    }

    override fun toString(): String {
        return list().joinToString("\r\n")
    }

}

class HttpHeader {
    val key: String
    val value: String

    constructor(httpHeader: String) {
        val httpHeaderParts = httpHeader.split(Regex(": "), 2)
        if (httpHeaderParts.size != 2)
            throw InvalidHeader(httpHeader)
        this.key = httpHeaderParts.component1()
        this.value = httpHeaderParts.component2()
    }

    constructor(key: String, value: String) {
        this.key = key
        this.value = value
    }

    constructor(header: Pair<String, String>) {
        this.key = header.first
        this.value = header.second
    }

    override fun toString(): String {
        return "$key: $value"
    }

}

class InvalidHeader(header: String) : InvalidHttpRequest("Header must be of structure key: value. $header is not") {

}