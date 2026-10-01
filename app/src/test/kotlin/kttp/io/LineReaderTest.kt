package kttp.io

import kttp.io.LineReader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayInputStream
import java.io.SequenceInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Timeout(5)
class LineReaderTest {


    @Test
    fun readsFirstThreeBytes() {
        val inputString = "Das ist ein Test"
        val input = ByteArrayInputStream(inputString.toByteArray())
        val lineReader = LineReader(input)

        val string = lineReader.readBytesAsString(3)

        assertEquals(inputString.substring(0, 3), string)


    }

    @Test
    fun canReadNewLine() {
        val inputString = "Das ist ein Test\r\nmit neuer Zeile"
        val input = ByteArrayInputStream(inputString.toByteArray())
        val lineReader = LineReader(input)

        val string = lineReader.readLine()

        assertEquals(inputString.substring(0, inputString.indexOf('\r')), string)


    }

    @Test
    fun canReadNewLineAndlast15Bytes() {
        val inputString = "Das ist ein Test\r\nmit neuer Zeile\r\nund noch eine"
        val input = ByteArrayInputStream(inputString.toByteArray())

        val lines = inputString.split("\r\n")

        val lineReader = LineReader(input)

        var string = lineReader.readLine()
        assertEquals(lines[0], string)

        string = lineReader.readBytesAsString(15)
        assertEquals(lines[1], string)


    }

    @Test
    fun canReadNewLineThen15BytesAndThenNewLine() {
        val inputString = "Das ist ein Test\r\nmit neuer Zeile und noch eine\r\n"
        val input = ByteArrayInputStream(inputString.toByteArray())
        val lineReader = LineReader(input)

        val lines = inputString.split("\r\n")
        var string = lineReader.readLine()
        var pos = inputString.indexOf('\n')
        assertEquals(lines[0], string)

        string = lineReader.readBytesAsString(15)
        assertEquals("mit neuer Zeile", string)

        string = lineReader.readLine()
        assertEquals(" und noch eine", string)


    }

    @Test
    fun readBytesMultipleTimes() {
        val inputString = "Das ist ein Test, welcher funktioniert!"
        val inputStream = inputString.byteInputStream()
        val lineReader = LineReader(inputStream)
        val buffer = ByteArray(5)
        var readBytes = 0
        var totalBytes = 0
        while (readBytes != -1) {
            readBytes = lineReader.read(buffer, 0, 5)
            if (readBytes != -1) {
                assertEquals(inputString.substring(totalBytes, totalBytes + readBytes), String(buffer, 0, readBytes))
                totalBytes += readBytes
            }
        }
        assertEquals(inputString.length, totalBytes)
    }

    @Test
    fun readLineThenReadRest(){
        val inputString = "Das ist ein Test\r\nmit neuer Zeile und noch eine\r\n"
        val input = ByteArrayInputStream(inputString.toByteArray())
        val lineReader = LineReader(input)

        val lines = inputString.split("\r\n")
        var string = lineReader.readLine()
        assertEquals(lines[0], string)
        string = lineReader.readAllBytes().toString(Charsets.UTF_8)
        assertEquals(inputString.substring(inputString.indexOf('\n') + 1), string)
    }

    @Test
    fun crlfSplitAcrossTwoReads_isNotPartOfTheLine() {
        // SequenceInputStream returns the end of the first stream and the start of the second in different reads
        val input = SequenceInputStream("GET / HTTP/1.1\r".byteInputStream(), "\nHost: localhost\r\n".byteInputStream())
        val lineReader = LineReader(input)

        assertEquals("GET / HTTP/1.1", lineReader.readLine())
        assertEquals("Host: localhost", lineReader.readLine())
    }

    @Test
    fun readLineAtEndOfStream_returnsNull() {
        val lineReader = LineReader("Das ist ein Test\r\n".byteInputStream())

        assertEquals("Das ist ein Test", lineReader.readLine())
        assertNull(lineReader.readLine())
    }

    @Test
    fun emptyLine_isNotEndOfStream() {
        val lineReader = LineReader("\r\n".byteInputStream())

        assertEquals("", lineReader.readLine())
        assertNull(lineReader.readLine())
    }
}