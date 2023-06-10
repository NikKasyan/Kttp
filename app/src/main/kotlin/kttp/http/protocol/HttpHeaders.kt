package kttp.http.protocol

object CommonHeaders {
    const val CONTENT_LENGTH = "Content-Length"
    const val USER_AGENT = "User-Agent"
    const val HOST = "Host"
    const val ACCEPT_LANGUAGE = "Accept-Language"
}

class HttpHeaders(private val headers: HashMap<String, String> = HashMap()) {

    constructor(vararg headers: Pair<String, String>) : this() {
        add(*headers)
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

    fun add(vararg headers: Pair<String, String>) {
        this.headers.putAll(headers)
    }


    ///////////////////////////////////////////////////////
    //                  COMMON HEADERS                  //
    //////////////////////////////////////////////////////
    fun withContentLength(contentLength: Int): HttpHeaders {
        headers[CommonHeaders.CONTENT_LENGTH] = contentLength.toString()
        return this
    }

    fun hasContentLength(): Boolean {
        return headers.containsKey(CommonHeaders.CONTENT_LENGTH)
    }

    fun getContentLength(): Int {
        return headers[CommonHeaders.CONTENT_LENGTH]!!.toInt()
    }

    fun withUserAgent(userAgent: String): HttpHeaders {
        headers[CommonHeaders.USER_AGENT] = userAgent
        return this
    }

    fun hasUserAgent(): Boolean {
        return headers.containsKey(CommonHeaders.USER_AGENT)
    }

    fun getUserAgent(): String {
        return headers[CommonHeaders.USER_AGENT]!!
    }

    fun withHost(host: String): HttpHeaders {
        headers[CommonHeaders.HOST] = host
        return this
    }

    fun hasHost(): Boolean {
        return headers.containsKey(CommonHeaders.HOST)
    }

    fun getHost(): String {
        return headers[CommonHeaders.HOST]!!
    }

    fun withAcceptLanguage(vararg language: String): HttpHeaders {
        return withAcceptLanguage(language.toList())
    }

    fun withAcceptLanguage(language: List<String>): HttpHeaders {

        return withAcceptLanguage(language as Iterable<String>)
    }

    fun withAcceptLanguage(language: Iterable<String>): HttpHeaders {
        headers[CommonHeaders.ACCEPT_LANGUAGE] = language.joinToString(", ")
        return this
    }

    fun hasAcceptLanguage(): Boolean {
        return headers.containsKey(CommonHeaders.ACCEPT_LANGUAGE)
    }

    fun getAcceptLanguage(): String {
        return headers[CommonHeaders.ACCEPT_LANGUAGE]!!
    }

    fun getAcceptLanguageList(): List<String> {
        return getAcceptLanguage().split(",").map { it.trim() }
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