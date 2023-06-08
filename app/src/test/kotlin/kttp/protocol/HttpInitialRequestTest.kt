package kttp.protocol

import kttp.http.protocol.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import kotlin.test.assertEquals

class HttpInitialRequestTest {

    private val httpVersion = HttpVersion(1,1)
    @Test
    fun invalidHttpInitialRequests() {
        assertThrows<InvalidHttpRequestStructure> {  HttpInitialRequest("")}
        assertThrows<InvalidHttpRequestStructure> {  HttpInitialRequest(" ")}
        assertThrows<InvalidHttpRequestStructure> {  HttpInitialRequest("GET /")}

        assertThrows<InvalidRequestPath>("Invalid relative path should start with /") {  HttpInitialRequest("GET invalidPath $httpVersion")}
        assertThrows<InvalidRequestPath>("Invalid scheme may only be http or https") {  HttpInitialRequest("GET ssh://invalidPath.com/asd $httpVersion")}

        assertThrows<UnknownHttpMethod> {  HttpInitialRequest("GET1 http://absolute.com/absolute/asd $httpVersion")}

        assertThrows<InvalidHttpVersion> {  HttpInitialRequest("GET http://absolute.com/absolute/asd HTTP/4.123.1")}

    }

    @Test
    fun validHttpInitialRequests(){
        var httpRequest = HttpInitialRequest("GET http://absolute.com/absolute/asd $httpVersion")

        assertEquals(httpRequest.method, Method.GET)
        assertEquals(httpRequest.uri, URI("http://absolute.com/absolute/asd"))
        assertEquals(httpRequest.httpVersion.majorVersion, httpVersion.majorVersion)
        assertEquals(httpRequest.httpVersion.minorVersion, httpVersion.minorVersion)

        httpRequest = HttpInitialRequest("GET /absolute/asd $httpVersion")

        assertEquals(httpRequest.method, Method.GET)
        assertEquals(httpRequest.uri, URI("/absolute/asd"))
        assertEquals(httpRequest.httpVersion.majorVersion, httpVersion.majorVersion)
        assertEquals(httpRequest.httpVersion.minorVersion, httpVersion.minorVersion)

        httpRequest = HttpInitialRequest("GET / $httpVersion")

        assertEquals(httpRequest.method, Method.GET)
        assertEquals(httpRequest.uri, URI("/"))
        assertEquals(httpRequest.httpVersion.majorVersion, httpVersion.majorVersion)
        assertEquals(httpRequest.httpVersion.minorVersion, httpVersion.minorVersion)

        httpRequest = HttpInitialRequest("POST / $httpVersion")

        assertEquals(httpRequest.method, Method.POST)
        assertEquals(httpRequest.uri, URI("/"))
        assertEquals(httpRequest.httpVersion.majorVersion, httpVersion.majorVersion)
        assertEquals(httpRequest.httpVersion.minorVersion, httpVersion.minorVersion)
    }


}
