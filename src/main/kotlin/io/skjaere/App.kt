package io.skjaere

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import io.skjaere.nntp.NntpMockResponse
import io.skjaere.nntp.NntpMockResponses
import io.skjaere.nntp.RawYencBodyRequest
import io.skjaere.nntp.StatMockRequest
import io.skjaere.nntp.YencBodyRequest
import io.skjaere.yenc.YencEncoder
import java.util.Base64

val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

fun Application.module(nntpPort: ServerSocket) {
    install(ContentNegotiation) {
        json()
    }

    launch {
        startNntpServer(nntpPort)
    }

    routing {
        get("/") {
            call.respondText("Hello from Ktor!")
        }

        post("/mocks") {
            val mockResponse = call.receive<NntpMockResponse>()
            NntpMockResponses.addMockResponse(mockResponse)
            call.respondText(
                "Mock response for command '${mockResponse.command}' added/updated.",
                status = HttpStatusCode.OK
            )
        }

        get("/mocks") {
            call.respond(
                NntpMockResponses.getAllMockResponses()
                    .mapValues { it.value.textResponse ?: it.value.binaryResponse ?: "" })
        }

        delete("/mocks") {
            NntpMockResponses.clearMockResponses()
            call.respondText("All mock responses cleared.", status = HttpStatusCode.OK)
        }

        get("/stats") {
            call.respond(NntpMockResponses.getAllCommandCalls())
        }

        delete("/stats") {
            NntpMockResponses.clearCommandCalls()
            call.respondText("All command call statistics cleared.", status = HttpStatusCode.OK)
        }

        post("/mocks/yenc-body") {
            val request = call.receive<YencBodyRequest>()
            val rawData = Base64.getDecoder().decode(request.data)
            val filename = request.filename
                ?: request.articleId
                    .removePrefix("<").removeSuffix(">")
                    .replace("@", "_")
            val yencArticle = YencEncoder.encodeSinglePart(rawData, filename)
            NntpMockResponses.addYencBodyMock(request.articleId, yencArticle.data)
            call.respondText(
                "Yenc body mock for article '${request.articleId}' added.",
                status = HttpStatusCode.OK
            )
        }

        post("/mocks/yenc-body/raw") {
            val request = call.receive<RawYencBodyRequest>()
            val rawYencData = Base64.getDecoder().decode(request.data)
            NntpMockResponses.addYencBodyMock(request.articleId, rawYencData)
            call.respondText(
                "Raw yenc body mock for article '${request.articleId}' added.",
                status = HttpStatusCode.OK
            )
        }

        delete("/mocks/yenc-body") {
            NntpMockResponses.clearYencBodyMocks()
            call.respondText("All yenc body mocks cleared.", status = HttpStatusCode.OK)
        }

        post("/mocks/stat") {
            val request = call.receive<StatMockRequest>()
            NntpMockResponses.addStatMock(request.articleId, request.exists)
            call.respondText(
                "Stat mock for article '${request.articleId}' added (exists=${request.exists}).",
                status = HttpStatusCode.OK
            )
        }

        delete("/mocks/stat") {
            NntpMockResponses.clearStatMocks()
            call.respondText("All stat mocks cleared.", status = HttpStatusCode.OK)
        }
    }
}

suspend fun startNntpServer(socket: ServerSocket): Int = coroutineScope {
    val nntpPort = socket.localPort
    println("NNTP Server starting on port $nntpPort...")

    launch(Dispatchers.IO) {
        while (isActive) {
            val clientSocket = socket.accept()
            println("NNTP Client connected from ${clientSocket.inetAddress.hostAddress}")

            launch(Dispatchers.IO) {
                handleNntpClient(clientSocket)
            }
        }
    }
    nntpPort
}

