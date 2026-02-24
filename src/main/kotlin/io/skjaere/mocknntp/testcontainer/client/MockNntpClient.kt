package io.skjaere.mocknntp.testcontainer.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.skjaere.compressionutils.generation.ContainerType
import io.skjaere.mocknntp.testcontainer.TestArchiveHelper
import kotlinx.serialization.Serializable
import java.util.Base64
import java.util.UUID

data class NzbFileSpec(val filename: String, val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NzbFileSpec) return false
        return filename == other.filename && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * filename.hashCode() + data.contentHashCode()
}

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

    suspend fun addRawYencBodyExpectation(articleId: String, rawYencData: ByteArray) {
        val requestBody = RawYencBodyMockRequest(
            articleId = articleId,
            data = Base64.getEncoder().encodeToString(rawYencData)
        )
        httpClient.post("$baseUrl/mocks/yenc-body/raw") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
    }

    suspend fun clearYencBodyExpectations() {
        httpClient.delete("$baseUrl/mocks/yenc-body")
    }

    suspend fun addStatExpectation(articleId: String, exists: Boolean) {
        val requestBody = StatMockRequestDto(articleId = articleId, exists = exists)
        httpClient.post("$baseUrl/mocks/stat") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
    }

    suspend fun clearStatExpectations() {
        httpClient.delete("$baseUrl/mocks/stat")
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

    suspend fun prepareNzb(
        files: List<NzbFileSpec>,
        segmentSize: Int = 750_000,
        group: String = "alt.binaries.test",
        poster: String = "test@test.com"
    ): String {
        val nzbFiles = StringBuilder()
        for (spec in files) {
            val chunks = spec.data.toList().chunked(segmentSize).map { it.toByteArray() }
            val segments = StringBuilder()
            for ((index, chunk) in chunks.withIndex()) {
                val uuid = UUID.randomUUID().toString()
                val articleId = "$uuid@mock-nntp"
                addYencBodyExpectation(
                    articleId = "<$articleId>",
                    data = chunk,
                    filename = spec.filename
                )
                segments.append(
                    """      <segment bytes="${chunk.size}" number="${index + 1}">$articleId</segment>"""
                )
                segments.append("\n")
            }
            val epochSeconds = System.currentTimeMillis() / 1000
            nzbFiles.append("""  <file poster="$poster" date="$epochSeconds" subject="${spec.filename} (1/${chunks.size})">""")
            nzbFiles.append("\n")
            nzbFiles.append("    <groups>\n      <group>$group</group>\n    </groups>\n")
            nzbFiles.append("    <segments>\n")
            nzbFiles.append(segments)
            nzbFiles.append("    </segments>\n")
            nzbFiles.append("  </file>\n")
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE nzb PUBLIC "-//newzBin//DTD NZB 1.1//EN" "http://www.newzbin.com/DTD/nzb/nzb-1.1.dtd">
<nzb xmlns="http://www.newzbin.com/DTD/2003/nzb">
$nzbFiles</nzb>"""
    }

    suspend fun prepareArchiveNzb(
        fileContents: Map<String, ByteArray>,
        containerType: ContainerType,
        numberOfVolumes: Int = 1,
        segmentSize: Int = 750_000,
        group: String = "alt.binaries.test"
    ): String {
        val volumes = TestArchiveHelper.createArchive(fileContents, containerType, numberOfVolumes)
        val nzbFileSpecs = volumes.map { NzbFileSpec(it.filename, it.data) }
        return prepareNzb(nzbFileSpecs, segmentSize, group)
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

    @Serializable
    private data class RawYencBodyMockRequest(
        val articleId: String,
        val data: String
    )

    @Serializable
    private data class StatMockRequestDto(
        val articleId: String,
        val exists: Boolean
    )
}
