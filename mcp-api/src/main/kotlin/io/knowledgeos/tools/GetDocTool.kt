package io.knowledgeos.tools

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class GetDocTool(private val vaultPath: Path) {

    data class GetParams(val path: String)

    suspend fun execute(params: GetParams): String {
        val resolved = vaultPath.resolve(params.path).normalize()
        if (!resolved.startsWith(vaultPath.normalize())) {
            return Json.encodeToString(mapOf("status" to "error", "message" to "Path traversal denied"))
        }
        if (!resolved.exists()) {
            return Json.encodeToString(mapOf("status" to "error", "message" to "Document not found: ${params.path}"))
        }
        val content = Files.readString(resolved)
        return Json.encodeToString(mapOf("status" to "ok", "path" to params.path, "content" to content))
    }
}
