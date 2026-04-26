package io.knowledgeos.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.knowledgeos.tools.GetDocTool
import io.knowledgeos.tools.ListDocsTool
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
    updateDocTool: UpdateDocTool,
    getDocTool: GetDocTool,
    listDocsTool: ListDocsTool
) {
    route("/mcp") {
        mcp {
            createMcpServer(searchDocsTool, writeGuidelineTool, updateDocTool, getDocTool, listDocsTool)
        }
    }
}

private fun createMcpServer(
    searchDocsTool: SearchDocsTool,
    writeGuidelineTool: WriteGuidelineTool,
    updateDocTool: UpdateDocTool,
    getDocTool: GetDocTool,
    listDocsTool: ListDocsTool
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
                    put("preserve_frontmatter", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("description", JsonPrimitive("If true and content has no frontmatter, keep the original document's frontmatter"))
                    })
                },
                required = listOf("path", "content")
            ),
            description = "Update an existing document in the vault. Set preserve_frontmatter=true to update only the body while keeping the original frontmatter."
        )
    ) { request: CallToolRequest ->
        val docPath = request.arguments?.get("path")?.jsonPrimitive?.content ?: ""
        val docContent = request.arguments?.get("content")?.jsonPrimitive?.content ?: ""
        val preserveFm = request.arguments?.get("preserve_frontmatter")?.jsonPrimitive?.booleanOrNull ?: false
        val resultText = updateDocTool.execute(UpdateDocTool.UpdateParams(docPath, docContent, preserveFm))
        CallToolResult(content = listOf(TextContent(text = resultText)))
    }

    server.addTool(
        Tool(
            name = "get_doc",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("path", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to document relative to vault root"))
                    })
                },
                required = listOf("path")
            ),
            description = "Read the full content of a document from the vault by its path."
        )
    ) { request: CallToolRequest ->
        val docPath = request.arguments?.get("path")?.jsonPrimitive?.content ?: ""
        val resultText = getDocTool.execute(GetDocTool.GetParams(docPath))
        CallToolResult(content = listOf(TextContent(text = resultText)))
    }

    server.addTool(
        Tool(
            name = "list_docs",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("directory", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Optional subdirectory to list (relative to vault root). Lists all docs if omitted."))
                    })
                },
                required = emptyList()
            ),
            description = "List all markdown documents in the vault (or a subdirectory). Returns relative paths."
        )
    ) { request: CallToolRequest ->
        val dir = request.arguments?.get("directory")?.jsonPrimitive?.contentOrNull
        val resultText = listDocsTool.execute(ListDocsTool.ListParams(dir))
        CallToolResult(content = listOf(TextContent(text = resultText)))
    }

    return server
}
