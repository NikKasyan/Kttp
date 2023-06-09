package kttp.http.protocol

import java.net.URI
import java.net.URISyntaxException

class HttpInitialRequest(requestString: String) {

    val method: Method
    val uri: URI
    val httpVersion: HttpVersion

    init {
        val requestParts = requestString.split(" ")

        if(requestParts.size != 3)
            throw InvalidHttpRequestStructure("Http Request has to have the structure \"METHOD PATH HTTP-Version\"")

        val (methodString, path, httpVersionString) = requestParts

        method = getMethodByName(methodString)
        this.uri = parseURI(path)
        httpVersion = HttpVersion(httpVersionString)

    }

    //Todo: Implement rest of https://www.rfc-editor.org/rfc/rfc7230#section-5.3
    private fun parseURI(path: String): URI {
        try {
            val uri = URI(path)

            if(uri.isAbsolute && (uri.scheme != "http" && uri.scheme != "https"))
                throw InvalidRequestPath("Absolute Request uri may only have scheme http or https")

            if(!uri.isAbsolute && !uri.path.startsWith("/"))
                throw InvalidRequestPath("Absolute Request path must begin with a /")

            return uri
        } catch (e: URISyntaxException) {
            throw InvalidRequestPath("Invalid Path $path")
        }
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
