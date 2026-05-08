package io.knowledgeos.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.knowledgeos.tools.GetDocTool
import io.knowledgeos.tools.ListDocsTool
import io.knowledgeos.tools.SearchDocsTool
import io.knowledgeos.tools.UpdateDocTool
import io.knowledgeos.tools.WriteDocTool
import io.ktor.server.application.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*

private val mcpLog = KotlinLogging.logger {}

private const val CYAN = "[36m"
private const val GREEN = "[32m"
private const val RESET = "[0m"

private val prettyJson = Json { prettyPrint = true }

private fun String.prettyForLog(): String = try {
    prettyJson.encodeToString(prettyJson.parseToJsonElement(this))
} catch (_: Exception) {
    this
}

fun Application.installMcpRoutes(
    searchDocsTool: SearchDocsTool,
    writeDocTool: WriteDocTool,
    updateDocTool: UpdateDocTool,
    getDocTool: GetDocTool,
    listDocsTool: ListDocsTool,
    enableDnsRebindingProtection: Boolean = true,
    allowedHosts: List<String>? = null,
    allowedOrigins: List<String>? = null,
) {
    mcpStreamableHttp(
        path = "/mcp",
        enableDnsRebindingProtection = enableDnsRebindingProtection,
        allowedHosts = allowedHosts,
        allowedOrigins = allowedOrigins,
    ) {
        createMcpServer(searchDocsTool, writeDocTool, updateDocTool, getDocTool, listDocsTool)
    }
}

private fun createMcpServer(
    searchDocsTool: SearchDocsTool,
    writeDocTool: WriteDocTool,
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
                    put("filters", buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("description", JsonPrimitive("Optional metadata filters. Each key maps to an exact frontmatter field value (e.g. {\"kind\": \"rule\", \"tags\": \"db\"}). Empty values are ignored."))
                        put("additionalProperties", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                        })
                    })
                },
                required = listOf("query")
            ),
            description = "Search the markdown vault for chunks relevant to the query. Returns top-K results from BM25+vector retrieval with reranking. Filters match exact values of arbitrary frontmatter fields."
        )
    ) { request: CallToolRequest ->
        val query = request.arguments?.get("query")?.jsonPrimitive?.content ?: ""
        val filters = request.arguments?.get("filters")?.jsonObject?.entries
            ?.mapNotNull { (k, v) ->
                val value = (v as? JsonPrimitive)?.contentOrNull
                if (value != null) k to value else null
            }
            ?.toMap()
        mcpLog.debug { "${CYAN}search_docs called: query='$query' filters=$filters${RESET}" }
        val resultText = searchDocsTool.execute(SearchDocsTool.SearchParams(query, filters))
        mcpLog.debug { "${GREEN}search_docs result:\n${resultText.prettyForLog()}${RESET}" }
        CallToolResult(content = listOf(TextContent(text = resultText)))
    }

    server.addTool(
        Tool(
            name = "write_doc",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("path", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Path to the new document, relative to vault root. Must end with .md. Subdirectories are created if missing."))
                    })
                    put("content", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Markdown body content (without frontmatter)."))
                    })
                    put("frontmatter", buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("description", JsonPrimitive("Optional free-form YAML frontmatter as a JSON object. Keys and values are user-defined. Example: {\"title\": \"My doc\", \"tags\": [\"db\", \"sql\"]}."))
                        put("additionalProperties", JsonPrimitive(true))
                    })
                },
                required = listOf("path", "content")
            ),
            description = "Write or overwrite a markdown document at an arbitrary path inside the vault. Frontmatter is optional and free-form — KnowledgeOS does not enforce any schema."
        )
    ) { request: CallToolRequest ->
        val path = request.arguments?.get("path")?.jsonPrimitive?.content ?: ""
        val content = request.arguments?.get("content")?.jsonPrimitive?.content ?: ""
        val frontmatter = (request.arguments?.get("frontmatter") as? JsonObject)?.toMap()
        mcpLog.debug { "${CYAN}write_doc called: path='$path' frontmatter=$frontmatter\n$content${RESET}" }
        val resultText = writeDocTool.execute(WriteDocTool.WriteParams(path, content, frontmatter))
        mcpLog.debug { "${GREEN}write_doc result:\n${resultText.prettyForLog()}${RESET}" }
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
        mcpLog.debug { "${CYAN}update_doc called: path='$docPath' preserve_frontmatter=$preserveFm\n$docContent${RESET}" }
        val resultText = updateDocTool.execute(UpdateDocTool.UpdateParams(docPath, docContent, preserveFm))
        mcpLog.debug { "${GREEN}update_doc result:\n${resultText.prettyForLog()}${RESET}" }
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
        mcpLog.debug { "${CYAN}get_doc called: path='$docPath'${RESET}" }
        val resultText = getDocTool.execute(GetDocTool.GetParams(docPath))
        mcpLog.debug { "${GREEN}get_doc result (path='$docPath'): ${resultText.take(200).replace('\n', ' ')}…${RESET}" }
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
        mcpLog.debug { "${CYAN}list_docs called: directory=$dir${RESET}" }
        val resultText = listDocsTool.execute(ListDocsTool.ListParams(dir))
        mcpLog.debug { "${GREEN}list_docs result:\n${resultText.prettyForLog()}${RESET}" }
        CallToolResult(content = listOf(TextContent(text = resultText)))
    }

    return server
}
