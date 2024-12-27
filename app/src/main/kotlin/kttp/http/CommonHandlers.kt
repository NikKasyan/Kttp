package kttp.http

import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.HttpResponse
import kttp.http.protocol.Method
import kttp.http.protocol.MimeTypes
import java.util.*


val NOT_FOUND_HANDLER = {
    HttpReqHandler("", EnumSet.allOf(Method::class.java)) {
        val notFound = "Not Found ${request.uri.path}"
        respond(HttpResponse.notFound(body = notFound))
    }
}

val FileHost: (String) -> Handler = { root: String ->
    {
            val path = request.uri.path + "../"

            val file = java.io.File(root, path)
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
                respond(HttpResponse.notFound(body = "Not Found $path"))
            }

    }
}

