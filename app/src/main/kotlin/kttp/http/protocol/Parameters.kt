package kttp.http.protocol

import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.util.*
import java.lang.reflect.Method as JMethod


class Parameters(private val parameters: MutableMap<String, MutableList<String>> = mutableMapOf()) {

    var size = 0
        private set

    companion object {
        fun fromQuery(query: String?): Parameters {
            val parameters = Parameters()
            parameters.addFromQuery(query)
            return parameters
        }
    }
    fun addFromQuery(query: String?) {
        if (query == null)
            return
        query.split("&").forEach {
            val (key, value) = it.split("=")
            this[key] = URIUtil.decodeURI(value)
        }
    }

    operator fun set(key: String, value: String) {
        add(key, value)
    }

    fun add(key: String, vararg value: String) {
        size += value.size
        if (parameters.containsKey(key)) {
            parameters[key]!!.addAll(value)
        } else {
            parameters[key] = value.toMutableList()
        }
    }

    fun add(key: String, values: List<String>) {
        size += values.size
        if (parameters.containsKey(key)) {
            parameters[key]!!.addAll(values)
        } else {
            parameters[key] = values.toMutableList()
        }
    }

    operator fun get(key: String): String {
        return getFirst(key)
    }

    fun getFirst(key: String): String {
        return getFirstOrNull(key)!!
    }

    fun getFirstOrNull(key: String): String? {
        return getAll(key).firstOrNull()
    }

    fun getAll(key: String): List<String> {
        return parameters[key] ?: emptyList()
    }

    fun remove(key: String) {
        size -= parameters[key]?.size ?: 0
        parameters.remove(key)
    }

    fun clear() {
        size = 0
        parameters.clear()
    }

    fun has(key: String): Boolean {
        return parameters.containsKey(key)
    }

    override fun toString(): String {
        return parameters.entries.joinToString("&") { (key, values) ->
            values.joinToString("&") { "${URIUtil.encodeURI(key)}=${URIUtil.encodeURI(it)}" }
        }
    }

    fun isEmpty(): Boolean = size == 0

    fun filter(predicate: (String, List<String>) -> Boolean): Parameters {
        return Parameters(parameters.filter { predicate(it.key, it.value) }.toMutableMap())
    }

    fun forEach(action: (String, List<String>) -> Unit) {
        parameters.forEach(action)
    }

    inline fun <reified T : Any> to(
        mappingByField: MappingByField = emptyMap(),
        mappingByType: MappingByType = emptyMap(),
        constructors: ConstructorMap = emptyMap()
    ): T {
        return to(T::class.java, mappingByField, mappingByType, constructors)
    }

    fun <T : Any> to(
        clazz: Class<T>,
        mappingByField: MappingByField,
        mappingByType: MappingByType,
        constructors: ConstructorMap,
        baseRoute: String = ""
    ): T {
        return ParametersToObject(
            clazz,
            mappingByField,
            mappingByType,
            this,
            constructors,
            baseRoute = baseRoute
        ).toObject()
    }

}

typealias MappingFunction = (values: String, parameters: Parameters) -> Any

typealias MappingByField = Map<String, MappingFunction>

typealias MappingByType = Map<Class<*>, MappingFunction>

typealias ConstructorMap = Map<Class<*>, Constructor<Any>>
typealias Constructor<T> = () -> T

val defaultMappingByType: MappingByType = mapOf(
    String::class.java to { v, _ -> v },
    Int::class.java to { values, _ -> values.toInt() },
    Long::class.java to { values, _ -> values.toLong() },
    Float::class.java to { values, _ -> values.toFloat() },
    Double::class.java to { values, _ -> values.toDouble() },
    Boolean::class.java to { values, _ -> values.toBoolean() },
    UUID::class.java to { values, _ -> UUID.fromString(values) }
)


