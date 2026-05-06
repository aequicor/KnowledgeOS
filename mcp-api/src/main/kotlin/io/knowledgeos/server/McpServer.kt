package io.knowledgeos.server

import io.knowledgeos.tools.GetDocTool
import io.knowledgeos.tools.ListDocsTool
import io.knowledgeos.tools.SearchDocsTool
import io.knowledgeos.tools.UpdateDocTool
import io.knowledgeos.tools.WriteGuidelineTool
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class McpServer(
    private val port: Int,
    searchDocsTool: SearchDocsTool,
    writeGuidelineTool: WriteGuidelineTool,
    updateDocTool: UpdateDocTool,
    getDocTool: GetDocTool,
    listDocsTool: ListDocsTool,
    enableDnsRebindingProtection: Boolean = true,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
) {
    private var server: Any? = null

    private val module: Application.() -> Unit = {
        installMcpRoutes(
            searchDocsTool = searchDocsTool,
            writeGuidelineTool = writeGuidelineTool,
            updateDocTool = updateDocTool,
            getDocTool = getDocTool,
            listDocsTool = listDocsTool,
            enableDnsRebindingProtection = enableDnsRebindingProtection,
            allowedHosts = allowedHosts,
            allowedOrigins = allowedOrigins,
        )
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
