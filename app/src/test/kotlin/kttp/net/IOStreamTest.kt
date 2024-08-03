package kttp.net

import kttp.mock.SimpleTestServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals

@Timeout(5, unit = TimeUnit.SECONDS)
class IOStreamTest {

    private val defaultPort = 10000
    private lateinit var server: SimpleTestServer
    private lateinit var client: ClientConnection

    @BeforeEach
    fun setup() {
        this.server = SimpleTestServer(defaultPort, 1)
        thread { this.server.acceptSocket() }
        this.client = ClientConnection(Socket(InetAddress.getLocalHost(), defaultPort))
    }

    @Test
    fun serverWrites_clientReceives() {
        val message = "Hello"
        server.write(message)
        server.write(message)
        var receivedMessage = client.io.readLine()

        Assertions.assertEquals(message, receivedMessage)
        receivedMessage = client.io.readLine()

        Assertions.assertEquals(message, receivedMessage)
    }

    @Test
    fun serverWritesThrice_clientReceivesLineNoLineAndLine() {
        val message = "Hello"
        server.write(message)
        server.write(message)
        server.write(message)
        var receivedMessage = client.io.readLine()

        Assertions.assertEquals(message, receivedMessage)

        receivedMessage = client.io.readBytesAsString(message.length + 2)

        Assertions.assertEquals(message+"\r\n", receivedMessage)
        receivedMessage = client.io.readLine()

        Assertions.assertEquals(message, receivedMessage)
    }

    @Test
    fun clientWritesTwoLines_ServerReceivesTwo() {
        val message = "Hello"
        client.io.write(message + "\r\n")
        client.io.write(message + "\r\n")
        var receivedMessage = server.readLine()
        Assertions.assertEquals(message, receivedMessage)
        receivedMessage = server.readLine()
        Assertions.assertEquals(message, receivedMessage)
    }

    @Test
    fun serverWrites_clientReads_serverStops_shouldThrowEndOfStream() {
        thread { Thread.sleep(500); server.stop() }
        assert(client.io.readLine().isEmpty())
    }

    @Test
    fun serverWritesToClosedStream_shouldThrowStreamAlreadyClosed() {
        client.close()
        server.write("Test")
        assertThrows<StreamAlreadyClosed> { client.io.readLine() }

    }

    @Test()
    fun serverStartsReadToClosedStream_shouldThrowEndOfStream() {
        thread {
            Thread.sleep(500)
            client.close()
        }

        assert(server.readLine().isEmpty())
    }

    @Test
    fun createIoStreamWithTimeout_ShouldThrowEndOfStreamAfterTimeout() {

        assertThrows<EndOfStream> { server.readLine() }
    }

    @Test
    fun readBytesMultipleTimes() {
        val inputString = "Das ist ein Test, welcher funktioniert!"
        val inputStream = inputString.byteInputStream()
        val ioStream = IOStream(inputStream, OutputStream.nullOutputStream())
        val buffer = ByteArray(5)
        var readBytes = 0
        var totalBytes = 0
        while (readBytes != -1) {
            readBytes = ioStream.read(buffer, 0, 5)
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
        val ioStream = IOStream(input, OutputStream.nullOutputStream())

        val lines = inputString.split("\r\n")
        var string = ioStream.readLine()
        assertEquals(lines[0], string)
        string = ioStream.readAllBytes().toString(Charsets.UTF_8)
        assertEquals(inputString.substring(inputString.indexOf('\n') + 1), string)
    }

    @AfterEach
    fun teardown() {
        server.stop()
        client.close()
    }
}