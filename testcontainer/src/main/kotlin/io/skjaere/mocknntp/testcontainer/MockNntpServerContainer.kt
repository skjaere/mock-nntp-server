package io.skjaere.mocknntp.testcontainer

import io.skjaere.mocknntp.testcontainer.client.MockNntpClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

class MockNntpServerContainer(image: String = "mock-nntp-server:latest") :
    GenericContainer<MockNntpServerContainer>(image) {

    companion object {
        const val HTTP_PORT = 8081
        const val NNTP_PORT = 1119
    }

    init {
        withExposedPorts(HTTP_PORT, NNTP_PORT)
        waitingFor(Wait.forHttp("/").forPort(HTTP_PORT))
    }

    val httpPort: Int
        get() = getMappedPort(HTTP_PORT)

    val httpUrl: String
        get() = "http://$host:$httpPort"

    val client: MockNntpClient by lazy { MockNntpClient(httpUrl) }

    val nntpPort: Int
        get() = getMappedPort(NNTP_PORT)

    val nntpHost: String
        get() = host
}
