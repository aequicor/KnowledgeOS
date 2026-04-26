package io.knowledgeos.server

import io.knowledgeos.tools.GetDocTool
import io.knowledgeos.tools.ListDocsTool
import io.knowledgeos.tools.SearchDocsTool
import io.knowledgeos.tools.UpdateDocTool
import io.knowledgeos.tools.WriteGuidelineTool
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class McpServer(
    private val port: Int,
    searchDocsTool: SearchDocsTool,
    writeGuidelineTool: WriteGuidelineTool,
    updateDocTool: UpdateDocTool,
    getDocTool: GetDocTool,
    listDocsTool: ListDocsTool
) {
    private var server: Any? = null

    private val module: Application.() -> Unit = {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(SSE)
        routing {
            mcpRoutes(searchDocsTool, writeGuidelineTool, updateDocTool, getDocTool, listDocsTool)
        }
    }

    fun start() {
        server = embeddedServer(CIO, port = port, module = module)
        @Suppress("UNCHECKED_CAST")
        (server as EmbeddedServer<*, *>).start(wait = false)
        logger.info { "MCP Server started on port $port" }
    }

    fun stop() {
        @Suppress("UNCHECKED_CAST")
        (server as? EmbeddedServer<*, *>)?.stop(1000, 2000)
        logger.info { "MCP Server stopped" }
    }
}
