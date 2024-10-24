package kttp.http.protocol

import java.net.URI
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
    const val TE = "TE"
    const val DATE = "Date"
    const val ACCEPT = "Accept"
    const val CONTENT_ENCODING = "Content-Encoding"
    const val CONNECTION = "Connection"
    //Todo: Add missing Common Headers
    // Trailer: https://www.rfc-editor.org/rfc/rfc9110#name-trailer

}

//Todo: Might need to refactor this as TransferEncoding, ContentEncoding and TE have similar structure
// But allow different values
open class Encoding constructor(val value: String, val parameters: Map<String, String> = emptyMap()) {
    override fun toString(): String {
        return value + if (parameters.isEmpty()) "" else "; " + parameters.map { "${it.key}=${it.value}" }.joinToString("; ")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TransferEncoding

        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}
class TransferEncoding private constructor(value: String, parameters: Map<String, String> = emptyMap()): Encoding(value, parameters) {

    companion object {
        fun byEncoding(value: String): TransferEncoding {
            return when (value.lowercase()) {
                CHUNKED.value -> CHUNKED
                IDENTITY.value -> IDENTITY
                GZIP.value -> GZIP
                "x-gzip" -> GZIP
                COMPRESS.value -> COMPRESS
                "x-compress" -> COMPRESS
                DEFLATE.value -> DEFLATE
                else -> {
                    if (value.contains(";"))
                        byEncodingWithParameter(value)
                    else
                        throw UnknownTransferEncoding(value)
                }
            }
        }

        private fun byEncodingWithParameter(value: String): TransferEncoding {
            val parts = value.split(";", limit = 2)
            if (parts.size == 1)
                return TransferEncoding(parts[0])
            val parameters = parts[1].split(";").map {
                val keyValue = it.split("=", limit = 2)
                if (keyValue.size == 1)
                    return@map Pair(keyValue[0], "")
                Pair(keyValue[0], keyValue[1])
            }.toMap()
            val encoding = byEncoding(parts[0])
            return TransferEncoding(encoding.value, parameters)
        }

        val CHUNKED = TransferEncoding("chunked")
        val IDENTITY = TransferEncoding("identity")
        val GZIP = TransferEncoding("gzip")
        val COMPRESS = TransferEncoding("compress")
        val DEFLATE = TransferEncoding("deflate")

    }
}

enum class Connection(val value: String) {
    CLOSE("close"),
    KEEP_ALIVE("keep-alive"),
    UPGRADE("upgrade")
}




object DateFormats {
    private const val IMF_FIX_DATE = "EEE, dd MMM yyyy HH:mm:ss z"


    fun createImfFixDateFormat(locale: Locale = Locale.US, timeZone: TimeZone = TimeZone.getDefault()): DateFormat {

        val dateFormat = SimpleDateFormat(IMF_FIX_DATE, locale)
        dateFormat.timeZone = timeZone
        return dateFormat
    }
}


fun checkHeaderNotContainsBareCR(header: HttpHeader) {
    if (hasBareCR(header.key))
        throw InvalidHeaderStructure("Header key may not contain a bare CR")
    if (hasBareCR(header.value))
        throw InvalidHeaderStructure("Header value may not contain a bare CR")
}

//Todo: Handle multiple Headers https://www.rfc-editor.org/rfc/rfc9110#name-field-lines-and-combined-fi
class HttpHeaders(headers: Map<String, String> = HashMap()) : Iterable<HttpHeader> {

    private val headers: MutableMap<String, String>

    init {
        this.headers = headers.toMutableMap()
    }

    constructor(vararg headers: Pair<String, String>) : this() {
        add(*headers)
    }

    constructor(headers: List<HttpHeader>) : this() {
        add(headers)
    }

    override fun iterator(): Iterator<HttpHeader> {
        return headers.map { HttpHeader(it.key, it.value) }.iterator()
    }

    operator fun set(key: String, value: String) {
        headers[key] = value
    }

    operator fun get(key: String): String? = headers[key]

    fun remove(key: String) {
        headers.remove(key)
    }

