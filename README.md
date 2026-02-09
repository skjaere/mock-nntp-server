# Mock NNTP Server

[![CI](https://github.com/skjaere/mock-nntp-server/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/skjaere/mock-nntp-server/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/skjaere/mock-nntp-server/branch/main/graph/badge.svg)](https://codecov.io/gh/skjaere/mock-nntp-server)

A mock NNTP (Network News Transfer Protocol) server built with Kotlin and Ktor, designed to simulate NNTP responses and track command usage. This project provides HTTP REST endpoints to configure mock NNTP responses and retrieve statistics about NNTP commands received.

## Features

-   **Kotlin & Ktor:** Developed using Kotlin and the Ktor asynchronous framework.
-   **Configurable Mock Responses:** HTTP REST endpoints to add, retrieve, and clear mock responses for specific NNTP commands.
-   **Command Call Statistics:** HTTP REST endpoints to track and retrieve how many times each NNTP command has been called.
-   **Dockerized:** Easily deployable as a Docker container.

## How to Run with Docker

This project uses [Jib](https://github.com/GoogleContainerTools/jib) to build optimized Docker images without a Docker daemon.

1.  **Build the Docker image with Jib:**
    Navigate to the project root directory and run:
    ```bash
    ./gradlew :app:jibDockerBuild --no-configuration-cache
    ```
    This command builds the Docker image and loads it into your local Docker daemon.

2.  **Run the Docker container:**
    This will start the Ktor HTTP server on port `8081` and the mock NNTP server on port `1119`.
    ```bash
    docker run -p 8081:8081 -p 1119:1119 mock-nntp-server:latest
    ```
    The server will start, and you will see logs in your terminal, including messages like "NNTP Server starting on port 1119...".

## Ktor REST API Usage

The Ktor HTTP server runs on `http://localhost:8081`.

### 1. Add/Update a Mock NNTP Response

Use a `POST` request to `/mocks` to configure what response the mock NNTP server should send for a given command.

**Endpoint:** `POST /mocks`
**Content-Type:** `application/json`

**Example Request:**
```bash
curl -X POST -H "Content-Type: application/json" -d '{"command": "ARTICLE", "response": "220 Article retrieved, body follows (text/plain)
This is a mocked article content.
."}' http://localhost:8081/mocks
```
*Note: NNTP responses often require `
` for line endings, and `.` on a line by itself to signify the end of the multiline response.*

### 2. Retrieve All Mock NNTP Responses

Use a `GET` request to `/mocks` to see all configured mock responses.

**Endpoint:** `GET /mocks`

**Example Request:**
```bash
curl http://localhost:8081/mocks
```

**Example Response:**
```json
{
  "ARTICLE": "220 Article retrieved, body follows (text/plain)
This is a mocked article content.
."
}
```

### 3. Clear All Mock NNTP Responses

Use a `DELETE` request to `/mocks` to remove all configured mock responses.

**Endpoint:** `DELETE /mocks`

**Example Request:**
```bash
curl -X DELETE http://localhost:8081/mocks
```

### 4. Retrieve NNTP Command Call Statistics

Use a `GET` request to `/stats` to see how many times each NNTP command has been called.

**Endpoint:** `GET /stats`

**Example Request:**
```bash
curl http://localhost:8081/stats
```

**Example Response (after some NNTP interaction):**
```json
{
  "ARTICLE": 5,
  "GROUP": 2
}
```

### 5. Clear NNTP Command Call Statistics

Use a `DELETE` request to `/stats` to reset all command call counters.

**Endpoint:** `DELETE /stats`

**Example Request:**
```bash
curl -X DELETE http://localhost:8081/stats
```

### 6. Add a Yenc-Encoded Body Response

Use a `POST` request to `/mocks/yenc-body` to configure a yenc-encoded body response for a specific article ID. The binary data is provided as base64, and the server automatically yenc-encodes it. When a `BODY <articleId>` command is received over NNTP, the yenc-encoded data is returned directly.

**Endpoint:** `POST /mocks/yenc-body`
**Content-Type:** `application/json`

**Request Body:**
```json
{
  "articleId": "<test.article@example.com>",
  "data": "SGVsbG8sIFdvcmxkIQ==",
  "filename": "hello.txt"
}
```

- `articleId` (required): The NNTP article ID to match against.
- `data` (required): Base64-encoded binary data to be yenc-encoded.
- `filename` (optional): Filename for yenc headers. If omitted, derived from the article ID.

**Example Request:**
```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{"articleId": "<test@example.com>", "data": "SGVsbG8sIFdvcmxkIQ==", "filename": "hello.txt"}' \
  http://localhost:8081/mocks/yenc-body
```

*Note: The yenc encoding is performed automatically by the server. Article-keyed yenc mocks take priority over command-level BODY mocks.*

### 7. Add a Raw (Pre-Encoded) Yenc Body Response

Use a `POST` request to `/mocks/yenc-body/raw` to provide pre-encoded yenc bytes for a specific article ID. Unlike `/mocks/yenc-body`, this endpoint stores the data as-is without any encoding — use this when you need full control over the yenc format (e.g. multipart articles with `=ypart` headers).

**Endpoint:** `POST /mocks/yenc-body/raw`
**Content-Type:** `application/json`

**Request Body:**
```json
{
  "articleId": "<part2@example.com>",
  "data": "PBase64-encoded raw yenc bytes including =ybegin, encoded data, and =yend>"
}
```

- `articleId` (required): The NNTP article ID to match against.
- `data` (required): Base64-encoded pre-built yenc data (including `=ybegin`, encoded content, and `=yend` lines).

**Example Request:**
```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{"articleId": "<part2@example.com>", "data": "PXliZWdpbiBwYXJ0PTIgdG90YWw9MyBsaW5lPTEyOCBzaXplPTMwMDAgbmFtZT1hcmNoaXZlLnJhcg0K..."}' \
  http://localhost:8081/mocks/yenc-body/raw
```

*Note: Raw yenc mocks are stored alongside auto-encoded yenc mocks and follow the same lookup priority.*

### 8. Clear All Yenc Body Mocks

Use a `DELETE` request to `/mocks/yenc-body` to remove all yenc body mocks.

**Endpoint:** `DELETE /mocks/yenc-body`

**Example Request:**
```bash
curl -X DELETE http://localhost:8081/mocks/yenc-body
```

## NNTP Server Usage

The mock NNTP server listens on port `1119`. You can interact with it using a simple `telnet` client or any NNTP client library.

1.  **Connect to the NNTP server:**
    ```bash
    telnet localhost 1119
    ```
    *(You might need to install `telnet` if it's not available on your system.)*

2.  **Send an NNTP command:**
    After connecting, type an NNTP command (e.g., `ARTICLE <message-id>`) and press Enter. If a mock response is configured for that command, the server will send it back.

    Example interaction:
    ```
    # (after connecting with telnet)
    ARTICLE 12345
    # (server responds with the configured mock response for ARTICLE)
    ```

### NNTP Response Lookup Order

When the server receives an NNTP command, it resolves the response in this order:

1. **Article-keyed yenc body mock** — If the command is `BODY` and an article ID is provided, the server checks for a yenc body mock matching that exact article ID.
2. **Command-level mock** — Falls back to the command-keyed mock (e.g., all `BODY` commands return the same response).
3. **500 error** — If no mock is configured, responds with `500 Command not recognized`.

## Testcontainer Module

The `testcontainer` module provides a Testcontainers wrapper and a Kotlin client DSL for use in integration tests. It is published as a Maven artifact and can be added as a test dependency.

### Dependency

Add the testcontainer module to your project:

```kotlin
// build.gradle.kts
repositories {
    mavenLocal()
}

dependencies {
    testImplementation("io.skjaere.mocknntp:testcontainer:0.1.0")
    testImplementation("org.testcontainers:testcontainers:2.0.3")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.3")
}
```

Publish to mavenLocal first (from the mock-nntp-server project root):
```bash
./gradlew :testcontainer:publishToMavenLocal
```

### MockNntpServerContainer

`MockNntpServerContainer` extends Testcontainers' `GenericContainer` and manages the mock NNTP server Docker image lifecycle.

```kotlin
import io.skjaere.mocknntp.testcontainer.MockNntpServerContainer

val container = MockNntpServerContainer() // defaults to "mock-nntp-server:latest"
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

## Technologies Used

*   **Kotlin**
*   **Ktor Framework**
*   **Gradle**
*   **Docker**
*   **Testcontainers 2.x**
*   **`kotlinx.coroutines`**
*   **rapidyenc** (native yenc encoding via JNA)