//Todo: Parameters needs major refactoring, should reuse mapping code
private class ParametersToObject<T : Any>(
    private val clazz: Class<T>,
    private val mappingByField: MappingByField,
    private val mappingByType: MappingByType,
    private val parameters: Parameters,
    private val constructors: ConstructorMap,
    private val baseRoute: String = ""
) {

    fun toObject(): T {
        val mapping = mappingByType[clazz]
        if (mapping != null) {
            return mapping("", parameters) as T
        }
        val obj = createNewInstance()
        clazz.declaredFields.forEach { field ->
            field.isAccessible = true
            val mappedValue = mapValue(field) ?: getFieldValue(field, obj)

            val setter = getSetter(field)
            if (setter != null) {
                setter.isAccessible = true
                try {
                    setter.invoke(obj, mappedValue)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Setter for field ${field.name} in class ${clazz.name} has invalid argument type ${mappedValue?.javaClass}")
                }
            } else {
                field.set(obj, mappedValue)
            }

        }
        return obj
    }

    private fun createNewInstance(): T {
        val constructor = constructors[clazz]
        if (constructor != null) {
            val instance = constructor()
            if (instance.javaClass == clazz)
                return instance as T
            else throw IllegalArgumentException("Constructor for class ${clazz.name} returned an instance of ${instance.javaClass}")
        }
        try {
            return clazz.getConstructor().newInstance()
                ?: throw IllegalArgumentException("No constructor found for class ${clazz.name}")
        } catch (e: NoSuchMethodException) {
            throw IllegalArgumentException("No constructor found for class ${clazz.name}")
        }
    }

    private fun getFieldValue(field: Field, obj: Any): Any? {
        val getter = getGetter(field)
        if (getter != null) {
            getter.isAccessible = true
            return getter.invoke(obj)
        }
        field.isAccessible = true
        return field.get(obj)
    }

    private fun mapValue(field: Field): Any? {
        val name = field.name
        val type = field.type
        val value = parameters.getAll("${baseRoute}${name}")
        if (Iterable::class.java.isAssignableFrom(type)) { // is Iterable type
            return mapToIterable(field)
        } else if (type.isArray) {
            return mapToArray(field)
        } else if (type.isEnum) {
            return mapToEnum(field)
        } else if(Map::class.java.isAssignableFrom(type)){
            return mapToMap(field)
        }
        val mapping = getMappingByField(name, type)
        val singleValue = value.firstOrNull()
        if (mapping != null) {
            if (singleValue == null)
                return null
            return mapping(singleValue, parameters)
        }
        return mapToObject(field)

    }

    private fun mapToMap(field: Field): Map<*, *> {
        val mapBaseRoute = "${baseRoute}${field.name}."
        val values = parameters.filter{k,_ -> k.startsWith(mapBaseRoute) }
        val genericType = field.genericType as ParameterizedType
        val keyType = genericType.actualTypeArguments[0] as Class<*>
        val valueType = genericType.actualTypeArguments[1] as Class<*>

        val map = mutableMapOf<Any, Any?>()

        val keyMapping = getMappingByField("", keyType) ?: { v, _ -> v }
        val valueMapping = getMappingByField("", valueType) ?: { v, _ -> v }

        parameters.forEach{ k, v ->
            if(k.startsWith(mapBaseRoute)){
                val key = keyMapping(k.removePrefix(mapBaseRoute), parameters)
                val value = valueMapping(v.first(), parameters)
                map[key] = value
            }
        }

        return map
    }

    private fun mapToEnum(field: Field): Any {
        val value = parameters.getFirst("${baseRoute}${field.name}")
        val ordinal = value.toIntOrNull()
        if (ordinal != null) {
            return field.type.enumConstants[ordinal]
        }
        return field.type.enumConstants.first { it.toString() == value }
    }


    private fun mapToIterable(
        field: Field
    ): MutableCollection<Any> {
        val value = parameters.getAll("${baseRoute}${field.name}")
        val type = field.type
        val genericType = field.genericType
        val typeArgument = (genericType as ParameterizedType).actualTypeArguments[0]
        val mapping = getMappingByField(field.name, typeArgument as Class<*>) ?: { v, _ -> v }
        val collection = getCollectionInstance(type)
        value.map { mapping(it, parameters) }.forEach { collection.add(it) }
        return collection
    }

    private fun getCollectionInstance(type: Class<*>): MutableCollection<Any> {
        if (!type.isInterface) {
            return type.getConstructor().newInstance() as MutableCollection<Any>
        }
        return when {
            List::class.java.isAssignableFrom(type) -> mutableListOf()
            Set::class.java.isAssignableFrom(type) -> mutableSetOf()
            else -> mutableListOf()
        }
    }


    private fun mapToArray(field: Field): Any {
        val value = parameters.getAll("${baseRoute}${field.name}")
        val type = field.type
        val componentType = type.componentType
        if (isPrimitiveArray(componentType)) {
            return mapToPrimitiveArray(componentType, value)
        }
        val mapping = getMappingByField(field.name, componentType) ?: { v, _ -> v }
        return value.map { mapping(it, parameters) }.toTypedArray()
    }

    private fun isPrimitiveArray(type: Class<*>): Boolean {
        return type == Int::class.java
                || type == Long::class.java
                || type == Float::class.java
                || type == Double::class.java
                || type == Boolean::class.java
                || type == Char::class.java
                || type == Byte::class.java
                || type == Short::class.java
                || type.isPrimitive
    }

    private fun mapToPrimitiveArray(type: Class<*>, value: List<String>): Any {
        return when (type) {
            Int::class.java -> value.map { it.toInt() }.toIntArray()
            Long::class.java -> value.map { it.toLong() }.toLongArray()
            Float::class.java -> value.map { it.toFloat() }.toFloatArray()
            Double::class.java -> value.map { it.toDouble() }.toDoubleArray()
            Boolean::class.java -> value.map { it.toBoolean() }.toBooleanArray()
            Char::class.java -> value.map { it.single() }.toCharArray()
            Byte::class.java -> value.map { it.toByte() }.toByteArray()
            Short::class.java -> value.map { it.toShort() }.toShortArray()
            else -> throw IllegalArgumentException("Unsupported primitive array type: $type")
        }
    }

    private fun mapToObject(field: Field): Any {
        val name = field.name
        val params = parameters.filter { k, _ -> k.startsWith("$name.") }
        return params.to(field.type, mappingByField, mappingByType, constructors, "$name.")
    }

    private fun getSetterName(field: Field): String {
        return "set${
            field.name.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.getDefault()
                ) else it.toString()
            }
        }"
    }

    private fun getSetter(field: Field): JMethod? {
        return clazz.declaredMethods.firstOrNull { it.name == getSetterName(field) && it.parameterCount == 1 && it.parameterTypes[0] == field.type }
    }

    private fun getGetterName(field: Field): String {
        return "get${
            field.name.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.getDefault()
                ) else it.toString()
            }
        }"
    }

    private fun getGetter(field: Field): JMethod? {
        return clazz.declaredMethods.firstOrNull { it.name == getGetterName(field) && it.parameterCount == 0 && it.returnType == field.type }
    }

    private fun getMappingByField(fieldName: String = "", clazz: Class<*>): MappingFunction? {
        return mappingByField[fieldName] ?: mappingByType[clazz] ?: defaultMappingByType[clazz]
    }


}

