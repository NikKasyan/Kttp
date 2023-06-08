package kttp.http.protocol

import java.lang.RuntimeException

open class InvalidHttpRequest(msg: String) : RuntimeException(msg)