package io.knowledgeos.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.knowledgeos.tools.SearchDocsTool
import io.knowledgeos.tools.UpdateDocTool
import io.knowledgeos.tools.WriteGuidelineTool
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*

private val mcpLog = KotlinLogging.logger {}

fun Routing.mcpRoutes(
    searchDocsTool: SearchDocsTool,
    writeGuidelineTool: WriteGuidelineTool,
    updateDocTool: UpdateDocTool
) {
    route("/mcp") {
        mcp {
            createMcpServer(searchDocsTool, writeGuidelineTool, updateDocTool)
        }
    }
}

private fun createMcpServer(
    searchDocsTool: SearchDocsTool,
    writeGuidelineTool: WriteGuidelineTool,
    updateDocTool: UpdateDocTool
): Server {
    val server = Server(
        serverInfo = Implementation("KnowledgeOS", "0.1.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = null)
            )
        )
    )

    server.addTool(
        Tool(
            name = "search_docs",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Search query"))
                    })
                    put("genre", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Optional genre filter: concept, how-to, reference, tutorial, guideline"))
                    })
                    put("topic", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Optional topic filter"))
                    })
                },
                required = listOf("query")
            ),
            description = "Search documentation vault for relevant chunks. Returns top-K chunks from the retrieval pipeline."
        )
    ) { request: CallToolRequest ->
        mcpLog.info { "search_docs arguments: ${request.arguments}" }
        val query = request.arguments?.get("query")?.jsonPrimitive?.content ?: ""
        val genre = request.arguments?.get("genre")?.jsonPrimitive?.contentOrNull
        val topic = request.arguments?.get("topic")?.jsonPrimitive?.contentOrNull
        mcpLog.info { "search_docs query='$query' genre='$genre' topic='$topic'" }
        val resultText = searchDocsTool.execute(SearchDocsTool.SearchParams(query, genre, topic))
        CallToolResult(content = listOf(TextContent(text = resultText)))
    }

    server.addTool(
        Tool(
            name = "write_guideline",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("content", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Markdown content of the guideline"))
                    })
                    put("topic", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Topic name (used as filename)"))
                    })
                    put("library", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Optional library name"))
                    })
                    put("related", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                        put("description", JsonPrimitive("Optional related wikilinks"))
                    })
                },
                required = listOf("content", "topic")
            ),
            description = "Write a new guideline document to the vault. Creates a markdown file with proper frontmatter."
        )
    ) { request: CallToolRequest ->
        val content = request.arguments?.get("content")?.jsonPrimitive?.content ?: ""
        val topic = request.arguments?.get("topic")?.jsonPrimitive?.content ?: ""
        val library = request.arguments?.get("library")?.jsonPrimitive?.contentOrNull
        val related = request.arguments?.get("related")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        val resultText = writeGuidelineTool.execute(WriteGuidelineTool.WriteParams(content, topic, library, related))
        CallToolResult(content = listOf(TextContent(text = resultText)))
    }

    server.addTool(
        Tool(
            name = "update_doc",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("path", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to existing document relative to vault root"))
                    })
                    put("content", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("New markdown content"))
                    })
                },
                required = listOf("path", "content")
            ),
            description = "Update an existing document in the vault. Replaces the file content atomically."
        )
    ) { request: CallToolRequest ->
        val docPath = request.arguments?.get("path")?.jsonPrimitive?.content ?: ""
        val docContent = request.arguments?.get("content")?.jsonPrimitive?.content ?: ""
        val resultText = updateDocTool.execute(UpdateDocTool.UpdateParams(docPath, docContent))
        CallToolResult(content = listOf(TextContent(text = resultText)))
    }

    return server
}
