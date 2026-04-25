---
genre: guideline
title: adding-mcp-tools
topic: adding-mcp-tools
confidence: high
source: agent
updated: 2026-04
---

# Adding a New MCP Tool

To add a new MCP tool to KnowledgeOS, follow these 4 steps:

## 1. Create the Tool Class

Create a file in `mcp-api/src/main/kotlin/io/knowledgeos/tools/YourTool.kt`:

- Define a `data class` with `@Serializable` annotation for input parameters
- Implement `suspend fun execute(params: YourParams): String`

Example (see [[SearchDocsTool.kt]]):

```kotlin
package io.knowledgeos.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class YourTool {
    @Serializable
    data class YourParams(val field1: String, val field2: Int = 42)

    suspend fun execute(params: YourParams): String {
        // your logic here
        return Json.encodeToString(result)
    }
}
```

## 2. Register in `Routes.kt`

In `mcp-api/src/main/kotlin/io/knowledgeos/server/Routes.kt`:

1. Add import for your tool class
2. Add parameter to `mcpRoutes()` function signature
3. Pass it to `createMcpServer()`
4. Add `server.addTool(Tool(...)) { ... }` block:
   - Define `Tool` with `name`, `inputSchema` (properties + required), and `description`
   - Extract arguments from `request.arguments` using JSON primitives
   - Call `yourTool.execute(YourTool.YourParams(...))`
   - Return `CallToolResult(content = listOf(TextContent(text = resultText)))`

## 3. Update `McpServer.kt`

In `mcp-api/src/main/kotlin/io/knowledgeos/server/McpServer.kt`:

1. Add your tool as a constructor parameter
2. Pass it to `mcpRoutes()` in the `routing` block

## 4. Wire in `Main.kt`

In `mcp-api/src/main/kotlin/io/knowledgeos/Main.kt`:

1. Import your tool
2. Instantiate the tool with required dependencies
3. Pass it to `McpServer(...)` constructor

## Pattern Summary

Files to touch:
- **New file**: `mcp-api/src/main/kotlin/io/knowledgeos/tools/YourTool.kt` — tool logic
- **Edit**: `Routes.kt` — add `server.addTool(Tool(...)) { ... }` block, update function signatures
- **Edit**: `McpServer.kt` — add constructor parameter, pass to `mcpRoutes`
- **Edit**: `Main.kt` — instantiate tool, pass to `McpServer`
