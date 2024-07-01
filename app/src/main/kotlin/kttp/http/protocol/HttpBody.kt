package kttp.http.protocol

import kttp.net.IOStream
import java.io.InputStream
import java.io.OutputStream

class HttpBody(private val body: InputStream, val contentLength: Int? = null): InputStream() {

    companion object {
        fun fromString(body: String): HttpBody {

            return HttpBody(body.byteInputStream(), body.length)
        }
    }
    fun hasContentLength(): Boolean {
        return contentLength != null
    }


    //Todo: Handle also Transfer-Encoding https://www.rfc-editor.org/rfc/rfc9112#name-transfer-encoding
    fun readAsString(io: IOStream, headers: HttpHeaders): String {
        val contentLength = headers.contentLength ?: contentLength
        if (contentLength != null) {
            return io.readBytes(contentLength).toString(Charsets.UTF_8)
        }
        return ""
    }

    //////////////////
    // InputStream //
    ////////////////
    override fun read(): Int {
        return body.read()
    }

    override fun read(b: ByteArray): Int {
        return body.read(b)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return body.read(b, off, len)
    }

    override fun close() {
        body.close()
    }

    override fun readAllBytes(): ByteArray {
        if(hasContentLength())
            return body.readNBytes(contentLength!!)
        return body.readAllBytes()
    }

    override fun readNBytes(len: Int): ByteArray {
        return body.readNBytes(len)
    }

    override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int {
        return body.readNBytes(b, off, len)
    }

    override fun skip(n: Long): Long {
        return body.skip(n)
    }

    override fun skipNBytes(n: Long) {
        body.skipNBytes(n)
    }

    override fun available(): Int {
        return body.available()
    }

    override fun mark(readlimit: Int) {
        body.mark(readlimit)
    }

    override fun reset() {
        body.reset()
    }

    override fun markSupported(): Boolean {
        return body.markSupported()
    }

    override fun transferTo(out: OutputStream?): Long {
        return body.transferTo(out)
    }

}