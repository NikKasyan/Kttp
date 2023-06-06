
package kttp

import kotlin.test.Test
import kotlin.test.assertNotNull

class KttpTest {
    @Test fun appHasAGreeting() {
        val classUnderTest = App()
        assertNotNull(classUnderTest.greeting, "app should have a greeting")
    }
}
