package kttp.http.protocol

import java.net.URI
import java.net.URISyntaxException

class RequestLine {

    val method: Method
    val uri: URI
    val httpVersion: HttpVersion

    constructor(requestString: String) {
        val requestParts = requestString.split(" ")

        if (requestParts.size != 3)
            throw InvalidHttpRequestStructure("Http Request has to have the structure \"METHOD PATH HTTP-Version\"")

        val (methodString, path, httpVersionString) = requestParts

        method = getMethodByName(methodString)
        this.uri = parseURI(path)
        httpVersion = HttpVersion(httpVersionString)
    }

    constructor(method: Method, uri: URI, httpVersion: HttpVersion = HttpVersion.DEFAULT_VERSION) {
        checkURI(uri)
        this.method = method
        this.uri = uri
        this.httpVersion = httpVersion
    }

    //Todo: Implement rest of https://www.rfc-editor.org/rfc/rfc7230#section-5.3
    private fun parseURI(path: String): URI {
        try {
            val uri = URI(path)
            checkURI(uri)
            return uri
        } catch (e: URISyntaxException) {
            throw InvalidRequestPath("Invalid Path $path")
        }
    }

    private fun checkURI(uri: URI) {
        if (uri.isAbsolute)
            checkAbsoluteUri(uri)

        if (!uri.isAbsolute && !uri.path.startsWith("/"))
            throw InvalidRequestPath("Absolute Request path must begin with a /")
    }

    private fun checkAbsoluteUri(uri: URI) {
        if (uri.scheme != "http" && uri.scheme != "https")
            throw InvalidRequestPath("Absolute Request uri may only have scheme http or https")
        if (uri.host.isEmpty())
            throw InvalidRequestPath("Host of absolute URI may not be empty")

    }

    private fun getMethodByName(methodString: String): Method {
        try {
            return Method.valueOf(methodString)
        } catch (e: IllegalArgumentException) {
            throw UnknownHttpMethod("Unknown Method $methodString")
        }
    }

    override fun toString(): String {
        return "$method $uri $httpVersion"
    }

}

class UnknownHttpMethod(msg: String) : InvalidHttpRequest(msg)
class InvalidRequestPath(msg: String) : InvalidHttpRequest(msg)

class InvalidHttpRequestStructure(msg: String) : InvalidHttpRequest(msg)
