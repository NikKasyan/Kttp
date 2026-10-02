package kttp

import kttp.http.server.resolvePath
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommonHandlersTest {

    private val root = Path.of("C:/srv/www").toAbsolutePath()

    @Test
    fun resolvePathWithDotDotSegmentsThrows() {
        assertFailsWith<IllegalArgumentException> { resolvePath(root, "/../../secret.txt") }
    }

    @Test
    fun resolvePathWithAbsolutePathThrows() {
        assertFailsWith<IllegalArgumentException> { resolvePath(root, "/C:/Windows/win.ini") }
    }

    @Test
    fun resolvePathWithinRootResolvesNormally() {
        assertEquals(root.resolve("foo/bar.txt"), resolvePath(root, "/foo/bar.txt"))
    }

    @Test
    fun resolvePathWithEmptyOrRootReturnsRoot() {
        assertEquals(root, resolvePath(root, ""))
        assertEquals(root, resolvePath(root, "/"))
    }
}
