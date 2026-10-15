package io.skjaere

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.*
import io.skjaere.nntp.NntpMockResponse
import io.skjaere.nntp.StatMockRequest
import io.skjaere.nntp.YencBodyRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.TimeZone

/**
 * The commands the mock answers on its own when no mock is registered for them, as RFC 3977
 * does: STAT for an unknown article, QUIT, DATE and CAPABILITIES.
 */
class NntpBuiltInCommandsTest {

    // Opens one NNTP session, discards the welcome line and hands the session to [block]
    private suspend fun <T> withNntpSession(
        nntpPort: Int,
        block: (reader: BufferedReader, writer: PrintWriter) -> T
    ): T = withContext(Dispatchers.IO) {
        Socket("localhost", nntpPort).use { socket ->
            socket.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)
            reader.readLine() // Welcome message
            block(reader, writer)
        }
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    // Starts the application (and its NNTP server) with no mocks registered
    private suspend fun ApplicationTestBuilder.startWithoutMocks(): Int {
        val port = ServerSocket(0)
        application { module(port) }
        client.delete("/mocks")
        return port.localPort
    }

    @Test
    fun testStatForUnknownArticleAnswers430() {
        testApplication {
            val nntpPort = startWithoutMocks()

            val statusLine = withNntpSession(nntpPort) { reader, writer ->
                writer.println("STAT <unknown.article@example.com>")
                reader.readLine()
            }

            assertEquals("430", statusLine?.take(3), "STAT status line: $statusLine")
        }
    }

    @Test
    fun testStatMocksStillDecideTheAnswer() {
        testApplication {
            val nntpPort = startWithoutMocks()
            val client = jsonClient()
            val present = "<present@example.com>"
            val absent = "<absent@example.com>"
            val yencOnly = "<yenc.only@example.com>"

            listOf(present to true, absent to false).forEach { (articleId, exists) ->
                client.post("/mocks/stat") {
                    contentType(ContentType.Application.Json)
                    setBody(StatMockRequest(articleId = articleId, exists = exists))
                }
            }
            client.post("/mocks/yenc-body") {
                contentType(ContentType.Application.Json)
                setBody(YencBodyRequest(articleId = yencOnly, data = Base64.getEncoder().encodeToString("data".toByteArray())))
            }

            val statusLines = withNntpSession(nntpPort) { reader, writer ->
                listOf(present, absent, yencOnly).map { articleId ->
                    writer.println("STAT $articleId")
                    reader.readLine()
                }
            }

            assertEquals(listOf("223", "430", "223"), statusLines.map { it?.take(3) }, "STAT answers: $statusLines")
        }
    }

    @Test
    fun testQuitAnswers205AndClosesTheConnection() {
        testApplication {
            val nntpPort = startWithoutMocks()

            val (statusLine, afterQuit) = withNntpSession(nntpPort) { reader, writer ->
                writer.println("QUIT")
                val status = reader.readLine()
                // End of stream rather than a read timeout: the server closed the connection
                status to if (status?.startsWith("205") == true) reader.readLine() else "(not read)"
            }

            assertEquals("205", statusLine?.take(3), "QUIT status line: $statusLine")
            assertNull(afterQuit, "Connection still open after QUIT")
        }
    }

    @Test
    fun testDateAnswers111WithTheTimeInUtc() {
        // A zone far from UTC, so an answer in the JVM's local time can't pass for UTC
        val originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
        try {
            testApplication {
                val nntpPort = startWithoutMocks()

                val statusLine = withNntpSession(nntpPort) { reader, writer ->
                    writer.println("DATE")
                    reader.readLine()
                }

                assertTrue(statusLine?.matches(Regex("""111 \d{14}""")) == true, "DATE status line: $statusLine")
                val serverTime = LocalDateTime.parse(
                    statusLine!!.removePrefix("111 "),
                    DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                ).toInstant(ZoneOffset.UTC)
                val skew = Duration.between(serverTime, Instant.now()).abs()
                assertTrue(skew < Duration.ofMinutes(1), "DATE $serverTime is $skew away from now in UTC")
            }
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun testCapabilitiesAnswers101WithAList() {
        testApplication {
            val nntpPort = startWithoutMocks()

            val (statusLine, capabilities) = withNntpSession(nntpPort) { reader, writer ->
                writer.println("CAPABILITIES")
                val status = reader.readLine()
                val lines = if (status?.startsWith("101") == true) {
                    generateSequence { reader.readLine() }.takeWhile { it != "." }.toList()
                } else {
                    emptyList()
                }
                status to lines
            }

            assertEquals("101", statusLine?.take(3), "CAPABILITIES status line: $statusLine")
            assertEquals("VERSION 2", capabilities.firstOrNull(), "Capabilities: $capabilities")
            assertTrue("READER" in capabilities, "Capabilities: $capabilities")
        }
    }

    @Test
    fun testCommandMocksStillOverrideBuiltInAnswers() {
        testApplication {
            val nntpPort = startWithoutMocks()
            val client = jsonClient()

            listOf("DATE", "STAT").forEach { command ->
                client.post("/mocks") {
                    contentType(ContentType.Application.Json)
                    setBody(NntpMockResponse(command = command, textResponse = "mocked $command"))
                }
            }

            val statusLines = withNntpSession(nntpPort) { reader, writer ->
                listOf("DATE", "STAT <no.stat.mock@example.com>").map { command ->
                    writer.println(command)
                    val status = reader.readLine()
                    generateSequence { reader.readLine() }.takeWhile { it != "." }.toList() // Mocked body
                    status
                }
            }

            assertEquals(listOf("220", "220"), statusLines.map { it?.take(3) }, "Answers: $statusLines")
        }
    }
}
