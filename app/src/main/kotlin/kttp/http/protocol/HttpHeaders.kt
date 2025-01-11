package kttp.http.protocol

import kttp.http.server.MissingHostHeader
import kttp.io.IOStream
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
    const val SERVER = "Server"
    const val CONTENT_TYPE = "Content-Type"
    const val ACCEPT_ENCODING = "Accept-Encoding"
    const val UPGRADE = "Upgrade"
    // Websocket
    const val SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key"
    const val SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version"
    const val SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol"
    const val SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept"
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

        other as Encoding

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

    }
}

class ContentEncoding private constructor(value: String, parameters: Map<String, String> = emptyMap()): Encoding(value, parameters) {

    companion object {
        fun byEncoding(value: String): ContentEncoding {
            return when (value.lowercase()) {
                GZIP.value -> GZIP
                "x-gzip" -> GZIP
                COMPRESS.value -> COMPRESS
                "x-compress" -> COMPRESS
                DEFLATE.value -> DEFLATE
                BZIP2.value -> BZIP2
                BR.value -> BR
                else -> {
                    if (value.contains(";"))
                        byEncodingWithParameter(value)
                    else
                        throw UnknownContentEncoding(value)
                }
            }
        }

        private fun byEncodingWithParameter(value: String): ContentEncoding {
            val parts = value.split(";", limit = 2)
            if (parts.size == 1)
                return ContentEncoding(parts[0])
            val parameters = parts[1].split(";").map {
                val keyValue = it.split("=", limit = 2)
                if (keyValue.size == 1)
                    return@map Pair(keyValue[0], "")
                Pair(keyValue[0], keyValue[1])
            }.toMap()
            val encoding = byEncoding(parts[0])
            return ContentEncoding(encoding.value, parameters)
        }

        val GZIP = ContentEncoding("gzip")
        val COMPRESS = ContentEncoding("compress")
        val DEFLATE = ContentEncoding("deflate")
        val BZIP2 = ContentEncoding("bzip2")
        val BR = ContentEncoding("br")
    }
}

enum class Connection(val value: String) {
    CLOSE("close"),
    KEEP_ALIVE("keep-alive"),
    UPGRADE("upgrade")
}

object MimeTypes {
    const val TEXT_PLAIN = "text/plain"
    const val TEXT_HTML = "text/html"
    const val TEXT_CSS = "text/css"
    const val TEXT_JAVASCRIPT = "text/javascript"
    const val TEXT_XML = "text/xml"
    const val TEXT_CSV = "text/csv"
    const val TEXT_MARKDOWN = "text/markdown"
    const val TEXT_RTF = "text/rtf"
    const val TEXT_VCARD = "text/vcard"
    const val TEXT_VCALENDAR = "text/vcalendar"

    const val IMAGE_GIF = "image/gif"
    const val IMAGE_JPEG = "image/jpeg"
    const val IMAGE_PNG = "image/png"
    const val IMAGE_SVG = "image/svg+xml"
    const val IMAGE_WEBP = "image/webp"
    const val IMAGE_ICO = "image/x-icon"
    const val IMAGE_BMP = "image/bmp"
    const val IMAGE_TIFF = "image/tiff"
    const val IMAGE_PSD = "image/vnd.adobe.photoshop"

    const val AUDIO_MP3 = "audio/mpeg"
    const val AUDIO_OGG = "audio/ogg"
    const val AUDIO_WAV = "audio/wav"
    const val AUDIO_FLAC = "audio/flac"
    const val AUDIO_AAC = "audio/aac"
    const val AUDIO_WMA = "audio/x-ms-wma"
    const val AUDIO_AIFF = "audio/x-aiff"
    const val AUDIO_MIDI = "audio/midi"
    const val VIDEO_MP4 = "video/mp4"
    const val VIDEO_WEBM = "video/webm"
    const val VIDEO_OGG = "video/ogg"
    const val VIDEO_FLV = "video/x-flv"

    const val APPLICATION_JSON = "application/json"
    const val APPLICATION_XML = "application/xml"
    const val APPLICATION_XHTML = "application/xhtml+xml"
    const val APPLICATION_PDF = "application/pdf"
    const val APPLICATION_ZIP = "application/zip"
    const val APPLICATION_GZIP = "application/gzip"
    const val APPLICATION_TAR = "application/x-tar"

    const val APPLICATION_JAVASCRIPT = "application/javascript"
    const val APPLICATION_TYPESCRIPT = "application/typescript"
    const val APPLICATION_WASM = "application/wasm"

    const val APPLICATION_OCTET_STREAM = "application/octet-stream"

    const val MULTIPART_FORM_DATA = "multipart/form-data"
    const val MULTIPART_MIXED = "multipart/mixed"


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

    private val headers: MutableMap<String, String> = headers.toMutableMap()

    constructor(vararg headers: Pair<String, String>) : this() {
        add(*headers)
    }

