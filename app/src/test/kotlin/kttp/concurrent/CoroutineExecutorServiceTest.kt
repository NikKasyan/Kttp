package kttp.concurrent

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kttp.http.HttpClient
import kttp.http.server.HttpServer
import kttp.http.protocol.HttpStatus
import kttp.http.server.onGet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.concurrent.thread

@Timeout(10)
class CoroutineExecutorServiceTest {

    private val executor = CoroutineExecutorService()
    private val server = HttpServer(executorService = executor)

    init {
        // Disable Logback logging
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off")
        thread {
            server.start()
        }
        server.onGet("/") {
            respond("Hello World")
        }
        server.waitUntilStarted()
    }

    @Test
    fun test1000ConcurrentConnections() {
        runBlocking {
            val jobs = List(1000) {
                async {
                    val response = HttpClient.get(server.getBaseUri())
                    assertEquals(HttpStatus.OK, response.statusLine.status)
                    assertEquals("Hello World", response.body.readAsString())
                }
            }
            jobs.awaitAll()
        }

    }

}