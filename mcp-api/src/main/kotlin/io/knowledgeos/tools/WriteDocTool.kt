package io.knowledgeos.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class WriteDocTool(private val vaultPath: Path) {

    @Serializable
    data class WriteParams(
        val path: String,
        val content: String,
        val frontmatter: Map<String, JsonElement>? = null
    )

    suspend fun execute(params: WriteParams): String {
        if (params.path.isBlank()) {
            return error("Path must not be blank")
        }
        if (!params.path.endsWith(".md")) {
            return error("Path must end with .md")
        }

        val resolved = vaultPath.resolve(params.path).normalize()
        if (!resolved.startsWith(vaultPath.normalize())) {
            return error("Path traversal denied")
        }

        Files.createDirectories(resolved.parent)

        val finalContent = buildString {
            val fm = params.frontmatter
            if (!fm.isNullOrEmpty()) {
                appendLine("---")
                renderYaml(fm)
                appendLine("---")
                appendLine()
            }
            append(params.content.trimStart())
            if (!params.content.endsWith("\n")) appendLine()
        }

        Files.writeString(resolved, finalContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        return Json.encodeToString(
            mapOf("status" to "created", "path" to vaultPath.relativize(resolved).toString().replace('\\', '/'))
        )
    }

    private fun StringBuilder.renderYaml(fm: Map<String, JsonElement>, indent: String = "") {
        for ((key, value) in fm) {
            when (value) {
                is JsonPrimitive -> appendLine("$indent$key: ${formatScalar(value)}")
                is JsonArray -> {
                    appendLine("$indent$key:")
                    for (item in value) {
                        if (item is JsonPrimitive) appendLine("$indent  - ${formatScalar(item)}")
                    }
                }
                is JsonObject -> {
                    appendLine("$indent$key:")
                    renderYaml(value.toMap(), "$indent  ")
                }
            }
        }
    }

    private fun formatScalar(value: JsonPrimitive): String {
        if (!value.isString) return value.content
        val raw = value.content
        if (needsQuoting(raw)) return "\"${raw.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        return raw
    }

    private fun needsQuoting(s: String): Boolean {
        if (s.isEmpty()) return true
        val first = s.first()
        if (first.isWhitespace() || first in "&*!|>'\"%@`#,[]{}") return true
        if (s.contains(":") || s.contains("\n") || s.contains("#")) return true
        if (s.equals("true", true) || s.equals("false", true) ||
            s.equals("null", true) || s.equals("yes", true) || s.equals("no", true)) return true
        if (s.toDoubleOrNull() != null) return true
        return false
    }

    private fun error(message: String): String =
        Json.encodeToString(mapOf("status" to "error", "message" to message))
}
