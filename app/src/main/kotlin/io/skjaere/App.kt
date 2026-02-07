package org.example

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
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
import org.example.nntp.NntpMockResponse
import org.example.nntp.NntpMockResponses

val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
// Ktor Application module function
fun Application.module(nntpPort: ServerSocket) { // Default value for standalone run
    // Launch NNTP server within the Ktor application's lifecycle
    launch {
        startNntpServer(nntpPort)
    }
}

// startNntpServer now returns the actual bound port
suspend fun startNntpServer(socket: ServerSocket): Int = coroutineScope {
    //val serverSocket = ServerSocket(port) // Dynamic NNTP port
    val nntpPort = socket.localPort
    println("NNTP Server starting on port $nntpPort...")

    launch(Dispatchers.IO) { // Launch accept loop in IO dispatcher
        while (isActive) {
            val clientSocket = socket.accept()
            println("NNTP Client connected from ${clientSocket.inetAddress.hostAddress}")

            // Launch a new coroutine for each client connection
            launch(Dispatchers.IO) {
                handleNntpClient(clientSocket)
            }
        }
    }
    nntpPort // Return the bound port
}

fun handleNntpClient(clientSocket: Socket) {
    clientSocket.use { socket ->
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = PrintWriter(socket.getOutputStream(), true)

        // Send initial welcome message
        println("SERVER SEND: 200 service available")
        writer.println("200 service available")

        var line: String? = null // Initialize line to null
        try {
            while (socket.isConnected && reader.readLine().also { line = it } != null) {
                println("NNTP Client command: $line")
                val parts = line!!.split(" ", limit = 2)
                val command = parts[0].uppercase()

                NntpMockResponses.incrementCommandCall(command)

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
    embeddedServer(Netty, port = 8081, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json()
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
        }
    }.start(wait = true) // Blocks the main thread
    appScope.launch { startNntpServer(ServerSocket(0)) }
}
