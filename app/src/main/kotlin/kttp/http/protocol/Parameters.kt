package kttp.http.protocol

import java.lang.reflect.Field
import java.util.*
import java.lang.reflect.Method as JMethod


class Parameters {
    private val parameters = mutableMapOf<String, MutableList<String>>()

    var size = 0
        private set

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

    inline fun <reified T> to(
        mappingByField: MappingByField = emptyMap(),
        mappingByType: MappingByType = emptyMap()
    ): T {
        return to(T::class.java, mappingByField, mappingByType)
    }

    fun <T> to(clazz: Class<T>, mappingByField: MappingByField, mappingByType: MappingByType): T {
        return ParametersToObject(clazz, mappingByField, mappingByType).toObject(this)
    }

}

typealias MappingByField = Map<String, (values: String) -> Any>

typealias MappingByType = Map<Class<*>, (values: String) -> Any>

val defaultMappingByType: MappingByType = mapOf(
    String::class.java to { values -> values },
    Int::class.java to { values -> values.toInt() },
    Long::class.java to { values -> values.toLong() },
    Float::class.java to { values -> values.toFloat() },
    Double::class.java to { values -> values.toDouble() },
    Boolean::class.java to { values -> values.toBoolean() },
    UUID::class.java to { values -> UUID.fromString(values) }
)

private class ParametersToObject<T>(
    private val clazz: Class<T>,
    private val mappingByField: MappingByField,
    private val mappingByType: MappingByType
) {
    fun toObject(parameters: Parameters): T {
        val obj = clazz.getConstructor().newInstance()
        clazz.declaredFields.forEach { field ->
            field.isAccessible = true
            val value = parameters.getAll(field.name)
            val type = field.type
            val valueToSet = getValueToSet(value, type, field)

            val setter = getSetter(field)
            if (setter != null) {
                setter.isAccessible = true
                setter.invoke(obj, valueToSet)
            } else {
                field.set(obj, valueToSet)
            }

        }
        return obj
    }

    private fun getValueToSet(value: List<String>, type: Class<*>, field: Field): Any {
        return if (Iterable::class.java.isAssignableFrom(type)) { // is Iterable type
            val genericType = field.genericType
            val typeArgument = (genericType as java.lang.reflect.ParameterizedType).actualTypeArguments[0]
            val mapping = getMappingByField(field, typeArgument as Class<*>)
            val collection = getCollectionInstance(type)
            value.map { mapping(it) }.forEach { collection.add(it) }
            collection
        } else {
            val mapping = getMappingByField(field)
            mapping(value.first())
        }
    }

    private fun getCollectionInstance(type: Class<*>): MutableCollection<Any> {
        if(!type.isInterface){
            return type.getConstructor().newInstance() as MutableCollection<Any>
        }
        return when {
            List::class.java.isAssignableFrom(type) || Iterable::class.java.isAssignableFrom(type) -> mutableListOf()
            Set::class.java.isAssignableFrom(type) -> mutableSetOf()
            else -> throw IllegalArgumentException("Unsupported collection type: $type")
        }
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

    private fun getMappingByField(field: Field, clazz: Class<*> = field.type): (String) -> Any {
        return mappingByField[field.name] ?: mappingByType[clazz] ?: defaultMappingByType[clazz] ?: { it }
    }
}

