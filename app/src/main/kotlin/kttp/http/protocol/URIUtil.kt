package kttp.http.protocol

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

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
            urlDecode(getPath(uri.path)),
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

    fun getPath(path: String?): String {
        if (path.isNullOrEmpty())
            return "/"
        return path
    }

    fun urlDecode(string: String?) = if(string != null) URLDecoder.decode(string, Charsets.UTF_8) else null

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

    fun checkURI(uri: URI) {
        if (uri.isAbsolute)
            checkAbsoluteUri(uri)

        if (!uri.isAbsolute && !uri.path.startsWith("/"))
            throw InvalidHttpRequestPath("Absolute Request path must begin with a /")
    }

    fun checkAbsoluteUri(uri: URI) {
        if (uri.scheme != "http" && uri.scheme != "https")
            throw InvalidHttpRequestPath("Absolute Request uri may only have scheme http or https")
        if (uri.host.isEmpty())
            throw InvalidHttpRequestPath("Host of absolute URI may not be empty")

    }

}
