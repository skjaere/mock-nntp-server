package io.skjaere.mocknntp.testcontainer.client

@DslMarker
annotation class ExpectationDslMarker

@ExpectationDslMarker
class ExpectationBuilder {
    private var whenBuilder: WhenBuilder? = null
    private var responseBuilder: ResponseBuilder? = null

    fun given(block: WhenBuilder.() -> Unit) {
        whenBuilder = WhenBuilder().apply(block)
    }

    fun thenRespond(block: ResponseBuilder.() -> Unit) {
        responseBuilder = ResponseBuilder().apply(block)
    }

    internal fun build(): Expectation {
        val w = requireNotNull(whenBuilder) { "A `given` block is required" }
        val r = requireNotNull(responseBuilder) { "A `thenRespond` block is required" }
        return Expectation(
            command = requireNotNull(w.command) { "command is required in `given` block" },
            argument = w.argument,
            textResponse = r.textResponse,
            binaryResponse = r.binaryResponse,
            yencBody = r.yencBody,
            yencFilename = r.yencFilename,
            status = r.status
        )
    }
}

@ExpectationDslMarker
class WhenBuilder {
    var command: NntpCommand? = null
    var argument: String? = null
}

@ExpectationDslMarker
class ResponseBuilder {
    internal var textResponse: String? = null
    internal var binaryResponse: ByteArray? = null
    internal var yencBody: ByteArray? = null
    internal var yencFilename: String? = null
    internal var status: Int? = null

    fun withBinaryBodyResponse(block: BinaryBodyResponseBuilder.() -> Unit) {
        val builder = BinaryBodyResponseBuilder().apply(block)
        binaryResponse = builder.body
        status = builder.status
    }

    fun withTextResponse(block: TextResponseBuilder.() -> Unit) {
        val builder = TextResponseBuilder().apply(block)
        textResponse = builder.body
        status = builder.status
    }

    fun withYencBodyResponse(block: YencBodyResponseBuilder.() -> Unit) {
        val builder = YencBodyResponseBuilder().apply(block)
        yencBody = builder.body
        yencFilename = builder.filename
    }
}

@ExpectationDslMarker
class BinaryBodyResponseBuilder {
    var status: Int = 222
    var body: ByteArray = ByteArray(0)
}

@ExpectationDslMarker
class TextResponseBuilder {
    var status: Int = 220
    var body: String = ""
}

@ExpectationDslMarker
class YencBodyResponseBuilder {
    var body: ByteArray = ByteArray(0)
    var filename: String? = null
}

internal data class Expectation(
    val command: NntpCommand,
    val argument: String?,
    val textResponse: String?,
    val binaryResponse: ByteArray?,
    val yencBody: ByteArray?,
    val yencFilename: String?,
    val status: Int?
)