    constructor(headers: List<HttpHeader>) : this() {
        add(headers)
    }

    constructor(postConstruct: HttpHeaders.() -> Unit) : this() {
        postConstruct()
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

    fun has(key: String): Boolean = headers.containsKey(key)

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

    fun removeContentLength() {
        headers.remove(CommonHeaders.CONTENT_LENGTH)
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
        val port = if (uri.port == -1 || uri.port == 80 || uri.port == 443) "" else ":${uri.port}"
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

    fun withDate(date: Date = Date(), timeZone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.US): HttpHeaders {
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

    var server: String?
        get() = if (hasServer()) server() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.SERVER)
            else
                withServer(value)
        }

    fun withServer(server: String): HttpHeaders {
        headers[CommonHeaders.SERVER] = server
        return this
    }

    fun hasServer(): Boolean {
        return headers.containsKey(CommonHeaders.SERVER)
    }

    fun server(): String {
        return headers[CommonHeaders.SERVER]!!
    }

    fun serverAsString(): String {
        return headers[CommonHeaders.SERVER]!!
    }

    var contentType : String?
        get() = if (hasContentType()) contentType() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.CONTENT_TYPE)
            else
                withContentType(value)
        }

    /**
     * Adds the Content-Type header with the given contentType
     * @see MimeTypes for common content types
     * @param contentType The content type to add
     * @return The HttpHeaders object
     */
    fun withContentType(contentType: String): HttpHeaders {
        headers[CommonHeaders.CONTENT_TYPE] = contentType
        return this
    }

    fun hasContentType(): Boolean {
        return headers.containsKey(CommonHeaders.CONTENT_TYPE)
    }

    fun contentType(): String {
        return headers[CommonHeaders.CONTENT_TYPE]!!
    }

    fun contentTypeAsString(): String {
        return headers[CommonHeaders.CONTENT_TYPE]!!
    }

    var acceptEncoding: String?
        get() = if (hasAcceptEncoding()) acceptEncoding() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.ACCEPT_ENCODING)
            else
                withAcceptEncoding(value)
        }

    fun withAcceptEncoding(vararg encoding: String): HttpHeaders {
        return withAcceptEncoding(encoding.joinToString())
    }

    fun withAcceptEncoding(acceptEncoding: String): HttpHeaders {
        headers[CommonHeaders.ACCEPT_ENCODING] = acceptEncoding
        return this
    }
    fun withAcceptEncoding(vararg encoding: Encoding): HttpHeaders {
        return withAcceptEncoding(encoding.joinToString { it.value })
    }

    fun hasAcceptEncoding(): Boolean {
        return headers.containsKey(CommonHeaders.ACCEPT_ENCODING)
    }

    fun acceptEncoding(): String {
        return headers[CommonHeaders.ACCEPT_ENCODING]!!
    }

    fun acceptEncodingAsList(): List<String> {
        return acceptEncoding().split(",").map { it.trim() }
    }

    fun removeAcceptEncoding() {
        headers.remove(CommonHeaders.ACCEPT_ENCODING)
    }

    fun acceptsEncoding(transferEncoding: Encoding) : Boolean {
        if(!hasAcceptEncoding() || acceptEncodingAsList().isEmpty())
            return false // If no Accept-Encoding is present, then it accepts no encodings
        return acceptEncodingAsList().contains(transferEncoding.value)
    }

    var contentEncoding: ContentEncoding?
        get() = if (hasContentEncoding()) contentEncoding() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.CONTENT_ENCODING)
            else
                withContentEncoding(value)
        }

    fun withContentEncoding(contentEncoding: ContentEncoding): HttpHeaders {
        headers[CommonHeaders.CONTENT_ENCODING] = contentEncoding.toString()
        return this
    }

    fun hasContentEncoding(): Boolean {
        return headers.containsKey(CommonHeaders.CONTENT_ENCODING)
    }

    fun contentEncoding(): ContentEncoding {
        return ContentEncoding.byEncoding(headers[CommonHeaders.CONTENT_ENCODING]!!)
    }

    fun contentEncodingAsString(): String {
        return headers[CommonHeaders.CONTENT_ENCODING]!!
    }

    var upgrade: String?
        get() = if (hasUpgrade()) upgrade() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.UPGRADE)
            else
                withUpgrade(value)
        }

    fun withUpgrade(upgrade: String): HttpHeaders {
        headers[CommonHeaders.UPGRADE] = upgrade
        return this
    }

    fun withWebsocketUpgrade(): HttpHeaders {
        return withUpgrade("websocket")
    }

    fun hasWebsocketUpgrade(): Boolean {
        return hasUpgrade() && upgrade() == "websocket"
    }

    fun hasUpgrade(): Boolean {
        return headers.containsKey(CommonHeaders.UPGRADE)
    }

    fun upgrade(): String {
        return headers[CommonHeaders.UPGRADE]!!
    }

    var webSocketKey: String?
        get() = if (hasWebSocketKey()) webSocketKey() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.SEC_WEBSOCKET_KEY)
            else
                withWebSocketKey(value)
        }

    fun withWebSocketKey(secWebSocketKey: String): HttpHeaders {
        headers[CommonHeaders.SEC_WEBSOCKET_KEY] = secWebSocketKey
        return this
    }

    fun hasWebSocketKey(): Boolean {
        return headers.containsKey(CommonHeaders.SEC_WEBSOCKET_KEY)
    }

    fun webSocketKey(): String {
        return headers[CommonHeaders.SEC_WEBSOCKET_KEY]!!
    }

    var webSocketVersion: String?
        get() = if (hasWebSocketVersion()) webSocketVersion() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.SEC_WEBSOCKET_VERSION)
            else
                withWebSocketVersion(value)
        }

    fun withWebSocketVersion(secWebSocketVersion: String): HttpHeaders {
        headers[CommonHeaders.SEC_WEBSOCKET_VERSION] = secWebSocketVersion
        return this
    }

    fun hasWebSocketVersion(): Boolean {
        return headers.containsKey(CommonHeaders.SEC_WEBSOCKET_VERSION)
    }

    fun webSocketVersion(): String {
        return headers[CommonHeaders.SEC_WEBSOCKET_VERSION]!!
    }

    var webSocketProtocol: String?
        get() = if (hasWebSocketProtocol()) webSocketProtocol() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.SEC_WEBSOCKET_PROTOCOL)
            else
                withWebSocketProtocol(value)
        }

    fun withWebSocketProtocol(vararg  secWebSocketProtocol: String): HttpHeaders {
        return withWebSocketProtocol(secWebSocketProtocol.joinToString(", "))
    }

    fun withWebSocketProtocol(secWebSocketProtocol: String): HttpHeaders {
        headers[CommonHeaders.SEC_WEBSOCKET_PROTOCOL] = secWebSocketProtocol
        return this
    }

    fun hasWebSocketProtocol(): Boolean {
        return headers.containsKey(CommonHeaders.SEC_WEBSOCKET_PROTOCOL)
    }

    fun webSocketProtocol(): String {
        return headers[CommonHeaders.SEC_WEBSOCKET_PROTOCOL]!!
    }

    fun webSocketProtocolAsList(): List<String> {
        return webSocketProtocol().split(",").map { it.trim() }
    }

    var webSocketAccept: String?
        get() = if (hasWebSocketAccept()) webSocketAccept() else null
        set(value) {
            if (value == null)
                headers.remove(CommonHeaders.SEC_WEBSOCKET_ACCEPT)
            else
                withWebSocketAccept(value)
        }

    fun withWebSocketAccept(secWebSocketAccept: String): HttpHeaders {
        headers[CommonHeaders.SEC_WEBSOCKET_ACCEPT] = secWebSocketAccept
        return this
    }

    fun hasWebSocketAccept(): Boolean {
        return headers.containsKey(CommonHeaders.SEC_WEBSOCKET_ACCEPT)
    }

    fun webSocketAccept(): String {
        return headers[CommonHeaders.SEC_WEBSOCKET_ACCEPT]!!
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

    fun addMissingHeaders(headers: HttpHeaders) {
        headers.forEach {
            if (!has(it.key))
                add(it)
        }
    }

}

