package kttp.protocol

import kttp.http.protocol.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestLineTest {

    private val httpVersion = HttpVersion(1,1)
    @Test
    fun invalidHttpInitialRequests() {
        assertThrows<InvalidHttpRequestStructure> {  RequestLine("")}
        assertThrows<InvalidHttpRequestStructure> {  RequestLine(" ")}
        assertThrows<InvalidHttpRequestStructure> {  RequestLine("GET /")}

        assertThrows<InvalidHttpRequestPath>("Invalid relative path should start with /") {  RequestLine("GET invalidPath $httpVersion")}
        assertThrows<InvalidHttpRequestPath>("Invalid scheme may only be http or https") {  RequestLine("GET ssh://invalidPath.com/asd $httpVersion")}
        assertThrows<InvalidHttpRequestPath>("Invalid scheme may only be http or https") {  RequestLine("GET ssh://invalidPath.com/asd $httpVersion")}

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

    @Test
    fun httpRequestLineWithWhiteSpaceInTargetIsInvalid(){
        //https://www.rfc-editor.org/rfc/rfc9112#section-3.2-4
        assertThrows<InvalidHttpRequestPath>("Invalid relative path should start with /") {  RequestLine("GET /asd\t $httpVersion")}
        assertThrows<InvalidHttpRequestPath>("Invalid relative path should start with /") {  RequestLine("GET /asd\t $httpVersion")}
        assertThrows<InvalidHttpRequestPath>("Invalid relative path should start with /") {  RequestLine("GET /asd\r $httpVersion")}
        assertThrows<InvalidHttpRequestPath>("Invalid relative path should start with /") {  RequestLine("GET /asd\n $httpVersion")}
    }

    @Test
    fun testParameters(){
        val httpRequest = RequestLine("GET /asd?param1=value1&param2=value2 $httpVersion")
        assertEquals(httpRequest.parameters.size, 2)
        assertEquals(httpRequest.parameters["param1"], "value1")
        assertEquals(httpRequest.parameters["param2"], "value2")
    }

    @Test
    fun testEncodedParameterShouldBeDecoded(){
        val parameter = "value with space"
        val httpRequest = RequestLine("GET /asd?param1=${URIUtil.encodeURI(parameter)} $httpVersion")
        assertEquals(httpRequest.parameters.size, 1)
        assertEquals(httpRequest.parameters["param1"], parameter)
    }

    @Test
    fun encodedSeparatorsInQuery_areDecodedOnce(){
        val httpRequest = RequestLine("GET /?a=%26b&c=%2525 $httpVersion")
        assertEquals("&b", httpRequest.parameters["a"])
        assertEquals("%25", httpRequest.parameters["c"])
    }

    @Test
    fun queryParameterWithoutValue_hasEmptyValue(){
        val httpRequest = RequestLine("GET /?flag $httpVersion")
        assertEquals("", httpRequest.parameters["flag"])
    }

    @Test
    fun encodedPath_isSentEncoded(){
        val requestLine = RequestLine(Method.GET, URI("/a%20b"))
        assertEquals("GET /a%20b $httpVersion\r\n", requestLine.toString())
    }

    @Test
    fun plusInAbsolutePath_isNotDecodedToSpace(){
        assertEquals("/a+b", RequestLine("GET http://example.com/a+b $httpVersion").uri.path)
    }

    @Test
    fun dotSegmentsInAbsolutePath_areRemoved(){
        assertEquals("/a/c/d", RequestLine("GET http://example.com/a/b/../c/./d $httpVersion").uri.path)
        assertEquals("/a/", RequestLine("GET http://example.com/a/b/.. $httpVersion").uri.path)
    }

    @Test
    fun asteriskForm_isAcceptedForOptions(){
        val requestLine = RequestLine("OPTIONS * $httpVersion")
        assertTrue(requestLine.isAsteriskForm)
        assertEquals("OPTIONS * $httpVersion\r\n", requestLine.toString())
    }

    @Test
    fun asteriskForm_isRejectedForOtherMethods(){
        assertThrows<InvalidHttpRequestPath> { RequestLine("GET * $httpVersion") }
    }

    @Test
    fun authorityForm_isAcceptedForConnect(){
        val requestLine = RequestLine("CONNECT example.com:443 $httpVersion")
        assertTrue(requestLine.isAuthorityForm)
        assertEquals("CONNECT example.com:443 $httpVersion\r\n", requestLine.toString())
    }

    @Test
    fun connectWithoutAuthorityForm_isInvalid(){
        assertThrows<InvalidHttpRequestPath> { RequestLine("CONNECT example.com $httpVersion") }
        assertThrows<InvalidHttpRequestPath> { RequestLine("CONNECT /path $httpVersion") }
    }

}
