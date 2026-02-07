# Mock NNTP Server

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
    ./gradlew jibDockerBuild
    ```
    This command builds the Docker image and pushes it to your local Docker daemon.

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

    *Note: Currently, the NNTP server only increments the call count and returns a basic mock if configured. It does not perform actual NNTP protocol parsing beyond the command name.*

## Technologies Used

*   **Kotlin**
*   **Ktor Framework**
*   **Gradle**
*   **Docker**
*   **`kotlinx.coroutines`**
