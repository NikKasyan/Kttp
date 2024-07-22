package kttp.http.protocol

enum class HttpStatus(val code: Int, private val msg: String = "") {
    CONTINUE(100),
    SWITCHING_PROTOCOLS(101),

    OK(200, "OK"),
    CREATED(201),
    ACCEPTED(202),
    NON_AUTHORITATIVE_INFORMATION(203, "Non-Authoritative Information"),
    NO_CONTENT(204),
    RESET_CONTENT(205),
    PARTIAL_CONTENT(206),

    MULTIPLE_CHOICES(300),
    MOVED_PERMANENTLY(301),
    FOUND(302),
    SEE_OTHER(303),
    USE_PROXY(304),
    TEMPORARY_REDIRECT(307),

    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    PAYMENT_REQUIRED(402),
    FORBIDDEN(403),
    NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    NOT_ACCEPTABLE(406),
    PROXY_AUTHENTICATION_REQUIRED(407),
    REQUEST_TIME_OUT(408, "Request Time-out"),
    CONFLICT(409),
    GONE(410),
    LENGTH_REQUIRED(411),
    PRECONDITION_FAILED(412),
    REQUEST_ENTITY_TOO_LARGE(413),
    REQUEST_URI_TOO_LARGE(414, "Request-URI Too Large"),
    UNSUPPORTED_MEDIA_TYPE(415),
    REQUESTED_RANGE_NOT_SATISFIABLE(416, "Requested range not satisfiable"),
    EXPECTATION_FAILED(417),

    INTERNAL_SERVER_ERROR(500),
    NOT_IMPLEMENTED(501),
    BAD_GATEWAY(502),
    SERVICE_UNAVAILABLE(503),
    GATEWAY_TIME_OUT(504, "Gateway Time-out"),
    HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version not supported");


    val message: String
        get() {
            if (msg.isEmpty()) {
                return name.lowercase().split("_").joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }
            }
            return msg
        }

    companion object {
        fun byCode(code: Int): HttpStatus {
            return values().find { it.code == code } ?: throw InvalidHttpStatus("No status found for code $code")
        }
    }

}

class InvalidHttpStatus(msg: String) : RuntimeException(msg)