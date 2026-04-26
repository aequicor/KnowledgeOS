package io.knowledgeos.tools

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

class ListDocsTool(private val vaultPath: Path) {

    data class ListParams(val directory: String? = null)

    suspend fun execute(params: ListParams): String {
        val searchRoot = if (params.directory != null) {
            vaultPath.resolve(params.directory).normalize()
        } else {
            vaultPath
        }
        if (!searchRoot.startsWith(vaultPath.normalize())) {
            return Json.encodeToString(mapOf("status" to "error", "message" to "Path traversal denied"))
        }
        if (!searchRoot.exists()) {
            return Json.encodeToString(mapOf("status" to "error", "message" to "Directory not found: ${params.directory}"))
        }
        if (!searchRoot.isDirectory()) {
            return Json.encodeToString(mapOf("status" to "error", "message" to "Not a directory: ${params.directory}"))
        }
        val docs = Files.walk(searchRoot)
            .filter { it.isRegularFile() && it.name.endsWith(".md") }
            .map { vaultPath.relativize(it).toString().replace('\\', '/') }
            .sorted()
            .toList()
        return Json.encodeToString(buildJsonObject {
            put("status", "ok")
            put("docs", Json.encodeToJsonElement(docs))
            put("count", docs.size.toString())
        })
    }
}
