package kttp.http.protocol

import kttp.http.protocol.transfer.ChunkedInputStream
import kttp.http.protocol.transfer.ChunkingInputStream
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

        fun withTransferEncodingRequest(body: InputStream, httpHeaders: HttpHeaders): HttpBody {
            val transferEncodings = httpHeaders.transferEncodingAsList()
            if (transferEncodings.isEmpty())
                return HttpBody(body, httpHeaders.contentLength)
            return buildTransferPipelineForRequest(body, transferEncodings, httpHeaders)
        }

        fun withTransferEncodingResponse(body: InputStream, httpHeaders: HttpHeaders): HttpBody {
            val transferEncodings = httpHeaders.transferEncodingAsList()
            if (transferEncodings.isEmpty())
                return HttpBody(body, httpHeaders.contentLength)
            return buildTransferEncodedPipelineForResponse(body, transferEncodings, httpHeaders)
        }

        fun empty(): HttpBody {
            return HttpBody()
        }

    }

    fun toEncodedRequestBody(httpHeaders: HttpHeaders): HttpBody {
        return withTransferEncodingRequest(body, httpHeaders)
    }

    fun toEncodedResponseBody(httpHeaders: HttpHeaders): HttpBody {
        return withTransferEncodingResponse(body, httpHeaders)
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

private fun buildTransferPipelineForRequest(
    body: InputStream,
    transferEncodings: List<TransferEncoding>,
    httpHeaders: HttpHeaders
): HttpBody {
    var currentBody = body
    for (transferEncoding in transferEncodings) {
        currentBody = when (transferEncoding) {
            TransferEncoding.CHUNKED -> ChunkedInputStream(currentBody, httpHeaders)
            TransferEncoding.DEFLATE -> InflaterInputStream(currentBody)
            TransferEncoding.GZIP -> GZIPInputStream(currentBody)
            else -> throw NotImplementedError("Transfer-Encoding $transferEncoding for Request not implemented")
        }
    }
    return HttpBody(currentBody, httpHeaders.contentLength)
}

private fun buildTransferEncodedPipelineForResponse(
    body: InputStream,
    transferEncodings: List<TransferEncoding>,
    httpHeaders: HttpHeaders
): HttpBody {
    var currentBody = body
    for (transferEncoding in transferEncodings) {
        currentBody = when (transferEncoding) {
            TransferEncoding.CHUNKED -> ChunkingInputStream(currentBody, httpHeaders)
            TransferEncoding.DEFLATE  -> DeflaterInputStream(currentBody)
            TransferEncoding.GZIP -> GZIPInputStream(currentBody)
            else -> throw NotImplementedError("Transfer-Encoding $transferEncoding for Response not implemented")
        }
    }
    return HttpBody(currentBody, httpHeaders.contentLength)
}