package kttp.http.protocol

import java.net.URI

class RequestLine {

    val method: Method
    val uri: URI
    val httpVersion: HttpVersion
    val parameters = Parameters()


    constructor(requestString: String) {
        val requestParts = requestString.split(" ")

        if (requestParts.size != 3)
            throw InvalidHttpRequestStructure("Http Request has to have the structure \"METHOD PATH HTTP-Version\". Invalid: $requestString")

        val (methodString, path, httpVersionString) = requestParts

        checkRequestLineNotContainsBareCR(methodString, path, httpVersionString)

        method = Method.byName(methodString)
        uri = normalizeIfPathIsNotAbsolute(URIUtil.parseRequestTarget(method, path))
        httpVersion = HttpVersion(httpVersionString)
        this.parameters.addFromQuery(uri.rawQuery)
    }

    constructor(method: Method, uri: URI, httpVersion: HttpVersion = HttpVersion.DEFAULT_VERSION) {
        this.method = method
        this.uri = normalizeIfPathIsNotAbsolute(uri)
        this.httpVersion = httpVersion
        this.parameters.addFromQuery(this.uri.rawQuery)
    }

    constructor(method: Method, uri: String, httpVersion: HttpVersion) : this(method, URI(uri), httpVersion)

    override fun toString(): String {
        return "$method ${requestTarget()} $httpVersion\r\n"
    }

    // https://www.rfc-editor.org/rfc/rfc9112#section-3.2.3
    val isAuthorityForm: Boolean
        get() = method == Method.CONNECT

    // https://www.rfc-editor.org/rfc/rfc9112#section-3.2.4
    val isAsteriskForm: Boolean
        get() = uri.toString() == URIUtil.ASTERISK_FORM

    private fun requestTarget(): String {
        if (isAuthorityForm)
            return uri.rawAuthority
        if (isAsteriskForm)
            return URIUtil.ASTERISK_FORM
        // The path is sent as it is encoded, the decoded path could contain spaces
        val path = uri.rawPath
        return if (parameters.isEmpty()) path else "$path?$parameters"
    }

    private fun normalizeIfPathIsNotAbsolute(uri: URI): URI {
        return if (uri.isAbsolute)
            URIUtil.normalizeURI(uri)
        else
            uri
    }

}
// Check if the string contains a bare CR https://www.rfc-editor.org/rfc/rfc9112#name-message-parsing
private fun checkRequestLineNotContainsBareCR(methodString: String, pathString: String, httpVersionString: String) {
    if (hasBareCR(methodString))
        throw InvalidHttpRequestLine("Method must not contain bare CR")
    if (hasBareCR(pathString))
        throw InvalidHttpRequestPath("Path must not contain bare CR")
    if (hasBareCR(httpVersionString))
        throw InvalidHttpRequestLine("HTTP-Version must not contain bare CR")
}

open class InvalidHttpRequestLine(msg: String) : InvalidHttpRequest(msg)

class UnknownHttpMethod(msg: String) : InvalidHttpRequestLine(msg)
class InvalidHttpRequestPath(msg: String) : InvalidHttpRequestLine(msg)

class InvalidHttpRequestStructure(msg: String) : InvalidHttpRequestLine(msg)

class RequestLineTooLong : InvalidHttpRequestLine("Request Line is too long")

class UriTooLong : InvalidHttpRequestLine("URI is too long")