    fun add(headerString: String): HttpHeaders {
        add(HttpHeader(headerString))
        return this
    }

    fun add(header: HttpHeader): HttpHeaders {
        if (header.key == CommonHeaders.HOST && hasHost()) // https://www.rfc-editor.org/rfc/rfc9112#section-3.2-6
            throw TooManyHostHeaders()
        checkHeaderNotContainsBareCR(header) // https://www.rfc-editor.org/rfc/rfc9112#name-message-parsing
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

    fun add(vararg headers: HttpHeader): HttpHeaders {
        add(headers.toList())
        return this
    }

    ///////////////////////////////////////////////////////
    //                  COMMON HEADERS                  //
    //////////////////////////////////////////////////////

    var contentLength: Long?
        get() = if (hasContentLength()) contentLengthLong() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.CONTENT_LENGTH)
            else
                withContentLength(value)
        }

    fun withContentLength(contentLength: Long): HttpHeaders {
        headers[CommonHeaders.CONTENT_LENGTH] = contentLength.toString()
        return this
    }

    fun hasContentLength(): Boolean {
        return headers.containsKey(CommonHeaders.CONTENT_LENGTH)
    }

    fun contentLength(): Int {
        return contentLengthLong().toInt()
    }

    fun contentLengthLong(): Long {
        return contentLengthAsString().toLongOrNull() ?: contentLengthFromList()
    }

    fun contentLengthAsString(): String {
        return headers[CommonHeaders.CONTENT_LENGTH]!!
    }

    private fun contentLengthFromList(): Long {
        val contentLengths = contentLengthAsString().split(",").map { it.trim().toLongOrNull() }
        if (contentLengths.any { it == null })
            throw InvalidContentLength("Content-Length must be a number")
        val distinctContentLengths = contentLengths.distinct()
        if (distinctContentLengths.size > 1)
            throw InvalidContentLength("Content-Length must be the same for all parts")
        return distinctContentLengths.first()!!
    }

