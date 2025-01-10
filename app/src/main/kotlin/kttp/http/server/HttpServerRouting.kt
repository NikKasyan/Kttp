package kttp.http.server

import kttp.http.protocol.HttpResponse
import kttp.http.protocol.Method
import java.util.EnumSet

typealias OnHttpRequest = HttpExchange.() -> Unit

fun HttpServer.onGet(path: String, onHttpRequest: OnHttpRequest): HttpServer {
    return addHttpReqHandler(path, EnumSet.of(Method.GET), onHttpRequest)
}

fun HttpServer.onPost(path: String, onHttpRequest: OnHttpRequest): HttpServer {
    return addHttpReqHandler(path, EnumSet.of(Method.POST), onHttpRequest)
}

fun HttpServer.onPut(path: String, onHttpRequest: OnHttpRequest): HttpServer {
    return addHttpReqHandler(path, EnumSet.of(Method.PUT), onHttpRequest)
}

fun HttpServer.onDelete(path: String, onHttpRequest: OnHttpRequest): HttpServer {
    return addHttpReqHandler(path, EnumSet.of(Method.DELETE), onHttpRequest)
}

fun HttpServer.onPatch(path: String, onHttpRequest: OnHttpRequest): HttpServer {
    return addHttpReqHandler(path, EnumSet.of(Method.PATCH), onHttpRequest)
}

fun HttpServer.onHead(path: String, onHttpRequest: OnHttpRequest): HttpServer {
    return addHttpReqHandler(path, EnumSet.of(Method.HEAD), onHttpRequest)
}

fun HttpServer.onOptions(path: String, onHttpRequest: OnHttpRequest): HttpServer {
    return addHttpReqHandler(path, EnumSet.of(Method.OPTIONS), onHttpRequest)
}

fun HttpServer.onConnect(path: String, onHttpRequest: OnHttpRequest): HttpServer {
    return addHttpReqHandler(path, EnumSet.of(Method.CONNECT), onHttpRequest)
}

fun HttpServer.onTrace(path: String, onHttpRequest: OnHttpRequest): HttpServer {
    return addHttpReqHandler(path, EnumSet.of(Method.TRACE), onHttpRequest)
}
fun HttpServer.on(method: Method, path: String, onHttpRequest: OnHttpRequest): HttpServer {
    return addHttpReqHandler(path, EnumSet.allOf(Method::class.java), onHttpRequest)
}

fun HttpServer.on(methods: List<Method>, path: String, onHttpRequest: OnHttpRequest): HttpServer {
    return addHttpReqHandler(path, EnumSet.allOf(Method::class.java), onHttpRequest)
}

fun HttpServer.on(methods: EnumSet<Method>, path: String, onHttpRequest: OnHttpRequest): HttpServer {
    return addHttpReqHandler(path, EnumSet.allOf(Method::class.java), onHttpRequest)
}

fun HttpServer.on(path: String, onHttpRequest: OnHttpRequest): HttpServer {
    return addHttpReqHandler(path, EnumSet.allOf(Method::class.java), onHttpRequest)
}

fun HttpServer.addHttpReqHandler(path: String, methods: EnumSet<Method>, onHttpRequest: OnHttpRequest): HttpServer {
    val handler = ReqHandler(path, methods, onHttpRequest)
    return addHttpReqHandler(handler)
}