package com.financeangle.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

fun main() {
    McpServer().start()
}

class McpServer {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    private val backendBaseUrl: String = (System.getenv("FINANCE_ANGLE_BASE_URL") ?: "http://app:8080").trimEnd('/')

    private val tools: Map<String, ToolDefinition> = buildToolsMap()

    fun start() {
        val input = System.`in`
        while (true) {
            val request = try {
                readRequest(input) ?: break
            } catch (e: EOFException) {
                break
            } catch (e: Exception) {
                System.err.println("Failed to read request: ${e.message}")
                continue
            }
            try {
                handleRequest(request)
            } catch (e: Exception) {
                System.err.println("Error handling request: ${e.message}")
                sendError(request.id, message = e.message ?: "Unhandled error")
            }
        }
    }

    private fun handleRequest(request: JsonRpcRequest) {
        when (request.method) {
            "initialize" -> sendResponse(
                request.id,
                objectMapper.createObjectNode().apply {
                    put("protocolVersion", "1.0")
                    val serverNode = objectMapper.createObjectNode().apply {
                        put("name", "finance-angle-mcp")
                        put("version", "0.1.0")
                    }
                    set<ObjectNode>("server", serverNode)
                    val capabilitiesNode = objectMapper.createObjectNode().apply {
                        val toolsCapability = objectMapper.createObjectNode().apply {
                            put("listChanged", false)
                        }
                        set<ObjectNode>("tools", toolsCapability)
                    }
                    set<ObjectNode>("capabilities", capabilitiesNode)
                }
            )

            "ping" -> sendResponse(request.id, objectMapper.createObjectNode())

            "tools/list" -> sendResponse(
                request.id,
                objectMapper.createObjectNode().apply {
                    putNull("cursor")
                    val toolsNode: JsonNode = objectMapper.valueToTree(tools.values.map { it.toListEntry() })
                    set<JsonNode>("tools", toolsNode)
                }
            )

            "tools/call" -> handleToolCall(request)

            else -> sendError(request.id, code = -32601, message = "Method ${request.method} not implemented")
        }
    }

    private fun handleToolCall(request: JsonRpcRequest) {
        val paramsNode = request.params
        if (paramsNode == null || !paramsNode.isObject) {
            sendError(request.id, message = "Invalid params for tools/call")
            return
        }
        val toolName = paramsNode.get("toolName")?.asText()
        if (toolName.isNullOrBlank()) {
            sendError(request.id, message = "toolName is required for tools/call")
            return
        }
        val tool = tools[toolName]
        if (tool == null) {
            sendError(request.id, message = "Unknown tool $toolName")
            return
        }
        val argsNode = paramsNode.get("arguments")
        try {
            val result = tool.handler.invoke(argsNode)
            sendResponse(request.id, objectMapper.valueToTree(result))
        } catch (e: Exception) {
            sendError(request.id, message = e.message ?: "Tool execution failed")
        }
    }

