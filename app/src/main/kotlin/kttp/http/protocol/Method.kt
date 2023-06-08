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

    fun allowsBody(): Boolean {
        return when (this) {
            POST, PUT, PATCH -> true
            else -> false
        }
    }
}