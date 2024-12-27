package kttp.http

import kttp.http.protocol.HttpRequest
import kttp.http.protocol.HttpResponse
import kttp.io.IOStream
import java.io.InputStream
import java.util.*

class HttpExchange(val request: HttpRequest,
                   private val response: HttpResponse,
                   private val io: IOStream): AutoCloseable {

    private var headerWritten = false
    private var closed = false

    private fun writeHeaders(response: HttpResponse = this.response) {
        if (headerWritten)
            return
        addMandatoryHeadersIfMissing(response)
        io.write(response.statusLine.toString())
        io.write("\r\n")
        io.write(response.headers.toString())
        io.write("\r\n\r\n")
        headerWritten = true
    }

    private fun writeBody(response: HttpResponse = this.response) {
        writeHeaders(response)
        io.writeFromStream(response.body)
    }

    fun write(inputStream: InputStream) {
        writeHeaders()
        io.writeFromStream(inputStream)
    }

    fun write(bytes: ByteArray) {
        writeHeaders()
        io.writeBytes(bytes)
    }

    fun writeln(string: String = "") {
        writeHeaders()
        io.writeln(string)
    }

    fun write(string: String) {
        writeHeaders()
        io.write(string)
    }

    fun respond(response: HttpResponse) {
        if(closed)
            return
        writeBody(response)

    }

    fun respond(body: String) {
        respond(HttpResponse.ok(body = body))
    }

    override fun close() {
        if(closed)
            return
        writeBody()
        response.close()
    }

}



 fun addMandatoryHeadersIfMissing(httpResponse: HttpResponse) {
    val headers = httpResponse.headers
    if (!headers.hasDate())
        headers.withDate(Date())

}