private val tokenRegex = Regex("[A-Za-z0-9!#$%&'*+\\-.^_`|~]+")

private val endsWithWhiteSpace = Regex("\\s$")

private val startsWithWhiteSpace = Regex("^\\s+")


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

private fun checkHeaders(headers: HttpHeaders) {
    // https://www.rfc-editor.org/rfc/rfc9112#section-6.3-2.3
    if(headers.hasContentLength() && headers.hasTransferEncoding())
        throw InvalidHeaderStructure("Cannot have both Content-Length and Transfer-Encoding")
    if(headers.hasTransferEncoding()
        && headers.transferEncodings().contains(TransferEncoding.CHUNKED)
        && headers.transferEncodings().last() != TransferEncoding.CHUNKED)
        throw InvalidHeaderStructure("Transfer-Encoding must end with chunked if present")
    if(headers.hasContentLength() && headers.contentLengthLong() < 0)
        throw InvalidHeaderStructure("Content-Length must be a number")
}

fun readHeaders(io: IOStream): HttpHeaders {
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
    checkHeaders(headers)
    return headers
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

class UnknownContentEncoding(contentEncoding: String) : InvalidHeader("Unknown Content Encoding: $contentEncoding")

class InvalidContentLength(message: String) : InvalidHeader("Invalid Content-Length: $message")

class InvalidTransferEncoding(message: String) : InvalidHeader("Invalid Transfer-Encoding: $message")
open class InvalidHeader(message: String) : InvalidHttpRequest(message)