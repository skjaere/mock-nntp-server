package io.skjaere

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.example.nntp.NntpMockResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.Base64

import io.ktor.server.testing.* // Import for testApplication
import kotlinx.serialization.json.Json // Explicitly import Json
import io.ktor.client.call.body // Explicitly import body
import io.ktor.serialization.kotlinx.json.json // Explicitly import json for ContentNegotiation
import org.example.module
import java.net.ServerSocket
import java.net.SocketTimeoutException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NntpIntegrationTest {

    // Removed class-level NNTP_PORT

    // Helper function to send NNTP commands and read responses
    private suspend fun sendNntpCommand(nntpPort: Int, command: String): String = withContext(Dispatchers.IO) {
        var response = ""
        Socket("localhost", nntpPort).use { socket -> // Use passed nntpPort
            socket.soTimeout = 60000 // Set a read timeout to 60 seconds
            PrintWriter(socket.getOutputStream(), true).use { writer ->
                BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                    // Read and discard initial welcome message
                    val welcomeMessage = reader.readLine()
                    println("NNTP Client received welcome: $welcomeMessage")

                    writer.println(command)
                    // Read response until a line containing only '.' (NNTP end of multiline response)
                    // or until an error code (5xx) or timeout
                    var line: String? = null
                    while (true) {
                        try {
                            line = reader.readLine()
                            if (line == null) break // Connection closed
                            response += "$line\n"
                            if (line == ".") break // End of multiline
                            if (line.startsWith("5") && line.length >= 3 && line[3] == ' ') break // NNTP error response (e.g., 500 )
                        } catch (e: SocketTimeoutException) {
                            println("NNTP Read Timeout for command: $command")
                            break // Exit loop on timeout
                        }
                    }
                }
            }
        }
        response.trim()
    }

    // Helper to send NNTP command and receive raw bytes for binary content
    private suspend fun sendNntpCommandForBinary(nntpPort: Int, command: String): ByteArray = withContext(Dispatchers.IO) {
        Socket("localhost", nntpPort).use { socket -> // Use passed nntpPort
            socket.soTimeout = 60000 // Set a read timeout to 60 seconds
            PrintWriter(socket.getOutputStream(), true).use { writer ->
                BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                    // Read and discard initial welcome message
                    reader.readLine() // Welcome message

                    writer.println(command)
                    // Read the first header line (e.g., 222 Body follows)
                    val firstHeaderLine = reader.readLine()
                    println("NNTP Client received first header: $firstHeaderLine")
                    if (firstHeaderLine?.startsWith("222") != true) {
                        throw IllegalStateException("Expected 222 header for binary response, got: $firstHeaderLine")
                    }

                    // Read subsequent header lines until a blank line is encountered
                    var headerLine: String?
                    do {
                        headerLine = reader.readLine()
                        println("NNTP Client received header: $headerLine")
                    } while (headerLine != null && headerLine.isNotBlank()) // Loop until blank line or EOF

                    if (headerLine == null) {
                        throw IllegalStateException("Unexpected end of stream while reading headers for binary body")
                    }

                    // Read Base64 encoded body lines until '.' on a line by itself
                    val base64ContentBuilder = StringBuilder()
                    var line: String?
                    while (true) {
                        line = reader.readLine()
                        if (line == null) throw IllegalStateException("Unexpected end of stream while reading binary body")
                        if (line == ".") break // End of body
                        base64ContentBuilder.append(line)
                    }
                    return@withContext Base64.getDecoder().decode(base64ContentBuilder.toString())
                }
            }
        }
    }


    @Test
    fun testKtorRootEndpoint() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { // Configure Json explicitly
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }
        val nntpPortDeferred = CompletableDeferred<Int>()
        val port = ServerSocket(0)
        application { module(port) }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello from Ktor!", response.bodyAsText())
    }

    @Test
    fun testAddAndRetrieveMockResponses() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { isLenient = true; ignoreUnknownKeys = true })
            }
        }
        val port = ServerSocket(0)
        application { module(port) }

        val testCommand = "TESTCOMMAND"
        val testTextResponse = "200 Test text response for $testCommand"

        client.delete("/mocks")

        val addResponse = client.post("/mocks") {
            contentType(ContentType.Application.Json)
            setBody(NntpMockResponse(command = testCommand, textResponse = testTextResponse))
        }
        assertEquals(HttpStatusCode.OK, addResponse.status)

        val mocksResponse = client.get("/mocks")
        val rawJson = mocksResponse.bodyAsText()
        println("Raw /mocks response: $rawJson")
        val mocks: Map<String, String> = Json { isLenient = true; ignoreUnknownKeys = true }.decodeFromString(rawJson)
        assertTrue(mocks.containsKey(testCommand.uppercase()))
        assertEquals(testTextResponse, mocks[testCommand.uppercase()])
    }

    @Test
    fun testNntpInteractionWithMockedResponse() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { isLenient = true; ignoreUnknownKeys = true })
            }
        }
        val port = ServerSocket(0)
        application { module(port) }

        val testCommand = "ARTICLE"
        val testMessageId = "<test.message.id@example.com>"
        val testMockResponseContent = "220 Article retrieved, body follows (text/plain)\\r\\nThis is a mocked article content for $testMessageId.\\r\\n."

        client.delete("/mocks")
        client.delete("/stats")

        client.post("/mocks") {
            contentType(ContentType.Application.Json)
            setBody(NntpMockResponse(command = testCommand, textResponse = testMockResponseContent))
        }

        val nntpResponse = sendNntpCommand(port.localPort, "$testCommand $testMessageId") // Pass nntpPort
        println("NNTP Response: \n$nntpResponse")
        assertTrue(nntpResponse.startsWith("220"))
        assertTrue(nntpResponse.contains("This is a mocked article content"))

        val statsResponse = client.get("/stats")
        val rawStatsJson = statsResponse.bodyAsText()
        println("Raw /stats response (first call): $rawStatsJson")
        val stats: Map<String, Int> = Json { isLenient = true; ignoreUnknownKeys = true }.decodeFromString(rawStatsJson)
        assertTrue(stats.containsKey(testCommand.uppercase()))
        assertEquals(1, stats[testCommand.uppercase()])

        sendNntpCommand(port.localPort, "$testCommand $testMessageId") // Pass nntpPort
        val statsAfterSecondCallResponse = client.get("/stats")
        val rawStatsAfterSecondCallJson = statsAfterSecondCallResponse.bodyAsText()
        println("Raw /stats response (second call): $rawStatsAfterSecondCallJson")
        val statsAfterSecondCall: Map<String, Int> = Json { isLenient = true; ignoreUnknownKeys = true }.decodeFromString(rawStatsAfterSecondCallJson)
        assertEquals(2, statsAfterSecondCall[testCommand.uppercase()])
    }

    @Test
    fun testNntpBinaryBodyResponse() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { isLenient = true; ignoreUnknownKeys = true })
            }
        }
        val port = ServerSocket(0)
        application { module(port) }

        val testCommand = "BODY"
        val testMessageId = "<binary.message.id@example.com>"
        val originalBinaryContent = "This is some test binary content with some special characters: !@#$%^&*()_+"
        val base64BinaryContent = Base64.getEncoder().encodeToString(originalBinaryContent.toByteArray(Charsets.UTF_8))

        client.delete("/mocks")
        client.delete("/stats")

        client.post("/mocks") {
            contentType(ContentType.Application.Json)
            setBody(NntpMockResponse(command = testCommand, binaryResponse = base64BinaryContent))
        }

        // Send NNTP BODY command and get raw bytes
        val receivedBytes = sendNntpCommandForBinary(port.localPort, "$testCommand $testMessageId") // Pass nntpPort
        val receivedContent = receivedBytes.toString(Charsets.UTF_8)
        println("NNTP Client received binary content: $receivedContent")

        assertEquals(originalBinaryContent, receivedContent)

        // Verify command call count
        val stats: Map<String, Int> = client.get("/stats").body<Map<String, Int>>()
        assertTrue(stats.containsKey(testCommand.uppercase()))
        assertEquals(1, stats[testCommand.uppercase()])
    }


    @Test
    fun testClearStats() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { isLenient = true; ignoreUnknownKeys = true })
            }
        }
        val port = ServerSocket(0)
        application { module(port) }

        val testCommand = "GROUP"
        val testTextResponse = "211 5 1 5 test.group"

        client.delete("/mocks")
        client.delete("/stats")

        client.post("/mocks") {
            contentType(ContentType.Application.Json)
            setBody(NntpMockResponse(command = testCommand, textResponse = testTextResponse))
        }

        sendNntpCommand(port.localPort, "$testCommand some.group") // Pass nntpPort
        val statsBeforeClearResponse = client.get("/stats")
        val rawStatsBeforeClearJson = statsBeforeClearResponse.bodyAsText()
        println("Raw /stats response (before clear): $rawStatsBeforeClearJson")
        val statsBeforeClear: Map<String, Int> = Json { isLenient = true; ignoreUnknownKeys = true }.decodeFromString(rawStatsBeforeClearJson)
        assertTrue(statsBeforeClear.containsKey(testCommand.uppercase()))
        assertEquals(1, statsBeforeClear[testCommand.uppercase()])

        val clearStatsResponse = client.delete("/stats")
        assertEquals(HttpStatusCode.OK, clearStatsResponse.status)

        val statsAfterClearResponse = client.get("/stats")
        val rawStatsAfterClearJson = statsAfterClearResponse.bodyAsText()
        println("Raw /stats response (after clear): $rawStatsAfterClearJson")
        val statsAfterClear: Map<String, Int> = Json { isLenient = true; ignoreUnknownKeys = true }.decodeFromString(rawStatsAfterClearJson)
        assertTrue(statsAfterClear.isEmpty())
    }
}