fun handleNntpClient(clientSocket: Socket) {
    clientSocket.use { socket ->
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = PrintWriter(socket.getOutputStream(), true)
        val outputStream = socket.getOutputStream()

        // Send initial welcome message
        println("SERVER SEND: 200 service available")
        writer.println("200 service available")

        var line: String? = null
        try {
            while (socket.isConnected && reader.readLine().also { line = it } != null) {
                println("NNTP Client command: $line")
                val parts = line!!.split(" ", limit = 2)
                val command = parts[0].uppercase()
                val argument = if (parts.size > 1) parts[1] else null

                NntpMockResponses.incrementCommandCall(command)

                // Check for STAT mocks (single-line response)
                if (command == "STAT" && argument != null) {
                    val statMock = NntpMockResponses.getStatMock(argument)
                    if (statMock != null) {
                        if (statMock) {
                            val responseLine = "223 0 $argument article exists"
                            println("SERVER SEND: $responseLine")
                            writer.println(responseLine)
                        } else {
                            val responseLine = "430 No Such Article Found"
                            println("SERVER SEND: $responseLine")
                            writer.println(responseLine)
                        }
                        continue
                    }
                    // Fall through: if a yenc body mock exists for this article, treat as found
                    if (NntpMockResponses.getYencBodyMock(argument) != null) {
                        val responseLine = "223 0 $argument article exists"
                        println("SERVER SEND: $responseLine")
                        writer.println(responseLine)
                        continue
                    }
                }

                // Check for article-keyed yenc body mock first
                if (command == "BODY" && argument != null) {
                    val yencData = NntpMockResponses.getYencBodyMock(argument)
                    if (yencData != null) {
                        val headerLine = "222 0 $argument body follows"
                        println("SERVER SEND: $headerLine")
                        writer.println(headerLine)
                        writer.flush()

                        // Write raw yenc bytes directly to OutputStream to avoid charset corruption
                        outputStream.write(yencData)
                        outputStream.write("\r\n.\r\n".toByteArray())
                        outputStream.flush()
                        continue
                    }
                }

                val mockResponse = NntpMockResponses.getMockResponse(command)

                if (mockResponse != null) {
                    if (mockResponse.binaryResponse != null) {
                        val firstLine = "222 Body follows"
                        println("SERVER SEND: $firstLine")
                        writer.println(firstLine)

                        val contentTypeLine = "Content-Type: application/octet-stream"
                        println("SERVER SEND: $contentTypeLine")
                        writer.println(contentTypeLine)

                        val transferEncodingLine = "Content-Transfer-Encoding: base64"
                        println("SERVER SEND: $transferEncodingLine")
                        writer.println(transferEncodingLine)

                        println("SERVER SEND: Blank line")
                        writer.println() // Blank line separating header from body

                        val base64Content = mockResponse.binaryResponse
                        base64Content.chunked(76).forEach { lineChunk ->
                            println("SERVER SEND BODY: $lineChunk")
                            writer.println(lineChunk)
                        }
                        println("SERVER SEND: .")
                        writer.println(".")
                    } else if (mockResponse.textResponse != null) {
                        val firstLine = "220 Article retrieved, body follows"
                        println("SERVER SEND: $firstLine")
                        writer.println(firstLine)

                        val formattedResponse = mockResponse.textResponse.replace("\\r\\n", "\r\n")
                        println("SERVER SEND BODY: $formattedResponse")
                        writer.println(formattedResponse)

                        println("SERVER SEND: .")
                        writer.println(".")
                    } else {
                        val errorLine = "500 No response content configured"
                        println("SERVER SEND: $errorLine")
                        writer.println(errorLine)
                    }
                } else {
                    val errorLine = "500 Command not recognized"
                    println("SERVER SEND: $errorLine")
                    writer.println(errorLine)
                }
            }
        } catch (e: Exception) {
            println("Error handling NNTP client ${socket.inetAddress.hostAddress}: ${e.message}")
        } finally {
            println("NNTP Client disconnected from ${socket.inetAddress.hostAddress}")
        }
    }
}

fun main() {
    val nntpSocket = ServerSocket(1119)
    appScope.launch { startNntpServer(nntpSocket) }
    embeddedServer(Netty, port = 8081, host = "0.0.0.0") {
        module(nntpSocket)
    }.start(wait = true)
}
