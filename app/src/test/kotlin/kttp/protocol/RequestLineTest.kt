package kttp.protocol

import kttp.http.protocol.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import kotlin.test.assertEquals

class RequestLineTest {

    private val httpVersion = HttpVersion(1,1)
    @Test
    fun invalidHttpInitialRequests() {
        assertThrows<InvalidHttpRequestStructure> {  RequestLine("")}
        assertThrows<InvalidHttpRequestStructure> {  RequestLine(" ")}
        assertThrows<InvalidHttpRequestStructure> {  RequestLine("GET /")}

        assertThrows<InvalidRequestPath>("Invalid relative path should start with /") {  RequestLine("GET invalidPath $httpVersion")}
        assertThrows<InvalidRequestPath>("Invalid scheme may only be http or https") {  RequestLine("GET ssh://invalidPath.com/asd $httpVersion")}

        assertThrows<UnknownHttpMethod> {  RequestLine("GET1 http://absolute.com/absolute/asd $httpVersion")}

        assertThrows<InvalidHttpVersion> {  RequestLine("GET http://absolute.com/absolute/asd HTTP/4.123.1")}

    }

    @Test
    fun validHttpInitialRequests(){
        var httpRequest = RequestLine("GET http://absolute.com/absolute/asd $httpVersion")

        assertEquals(httpRequest.method, Method.GET)
        assertEquals(httpRequest.uri, URI("http://absolute.com/absolute/asd"))
        assertEquals(httpRequest.httpVersion.majorVersion, httpVersion.majorVersion)
        assertEquals(httpRequest.httpVersion.minorVersion, httpVersion.minorVersion)

        httpRequest = RequestLine("GET /absolute/asd $httpVersion")

        assertEquals(httpRequest.method, Method.GET)
        assertEquals(httpRequest.uri, URI("/absolute/asd"))
        assertEquals(httpRequest.httpVersion.majorVersion, httpVersion.majorVersion)
        assertEquals(httpRequest.httpVersion.minorVersion, httpVersion.minorVersion)

        httpRequest = RequestLine("GET / $httpVersion")

        assertEquals(httpRequest.method, Method.GET)
        assertEquals(httpRequest.uri, URI("/"))
        assertEquals(httpRequest.httpVersion.majorVersion, httpVersion.majorVersion)
        assertEquals(httpRequest.httpVersion.minorVersion, httpVersion.minorVersion)

        httpRequest = RequestLine("POST / $httpVersion")

        assertEquals(httpRequest.method, Method.POST)
        assertEquals(httpRequest.uri, URI("/"))
        assertEquals(httpRequest.httpVersion.majorVersion, httpVersion.majorVersion)
        assertEquals(httpRequest.httpVersion.minorVersion, httpVersion.minorVersion)
    }


}
