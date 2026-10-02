package kttp.http.protocol

import kttp.http.server.MissingHostHeader
import kttp.io.CombinedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException

/**
 * @param secure Whether the request was received over TLS. The target URI then has the scheme "https".
 */
class HttpRequest(
    private val requestLine: RequestLine,
    val headers: HttpHeaders = HttpHeaders(),
    val body: HttpBody,
    secure: Boolean = false,
) {

    val httpVersion
        get() = requestLine.httpVersion
    val uri: URI
    val method
        get() = requestLine.method

    init {

        if (!headers.hasHost())
            throw MissingHostHeader()
        if(headers.hasTe(TransferEncoding.CHUNKED)) // https://www.rfc-editor.org/rfc/rfc9112#section-7.4-2
            throw InvalidTransferEncoding("TE may not be set to chunked in a request as the server should always support it")


        uri = reconstructTargetUri(parseHost(headers.host()), secure)
    }
    companion object {
        fun from(method: Method, uri: URI, httpHeaders: HttpHeaders = HttpHeaders(), body: HttpBody): HttpRequest {
            if(uri.isAbsolute && uri.host == null && !httpHeaders.hasHost())
                throw MissingHostHeader()
            // Only an absolute URI has a host to take the Host header from
            if(!httpHeaders.hasHost() && uri.host != null)
                httpHeaders.withHost(uri)
            if(body.hasContentLength() && !httpHeaders.hasContentLength())
                httpHeaders.withContentLength(body.contentLength!!)

            return HttpRequest(RequestLine(method, uri), httpHeaders, body)
        }
        fun from(method: Method, uri: URI, httpHeaders: HttpHeaders = HttpHeaders(), body: String = ""): HttpRequest {
            val bodyArray = body.toByteArray()
            val httpBody = HttpBody(ByteArrayInputStream(bodyArray), bodyArray.size.toLong())

            return from(method, uri, httpHeaders, httpBody)
        }

        fun from(method: Method, uriString: String, httpHeaders: HttpHeaders = HttpHeaders(), inputStream: InputStream?): HttpRequest {
            val contentLength = httpHeaders.contentLength
            val body = HttpBody(inputStream?: InputStream.nullInputStream(), contentLength)
            return from(method, URI.create(uriString), httpHeaders, body)
        }

        fun from(method: Method, uriString: String, httpHeaders: HttpHeaders = HttpHeaders(), body: String = ""): HttpRequest {
            val contentLength = httpHeaders.contentLength
            val requestBody = HttpBody(body.byteInputStream(), contentLength)
            return from(method, URI.create(uriString), httpHeaders, requestBody)
        }

        fun get(uri: URI, httpHeaders: HttpHeaders = HttpHeaders()): HttpRequest {
            return from(Method.GET, uri, httpHeaders)
        }
        fun get(uriString: String, httpHeaders: HttpHeaders = HttpHeaders()): HttpRequest {
            return from(Method.GET, URI.create(uriString), httpHeaders)
        }
    }

    // A Host with an invalid value must be answered with 400 https://www.rfc-editor.org/rfc/rfc9112#section-3.2
    private fun parseHost(host: String): URI {
        val hostUri = try {
            URI("http://$host").parseServerAuthority()
        } catch (e: URISyntaxException) {
            throw InvalidHost(host)
        }
        // Host = uri-host [ ":" port ] https://www.rfc-editor.org/rfc/rfc9110#section-7.2
        if (hostUri.host == null || hostUri.rawUserInfo != null || hostUri.rawPath.isNotEmpty()
            || hostUri.rawQuery != null || hostUri.rawFragment != null)
            throw InvalidHost(host)
        return hostUri
    }

    /**
     * Builds the target URI from the request-target and the Host https://www.rfc-editor.org/rfc/rfc9112#section-3.3
     */
    private fun reconstructTargetUri(host: URI, secure: Boolean): URI {
        val target = requestLine.uri
        // For absolute-form the target URI is the request-target, and Host is ignored https://www.rfc-editor.org/rfc/rfc9112#section-3.2.2
        if (target.isAbsolute)
            return target
        val scheme = if (secure) "https" else "http"
        val authority = if (requestLine.isAuthorityForm) target.rawAuthority else host.rawAuthority
        // Authority-form and asterisk-form have an empty path and query
        if (requestLine.isAuthorityForm || requestLine.isAsteriskForm)
            return URI("$scheme://$authority")
        val query = if (target.rawQuery == null) "" else "?${target.rawQuery}"
        return URIUtil.normalizeURI(URI("$scheme://$authority${target.rawPath}$query"))
    }

    override fun toString(): String {
        return "$requestLine$headers\r\n\r\n$body"
    }

    fun asStream(): InputStream {
        return CombinedInputStream(
            requestLine.toString().byteInputStream(),
            "$headers\r\n\r\n".byteInputStream(),
            body
        )
    }

    fun getParameters(): Parameters {
        return Parameters.fromQuery(uri.rawQuery)
    }
}

fun hasBareCR(string: String): Boolean {
    for (i in string.indices) {
        if (string[i] == '\r') {
            if (i + 1 >= string.length || string[i + 1] != '\n') {
                return true
            }
        }
    }
    return false
}

object GetRequest {
    fun from(uri: URI, httpHeaders: HttpHeaders = HttpHeaders()): HttpRequest {
        return HttpRequest.from(Method.GET, uri, httpHeaders)
    }
}

object PostRequest {
    fun from(uri: URI, httpHeaders: HttpHeaders = HttpHeaders(), body: String = ""): HttpRequest {
        return HttpRequest.from(Method.POST, uri, httpHeaders, body)
    }
    fun from(uriString: String, httpHeaders: HttpHeaders = HttpHeaders(), bodyStream: InputStream?): HttpRequest {
        return HttpRequest.from(Method.POST, uriString, httpHeaders, bodyStream)
    }
}

