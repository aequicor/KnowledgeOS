package io.knowledgeos.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists

class UpdateDocTool(private val vaultPath: Path) {

    @Serializable
    data class UpdateParams(
        val path: String,
        val content: String,
        val preserve_frontmatter: Boolean = false
    )

    suspend fun execute(params: UpdateParams): String {
        val resolved = vaultPath.resolve(params.path).normalize()
        if (!resolved.startsWith(vaultPath.normalize())) {
            return Json.encodeToString(mapOf("status" to "error", "message" to "Path traversal denied"))
        }
        if (!resolved.exists()) {
            return Json.encodeToString(mapOf("status" to "error", "message" to "Document not found: ${params.path}"))
        }

        val finalContent = if (params.preserve_frontmatter && !params.content.trimStart().startsWith("---")) {
            val existing = Files.readString(resolved)
            val existingFrontmatter = extractFrontmatter(existing)
            if (existingFrontmatter != null) existingFrontmatter + "\n" + params.content
            else params.content
        } else {
            params.content
        }

        Files.writeString(resolved, finalContent, StandardOpenOption.TRUNCATE_EXISTING)
        return Json.encodeToString(mapOf("status" to "updated", "path" to params.path))
    }

    private fun extractFrontmatter(content: String): String? {
        if (!content.trimStart().startsWith("---")) return null
        val start = content.indexOf("---")
        val end = content.indexOf("---", start + 3)
        if (end == -1) return null
        return content.substring(start, end + 3)
    }
}
