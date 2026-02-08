package io.skjaere.mocknntp.testcontainer.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import java.util.Base64

class MockNntpClient(private val baseUrl: String) : AutoCloseable {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun addExpectation(block: ExpectationBuilder.() -> Unit) {
        val expectation = ExpectationBuilder().apply(block).build()

        if (expectation.yencBody != null) {
            addYencBodyExpectation(
                articleId = requireNotNull(expectation.argument) { "argument (articleId) is required for yenc body expectations" },
                data = expectation.yencBody,
                filename = expectation.yencFilename
            )
            return
        }

        val requestBody = MockRequest(
            command = expectation.command.name,
            textResponse = expectation.textResponse,
            binaryResponse = expectation.binaryResponse?.let {
                Base64.getEncoder().encodeToString(it)
            }
        )
        httpClient.post("$baseUrl/mocks") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
    }

    suspend fun addYencBodyExpectation(articleId: String, data: ByteArray, filename: String? = null) {
        val requestBody = YencBodyMockRequest(
            articleId = articleId,
            data = Base64.getEncoder().encodeToString(data),
            filename = filename
        )
        httpClient.post("$baseUrl/mocks/yenc-body") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
    }

    suspend fun clearYencBodyExpectations() {
        httpClient.delete("$baseUrl/mocks/yenc-body")
    }

    suspend fun clearExpectations() {
        httpClient.delete("$baseUrl/mocks")
    }

    suspend fun getStats(): Map<String, Int> {
        return httpClient.get("$baseUrl/stats").body()
    }

    suspend fun clearStats() {
        httpClient.delete("$baseUrl/stats")
    }

    override fun close() {
        httpClient.close()
    }

    @Serializable
    private data class MockRequest(
        val command: String,
        val textResponse: String? = null,
        val binaryResponse: String? = null
    )

    @Serializable
    private data class YencBodyMockRequest(
        val articleId: String,
        val data: String,
        val filename: String? = null
    )
}
