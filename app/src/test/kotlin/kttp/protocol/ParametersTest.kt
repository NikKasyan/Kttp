package kttp.protocol

import kttp.http.protocol.Parameters
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertTrue { testObject.key2.containsAll(listOf("value2", "value")) }
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

    @Test
    fun testParameterToComplexObject() {
        // Given
        val parameters = Parameters()
        val query = "key1=1&key2=2&key3=true&nested.key1=value1&nested.key2=value2&nested.key3=value3&nested.key1=value4&nested.key2=value&nested.key3=value5"

        // When
        parameters.addFromQuery(query)

        val testObject = parameters.to<ParameterComplexObject>()

        // Then

        assertEquals(testObject.key1, 1, "key1")
        assertEquals(testObject.key2, 2, "key2")
        assertEquals(testObject.key3, true, "key3")
        assertEquals(testObject.nested.key1.size, 2, "nested.key1.size")
        assertEquals(testObject.nested.key1[0], "value1", "nested.key1[0]")
        assertEquals(testObject.nested.key1[1], "value4", "nested.key1[1]")
        assertEquals(testObject.nested.key2.size, 2, "nested.key2.size")
        assertTrue { testObject.nested.key2.containsAll(listOf("value", "value2")) }
        assertEquals(testObject.nested.key3.size, 2, "nested.key3.size")
        assertEquals(testObject.nested.key3[0], "value3", "nested.key3[0]")
        assertEquals(testObject.nested.key3[1], "value5", "nested.key3[1]")
    }
    @Test
    fun testParameterToPrimitiveArray() {
        // Given
        val parameters = Parameters()
        val query = "key1=1&key2=2&key3=true"

        // When
        parameters.addFromQuery(query)

        val testObject = parameters.to<ParameterWithPrimitiveArray>()

        // Then

        assertEquals(testObject.key1.size, 1)
        assertEquals(testObject.key1[0], 1)
        assertEquals(testObject.key2.size, 1)
        assertEquals(testObject.key2[0], 2)
        assertEquals(testObject.key3.size, 1)
        assertEquals(testObject.key3[0], true)
    }

    @Test
    fun testCustomMapping() {
        // Given
        val parameters = Parameters()
        val query = "key1=1&key2=2&key3=true&nested.key1=value1&nested.key2=value2&nested.key3=value3&nested.key1=value4&nested.key2=value&nested.key3=value5"

        // When
        parameters.addFromQuery(query)

        val testObject = parameters.to<ParameterComplexObject>(mappingByType = mapOf(
            ParameterComplexObject::class.java to { _,params -> ParameterComplexObject().also {obj ->
                obj.key1 = params["key1"].toLong()
                obj.key2 = params["key2"].toInt()
                obj.key3 = params["key3"].toBoolean()
                obj.nested = ParameterSimpleIterableObject().also {
                    it.key1 = params.getAll("nested.key1")
                    it.key2 = params.getAll("nested.key2").toSet()
                    it.key3 = params.getAll("nested.key3")
                }
            } }
        ))

        assertEquals(testObject.key1, 1, "key1")
        assertEquals(testObject.key2, 2, "key2")
        assertEquals(testObject.key3, true, "key3")
        assertEquals(testObject.nested.key1.size, 2, "nested.key1.size")
        assertEquals(testObject.nested.key1[0], "value1", "nested.key1[0]")
        assertEquals(testObject.nested.key1[1], "value4", "nested.key1[1]")
        assertEquals(testObject.nested.key2.size, 2, "nested.key2.size")
        assertTrue { testObject.nested.key2.containsAll(listOf("value", "value2")) }
        assertEquals(testObject.nested.key3.size, 2, "nested.key3.size")
        assertEquals(testObject.nested.key3[0], "value3", "nested.key3[0]")
        assertEquals(testObject.nested.key3[1], "value5", "nested.key3[1]")

    }

    @Test
    fun testParameterToMap() {
        // Given
        val parameters = Parameters()
        val query = "key1.key1=value1&key2.key2=value2&key3.key3=value3"

        // When
        parameters.addFromQuery(query)

        val testObject = parameters.to<ParameterWithMap>()

        // Then

        assertEquals(testObject.key1.size, 1)
        assertEquals(testObject.key1["key1"], "value1")
        assertEquals(testObject.key2.size, 1)
        assertEquals(testObject.key2["key2"], "value2")
        assertEquals(testObject.key3.size, 1)
        assertEquals(testObject.key3["key3"], "value3")
    }

    @Test
    fun testParameterToEnum() {
        // Given
        val parameters = Parameters()
        val query = "key1=VALUE1"

        // When
        parameters.addFromQuery(query)

        val testObject = parameters.to<ParameterWithEnum>()

        // Then

        assertEquals(testObject.key1, EnumClass.VALUE1)
    }
}

class ParameterSimpleObject {
    var key1: String = ""
    var key2: String = ""
    var key3: String = ""
}

class ParameterSimpleIterableObject {
    var key1: List<String> = emptyList()
    var key2: Set<String> = emptySet()
    var key3: List<String> = emptyList()
}

class ParameterNestedObject {
    var key1: String = ""
    var key2: String = ""
    var key3: String = ""
    lateinit var nested: ParameterSimpleObject
}

class ParameterComplexObject {
    var key1 = 0L
    var key2 = 0
    var key3: Boolean = false
    lateinit var nested: ParameterSimpleIterableObject
}

class ParameterWithPrimitiveArray {
    var key1: IntArray = intArrayOf()
    var key2: LongArray = longArrayOf()
    var key3: BooleanArray = booleanArrayOf()
}

class ParameterWithMap {
    var key1: Map<String, String> = emptyMap()
    var key2: Map<String, String> = emptyMap()
    var key3: Map<String, String> = emptyMap()
}


class ParameterWithEnum {
    var key1: EnumClass = EnumClass.VALUE1
}

enum class EnumClass {
    VALUE1, VALUE2
}