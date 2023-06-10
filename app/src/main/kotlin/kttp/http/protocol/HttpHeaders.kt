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
    fun addContentLength(contentLength: Int) {
        headers[CommonHeaders.CONTENT_LENGTH] = contentLength.toString()
    }

    fun hasContentLength(): Boolean {
        return headers.containsKey(CommonHeaders.CONTENT_LENGTH)
    }

    fun getContentLength(): Int {
        return headers[CommonHeaders.CONTENT_LENGTH]!!.toInt()
    }

    fun addUserAgent(userAgent: String) {
        headers[CommonHeaders.USER_AGENT] = userAgent
    }

    fun hasUserAgent(): Boolean {
        return headers.containsKey(CommonHeaders.USER_AGENT)
    }

    fun getUserAgent(): String {
        return headers[CommonHeaders.USER_AGENT]!!
    }

    fun addHost(host: String) {
        headers[CommonHeaders.HOST] = host
    }

    fun hasHost(): Boolean {
        return headers.containsKey(CommonHeaders.HOST)
    }

    fun getHost(): String {
        return headers[CommonHeaders.HOST]!!
    }

    fun addAcceptLanguage(vararg language: String) {
        headers[CommonHeaders.ACCEPT_LANGUAGE] = language.joinToString(", ")
    }

    fun addAcceptLanguage(language: List<String>) {
        headers[CommonHeaders.ACCEPT_LANGUAGE] = language.joinToString(", ")
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