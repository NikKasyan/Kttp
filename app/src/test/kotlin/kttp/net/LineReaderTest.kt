package kttp.net

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals

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
        val inputString = "Das ist ein Test\nmit neuer Zeile"
        val input = ByteArrayInputStream(inputString.toByteArray())
        val lineReader = LineReader(input)

        val string = lineReader.readLine()

        assertEquals(inputString.substring(0, inputString.indexOf('\n')), string)


    }

    @Test
    fun canReadNewLineAndlast15Bytes() {
        val inputString = "Das ist ein Test\nmit neuer Zeile\nund noch eine"
        val input = ByteArrayInputStream(inputString.toByteArray())
        val lineReader = LineReader(input)

        var string = lineReader.readLine()
        var pos = inputString.indexOf('\n')
        assertEquals(inputString.substring(0, pos), string)

        string = lineReader.readBytesAsString(15)
        assertEquals(inputString.substring(pos+1), string)



    }

    @Test
    fun canReadNewLineThen15BytesAndThenNewLine() {
        val inputString = "Das ist ein Test\nmit neuer Zeileund noch eine\n"
        val input = ByteArrayInputStream(inputString.toByteArray())
        val lineReader = LineReader(input)

        var string = lineReader.readLine()
        var pos = inputString.indexOf('\n')
        assertEquals(inputString.substring(0, pos), string)

        string = lineReader.readBytesAsString(15)
        assertEquals(inputString.substring(pos+1, pos+16), string)

        string = lineReader.readLine()
        assertEquals("und noch eine", string)


    }
}