    var userAgent: String?
        get() = if (hasUserAgent()) userAgent() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.USER_AGENT)
            else
                withUserAgent(value)
        }

    fun withUserAgent(userAgent: String): HttpHeaders {
        headers[CommonHeaders.USER_AGENT] = userAgent
        return this
    }

    fun hasUserAgent(): Boolean {
        return headers.containsKey(CommonHeaders.USER_AGENT)
    }

    fun userAgent(): String {
        return headers[CommonHeaders.USER_AGENT]!!
    }

    var host: String?
        get() = if (hasHost()) host() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.HOST)
            else
                withHost(value)
        }

    fun withHost(host: String): HttpHeaders {
        headers[CommonHeaders.HOST] = host
        return this
    }

    fun withHost(uri: URI): HttpHeaders {
        val port = if (uri.port == -1) "" else ":${uri.port}"
        headers[CommonHeaders.HOST] = "${uri.host}$port"
        return this
    }

    fun hasHost(): Boolean {
        return headers.containsKey(CommonHeaders.HOST)
    }

    fun host(): String {
        return headers[CommonHeaders.HOST]!!
    }

    var acceptLanguage: String?
        get() = if (hasAcceptLanguage()) acceptLanguage() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.ACCEPT_LANGUAGE)
            else
                withAcceptLanguage(value)
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

    fun acceptLanguage(): String {
        return headers[CommonHeaders.ACCEPT_LANGUAGE]!!
    }

    fun acceptLanguageAsList(): List<String> {
        return acceptLanguage().split(", ").map { it.trim() }
    }

    var transferEncoding: TransferEncoding?
        get() = if (hasTransferEncoding()) transferEncoding() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.TRANSFER_ENCODING)
            else
                withTransferEncoding(value)
        }

    fun withTransferEncoding(vararg transferEncoding: TransferEncoding): HttpHeaders {
        checkTransferEncoding(transferEncoding.toList())
        checkTransferEncodingHasNoParameter(transferEncoding.toList())
        headers[CommonHeaders.TRANSFER_ENCODING] = transferEncoding.joinToString { it.value }
        return this
    }

    fun hasTransferEncoding(): Boolean {
        return headers.containsKey(CommonHeaders.TRANSFER_ENCODING)
    }

    fun transferEncoding(): TransferEncoding {
        return transferEncodings().first()
    }

    fun transferEncodingAsString(): String {
        return headers[CommonHeaders.TRANSFER_ENCODING]!!
    }

    fun transferEncodingAsStrings(): List<String> {
        return transferEncodingAsString().split(", ").map { it.trim() }
    }

    fun transferEncodings(): List<TransferEncoding> {
        return transferEncodingAsStrings().map { TransferEncoding.byEncoding(it) }
    }

    var te: TransferEncoding?
        get() = if (hasTe()) te() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.TE)
            else
                withTe(value)
        }

    fun withTe(vararg te: TransferEncoding): HttpHeaders {
        checkTransferEncoding(te.toList())
        checkTransferEncodingHasOnlyAllowedParameter(te.toList(), listOf("q"))
        headers[CommonHeaders.TE] = te.joinToString { it.value }
        return this
    }

    fun hasTe(): Boolean {
        return headers.containsKey(CommonHeaders.TE)
    }

    fun hasTe(vararg te: TransferEncoding): Boolean {
        return hasTe() && te.all { headers[CommonHeaders.TE]!!.contains(it.value) }
    }

    fun te(): TransferEncoding {
        return teEncodings().first()
    }

    fun teAsString(): String {
        return headers[CommonHeaders.TE]!!
    }

    fun teAsStrings(): List<String> {
        return teAsString().split(", ").map { it.trim() }
    }

    fun teEncodings(): List<TransferEncoding> {
        return teAsStrings().map { TransferEncoding.byEncoding(it) }
    }


    var date: Date?
        get() = if (hasDate()) date() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.DATE)
            else
                withDate(value)
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

    fun date(dateFormat: DateFormat = DateFormats.createImfFixDateFormat()): Date? {
        if (!hasDate())
            return null
        return try {
            dateFormat.parse(headers[CommonHeaders.DATE])
        } catch (e: ParseException) {
            null
        }
    }

    fun dateAsString(): String = headers[CommonHeaders.DATE]!!

    var accept: String?
        get() = if (hasAccept()) accept() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.ACCEPT)
            else
                withAccept(value)
        }

    fun withAccept(accept: String): HttpHeaders {
        headers[CommonHeaders.ACCEPT] = accept
        return this
    }

    fun withAccepts(vararg accepts: String): HttpHeaders {
        return withAccepts(accepts.toList())
    }

    fun withAccepts(accepts: List<String>): HttpHeaders {
        return withAccepts(accepts.joinToString())
    }

    fun hasAccept(): Boolean {
        return headers.containsKey(CommonHeaders.ACCEPT)
    }

    fun accept(): String {
        return headers[CommonHeaders.ACCEPT]!!
    }

    fun acceptAsList(): List<String> {
        return accept().split(",").map { it.trim() }
    }

    var connection: Connection?
        get() = if (hasConnection()) connection() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.CONNECTION)
            else
                withConnection(value)
        }

    fun withConnection(connection: Connection): HttpHeaders {
        headers[CommonHeaders.CONNECTION] = connection.value
        return this
    }

    fun hasConnection(connection: Connection): Boolean {
        return headers[CommonHeaders.CONNECTION] == connection.value
    }

    fun withConnection(connection: String): HttpHeaders {
        headers[CommonHeaders.CONNECTION] = connection
        return this
    }

    fun hasConnection(): Boolean {
        return headers.containsKey(CommonHeaders.CONNECTION)
    }

    fun connection(): Connection {
        return Connection.entries.first { it.value == headers[CommonHeaders.CONNECTION] }
    }

    fun connectionAsString(): String {
        return headers[CommonHeaders.CONNECTION]!!
    }




    fun toList(): List<HttpHeader> {
        return headers.toList().map { HttpHeader(it) }
    }

    override fun toString(): String {
        return toList().joinToString("\r\n")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as HttpHeaders

        if (headers != other.headers) return false

        return true
    }

    override fun hashCode(): Int {
        return headers.hashCode()
    }

}

