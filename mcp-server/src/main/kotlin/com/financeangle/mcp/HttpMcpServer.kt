package com.financeangle.mcp

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
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
    val server = McpServer(baseUrl = baseUrl)

    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            jackson()
        }
        routing {
            get("/health") {
                call.respondText("ok", ContentType.Text.Plain)
            }
            post("/") {
                val payload = call.receiveText()
                if (payload.isBlank()) {
                    call.respondText(
                        text = "{\"error\":{\"code\":-32600,\"message\":\"Empty request\"}}",
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.BadRequest
                    )
                    return@post
                }
                val responseNode = server.processRawJson(payload)
                call.respondText(
                    text = responseNode.toString(),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            }
        }
    }.start(wait = true)
}