    private fun buildToolsMap(): Map<String, ToolDefinition> {
        val schemaReader: (String) -> ObjectNode = { json ->
            objectMapper.readTree(json) as ObjectNode
        }
        return listOf(
            ToolDefinition(
                name = "createTransaction",
                description = "Create a transaction entry in Finance Angle.",
                inputSchema = schemaReader(
                    """
                    {
                      "type": "object",
                      "required": ["amount"],
                      "properties": {
                        "amount": { "type": "number", "description": "Transaction amount in EUR." },
                        "category": {
                          "type": "string",
                          "enum": ["FOOD", "ENTERTAINMENT", "FAMILY", "TRANSPORT", "HEALTH", "HOUSING", "UTILITIES", "INCOME", "SAVINGS", "OTHER"],
                          "description": "Category for the transaction. Defaults to OTHER."
                        },
                        "occurredAt": {
                          "type": "string",
                          "format": "date-time",
                          "description": "ISO timestamp when the transaction happened. Defaults to now."
                        },
                        "notes": { "type": "string", "description": "Optional notes" },
                        "receiptReference": { "type": "string", "description": "Optional receipt reference" },
                        "sourceType": {
                          "type": "string",
                          "enum": ["MANUAL", "VOICE", "PHOTO", "CHATGPT"],
                          "description": "Source type override. Defaults to CHATGPT."
                        }
                      },
                      "additionalProperties": false
                    }
                    """
                ),
                handler = { args -> handleCreateTransaction(args) }
            ),
            ToolDefinition(
                name = "registerReceipt",
                description = "Register a receipt ingestion event before linking it to a transaction.",
                inputSchema = schemaReader(
                    """
                    {
                      "type": "object",
                      "required": ["externalId"],
                      "properties": {
                        "externalId": { "type": "string", "description": "External identifier" },
                        "receiptUri": { "type": "string", "description": "Optional storage URI for the receipt" },
                        "metadata": { "type": "string", "description": "Optional metadata payload" },
                        "status": {
                          "type": "string",
                          "enum": ["PENDING", "IN_PROGRESS", "COMPLETED", "FAILED"],
                          "description": "Receipt ingestion status"
                        }
                      },
                      "additionalProperties": false
                    }
                    """
                ),
                handler = { args -> handleRegisterReceipt(args) }
            ),
            ToolDefinition(
                name = "getReceiptStatus",
                description = "Fetch the current ingestion status for a receipt by external id.",
                inputSchema = schemaReader(
                    """
                    {
                      "type": "object",
                      "required": ["externalId"],
                      "properties": {
                        "externalId": { "type": "string", "description": "Receipt external id" }
                      },
                      "additionalProperties": false
                    }
                    """
                ),
                handler = { args -> handleGetReceiptStatus(args) }
            ),
            ToolDefinition(
                name = "createSavingsSnapshot",
                description = "Create a savings snapshot entry.",
                inputSchema = schemaReader(
                    """
                    {
                      "type": "object",
                      "required": ["amount"],
                      "properties": {
                        "amount": { "type": "number", "description": "Savings balance amount" },
                        "capturedAt": {
                          "type": "string",
                          "format": "date-time",
                          "description": "Timestamp when the balance was captured"
                        },
                        "notes": { "type": "string", "description": "Optional notes" }
                      },
                      "additionalProperties": false
                    }
                    """
                ),
                handler = { args -> handleCreateSavingsSnapshot(args) }
            ),
            ToolDefinition(
                name = "getLatestSavings",
                description = "Retrieve the latest recorded savings snapshot.",
                inputSchema = schemaReader(
                    """
                    {
                      "type": "object",
                      "properties": {},
                      "additionalProperties": false
                    }
                    """
                ),
                handler = { args -> handleGetLatestSavings(args) }
            ),
            ToolDefinition(
                name = "getTransactionSummary",
                description = "Retrieve a transaction summary for a given period.",
                inputSchema = schemaReader(
                    """
                    {
                      "type": "object",
                      "properties": {
                        "period": {
                          "type": "string",
                          "enum": ["WEEK", "MONTH", "QUARTER", "YEAR"],
                          "description": "Summary period"
                        },
                        "reference": {
                          "type": "string",
                          "format": "date-time",
                          "description": "Reference timestamp for the summary anchor"
                        }
                      },
                      "additionalProperties": false
                    }
                    """
                ),
                handler = { args -> handleGetTransactionSummary(args) }
            )
        ).associateBy { it.name }
    }

