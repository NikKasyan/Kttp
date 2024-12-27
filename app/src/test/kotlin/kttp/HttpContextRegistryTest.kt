package kttp

import kttp.http.HttpContextRegistry
import kttp.http.HttpReqHandler
import kttp.http.protocol.Method
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HttpContextRegistryTest {
    private val httpContextRegistry = HttpContextRegistry()

    @Test
    fun `fuzzy paths work`() {
        httpContextRegistry.addHandler(handler("/foo"))
        httpContextRegistry.addHandler(handler("/foo/bar"))
        httpContextRegistry.addHandler(handler("/foo/bar/baz"))
        httpContextRegistry.addHandler(handler("/foo/bar/*"))
        httpContextRegistry.addHandler(handler("/foo/bar/**"))

        val foo = httpContextRegistry.getHandlerByPath("/foo")
        val foobar = httpContextRegistry.getHandlerByPath("/foo/bar")
        val foobarbaz = httpContextRegistry.getHandlerByPath("/foo/bar/baz")
        val foobarbazqux = httpContextRegistry.getHandlerByPath("/foo/bar/bay")
        val foobarbazqux2 = httpContextRegistry.getHandlerByPath("/foo/bar/bay123/")
        val foobarbazqux3 = httpContextRegistry.getHandlerByPath("/foo/bar/bay123/123")
        val foobarbazqux4 = httpContextRegistry.getHandlerByPath("/foo/bar/bay123/123/123asd123/asd123a/asldkjasd/asd")


        assertNotNull(foo)
        assertNotNull(foobar)
        assertNotNull(foobarbaz)
        assertNotNull(foobarbazqux)
        assertNotNull(foobarbazqux2)
        assertNotNull(foobarbazqux3)
        assertNotNull(foobarbazqux4)

        assertEquals("/foo", foo.path)
        assertEquals("/foo/bar", foobar.path)
        assertEquals("/foo/bar/baz", foobarbaz.path)
        assertEquals("/foo/bar/*", foobarbazqux.path)
        assertEquals("/foo/bar/*", foobarbazqux2.path)
        assertEquals("/foo/bar/**", foobarbazqux3.path)
        assertEquals("/foo/bar/**", foobarbazqux4.path)
    }



    @AfterEach
    fun tearDown() {
        httpContextRegistry.clear()
    }
}

private fun handler(path: String): HttpReqHandler {
    return HttpReqHandler(path, EnumSet.allOf(Method::class.java)){
        respond(path)
    }
}