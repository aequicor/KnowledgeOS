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
        val content: String
    )

    suspend fun execute(params: UpdateParams): String {
        val resolved = vaultPath.resolve(params.path).normalize()
        if (!resolved.startsWith(vaultPath.normalize())) {
            return Json.encodeToString(mapOf("status" to "error", "message" to "Path traversal denied"))
        }
        if (!resolved.exists()) {
            return Json.encodeToString(mapOf("status" to "error", "message" to "Document not found: ${params.path}"))
        }
        Files.writeString(resolved, params.content, StandardOpenOption.TRUNCATE_EXISTING)
        return Json.encodeToString(mapOf("status" to "updated", "path" to params.path))
    }
}
