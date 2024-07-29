package kttp.net

import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset


//Todo: Have to rewrite using bytes instead of strings and not using UTF-8 as standard charset
// https://www.rfc-editor.org/rfc/rfc7230#page-19
class IOStream(private val inputStream: InputStream,
               private val outputStream: OutputStream,
               private val charset: Charset = Charsets.UTF_8,
               maxLineLengthInBytes: Int = 8192) : InputStream(), AutoCloseable {

    private val input = LineReader(inputStream, maxLineLengthInBytes)
    private val output = BufferedOutputStream(outputStream)

    private var isClosed = false

    fun writeln(string: String = "") {
        if(isClosed)
            throw StreamAlreadyClosed()
        write("${string}\r\n")
    }

    fun write(string: String) {
        if(isClosed)
            throw StreamAlreadyClosed()
        output.write(string.toByteArray(charset))
        output.flush()
    }

    fun writeBytes(bytes: ByteArray) {
        if(isClosed)
            throw StreamAlreadyClosed()
        output.write(bytes)
        output.flush()
    }

    fun readLine(): String {
        if(isClosed)
            throw StreamAlreadyClosed()
        try {
            return input.readLine() ?: ""
        } catch (ioException: IOException) {
            throw EndOfStream()
        }
    }

    fun readBytes(contentLength: Int): ByteArray {
        if(isClosed)
            throw StreamAlreadyClosed()
        val byteArray = ByteArray(contentLength)
        input.read(byteArray, 0, contentLength)
        return byteArray
    }

    fun readBytesAsString(contentLength: Int, charset: Charset = Charsets.UTF_8): String {
        return String(readBytes(contentLength), charset)
    }

    override fun close() {
        if (isClosed) {
            throw StreamAlreadyClosed()
        }
        isClosed = true
        try{
            inputStream.close()
        } catch (e: IOException) {
            //Ignore
        }
        try{
            outputStream.close()
        } catch (e: IOException) {
            //Ignore
        }
    }

    /////////////////
    // InputStream //
    ////////////////
    override fun read(): Int {
        if(isClosed)
            throw StreamAlreadyClosed()
        return input.read()
    }

    override fun read(b: ByteArray): Int {
        if(isClosed)
            throw StreamAlreadyClosed()
        return input.read(b)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if(isClosed)
            throw StreamAlreadyClosed()
        return input.read(b, off, len)
    }

    override fun readAllBytes(): ByteArray {
        if(isClosed)
            throw StreamAlreadyClosed()
        return input.readAllBytes()
    }

    override fun readNBytes(len: Int): ByteArray {
        if(isClosed)
            throw StreamAlreadyClosed()
        return input.readNBytes(len)
    }

    override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int {
        if(isClosed)
            throw StreamAlreadyClosed()
        return input.readNBytes(b, off, len)
    }

    override fun skip(n: Long): Long {
        if(isClosed)
            throw StreamAlreadyClosed()
        return input.skip(n)
    }

    override fun skipNBytes(n: Long) {
        if(isClosed)
            throw StreamAlreadyClosed()
        input.skipNBytes(n)
    }

    override fun available(): Int {
        if(isClosed)
            throw StreamAlreadyClosed()
        return input.available()
    }

    override fun mark(readlimit: Int) {
        if(isClosed)
            throw StreamAlreadyClosed()
        input.mark(readlimit)
    }

    override fun reset() {
        if(isClosed)
            throw StreamAlreadyClosed()
        input.reset()
    }

    override fun markSupported(): Boolean {
        if(isClosed)
            throw StreamAlreadyClosed()
        return input.markSupported()
    }

    override fun transferTo(out: OutputStream?): Long {
        if(isClosed)
            throw StreamAlreadyClosed()
        return input.transferTo(out)
    }
}

class EndOfStream : RuntimeException("End of Stream")

class StreamAlreadyClosed : RuntimeException("Stream already closed")