private val tokenRegex = Regex("[A-Za-z0-9!#$%&'*+\\-.^_`|~]+")

private val endsWithWhiteSpace = Regex("\\s$")

private fun checkTransferEncoding(transferEncoding: List<TransferEncoding>) {
    if (transferEncoding.contains(TransferEncoding.IDENTITY) && transferEncoding.size > 1)
        throw InvalidTransferEncoding("Identity must be the only Transfer Encoding if present")
    // May not contain multiple chunked encodings https://www.rfc-editor.org/rfc/rfc9112#section-6.1-4
    val chunkedCount = transferEncoding.count { it == TransferEncoding.CHUNKED }
    if (chunkedCount > 1)
        throw InvalidTransferEncoding("May not contain multiple Chunked Transfer Encodings")
    // Chunked must be the last encoding https://www.rfc-editor.org/rfc/rfc9112#section-6.1-4
    if (chunkedCount == 1 && transferEncoding.indexOf(TransferEncoding.CHUNKED) != transferEncoding.size - 1)
        throw InvalidTransferEncoding("Chunked must be the last Transfer Encoding")
}
private fun checkTransferEncodingHasNoParameter(transferEncodings: List<TransferEncoding>) {
    if(transferEncodings.any { it.parameters.isNotEmpty() })
        throw InvalidTransferEncoding("Transfer Encoding must not have parameters")
}

private fun checkTransferEncodingHasOnlyAllowedParameter(transferEncodings: List<TransferEncoding>, allowedParameters: List<String>) {
    if(transferEncodings.any { it.parameters.keys.any { key -> !allowedParameters.contains(key) } })
        throw InvalidTransferEncoding("Transfer Encoding must only have allowed parameters")
}

class HttpHeader {
    val key: String
    val value: String

    constructor(httpHeader: String) {
        checkHeader(httpHeader)
        val httpHeaderParts = httpHeader.split(Regex(":"), 2)
        if (httpHeaderParts.size != 2)
            throw InvalidHeaderStructure(httpHeader)

        checkHeaderName(httpHeaderParts[0])
        key = httpHeaderParts[0]
        value = httpHeaderParts[1].trim()

    }

    constructor(key: String, value: String) {
        checkHeaderName(key)
        this.key = key
        this.value = value.trim()
    }

    constructor(header: Pair<String, String>) : this(header.first, header.second)

    override fun toString(): String {
        return "$key: $value"
    }

    private fun checkHeader(headerString: String) {
        if (headerString.startsWith("\t") || headerString.startsWith(" ")) {
            if (!headerString.contains(":")) {
                throw LineFoldingNotAllowed()
            } else {
                throw HeaderStartsWithWhiteSpace()
            }
        }
    }

    private fun checkHeaderName(key: String) {

        if (key.matches(endsWithWhiteSpace))
            throw HeaderNameEndsWithWhiteSpace()
        if (!key.matches(tokenRegex))
            throw InvalidHeaderName()
    }

}

class InvalidHeaderStructure(header: String) :
    InvalidHttpRequest("Header must be of structure key: value. $header is not")

class InvalidHeaderName : InvalidHeader("Invalid header name")

class LineFoldingNotAllowed : InvalidHeader("Line folding is not allowed")

class HeaderNameEndsWithWhiteSpace : InvalidHeader("Header may not end with a whitespace")
class HeaderStartsWithWhiteSpace : InvalidHeader("Header may not start with a whitespace")

class TooManyHostHeaders : InvalidHeader("May not contain multiple Host Fields")

class UnknownTransferEncoding(transferEncoding: String) :
    InvalidHeader("Unknown Transfer Encoding: $transferEncoding")

class InvalidContentLength(message: String) : InvalidHeader("Invalid Content-Length: $message")

class InvalidTransferEncoding(message: String) : InvalidHeader("Invalid Transfer-Encoding: $message")
open class InvalidHeader(message: String) : InvalidHttpRequest(message)