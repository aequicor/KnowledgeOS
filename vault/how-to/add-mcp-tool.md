---
genre: how-to
title: Как добавить новый MCP-инструмент
topic: development
confidence: high
source: human
updated: 2026-04
related:
  - [[concepts/architecture]]
  - [[guidelines/writing-guidelines]]
---

# Как добавить новый MCP-инструмент

## Шаги

1. Создать класс инструмента в `mcp-api/src/main/kotlin/io/knowledgeos/tools/`
2. Определить `data class` с параметрами
3. Реализовать метод `execute(params): String`
4. Зарегистрировать инструмент в `Routes.kt` — добавить в `handleToolsList()` и `handleToolCall()`
5. Внедрить через `Main.kt` и передать в `McpServer`

## Пример

См. существующие инструменты:
- `SearchDocsTool.kt`
- `WriteGuidelineTool.kt`
- `UpdateDocTool.kt`
