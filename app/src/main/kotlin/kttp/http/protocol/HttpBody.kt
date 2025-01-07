package kttp.http.protocol

import kttp.http.protocol.transfer.ChunkedInputStream
import kttp.http.protocol.transfer.ChunkingInputStream
import kttp.http.protocol.transfer.GZIPingInputStream
import kttp.io.DefaultInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.DeflaterInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream


class HttpBody(
    private val body: InputStream = nullInputStream(),
    val contentLength: Long? = null
) : DefaultInputStream() {


    companion object {
        fun fromString(body: String): HttpBody {

            return HttpBody(body.byteInputStream(), body.length.toLong())
        }

        fun fromBytes(body: ByteArray): HttpBody {
            return HttpBody(ByteArrayInputStream(body), body.size.toLong())
        }

        fun withTransferEncodings(body: InputStream, httpHeaders: HttpHeaders): HttpBody {
            val transferEncodings = httpHeaders.transferEncodings()
            if (transferEncodings.isEmpty())
                return HttpBody(body, httpHeaders.contentLength)
            return wrapWithTransferEncoding(body, transferEncodings, httpHeaders)
        }

        fun withTransferDecoding(body: InputStream, httpHeaders: HttpHeaders): HttpBody {
            val transferEncodings = httpHeaders.transferEncodings()
            if (transferEncodings.isEmpty())
                return HttpBody(body, httpHeaders.contentLength)
            return wrapWithTransferDecoding(body, transferEncodings, httpHeaders)
        }

        fun withDecoding(body: InputStream, httpHeaders: HttpHeaders): HttpBody {
            val transferBody =
                if (httpHeaders.hasTransferEncoding())
                    wrapWithTransferDecoding(body, httpHeaders.transferEncodings(), httpHeaders)
                else body

            return if (httpHeaders.hasContentEncoding())
                wrapWithContentDecoding(transferBody, httpHeaders.contentEncoding())
            else
                HttpBody(transferBody, httpHeaders.contentLength)
        }

        fun withEncoding(body: InputStream, httpHeaders: HttpHeaders): HttpBody {
            val encodedContent = if (httpHeaders.hasContentEncoding())
                wrapWithContentEncoding(body, httpHeaders.contentEncoding())
            else
                body

            return if (httpHeaders.hasTransferEncoding())
                wrapWithTransferEncoding(encodedContent, httpHeaders.transferEncodings(), httpHeaders)
            else
                HttpBody(encodedContent, httpHeaders.contentLength)
        }



        fun empty(): HttpBody {
            return HttpBody()
        }

    }

    fun toDecodedBody(httpHeaders: HttpHeaders): HttpBody {
        return withDecoding(body, httpHeaders)
    }

    fun toEncodedBody(httpHeaders: HttpHeaders): HttpBody {
        return withEncoding(body, httpHeaders)
    }


    fun hasContentLength(): Boolean {
        return contentLength != null
    }


    //Todo: Handle also Transfer-Encoding https://www.rfc-editor.org/rfc/rfc9112#name-transfer-encoding
    fun readAsString(charset: Charset = Charsets.UTF_8): String {
        return readAllBytes().toString(charset)
    }

    //////////////////
    // InputStream //
    ////////////////
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        return body.read(bytes, offset, length)
    }

    override fun close() {
        body.close()
    }

    override fun readAllBytes(): ByteArray {
        if (hasContentLength() && contentLength!! < Int.MAX_VALUE)
            return readNBytes(contentLength.toInt())
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

    override fun transferTo(out: OutputStream): Long {
        return body.transferTo(out)
    }

}

private fun wrapWithTransferEncoding(
    body: InputStream,
    transferEncodings: List<TransferEncoding>,
    httpHeaders: HttpHeaders
): HttpBody {
    var currentBody = body
    for (transferEncoding in transferEncodings) {
        currentBody = when (transferEncoding) {
            TransferEncoding.CHUNKED -> ChunkingInputStream(currentBody, httpHeaders)
            else -> throw NotImplementedError("Transfer-Encoding $transferEncoding for Request not implemented")
        }
    }
    return HttpBody(currentBody, httpHeaders.contentLength)
}

private fun wrapWithTransferDecoding(
    body: InputStream,
    transferEncodings: List<TransferEncoding>,
    httpHeaders: HttpHeaders
): HttpBody {
    var currentBody = body
    for (transferEncoding in transferEncodings) {
        currentBody = when (transferEncoding) {
            TransferEncoding.CHUNKED -> ChunkedInputStream(currentBody, httpHeaders)
            else -> throw NotImplementedError("Transfer-Encoding $transferEncoding for Request not implemented")
        }
    }
    return HttpBody(currentBody, httpHeaders.contentLength)
}

private fun wrapWithContentDecoding(
    body: InputStream,
    contentEncoding: ContentEncoding
): HttpBody {
    var currentBody = body
    currentBody = when (contentEncoding) {
        ContentEncoding.GZIP -> GZIPInputStream(currentBody)
        ContentEncoding.DEFLATE -> InflaterInputStream(currentBody)
        ContentEncoding.BR -> throw NotImplementedError("Brotli encoding not implemented")
        else -> currentBody
    }
    return HttpBody(currentBody)
}

private fun wrapWithContentEncoding(
    body: InputStream,
    contentEncoding: ContentEncoding
): HttpBody {
    var currentBody = body
    currentBody = when (contentEncoding) {
        ContentEncoding.GZIP -> GZIPingInputStream(currentBody)
        ContentEncoding.DEFLATE -> DeflaterInputStream(currentBody)
        ContentEncoding.BR -> throw NotImplementedError("Brotli encoding not implemented")
        else -> currentBody
    }
    return HttpBody(currentBody)
}