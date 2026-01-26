package com.financeangle.mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit

class McpServerTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun initializeReturnsServerInfo() = withServer() { clientOut, clientIn ->
        sendJson(clientOut, """{"jsonrpc":"2.0","id":1,"method":"initialize"}""")
        val envelope = readEnvelope(clientIn)
        val node = mapper.readTree(envelope)
        assertEquals("2.0", node["jsonrpc"].asText())
        assertEquals(1, node["id"].asInt())
        val serverNode = node["result"]["serverInfo"]
        assertEquals("finance-angle-mcp", serverNode["name"].asText())
    }

    @Test
    fun toolsListContainsRegisterReceipt() = withServer() { clientOut, clientIn ->
        sendJson(clientOut, """{"jsonrpc":"2.0","id":1,"method":"initialize"}""")
        readEnvelope(clientIn)

        sendJson(clientOut, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
        val envelope = readEnvelope(clientIn)
        val node = mapper.readTree(envelope)
        val tools = node["result"]["tools"]
        val names = tools.map { it["name"].asText() }
        assertTrue(names.contains("registerReceipt"))
        assertTrue(names.contains("createTransaction"))
    }

    @Test
    fun unknownToolReturnsError() = withServer() { clientOut, clientIn ->
        sendJson(clientOut, """{"jsonrpc":"2.0","id":1,"method":"initialize"}""")
        readEnvelope(clientIn)

        sendJson(
            clientOut,
            """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"toolName":"missingTool"}}"""
        )
        val envelope = readEnvelope(clientIn)
        val node = mapper.readTree(envelope)
        val errorNode = node["error"]
        assertEquals("Unknown tool missingTool", errorNode["message"].asText())
    }

    @Test
    fun processRawJsonSupportsToolsList() {
        val server = McpServer(baseUrl = "http://localhost")
        val response = server.processRawJson("""{"jsonrpc":"2.0","id":10,"method":"tools/list"}""")
        val tools = response["result"]["tools"]
        val names = tools.map { it["name"].asText() }
        assertTrue(names.contains("createTransaction"))
        assertEquals(10, response["id"].asInt())
    }

    @Test
    fun registerReceiptToolPostsToBackend() {
        val mockServer = MockWebServer()
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "id": 42,
                      "externalId": "receipt-123",
                      "status": "PENDING",
                      "receiptUri": null,
                      "metadata": null
                    }
                    """.trimIndent()
                )
        )

        mockServer.start()
        try {
            val baseUrl = mockServer.url("/").toString().trimEnd('/')
            withServer(baseUrl = baseUrl) { clientOut, clientIn ->
                sendJson(clientOut, """{"jsonrpc":"2.0","id":1,"method":"initialize"}""")
                readEnvelope(clientIn)

                val payload = """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "method": "tools/call",
                      "params": {
                        "toolName": "registerReceipt",
                        "arguments": {
                          "externalId": "receipt-123",
                          "status": "PENDING"
                        }
                      }
                    }
                """.trimIndent()

                sendJson(clientOut, payload)
                val envelope = readEnvelope(clientIn)
                val node = mapper.readTree(envelope)
                val content = node["result"]["content"].first()
                val message = content["text"].asText()
                assertTrue(message.contains("receipt-123"))

                val recorded = mockServer.takeRequest(5, TimeUnit.SECONDS)
                    ?: fail("No HTTP request captured by MockWebServer")
                assertEquals("/api/receipts/ingest", recorded.path)
                val requestJson = mapper.readTree(recorded.body.readUtf8())
                assertEquals("receipt-123", requestJson["externalId"].asText())
                assertEquals("PENDING", requestJson["status"].asText())
            }
        } finally {
            mockServer.shutdown()
        }
    }

    private fun withServer(
        baseUrl: String = "http://localhost",
        block: (OutputStream, InputStream) -> Unit
    ) {
        val toServer = PipedOutputStream()
        val serverInput = PipedInputStream(toServer)
        val serverOutput = PipedOutputStream()
        val clientInput = PipedInputStream(serverOutput)
        val errorBuffer = ByteArrayOutputStream()
        val server = McpServer(
            input = serverInput,
            output = serverOutput,
            errorStream = PrintStream(errorBuffer, true),
            objectMapper = mapper,
            httpClient = HttpClient.newHttpClient(),
            baseUrl = baseUrl
        )

        val serverThread = thread(start = true, name = "mcp-server-test") {
            server.start()
        }

        try {
            block(toServer, clientInput)
        } finally {
            toServer.close()
            serverThread.join(1000)
            assertFalse(serverThread.isAlive, "MCP server thread did not terminate")
            clientInput.close()
            serverOutput.close()
            serverInput.close()
        }
    }

    private fun sendJson(out: OutputStream, json: String) {
        val payload = json.toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: ${payload.size}\r\n\r\n".toByteArray(StandardCharsets.UTF_8)
        out.write(header)
        out.write(payload)
        out.flush()
    }

    private fun readEnvelope(input: InputStream): String {
        val headerBuffer = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next == -1) {
                throw IllegalStateException("Stream closed before header completed")
            }
            headerBuffer.write(next)
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
        val headerString = headerBuffer.toByteArray().toString(StandardCharsets.UTF_8)
        val match = Regex("Content-Length:\\s*(\\d+)").find(headerString)
            ?: throw IllegalStateException("Missing Content-Length header: $headerString")
        val length = match.groupValues[1].toInt()
        val bodyBytes = input.readNBytes(length)
        if (bodyBytes.size != length) {
            throw IllegalStateException("Expected $length bytes but received ${bodyBytes.size}")
        }
        return bodyBytes.toString(StandardCharsets.UTF_8)
    }
}
