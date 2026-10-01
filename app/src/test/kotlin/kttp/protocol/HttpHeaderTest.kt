package kttp.protocol

import kttp.http.protocol.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Date
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun transferEncodingChunked_shouldBeOncePresent(){
        assertThrows<InvalidTransferEncoding> {  HttpHeaders().withTransferEncoding(TransferEncoding.CHUNKED, TransferEncoding.CHUNKED)}
    }

    @Test
    fun contentLengthWithMultipleEntries_shouldBeParsedCorrectly(){
        val headers = HttpHeaders(listOf(HttpHeader("Content-Length" to "100,100,100")))
        assertEquals(headers.contentLength, 100)
    }

    @Test
    fun connectionWithMultipleOptions_shouldBeParsedCorrectly(){
        val headers = HttpHeaders(listOf(HttpHeader("Connection" to "keep-alive, Upgrade")))
        assertEquals(listOf(Connection.KEEP_ALIVE, Connection.UPGRADE), headers.connection)
    }

    @Test
    fun missingConnection_hasNoConnectionOptions(){
        val headers = HttpHeaders()
        assertFalse(headers.hasConnection(Connection.CLOSE))
        assertEquals(emptyList(), headers.connection())
        assertEquals(emptyList(), headers.connectionAsStrings())
    }

    @Test
    fun connectionWithUnknownOption_keepsAllOptions(){
        val headers = HttpHeaders(listOf(HttpHeader("Connection" to "keep-alive, TE")))
        assertTrue(headers.hasConnection(Connection.KEEP_ALIVE))
        assertEquals(listOf(Connection.KEEP_ALIVE), headers.connection())
        assertEquals(listOf("keep-alive", "TE"), headers.connectionAsStrings())
    }

    // Example from https://www.rfc-editor.org/rfc/rfc9110#section-5.6.7
    private val exampleDate = Date(784111777000L)

    @Test
    fun date_isAlwaysFormattedInGmt(){
        val defaultTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            val headers = HttpHeaders().withDate(exampleDate)
            assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", headers.dateAsString())
        } finally {
            TimeZone.setDefault(defaultTimeZone)
        }
    }

    @Test
    fun dateInImfFixdateFormat_isParsed(){
        val headers = HttpHeaders(listOf(HttpHeader("Date" to "Sun, 06 Nov 1994 08:49:37 GMT")))
        assertEquals(exampleDate, headers.date())
    }

    @Test
    fun dateInObsoleteRfc850Format_isParsed(){
        val headers = HttpHeaders(listOf(HttpHeader("Date" to "Sunday, 06-Nov-94 08:49:37 GMT")))
        assertEquals(exampleDate, headers.date())
    }

    @Test
    fun dateInObsoleteAsctimeFormat_isParsed(){
        val headers = HttpHeaders(listOf(HttpHeader("Date" to "Sun Nov  6 08:49:37 1994")))
        assertEquals(exampleDate, headers.date())
    }

    @Test
    fun invalidDate_isNull(){
        val headers = HttpHeaders(listOf(HttpHeader("Date" to "yesterday")))
        assertNull(headers.date())
    }
}