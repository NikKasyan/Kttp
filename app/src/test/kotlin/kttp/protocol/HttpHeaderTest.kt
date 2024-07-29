package kttp.protocol

import kttp.http.protocol.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
}