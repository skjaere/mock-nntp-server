# Mock NNTP Server

[![CI](https://github.com/skjaere/mock-nntp-server/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/skjaere/mock-nntp-server/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/skjaere/mock-nntp-server/branch/main/graph/badge.svg)](https://codecov.io/gh/skjaere/mock-nntp-server)
[![](https://jitpack.io/v/skjaere/mock-nntp-server.svg)](https://jitpack.io/#skjaere/mock-nntp-server)

A mock NNTP (Network News Transfer Protocol) server built with Kotlin and Ktor, designed to simulate NNTP responses and track command usage. Includes a Testcontainers wrapper and Kotlin client DSL for use in integration tests.

## Features

-   **Kotlin & Ktor:** Developed using Kotlin and the Ktor asynchronous framework.
-   **Configurable Mock Responses:** HTTP REST endpoints to add, retrieve, and clear mock responses for specific NNTP commands.
-   **Command Call Statistics:** HTTP REST endpoints to track and retrieve how many times each NNTP command has been called.
-   **Yenc Support:** Automatic yenc encoding via [rapidyenc](https://github.com/skjaere/rapidyenc-kotlin-wrapper), or provide pre-encoded yenc data for full control.
-   **Testcontainers Integration:** Built-in `MockNntpServerContainer` and client DSL for easy use in JUnit 5 integration tests.
-   **Docker:** Published to `ghcr.io/skjaere/mock-nntp-server`.

## Using in Tests

### Dependency

Add the dependency via [JitPack](https://jitpack.io/#skjaere/mock-nntp-server):

```kotlin
// build.gradle.kts
repositories {
    maven("https://jitpack.io")
}

dependencies {
    testImplementation("com.github.skjaere:mock-nntp-server:v0.1.0")
    testImplementation("org.testcontainers:testcontainers:2.0.3")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.3")
}
```

### MockNntpServerContainer

`MockNntpServerContainer` extends Testcontainers' `GenericContainer` and manages the mock NNTP server Docker image lifecycle.

```kotlin
import io.skjaere.mocknntp.testcontainer.MockNntpServerContainer

val container = MockNntpServerContainer() // defaults to "ghcr.io/skjaere/mock-nntp-server:latest"
container.start()

// Access mapped ports
val nntpHost = container.nntpHost   // container hostname
val nntpPort = container.nntpPort   // mapped NNTP port (1119 inside container)
val httpUrl  = container.httpUrl    // e.g. "http://localhost:32789"

// Get a pre-configured client
val client = container.client

// Clean up
container.stop()
```

You can also provide a custom image name:

```kotlin
val container = MockNntpServerContainer("my-registry/mock-nntp-server:v2")
```

### MockNntpClient

`MockNntpClient` is an HTTP client that communicates with the mock server's REST API. It is available directly via `container.client` or can be instantiated standalone:

```kotlin
import io.skjaere.mocknntp.testcontainer.client.MockNntpClient

val client = MockNntpClient("http://localhost:8081")

// Add expectations (see DSL section below)
client.addExpectation { /* ... */ }

// Add a yenc body expectation (server auto-encodes the raw bytes)
client.addYencBodyExpectation(
    articleId = "<file.part1@example.com>",
    data = fileBytes,
    filename = "archive.rar"
)

// Add a raw yenc body expectation (pre-encoded, for multipart or custom yenc formats)
client.addRawYencBodyExpectation(
    articleId = "<file.part2@example.com>",
    rawYencData = preBuiltYencBytes
)

// Retrieve command call statistics
val stats: Map<String, Int> = client.getStats()

// Clear state
client.clearExpectations()          // clears command-level mocks (also clears yenc mocks and stats)
client.clearYencBodyExpectations()  // clears only yenc body mocks
client.clearStats()                 // clears only statistics

client.close()
```

### Expectation DSL

The DSL provides a type-safe builder for configuring mock expectations via `addExpectation`.

#### Text Response

```kotlin
client.addExpectation {
    given {
        command = NntpCommand.ARTICLE
        argument = "<articleId>"  // optional
    }
    thenRespond {
        withTextResponse {
            status = 220  // default
            body = "220 Article follows\r\nSubject: Test\r\n\r\nBody content"
        }
    }
}
```

#### Binary Body Response (Base64)

```kotlin
client.addExpectation {
    given {
        command = NntpCommand.BODY
    }
    thenRespond {
        withBinaryBodyResponse {
            status = 222  // default
            body = fileBytes  // ByteArray, sent as base64 over NNTP
        }
    }
}
```

#### Yenc-Encoded Body Response

For yenc-encoded responses keyed by article ID. The server automatically yenc-encodes the provided raw bytes. The `argument` in the `given` block is used as the article ID.

```kotlin
client.addExpectation {
    given {
        command = NntpCommand.BODY
        argument = "<file.part1@example.com>"  // required for yenc
    }
    thenRespond {
        withYencBodyResponse {
            body = rawFileBytes     // ByteArray, will be yenc-encoded server-side
            filename = "data.bin"   // optional, derived from articleId if omitted
        }
    }
}
```

### Available NNTP Commands

The `NntpCommand` enum defines all supported NNTP commands for the `given` block:

`ARTICLE`, `BODY`, `HEAD`, `STAT`, `GROUP`, `LISTGROUP`, `LAST`, `NEXT`, `POST`, `QUIT`

### Full Test Example

```kotlin
import io.skjaere.mocknntp.testcontainer.MockNntpServerContainer
import io.skjaere.mocknntp.testcontainer.client.NntpCommand
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class MyNntpIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val container = MockNntpServerContainer()
    }

    @Test
    fun `should return yenc-encoded body for article`() = runBlocking {
        val client = container.client
        val testData = "Hello, Usenet!".toByteArray()

        client.addExpectation {
            given {
                command = NntpCommand.BODY
                argument = "<test@example.com>"
            }
            thenRespond {
                withYencBodyResponse {
                    body = testData
                    filename = "hello.txt"
                }
            }
        }

        // Connect to container.nntpHost:container.nntpPort
        // and send: BODY <test@example.com>
        // The response will contain yenc-encoded data with =ybegin/=yend headers

        val stats = client.getStats()
        assert(stats["BODY"] == 1)

        client.clearExpectations()
    }
}
```

## Running Standalone

### Docker

The Docker image is published to GitHub Container Registry:

```bash
docker run -p 8081:8081 -p 1119:1119 ghcr.io/skjaere/mock-nntp-server:latest
```

### Building Locally

```bash
./gradlew jibDockerBuild
docker run -p 8081:8081 -p 1119:1119 ghcr.io/skjaere/mock-nntp-server:latest
```

The Ktor HTTP server runs on port `8081` and the mock NNTP server on port `1119`.

## REST API

### Mocks

| Method   | Endpoint             | Description                                   |
|----------|----------------------|-----------------------------------------------|
| `POST`   | `/mocks`             | Add/update a mock response for an NNTP command |
| `GET`    | `/mocks`             | List all configured mock responses             |
| `DELETE` | `/mocks`             | Clear all mock responses                       |
| `POST`   | `/mocks/yenc-body`   | Add a yenc body mock (auto-encoded)            |
| `POST`   | `/mocks/yenc-body/raw` | Add a raw yenc body mock (pre-encoded)       |
| `DELETE` | `/mocks/yenc-body`   | Clear all yenc body mocks                      |

### Statistics

| Method   | Endpoint  | Description                        |
|----------|-----------|------------------------------------|
| `GET`    | `/stats`  | Get command call counts            |
| `DELETE` | `/stats`  | Clear command call statistics      |

### NNTP Response Lookup Order

When the server receives an NNTP command, it resolves the response in this order:

1. **Article-keyed yenc body mock** -- If the command is `BODY` and an article ID is provided, the server checks for a yenc body mock matching that exact article ID.
2. **Command-level mock** -- Falls back to the command-keyed mock (e.g., all `BODY` commands return the same response).
3. **500 error** -- If no mock is configured, responds with `500 Command not recognized`.

## Technologies Used

*   **Kotlin**
*   **Ktor Framework**
*   **Gradle**
*   **Docker / Jib**
*   **Testcontainers 2.x**
*   **`kotlinx.coroutines`**
*   **rapidyenc** (native yenc encoding via JNA)
