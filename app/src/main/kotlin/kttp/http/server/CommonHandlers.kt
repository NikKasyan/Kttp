package kttp.http.server

import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.HttpResponse
import kttp.http.protocol.Method
import kttp.http.protocol.MimeTypes
import java.io.File
import java.nio.file.Path
import java.util.*


val NOT_FOUND_HANDLER = {
    ReqHandler("/**", EnumSet.allOf(Method::class.java)) {
        val notFound = "Not Found ${request.uri.path}"
        respond(notFound)
    }
}

fun HostFilesHandler(relativeHostFilePath: String, requestBase: String = "") = ReqHandler(sanitizeRequestBase(requestBase), Method.GET, hostFiles(relativeHostFilePath, requestBase))

private fun sanitizeRequestBase(requestBase: String): String {
    val requestPath = if (requestBase.isEmpty() || requestBase == "/") {
        ""
    } else {
        if (requestBase.endsWith("/")) {
            requestBase
        } else {
            "$requestBase/"
        }
    }
    return if(!requestPath.endsWith("**")){
        "$requestPath**"
    } else {
        requestPath
    }
}

fun hostFiles(relativePath: String, requestBase: String = "") = hostFiles(Path.of(File(relativePath).absolutePath), requestBase)
fun hostFiles(path: Path, requestBase: String = ""): OnHttpRequest {
    return Handler@{
        val requestedPath = request.uri.path.removePrefix(requestBase)
        val file = resolvePath(path, requestedPath).toFile()
        if (file.exists()) {
            if (file.isDirectory) {
                val files = file.list() ?: arrayOf()
                val body = files.joinToString("\n") { "<li>$it</li>" }
                val headers = HttpHeaders{
                    withContentType(MimeTypes.TEXT_HTML)
                }

                respond(HttpResponse.ok(headers, body = "<ul>$body</ul>"))

            } else
                respond(HttpResponse.ok(body = file.readText()))
        } else {
            respond(HttpResponse.notFound(body = "Not Found $requestedPath"))
        }
    }
}

fun resolvePath(path: Path, requestedPath: String): Path {
    if(requestedPath.isEmpty() || requestedPath == "/")
        return path
    return path.resolve(requestedPath.removePrefix("/"))

}
