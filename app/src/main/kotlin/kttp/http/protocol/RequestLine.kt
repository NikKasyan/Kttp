package kttp.http.protocol

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

class RequestLine {

    val method: Method
    var requestTarget: URI
        private set(value) {
            field = if(value.isAbsolute)
                URIUtil.normalizeURI(value)
            else
                value
        }
    val httpVersion: HttpVersion

    constructor(requestString: String) {
        val requestParts = requestString.split(" ")

        if (requestParts.size != 3)
            throw InvalidHttpRequestStructure("Http Request has to have the structure \"METHOD PATH HTTP-Version\"")

        val (methodString, path, httpVersionString) = requestParts

        method = Method.byName(methodString)
        this.requestTarget = URIUtil.parseURI(path)
        httpVersion = HttpVersion(httpVersionString)
    }

    constructor(method: Method, uri: URI, httpVersion: HttpVersion = HttpVersion.DEFAULT_VERSION) {
        this.method = method
        this.requestTarget = uri
        this.httpVersion = httpVersion
    }

    override fun toString(): String {
        return "$method $requestTarget $httpVersion\r\n"
    }

}

open class InvalidHttpRequestLine(msg: String) : InvalidHttpRequest(msg) {

}

class UnknownHttpMethod(msg: String) : InvalidHttpRequestLine(msg)
class InvalidRequestPath(msg: String) : InvalidHttpRequestLine(msg)

class InvalidHttpRequestStructure(msg: String) : InvalidHttpRequestLine(msg)
