package com.financeangle.mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("MCP_HTTP_PORT")?.toIntOrNull() ?: 3333
    val baseUrl = System.getenv("FINANCE_ANGLE_BASE_URL")
    val logLevel = LogLevel.fromEnv(System.getenv("MCP_LOG_LEVEL"))
    val mapper = jacksonObjectMapper()
    val server = McpServer(baseUrl = baseUrl, logLevel = logLevel)

    fun logDebug(message: String) {
        if (logLevel.priority <= LogLevel.DEBUG.priority) {
            System.err.println("[DEBUG][http] $message")
        }
    }

    fun logInfo(message: String) {
        if (logLevel.priority <= LogLevel.INFO.priority) {
            System.err.println("[INFO][http] $message")
        }
    }

    fun logWarn(message: String) {
        if (logLevel.priority <= LogLevel.WARN.priority) {
            System.err.println("[WARN][http] $message")
        }
    }

    logInfo("HTTP MCP server listening on port $port (backend=${server.currentBackendBaseUrl()})")
    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            jackson()
        }
        routing {
            get("/") {
                logDebug("Metadata requested")
                val metadata = mapOf(
                    "name" to "finance-angle-mcp",
                    "version" to "0.1.0",
                    "capabilities" to listOf("tools")
                )
                call.respondText(
                    text = mapper.writeValueAsString(metadata),
                    contentType = ContentType.Application.Json
                )
            }
            get("/health") {
                logDebug("Health check")
                call.respondText("ok", ContentType.Text.Plain)
            }
            post("/") {
                val payload = call.receiveText()
                if (payload.isBlank()) {
                    logWarn("Received empty request body")
                    call.respondText(
                        text = "{\"error\":{\"code\":-32600,\"message\":\"Empty request\"}}",
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.BadRequest
                    )
                    return@post
                }
                logDebug("Incoming JSON-RPC payload: ${payload.take(500)}")
                val responseNode = server.processRawJson(payload)
                logDebug("Responding with: ${responseNode.toString().take(500)}")
                call.respondText(
                    text = responseNode.toString(),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            }
        }
    }.start(wait = true)
}
