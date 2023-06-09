package kttp.http

import kttp.http.protocol.HttpHeaders
import kttp.http.protocol.HttpRequest
import kttp.http.protocol.Method
import java.net.Socket
import java.net.URI

class HttpClient(baseURL: String) {

    private val baseURI: URI

    companion object {
        fun get(requestUrl: String, httpHeaders: HttpHeaders = HttpHeaders()) {
            return HttpClient(requestUrl).get(httpHeaders = httpHeaders)
        }
    }

    init {
        this.baseURI = URI(baseURL)
        if (!this.baseURI.isAbsolute)
            TODO("Throw real exception when baseURI is not absolute")
    }

    fun get(requestUrl: String = "/", httpHeaders: HttpHeaders = HttpHeaders()) {
        return request(Method.GET, requestUrl, httpHeaders)
    }

    fun request(method: Method, requestUrl: String, httpHeaders: HttpHeaders = HttpHeaders()) {
        val socket = Socket(baseURI.host, getPort())
        val out = socket.getOutputStream()

        val request = HttpRequest.from(method, URI(requestUrl), httpHeaders)
        println(request.toString().replace("\r\n", "\\r\\n\r\n"))
        out.write(request.toString().toByteArray())
        out.flush()

        val input = socket.getInputStream().bufferedReader()

        var line: String?
        while ((input.readLine().also { line = it }) != null) {
            println(line)
        }
    }

    private fun getPort(): Int {
        if (baseURI.port != -1)
            return baseURI.port
        if (baseURI.scheme == "http")
            return 80
        if (baseURI.scheme == "https")
            return 443
        return -1
    }

}