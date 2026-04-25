---
genre: guideline
title: adding-mcp-tools
topic: adding-mcp-tools
confidence: high
source: agent
updated: 2026-04
related:
  - getting-started,   - architecture
---

# Adding a New MCP Tool

To add a new MCP tool to KnowledgeOS, follow these 4 steps:

## 1. Create the Tool Class (`mcp-api/src/main/kotlin/io/knowledgeos/tools/`)

Create a class with a serializable params data class and a `suspend fun execute(params): String` method:

```kotlin
package io.knowledgeos.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MyNewTool(private val someDependency: SomeType) {

    @Serializable
    data class MyParams(
        val field1: String,
        val field2: Int? = null
    )

    suspend fun execute(params: MyParams): String {
        // business logic here
        return Json.encodeToString(mapOf("status" to "ok"))
    }
}
```

## 2. Register the Tool in `Routes.kt`

In `mcp-api/src/main/kotlin/io/knowledgeos/server/Routes.kt`:

1. Import your new tool class
2. Add it as a parameter to `mcpRoutes()` and `createMcpServer()`
3. Call `server.addTool(...)` with a `Tool` definition (name, schema, description) and a handler lambda

## 3. Wire it in `Main.kt`

In `mcp-api/src/main/kotlin/io/knowledgeos/Main.kt`:

1. Instantiate your tool with its dependencies
2. Pass it to the `McpServer(...)` constructor

## 4. Update `McpServer.kt`

In `mcp-api/src/main/kotlin/io/knowledgeos/server/McpServer.kt`:

1. Add your tool as a constructor parameter
2. Pass it through to `mcpRoutes()`

## Pattern Summary

Files to touch:
- **New file**: `mcp-api/src/main/kotlin/io/knowledgeos/tools/YourTool.kt` — tool logic
- **Edit**: `Routes.kt` — add `server.addTool(Tool(...)) { ... }` block, update function signatures
- **Edit**: `Main.kt` — instantiate tool, pass to `McpServer`
- **Edit**: `McpServer.kt` — add constructor parameter, pass to `mcpRoutes`
