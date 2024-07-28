package kttp.http.protocol

import java.io.CharArrayWriter
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.Charset

object URIUtil {

    /**
     * Normalizes the given URI as defined in
     * https://www.rfc-editor.org/rfc/rfc9110#name-https-normalization-and-com
     * https://www.rfc-editor.org/rfc/rfc3986#section-6.2.3
     */
    fun normalizeURI(uri: URI): URI {
        return URI(
            uri.scheme.lowercase(), // https://www.rfc-editor.org/rfc/rfc9110#section-4.2.3-4.3
            null, // Shouldn't be set
            uri.host.lowercase(), // https://www.rfc-editor.org/rfc/rfc9110#section-4.2.3-4.3
            getPortByScheme(uri.scheme!!, uri.port),
            urlDecode(normalizePath(uri.path)),
            urlDecode(uri.query),
            urlDecode(uri.fragment)
        )
    }

    fun getPortByScheme(scheme: String, port: Int): Int {
        // If the port is the default port for the scheme, it MAY be omitted from an "origin" URI
        if (scheme == "http" && port == 80)
            return -1 // https://www.rfc-editor.org/rfc/rfc9110#section-4.2.3-4.1
        if (scheme == "https" && port == 443) {
            return -1
        }
        return port
    }

    private fun normalizePath(path: String?): String {
        if (path.isNullOrEmpty())
            return "/"
        return path
    }

    fun urlDecode(string: String?) = if (string != null) URLDecoder.decode(string, Charsets.UTF_8) else null

    //Todo: Implement rest of https://www.rfc-editor.org/rfc/rfc7230#section-5.3
    fun parseURI(path: String): URI {
        try {
            val uri = URI(path)
            checkURI(uri)
            return uri
        } catch (e: URISyntaxException) {
            throw InvalidHttpRequestPath("Invalid Path $path")
        }
    }

    private fun checkURI(uri: URI) {
        if (uri.isAbsolute)
            checkAbsoluteUri(uri)

        if (!uri.isAbsolute && !uri.path.startsWith("/"))
            throw InvalidHttpRequestPath("Absolute Request path must begin with a /")
    }

    private fun checkAbsoluteUri(uri: URI) {
        if (uri.scheme != "http" && uri.scheme != "https")
            throw InvalidHttpRequestPath("Absolute Request uri may only have scheme http or https")
        if (uri.host.isEmpty())
            throw InvalidHttpRequestPath("Host of absolute URI may not be empty")

    }

    fun encodeURI(s: String): String  = URIEncoder.encode(s)

    fun decodeURI(s: String): String = URLDecoder.decode(s, Charsets.UTF_8)
}

/**
 * Recreated URLEncoder because the conversion of the space (0x20) character to a plus (+) character is not correct for
 * URIs. The space character should be converted to %20.
 */
private object URIEncoder {
    private val charset = Charsets.UTF_8
    // Size is 256 because we want to lookup up to a byte
    private val dontNeedEncoding = BooleanArray(256).apply {
        for (i in 'a'.code..'z'.code) {
            this[i] = true
        }
        for (i in 'A'.code..'Z'.code) {
            this[i] = true
        }
        for (i in '0'.code..'9'.code) {
            this[i] = true
        }
        this['-'.code] = true
        this['_'.code] = true
        this['.'.code] = true
        this['*'.code] = true
    }

    fun encode(s: String, charset: Charset = URIEncoder.charset): String {
        var needToChange = false
        val out: StringBuilder = StringBuilder(s.length)
        val charArrayWriter = CharArrayWriter()

        var i = 0
        while (i < s.length) {
            var c: Int = s[i].code
            if (dontNeedEncoding[c]) {
                out.append(c.toChar())
                i++
            } else {
                do {
                    charArrayWriter.write(c)
                    if (c in 0xD800..0xDBFF) {
                        if (i + 1 < s.length) {
                            val d: Int = s[i + 1].code
                            if (d in 0xDC00..0xDFFF) {
                                charArrayWriter.write(d)
                                i++
                            }
                        }
                    }
                    i++
                } while (i < s.length && !dontNeedEncoding[s[i].also { c = it.code }.code])
                charArrayWriter.flush()
                val str: String = charArrayWriter.toString()
                val ba = str.toByteArray(charset)
                for (b in ba) {
                    out.append('%')
                    var ch = Character.forDigit(b.toInt() shr 4 and 0xF, 16)
                    if (Character.isLetter(ch)) {
                        ch -= 0x20
                    }
                    out.append(ch)
                    ch = Character.forDigit(b.toInt() and 0xF, 16)
                    if (Character.isLetter(ch)) {
                        ch -= 0x20
                    }
                    out.append(ch)
                }
                charArrayWriter.reset()
                needToChange = true
            }
        }


        return if (needToChange) out.toString() else s
    }
}