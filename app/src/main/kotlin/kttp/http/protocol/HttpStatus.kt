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
    IM_A_TEAPOT(418, "I'm a teapot"), // RFC 2324 but this is not rfc compliant
    // https://www.rfc-editor.org/rfc/rfc9110.html#name-418-unused
    // but come on, it's a teapot
    MISDIRECTED_REQUEST(421, "Misdirected Request"),
    UNPROCESSABLE_CONTENT(422, "Unprocessable Content"),
    UPGRADE_REQUIRED(426, "Upgrade Required"),
    PRECONDITION_REQUIRED(428, "Precondition Required"),

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
    val isInformational: Boolean
        get() = code in 100..199
    val isSuccess: Boolean
        get() = code in 200..299
    val isRedirection: Boolean
        get() = code in 300..399
    val isClientError: Boolean
        get() = code in 400..499
    val isServerError: Boolean
        get() = code in 500..599


    companion object {
        fun byCode(code: Int): HttpStatus {
            return values().find { it.code == code } ?: throw InvalidHttpStatus("No status found for code $code")
        }
    }

}

class InvalidHttpStatus(msg: String) : RuntimeException(msg)