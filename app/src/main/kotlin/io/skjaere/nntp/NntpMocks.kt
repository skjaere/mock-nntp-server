package org.example.nntp

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

// Data class to represent a mock NNTP response
@Serializable
data class NntpMockResponse(
    val command: String,
    val textResponse: String? = null, // Renamed 'response' to 'textResponse' for clarity
    val binaryResponse: String? = null // Base64 encoded binary content
)

// Singleton object to manage mock NNTP responses and track command calls
object NntpMockResponses {
    private val mocks: ConcurrentHashMap<String, NntpMockResponse> = ConcurrentHashMap()
    private val commandCalls: ConcurrentHashMap<String, Int> = ConcurrentHashMap()

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
        commandCalls.clear() // Also clear command calls when clearing mocks
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