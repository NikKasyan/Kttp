package kttp.http.protocol

import kttp.http.protocol.transfer.ChunkedInputStream
import kttp.http.protocol.transfer.ChunkingInputStream
import kttp.http.protocol.transfer.GZIPingInputStream
import kttp.io.DefaultInputStream
import kttp.io.LimitedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.DeflaterInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream


/**
 * @param framedBody The body as the message frames it, before content codings are removed.
 * Reading it to the end reads exactly this message's body from the connection.
 */
class HttpBody(
    private val body: InputStream = nullInputStream(),
    val contentLength: Long? = null,
    private val framedBody: InputStream = body
) : DefaultInputStream() {


    companion object {
        fun fromString(body: String): HttpBody {
            // Content-Length counts octets, not characters https://www.rfc-editor.org/rfc/rfc9110#section-8.6
            return fromBytes(body.toByteArray())
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
            return HttpBody(wrapWithTransferDecoding(body, transferEncodings, httpHeaders), httpHeaders.contentLength)
        }

        /**
         * Reads the body of a message whose header section was just read from [body].
         * The body length is decided as in https://www.rfc-editor.org/rfc/rfc9112#section-6.3
         */
        fun withDecoding(body: InputStream, httpHeaders: HttpHeaders): HttpBody {
            val framedBody =
                if (httpHeaders.hasTransferEncoding())
                    wrapWithTransferDecoding(body, httpHeaders.transferEncodings(), httpHeaders)
                else if (httpHeaders.hasContentLength())
                    LimitedInputStream(body, httpHeaders.contentLengthLong())
                else // Only a response can be without both, it then ends when the connection closes https://www.rfc-editor.org/rfc/rfc9112#section-6.3-2.8
                    body

            return if (httpHeaders.hasContentEncoding())
                HttpBody(wrapWithContentDecoding(framedBody, httpHeaders.contentEncoding()), framedBody = framedBody)
            else
                HttpBody(framedBody, httpHeaders.contentLength)
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
            return HttpBody(nullInputStream(), 0)
        }

    }

    /**
     * The trailer fields sent after a chunked body. They are only complete after the body was read.
     * They are kept apart from the header fields https://www.rfc-editor.org/rfc/rfc9110#section-6.5.1
     */
    val trailers: HttpHeaders
        get() = (framedBody as? ChunkedInputStream)?.trailers ?: HttpHeaders()

    /**
     * Reads and drops the rest of the body, so that the next message on the connection is read from its start.
     */
    fun discardRemaining() {
        framedBody.transferTo(OutputStream.nullOutputStream())
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
            else -> throw IllegalArgumentException("Transfer-Encoding $transferEncoding can't be sent")
        }
    }
    return HttpBody(currentBody, httpHeaders.contentLength)
}

private fun wrapWithTransferDecoding(
    body: InputStream,
    transferEncodings: List<TransferEncoding>,
    httpHeaders: HttpHeaders
): InputStream {
    var currentBody = body
    for (transferEncoding in transferEncodings) {
        currentBody = when (transferEncoding) {
            TransferEncoding.CHUNKED -> ChunkedInputStream(currentBody, httpHeaders)
            // A transfer coding the server does not understand is answered with 501 https://www.rfc-editor.org/rfc/rfc9112#section-6.1
            else -> throw UnknownTransferEncoding(transferEncoding.value)
        }
    }
    return currentBody
}

private fun wrapWithContentDecoding(
    body: InputStream,
    contentEncoding: ContentEncoding
): InputStream {
    return when (contentEncoding) {
        ContentEncoding.GZIP -> GZIPInputStream(body)
        ContentEncoding.DEFLATE -> InflaterInputStream(body)
        else -> throw UnsupportedContentEncoding(contentEncoding.value)
    }
}

private fun wrapWithContentEncoding(
    body: InputStream,
    contentEncoding: ContentEncoding
): HttpBody {
    var currentBody = body
    currentBody = when (contentEncoding) {
        ContentEncoding.GZIP -> GZIPingInputStream(currentBody)
        ContentEncoding.DEFLATE -> DeflaterInputStream(currentBody)
        else -> throw IllegalArgumentException("Content-Encoding $contentEncoding can't be sent")
    }
    return HttpBody(currentBody)
}

// A request whose content coding the server can't decode is answered with 415 https://www.rfc-editor.org/rfc/rfc9110#section-15.5.16
class UnsupportedContentEncoding(contentEncoding: String) :
    InvalidHttpRequest("Unsupported Content Encoding: $contentEncoding")
