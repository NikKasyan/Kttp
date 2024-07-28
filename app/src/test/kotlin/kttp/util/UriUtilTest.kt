package kttp.util

import kttp.http.protocol.URIUtil
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import kotlin.test.assertEquals

class UriUtilTest {

    @Test
    fun testUriEncoding() {
        val uri = "https://www.google.com/search?q=hello world"
        val encodedUri = URIUtil.encodeURI(uri)
        val decodedUri = URIUtil.decodeURI(encodedUri)
        assertEquals(uri, decodedUri)
    }

    @Test
    fun testUriEncodingWithSpecialCharacters() {
        val uri = "https://www.google.com/search?q=hello%20world"
        val encodedUri = URIUtil.encodeURI(uri)
        val decodedUri = URIUtil.decodeURI(encodedUri)
        assertEquals(uri, decodedUri)
    }

    @Test
    fun testUriEncodingWithSpecialCharacters2() {
        val uri = "https://www.google.com/search?q=hello%20world&name=John%20Doe"
        val encodedUri = URIUtil.encodeURI(uri)
        val decodedUri = URIUtil.decodeURI(encodedUri)
        assertEquals(uri, decodedUri)
    }

    @Test
    fun testUriEncodingWithUmLaute() {
        val uri = "https://www.google.com/search?q=äöüß"
        val encodedUri = URIUtil.encodeURI(uri)
        val decodedUri = URIUtil.decodeURI(encodedUri)
        assertEquals(uri, decodedUri)
    }

    @Test
    fun testUriEncodingWithUncommonCharacters() {
        val uri = "https://www.google.com/search?q=hello%20world%20%21%40%23%24%25%5E%26%2A%28%29%5B%5D%7B%7D%3A%3B%27%22%2C%3C%3E%2F%5C%7C%3F%60%7E"
        val decoded = URLDecoder.decode(uri, Charsets.UTF_8)
        val encodedUri = URIUtil.encodeURI(decoded)
        val decodedUri = URIUtil.decodeURI(encodedUri)
        assertEquals(decoded, decodedUri)
    }

    @Test
    fun testUriSpecialCharacters() {
        val special = "!@#$%^&*()[]{}:;'\"<>\\|?`~ "
        val uri = "https://www.google.com/search?q=hello$special"
        val encodedUri = URIUtil.encodeURI(uri)
        val decodedUri = URIUtil.decodeURI(encodedUri)
        assertEquals(uri, decodedUri)
    }

}