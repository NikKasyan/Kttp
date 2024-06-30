package kttp.http.protocol

enum class Method {
    GET,
    HEAD,
    POST,
    PUT,
    DELETE,
    CONNECT,
    OPTIONS,
    TRACE,
    PATCH;

    companion object {
        fun byName(methodString: String): Method {

            try {
                return Method.valueOf(methodString)
            } catch (e: IllegalArgumentException) {
                throw UnknownHttpMethod("Unknown Method $methodString")
            }
        }

    }
    fun allowsBody(): Boolean {
        return when (this) {
            POST, PUT, PATCH -> true
            else -> false
        }
    }

}