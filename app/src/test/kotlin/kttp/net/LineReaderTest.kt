package kttp.net

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals

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
}