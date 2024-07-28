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
        uri = normalizeIfPathIsNotAbsolute(URIUtil.parseURI(path))
        httpVersion = HttpVersion(httpVersionString)
        this.parameters.addFromQuery(uri.query)
    }

    constructor(method: Method, uri: URI, httpVersion: HttpVersion = HttpVersion.DEFAULT_VERSION) {
        this.method = method
        this.uri = normalizeIfPathIsNotAbsolute(uri)
        this.httpVersion = httpVersion
        this.parameters.addFromQuery(this.uri.query)
    }

    constructor(method: Method, uri: String, httpVersion: HttpVersion) : this(method, URI(uri), httpVersion)

    override fun toString(): String {
        return "$method ${requestTarget()} $httpVersion\r\n"
    }

    private fun requestTarget(): String {
        val path = uri.path
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
