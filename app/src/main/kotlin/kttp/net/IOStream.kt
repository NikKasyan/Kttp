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

    fun writeln(string: String) {
        write("${string}\r\n")
    }

    fun write(string: String) {
        output.write(string.toByteArray(charset))
        output.flush()
    }

    fun writeBytes(bytes: ByteArray) {
        output.write(bytes)
        output.flush()
    }

    fun readLine(): String {
        try {
            return input.readLine() ?: throw EndOfStream()
        } catch (ioException: IOException) {
            throw EndOfStream()
        }
    }

    fun readBytes(contentLength: Int): ByteArray {
        val byteArray = ByteArray(contentLength)
        input.read(byteArray, 0, contentLength)
        return byteArray
    }

    fun readBytesAsString(contentLength: Int, charset: Charset = Charsets.UTF_8): String {
        return String(readBytes(contentLength), charset)
    }

    override fun close() {
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
        return input.read()
    }

    override fun read(b: ByteArray): Int {
        return input.read(b)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return input.read(b, off, len)
    }

    override fun readAllBytes(): ByteArray {
        return input.readAllBytes()
    }

    override fun readNBytes(len: Int): ByteArray {
        return input.readNBytes(len)
    }

    override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int {
        return input.readNBytes(b, off, len)
    }

    override fun skip(n: Long): Long {
        return input.skip(n)
    }

    override fun skipNBytes(n: Long) {
        input.skipNBytes(n)
    }

    override fun available(): Int {
        return input.available()
    }

    override fun mark(readlimit: Int) {
        input.mark(readlimit)
    }

    override fun reset() {
        input.reset()
    }

    override fun markSupported(): Boolean {
        return input.markSupported()
    }

    override fun transferTo(out: OutputStream?): Long {
        return input.transferTo(out)
    }
}

class EndOfStream : RuntimeException()

class StreamAlreadyClosed : RuntimeException()
