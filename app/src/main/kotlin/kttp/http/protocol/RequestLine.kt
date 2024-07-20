package kttp.http.protocol

import java.net.URI

class RequestLine {

    val method: Method
    val requestTarget: URI
    val httpVersion: HttpVersion

    constructor(requestString: String) {
        val requestParts = requestString.split(" ")

        if (requestParts.size != 3)
            throw InvalidHttpRequestStructure("Http Request has to have the structure \"METHOD PATH HTTP-Version\"")

        val (methodString, path, httpVersionString) = requestParts

        method = Method.byName(methodString)
        requestTarget = normalizeIfPathIsNotAbsolute(URIUtil.parseURI(path))
        httpVersion = HttpVersion(httpVersionString)
    }

    constructor(method: Method, uri: URI, httpVersion: HttpVersion = HttpVersion.DEFAULT_VERSION) {
        this.method = method
        this.requestTarget = normalizeIfPathIsNotAbsolute(uri)
        this.httpVersion = httpVersion
    }

    override fun toString(): String {
        return "$method $requestTarget $httpVersion\r\n"
    }

    private fun normalizeIfPathIsNotAbsolute(uri: URI): URI {
        return if (uri.isAbsolute)
            URIUtil.normalizeURI(uri)
        else
            uri
    }

}

open class InvalidHttpRequestLine(msg: String) : InvalidHttpRequest(msg)

class UnknownHttpMethod(msg: String) : InvalidHttpRequestLine(msg)
class InvalidRequestPath(msg: String) : InvalidHttpRequestLine(msg)

class InvalidHttpRequestStructure(msg: String) : InvalidHttpRequestLine(msg)
