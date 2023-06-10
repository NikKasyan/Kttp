package kttp.protocol

import kttp.http.protocol.HttpVersion
import kttp.http.protocol.InvalidHttpVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class HttpVersionTest {

    @Test
    fun httpVersion_NotStartingWithHTTP_ShouldThrowInvalidHttpVersion() {
        assertThrows<InvalidHttpVersion> { HttpVersion("1.1") }
    }

    @Test
    fun httpVersion_NotEndingWithNoVersion_ShouldThrowInvalidHttpVersion() {
        assertThrows<InvalidHttpVersion> { HttpVersion("HTTP/") }
    }

    @Test
    fun httpVersion_EndingWithInValidVersion_ShouldThrowInvalidHttpVersion() {
        assertThrows<InvalidHttpVersion> { HttpVersion("HTTP/1.1a") }
    }

    @Test
    fun httpVersion_WithValidVersionStartingWithZeros_ShouldParseCorrectVersion() {
        assertThrows<InvalidHttpVersion> { HttpVersion("HTTP/00001.001") }
    }

    @Test
    fun httpVersion_WithValidVersion_ShouldParseCorrectVersion() {
        val httpVersion = HttpVersion("HTTP/1.1")

        assertEquals(httpVersion.majorVersion, 1)
        assertEquals(httpVersion.minorVersion, 1)
    }

    @Test
    fun httpVersion_WithValidVersionMultipleDigits_ShouldParseCorrectVersion() {
        assertThrows<InvalidHttpVersion> { HttpVersion("HTTP/12.31") }
    }
}