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
        val scheme = uri.scheme.lowercase() // https://www.rfc-editor.org/rfc/rfc9110#section-4.2.3-4.3
        val host = uri.host.lowercase() // https://www.rfc-editor.org/rfc/rfc9110#section-4.2.3-4.3
        val port = getPortByScheme(scheme, uri.port)
        val authority = if (port == -1) host else "$host:$port"
        // The raw components are kept percent-encoded, decoding them would turn "%2F" into "/" or "%26" into "&"
        val path = removeDotSegments(normalizePath(uri.rawPath))
        val query = if (uri.rawQuery == null) "" else "?${uri.rawQuery}"
        val fragment = if (uri.rawFragment == null) "" else "#${uri.rawFragment}"
        return URI("$scheme://$authority$path$query$fragment")
    }

    /**
     * Removes "." and ".." segments from an absolute path https://www.rfc-editor.org/rfc/rfc3986#section-5.2.4
     */
    fun removeDotSegments(path: String): String {
        val segments = path.split("/").drop(1) // The path starts with "/", so the first part is empty
        val output = ArrayDeque<String>()
        for (segment in segments) {
            when (segment) {
                "." -> {}
                ".." -> output.removeLastOrNull()
                else -> output.addLast(segment)
            }
        }
        // "/a/b/.." becomes "/a/", the path still ends with a "/"
        if (segments.last() == "." || segments.last() == "..")
            output.addLast("")
        return "/" + output.joinToString("/")
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

    /**
     * Parses the request-target in the form the method allows https://www.rfc-editor.org/rfc/rfc9112#section-3.2
     */
    fun parseRequestTarget(method: Method, target: String): URI {
        // Only CONNECT uses authority-form, and it uses nothing else https://www.rfc-editor.org/rfc/rfc9112#section-3.2.3
        if (method == Method.CONNECT)
            return parseAuthorityForm(target)
        // Asterisk-form is only used with OPTIONS https://www.rfc-editor.org/rfc/rfc9112#section-3.2.4
        if (method == Method.OPTIONS && target == ASTERISK_FORM)
            return URI(ASTERISK_FORM)
        return parseURI(target)
    }

    const val ASTERISK_FORM = "*"

    // authority-form = uri-host ":" port https://www.rfc-editor.org/rfc/rfc9112#section-3.2.3
    private fun parseAuthorityForm(target: String): URI {
        val uri = try {
            URI("//$target").parseServerAuthority()
        } catch (e: URISyntaxException) {
            throw InvalidHttpRequestPath("Invalid authority $target")
        }
        if (uri.host == null || uri.port == -1 || uri.rawUserInfo != null || uri.rawPath.isNotEmpty() || uri.rawQuery != null)
            throw InvalidHttpRequestPath("Invalid authority $target")
        return uri
    }

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
        // host is null when the authority isn't a valid host and port, for example "example.com:abc"
        if (uri.host.isNullOrEmpty())
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