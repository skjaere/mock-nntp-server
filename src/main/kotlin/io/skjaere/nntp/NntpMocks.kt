package io.skjaere.nntp

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

// Data class to represent a mock NNTP response
@Serializable
data class NntpMockResponse(
    val command: String,
    val textResponse: String? = null, // Renamed 'response' to 'textResponse' for clarity
    val binaryResponse: String? = null // Base64 encoded binary content
)

@Serializable
data class YencBodyRequest(
    val articleId: String,
    val data: String, // Base64 encoded binary data
    val filename: String? = null
)

@Serializable
data class RawYencBodyRequest(
    val articleId: String,
    val data: String // Base64 encoded pre-built yenc data (=ybegin ... =yend)
)

@Serializable
data class StatMockRequest(
    val articleId: String,
    val exists: Boolean
)

// Singleton object to manage mock NNTP responses and track command calls
object NntpMockResponses {
    private val mocks: ConcurrentHashMap<String, NntpMockResponse> = ConcurrentHashMap()
    private val commandCalls: ConcurrentHashMap<String, Int> = ConcurrentHashMap()
    private val yencBodyMocks: ConcurrentHashMap<String, ByteArray> = ConcurrentHashMap()
    private val statMocks: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

    fun addMockResponse(mockResponse: NntpMockResponse) {
        require(mockResponse.textResponse != null || mockResponse.binaryResponse != null) {
            "Either textResponse or binaryResponse must be provided"
        }
        mocks[mockResponse.command.uppercase()] = mockResponse
    }

    fun getMockResponse(command: String): NntpMockResponse? {
        return mocks[command.uppercase()]
    }

    fun getAllMockResponses(): Map<String, NntpMockResponse> {
        return mocks.toMap()
    }

    fun clearMockResponses() {
        mocks.clear()
        commandCalls.clear()
        yencBodyMocks.clear()
        statMocks.clear()
    }

    fun addYencBodyMock(articleId: String, encodedData: ByteArray) {
        yencBodyMocks[articleId] = encodedData
    }

    fun getYencBodyMock(articleId: String): ByteArray? {
        return yencBodyMocks[articleId]
    }

    fun clearYencBodyMocks() {
        yencBodyMocks.clear()
    }

    fun addStatMock(articleId: String, exists: Boolean) {
        statMocks[articleId] = exists
    }

    fun getStatMock(articleId: String): Boolean? {
        return statMocks[articleId]
    }

    fun clearStatMocks() {
        statMocks.clear()
    }

    fun incrementCommandCall(command: String) {
        commandCalls.compute(command.uppercase()) { _, count -> (count ?: 0) + 1 }
    }

    fun getCommandCallCount(command: String): Int {
        return commandCalls[command.uppercase()] ?: 0
    }

    fun getAllCommandCalls(): Map<String, Int> {
        return commandCalls.toMap()
    }

    fun clearCommandCalls() {
        commandCalls.clear()
    }
}