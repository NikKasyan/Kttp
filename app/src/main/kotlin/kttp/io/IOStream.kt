package kttp.io

import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset


//Todo: Have to rewrite using bytes instead of strings and not using UTF-8 as standard charset
// https://www.rfc-editor.org/rfc/rfc7230#page-19
// Todo: Might need to add opt-out from automatic flushing
class IOStream(private val inputStream: InputStream,
               private val outputStream: OutputStream,
               private val charset: Charset = Charsets.US_ASCII,
               maxLineLengthInBytes: Int = 8192) : DefaultInputStream(), AutoCloseable {

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

    fun writeFromStream(inputStream: InputStream) {
        if(isClosed)
            throw StreamAlreadyClosed()
        inputStream.transferTo(output)
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


    fun readBytesAsString(contentLength: Int, charset: Charset = this.charset): String {
        return String(readNBytes(contentLength), charset)
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
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if(isClosed)
            throw StreamAlreadyClosed()
        return input.read(b, off, len)
    }

    override fun available(): Int {
        if(isClosed)
            return 0
        return input.available()
    }

}

class EndOfStream : RuntimeException("End of Stream")

class StreamAlreadyClosed : RuntimeException("Stream already closed")
