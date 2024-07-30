package kttp.protocol

import kttp.http.protocol.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class HttpHeaderTest {

    @Test
    fun headerStartingWithWhitespace_shouldThrowInvalidHeader(){

        assertThrows<InvalidHeaderName> {  HttpHeader(" Test" to "")}
        assertThrows<InvalidHeaderName> {  HttpHeader("\tTest" to "")}
        assertThrows<InvalidHeaderName> {  HttpHeader("\u0008Test" to "")}
    }

    @Test
    fun headerShouldHaveAtMostOneHost(){
        val headers = listOf(
            HttpHeader("Host" to "localhost"),
            HttpHeader("Host" to "localhost:8080")
        )

        assertThrows<TooManyHostHeaders> {  HttpHeaders(headers)}
    }

    @Test
    fun headerShouldRemoveLeadingAndTrailingWhitespace(){
        val header = HttpHeader("Test" to " Value ")
        assertEquals(header.key, "Test")
        assertEquals(header.value,"Value")
    }

    @Test
    fun headerShouldHaveOptionalSpaceAfterColon(){
        val header = HttpHeader("Test:Value")
        assertEquals(header.toString(), "Test: Value")
    }
    @Test
    fun headerShouldNotHaveLineFolding() {
        assertThrows<LineFoldingNotAllowed>{"Test: Value\r\n 2".split("\r\n").map { HttpHeader(it) } }
    }

    @Test
    fun transferEncodingChunked_shouldBeParsedCorrectly(){
        val headers = HttpHeaders(listOf(HttpHeader("Transfer-Encoding" to "chunked")))
        assertEquals(headers.transferEncoding, TransferEncoding.CHUNKED)
    }

    @Test
    fun transferEncodingIdentity_shouldBeParsedCorrectly(){
        val headers = HttpHeaders(listOf(HttpHeader("Transfer-Encoding" to "identity")))
        assertEquals(headers.transferEncoding, TransferEncoding.IDENTITY)
    }

    @Test
    fun transferEncodingIdentityWithChunked_shouldThrowException(){
        assertThrows<InvalidTransferEncoding> { HttpHeaders().withTransferEncoding(TransferEncoding.IDENTITY, TransferEncoding.CHUNKED) }
    }
    @Test
    fun transferEncodingChunked_shouldBeLast(){
        assertThrows<InvalidTransferEncoding> {  HttpHeaders().withTransferEncoding(TransferEncoding.CHUNKED, TransferEncoding.GZIP)}
    }

    @Test
    fun transferEncodingChunked_shouldBeOncePresent(){
        assertThrows<InvalidTransferEncoding> {  HttpHeaders().withTransferEncoding(TransferEncoding.CHUNKED, TransferEncoding.CHUNKED)}
    }

    @Test
    fun contentLengthWithMultipleEntries_shouldBeParsedCorrectly(){
        val headers = HttpHeaders(listOf(HttpHeader("Content-Length" to "100,100,100")))
        assertEquals(headers.contentLength, 100)
    }
}