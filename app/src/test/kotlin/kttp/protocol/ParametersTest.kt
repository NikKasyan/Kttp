package kttp.protocol

import kttp.http.protocol.Parameters
import org.junit.jupiter.api.Test
import kotlin.properties.Delegates
import kotlin.test.assertEquals

class ParametersTest {
    @Test
    fun testAddFromQuery() {
        // Given
        val parameters = Parameters()
        val query = "key1=value1&key2=value2&key3=value3"

        // When
        parameters.addFromQuery(query)

        // Then
        assertEquals(parameters.size, 3)
        assertEquals(parameters["key1"], "value1")
        assertEquals(parameters["key2"], "value2")
        assertEquals(parameters["key3"], "value3")
    }

    @Test
    fun testParameterToSimpleObject() {
        // Given
        val parameters = Parameters()
        val query = "key1=value1&key2=value2&key3=value3"

        // When
        parameters.addFromQuery(query)

        val testObject = parameters.to<ParameterSimpleObject>()

        // Then

        assertEquals(testObject.key1, "value1")
        assertEquals(testObject.key2, "value2")
        assertEquals(testObject.key3, "value3")
    }

    @Test
    fun testParameterToSimpleIterableObject() {
        // Given
        val parameters = Parameters()
        val query = "key1=value1&key2=value2&key3=value3&key1=value4&key2=value"

        // When
        parameters.addFromQuery(query)

        val testObject = parameters.to<ParameterSimpleIterableObject>()

        // Then

        assertEquals(testObject.key1.size, 2)
        assertEquals(testObject.key1[0], "value1")
        assertEquals(testObject.key1[1], "value4")
        assertEquals(testObject.key2.size, 2)
        assertEquals(testObject.key2[0], "value2")
        assertEquals(testObject.key2[1], "value")
        assertEquals(testObject.key3.size, 1)
        assertEquals(testObject.key3[0], "value3")
    }
    @Test
    fun testParameterToNestedObject() {
        // Given
        val parameters = Parameters()
        val query = "key1=value1&key2=value2&key3=value3&nested.key1=value4&nested.key2=value5&nested.key3=value6"

        // When

        parameters.addFromQuery(query)

        val testObject = parameters.to<ParameterNestedObject>()

        // Then

        assertEquals(testObject.key1, "value1")
        assertEquals(testObject.key2, "value2")
        assertEquals(testObject.key3, "value3")
        assertEquals(testObject.nested.key1, "value4")
        assertEquals(testObject.nested.key2, "value5")

    }
}

class ParameterSimpleObject {
    var key1: String = ""
    var key2: String = ""
    var key3: String = ""
}

class ParameterSimpleIterableObject {
    var key1: List<String> = emptyList()
    var key2: List<String> = emptyList()
    var key3: List<String> = emptyList()
}

class ParameterNestedObject {
    var key1: String = ""
    var key2: String = ""
    var key3: String = ""
    lateinit var nested: ParameterSimpleObject
}

class ParameterComplexObject {
    var key1 by Delegates.notNull<Long>()
    var key2 by Delegates.notNull<Int>()
    var key3: Boolean by Delegates.notNull()
    lateinit var nested: ParameterSimpleIterableObject
}

class ParameterWithPrimitiveArray {
    var key1: IntArray = intArrayOf()
    var key2: LongArray = longArrayOf()
    var key3: BooleanArray = booleanArrayOf()
}

class ParameterWithComplexArray {
    var key1: Array<ParameterSimpleObject> = arrayOf()
    var key2: Array<ParameterSimpleIterableObject> = arrayOf()
    var key3: Array<ParameterNestedObject> = arrayOf()
}

