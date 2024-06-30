package kttp.http.protocol

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

object CommonHeaders {
    const val CONTENT_LENGTH = "Content-Length"
    const val USER_AGENT = "User-Agent"
    const val HOST = "Host"
    const val ACCEPT_LANGUAGE = "Accept-Language"
    const val TRANSFER_ENCODING = "Transfer-Encoding"
    const val DATE = "Date"
    //Todo: Add missing Common Headers
    // Trailer: https://www.rfc-editor.org/rfc/rfc9110#name-trailer

}

object DateFormats {
    private const val IMF_FIX_DATE = "EEE, dd MMM yyyy HH:mm:ss z"


    fun createImfFixDateFormat(locale: Locale = Locale.US, timeZone: TimeZone = TimeZone.getDefault()): DateFormat {

        val dateFormat = SimpleDateFormat(IMF_FIX_DATE, locale)
        dateFormat.timeZone = timeZone
        return dateFormat
    }
}

//Todo: Handle multiple Headers https://www.rfc-editor.org/rfc/rfc9110#name-field-lines-and-combined-fi
class HttpHeaders(private val headers: HashMap<String, String> = HashMap()) {

    constructor(vararg headers: Pair<String, String>) : this() {
        add(*headers)
    }

    operator fun set(key: String, value: String) {
        headers[key] = value
    }

    operator fun get(key: String): String? = headers[key]

    fun add(headerString: String): HttpHeaders {
        add(HttpHeader(headerString))
        return this
    }

    fun add(header: HttpHeader): HttpHeaders {
        if (header.key == CommonHeaders.HOST && hasHost()) // https://www.rfc-editor.org/rfc/rfc9112#section-3.2-6
            throw InvalidHeader("May not contain multiple Host Fields")
        headers[header.key] = header.value
        return this
    }

    fun add(header: Pair<String, String>): HttpHeaders {
        add(HttpHeader(header))
        return this
    }

    fun add(vararg headers: Pair<String, String>): HttpHeaders {
        add(headers.map { HttpHeader(it) })
        return this
    }

    fun add(headers: List<HttpHeader>): HttpHeaders {
        headers.forEach { add(it) }
        return this
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
        return getAcceptLanguage().split(", ").map { it.trim() }
    }


    fun withTransferEncoding(transferEncoding: String): HttpHeaders {
        headers[CommonHeaders.TRANSFER_ENCODING] = transferEncoding
        return this
    }

    fun hasTransferEncoding(): Boolean {
        return headers.containsKey(CommonHeaders.TRANSFER_ENCODING)
    }

    fun getTransferEncoding(): String {
        return headers[CommonHeaders.TRANSFER_ENCODING]!!
    }

    fun getTransferEncodingAsList(): List<String> {
        return getTransferEncoding().split(", ")
    }

    fun withDate(date: Date, timeZone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.US): HttpHeaders {
        val dateString = convertToImfFixDate(date, timeZone, locale)
        headers[CommonHeaders.DATE] = dateString
        return this
    }

    /**
     * Converts the given Date to a ImfFixDate String as the preferred dateString
     * as mentioned in https://www.rfc-editor.org/rfc/rfc9110#name-date-time-formats
     */
    private fun convertToImfFixDate(date: Date, timeZone: TimeZone, locale: Locale): String {
        val imfDateFormat = DateFormats.createImfFixDateFormat(locale, timeZone)
        return imfDateFormat.format(date)
    }

    fun hasDate(): Boolean {
        return headers.containsKey(CommonHeaders.DATE)
    }

    fun getDate(dateFormat: DateFormat = DateFormats.createImfFixDateFormat()): Date? {
        if (!hasDate())
            return null
        return try {
            dateFormat.parse(headers[CommonHeaders.DATE])
        } catch (e: ParseException) {
            null
        }
    }

    fun getDateAsString(): String = headers[CommonHeaders.DATE]!!

    fun list(): List<HttpHeader> {
        return headers.toList().map { HttpHeader(it) }
    }

    override fun toString(): String {
        return list().joinToString("\r\n")
    }

}

private val tokenRegex = Regex("[A-Za-z0-9!#$%&'*+\\-.^_`|~]+")

private val endsWithWhiteSpace = Regex("\\s$")

class HttpHeader {
    var key: String
        set(value) {
            if (value.matches(endsWithWhiteSpace))
                throw HeaderNameEndsWithWhiteSpace()
            if (!value.matches(tokenRegex))
                throw InvalidHeaderName()
            field = value
        }
    var value: String
        set(value) {
            field = value.trim()
        }

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

class InvalidHeader(header: String) : InvalidHttpRequest("Header must be of structure key: value. $header is not")

class InvalidHeaderName : InvalidHttpRequest("Invalid header name")

class HeaderNameEndsWithWhiteSpace : InvalidHttpRequest("Header may not end with a whitespace")
class HeaderStartsWithWhiteSpace : InvalidHttpRequest("Header may not end with a whitespace")