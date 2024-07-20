package kttp.protocol

import kttp.http.protocol.HttpHeader
import kttp.http.protocol.InvalidHeaderName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HttpHeaderTest {

    @Test
    fun headerStartingWithWhitespace_shouldThrowInvalidHeader(){

        assertThrows<InvalidHeaderName> {  HttpHeader(" Test" to "")}
        assertThrows<InvalidHeaderName> {  HttpHeader("\tTest" to "")}
        assertThrows<InvalidHeaderName> {  HttpHeader("\u0008Test" to "")}
    }
}