    private fun handleCreateTransaction(args: JsonNode?): ToolCallResult {
        val argsNode = args ?: throw IllegalArgumentException("amount is required")
        val amountNode = argsNode.get("amount") ?: throw IllegalArgumentException("amount is required")
        if (!amountNode.isNumber) {
            throw IllegalArgumentException("amount must be numeric")
        }
        val payload = objectMapper.createObjectNode().apply {
            put("amount", amountNode.decimalValue())

            val categoryNode = argsNode.get("category")
            val categoryValue = if (categoryNode?.isTextual == true) categoryNode.asText() else "OTHER"
            put("category", categoryValue)

            val occurredAtNode = argsNode.get("occurredAt")
            val occurredAtValue = if (occurredAtNode?.isTextual == true) occurredAtNode.asText() else currentIsoTimestamp()
            put("occurredAt", occurredAtValue)

            setNullableText(this, "notes", argsNode.get("notes"))
            setNullableText(this, "receiptReference", argsNode.get("receiptReference"))

            val sourceTypeNode = argsNode.get("sourceType")
            val sourceTypeValue = if (sourceTypeNode?.isTextual == true) sourceTypeNode.asText() else "CHATGPT"
            put("sourceType", sourceTypeValue)
        }
        val response = sendJsonRequest(
            HttpRequest.newBuilder(uriFor("/api/transactions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build()
        )
        if (response.status !in 200..299) {
            throw IllegalStateException("Transaction creation failed (${response.status}): ${response.bodyText}")
        }
        val responseNode = response.body ?: objectMapper.createObjectNode()
        val idText = responseNode.path("id").takeIf { !it.isMissingNode }?.asText() ?: "created"
        val amountText = responseNode.path("amount").takeIf { !it.isMissingNode }?.asText() ?: amountNode.asText()
        return ToolCallResult(
            content = listOf(
                TextContent(text = "Transaction $idText recorded for amount $amountText")
            )
        )
    }

    private fun handleRegisterReceipt(args: JsonNode?): ToolCallResult {
        val argsNode = args ?: throw IllegalArgumentException("externalId is required")
        val externalId = argsNode.get("externalId")?.asText()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("externalId is required")
        val payload = objectMapper.createObjectNode().apply {
            put("externalId", externalId)
            setNullableText(this, "receiptUri", argsNode.get("receiptUri"))
            setNullableText(this, "metadata", argsNode.get("metadata"))

            val statusNode = argsNode.get("status")
            val statusValue = if (statusNode?.isTextual == true) statusNode.asText() else "PENDING"
            put("status", statusValue)
        }
        val response = sendJsonRequest(
            HttpRequest.newBuilder(uriFor("/api/receipts/ingest"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build()
        )
        if (response.status !in 200..299) {
            throw IllegalStateException("Receipt registration failed (${response.status}): ${response.bodyText}")
        }
        val responseNode = response.body ?: objectMapper.createObjectNode()
        val statusText = responseNode.path("status").takeIf { !it.isMissingNode }?.asText() ?: payload.path("status").asText()
        return ToolCallResult(
            content = listOf(
                TextContent(text = "Registered receipt $externalId with status $statusText")
            )
        )
    }

    private fun handleGetReceiptStatus(args: JsonNode?): ToolCallResult {
        val argsNode = args ?: throw IllegalArgumentException("externalId is required")
        val externalId = argsNode.get("externalId")?.asText()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("externalId is required")
        val response = sendJsonRequest(
            HttpRequest.newBuilder(uriFor("/api/receipts/${encodePathSegment(externalId)}"))
                .GET()
                .build()
        )
        return when (response.status) {
            200 -> {
                val node = response.body ?: objectMapper.createObjectNode()
                val status = node.path("status").asText("UNKNOWN")
                ToolCallResult(content = listOf(TextContent(text = "Receipt $externalId is $status")))
            }
            404 -> ToolCallResult(content = listOf(TextContent(text = "Receipt $externalId not found")))
            else -> throw IllegalStateException("Receipt status request failed (${response.status}): ${response.bodyText}")
        }
    }

    private fun handleCreateSavingsSnapshot(args: JsonNode?): ToolCallResult {
        val argsNode = args ?: throw IllegalArgumentException("amount is required")
        val amountNode = argsNode.get("amount") ?: throw IllegalArgumentException("amount is required")
        if (!amountNode.isNumber) {
            throw IllegalArgumentException("amount must be numeric")
        }
        val payload = objectMapper.createObjectNode().apply {
            put("amount", amountNode.decimalValue())

            val capturedAtNode = argsNode.get("capturedAt")
            val capturedAtValue = if (capturedAtNode?.isTextual == true) capturedAtNode.asText() else currentIsoTimestamp()
            put("capturedAt", capturedAtValue)

            setNullableText(this, "notes", argsNode.get("notes"))
        }
        val response = sendJsonRequest(
            HttpRequest.newBuilder(uriFor("/api/savings/snapshots"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build()
        )
        if (response.status !in 200..299) {
            throw IllegalStateException("Savings snapshot failed (${response.status}): ${response.bodyText}")
        }
        val node = response.body ?: objectMapper.createObjectNode()
        val idText = node.path("id").asText("created")
        val amountText = node.path("amount").asText(amountNode.asText())
        return ToolCallResult(content = listOf(TextContent(text = "Logged savings snapshot $idText for amount $amountText")))
    }

    private fun handleGetLatestSavings(@Suppress("UNUSED_PARAMETER") args: JsonNode?): ToolCallResult {
        val response = sendJsonRequest(
            HttpRequest.newBuilder(uriFor("/api/savings/snapshots/latest"))
                .GET()
                .build()
        )
        return when (response.status) {
            200 -> {
                val node = response.body ?: objectMapper.createObjectNode()
                ToolCallResult(content = listOf(TextContent(text = objectMapper.writeValueAsString(node))))
            }
            404 -> ToolCallResult(content = listOf(TextContent(text = "No savings snapshot recorded yet")))
            else -> throw IllegalStateException("Latest savings request failed (${response.status}): ${response.bodyText}")
        }
    }

    private fun handleGetTransactionSummary(args: JsonNode?): ToolCallResult {
        val queryParams = mutableListOf<String>()
        val period = args?.get("period")?.asText()?.takeIf { it.isNotBlank() }
        if (period != null) {
            queryParams += "period=${encodeQueryComponent(period)}"
        }
        val reference = args?.get("reference")?.asText()?.takeIf { it.isNotBlank() }
        if (reference != null) {
            queryParams += "reference=${encodeQueryComponent(reference)}"
        }
        val path = if (queryParams.isEmpty()) {
            "/api/transactions/summary"
        } else {
            "/api/transactions/summary?${queryParams.joinToString("&")}"
        }
        val response = sendJsonRequest(
            HttpRequest.newBuilder(uriFor(path))
                .GET()
                .build()
        )
        if (response.status !in 200..299) {
            throw IllegalStateException("Summary request failed (${response.status}): ${response.bodyText}")
        }
        val node = response.body ?: objectMapper.createObjectNode()
        return ToolCallResult(content = listOf(TextContent(text = objectMapper.writeValueAsString(node))))
    }

    private fun sendResponse(id: JsonNode?, result: JsonNode) {
        val envelope = objectMapper.createObjectNode().apply {
            put("jsonrpc", "2.0")
            if (id != null && !id.isNull) {
                set<JsonNode>("id", id)
            } else {
                putNull("id")
            }
            set<JsonNode>("result", result)
        }
        writeMessage(envelope)
    }

    private fun sendError(id: JsonNode?, code: Int = -32000, message: String, data: String? = null) {
        val envelope = objectMapper.createObjectNode().apply {
            put("jsonrpc", "2.0")
            if (id != null && !id.isNull) {
                set<JsonNode>("id", id)
            } else {
                putNull("id")
            }
            set<ObjectNode>("error", objectMapper.createObjectNode().apply {
                put("code", code)
                put("message", message)
                if (data != null) {
                    put("data", data)
                }
            })
        }
        writeMessage(envelope)
    }

    private fun writeMessage(node: JsonNode) {
        val payload = objectMapper.writeValueAsBytes(node)
        val header = "Content-Length: ${payload.size}\r\n\r\n"
        val out = System.out
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(payload)
        out.flush()
    }

    private fun readRequest(input: InputStream): JsonRpcRequest? {
        val headerBuffer = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte == -1) {
                if (headerBuffer.size() == 0) {
                    return null
                } else {
                    throw EOFException("Unexpected EOF while reading headers")
                }
            }
            headerBuffer.write(byte)
            if (headerBuffer.size() >= 4) {
                val bytes = headerBuffer.toByteArray()
                val len = bytes.size
                if (len >= 4 &&
                    bytes[len - 4] == '\r'.code.toByte() &&
                    bytes[len - 3] == '\n'.code.toByte() &&
                    bytes[len - 2] == '\r'.code.toByte() &&
                    bytes[len - 1] == '\n'.code.toByte()
                ) {
                    break
                }
            }
        }
        val headerString = headerBuffer.toByteArray().toString(StandardCharsets.UTF_8)
        val headers = headerString.split("\r\n")
            .filter { it.isNotBlank() }
            .associate { line ->
                val separatorIndex = line.indexOf(':')
                if (separatorIndex == -1) {
                    throw IllegalArgumentException("Invalid header line: $line")
                }
                val key = line.substring(0, separatorIndex).trim().lowercase()
                val value = line.substring(separatorIndex + 1).trim()
                key to value
            }
        val length = headers["content-length"]?.toIntOrNull()
            ?: throw IllegalArgumentException("Missing Content-Length header")
        val bodyBytes = input.readNBytes(length)
        if (bodyBytes.size != length) {
            throw EOFException("Unexpected EOF while reading body")
        }
        val bodyString = bodyBytes.toString(StandardCharsets.UTF_8)
        return objectMapper.readValue(bodyString, JsonRpcRequest::class.java)
    }

    private fun currentIsoTimestamp(): String = java.time.Instant.now().toString()

    private fun uriFor(path: String): URI {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return URI.create("$backendBaseUrl$normalizedPath")
    }

    private fun encodePathSegment(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun encodeQueryComponent(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun setNullableText(node: ObjectNode, field: String, value: JsonNode?) {
        if (value == null || value.isNull || (value.isTextual && value.asText().isBlank())) {
            node.putNull(field)
        } else {
            node.put(field, value.asText())
        }
    }

    private fun sendJsonRequest(request: HttpRequest): HttpJsonResponse {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val bodyText = response.body()
        val bodyNode = try {
            if (bodyText.isNullOrBlank()) null else objectMapper.readTree(bodyText)
        } catch (e: Exception) {
            null
        }
        return HttpJsonResponse(response.statusCode(), bodyNode, bodyText)
    }
}

data class JsonRpcRequest(
    val jsonrpc: String,
    val id: JsonNode?,
    val method: String,
    val params: JsonNode?
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: ObjectNode,
    val handler: (JsonNode?) -> ToolCallResult
) {
    fun toListEntry(): ToolListEntry = ToolListEntry(name, description, inputSchema)
}

data class ToolListEntry(
    val name: String,
    val description: String,
    val inputSchema: ObjectNode
)

data class ToolCallResult(
    val content: List<TextContent>
)

data class TextContent(
    val type: String = "text",
    val text: String
)

data class HttpJsonResponse(
    val status: Int,
    val body: JsonNode?,
    val bodyText: String
)
