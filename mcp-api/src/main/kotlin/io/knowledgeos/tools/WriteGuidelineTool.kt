package io.knowledgeos.tools

import io.knowledgeos.Config
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists

class WriteGuidelineTool(private val vaultPath: Path) {

    @Serializable
    data class WriteParams(
        val content: String,
        val topic: String,
        val library: String? = null,
        val related: List<String>? = null
    )

    suspend fun execute(params: WriteParams): String {
        val guidelinesDir = if (params.library != null) {
            vaultPath.resolve("guidelines/libs")
        } else {
            vaultPath.resolve("guidelines")
        }
        Files.createDirectories(guidelinesDir)

        val filename = "${params.topic}.md"
        val filePath = guidelinesDir.resolve(filename)

        val relatedWikilinks = params.related ?: emptyList()
        val relatedYaml = relatedWikilinks.joinToString("\n") { "  - $it" }
        val relatedSection = if (relatedWikilinks.isNotEmpty()) {
            "\n## Связанные документы\n" + relatedWikilinks.joinToString("\n") { "- [[$it]]" }
        } else ""

        val frontmatter = buildString {
            appendLine("---")
            appendLine("genre: guideline")
            appendLine("title: ${params.topic}")
            appendLine("topic: ${params.topic}")
            if (params.library != null) appendLine("library: ${params.library}")
            appendLine("confidence: high")
            appendLine("source: agent")
            appendLine("updated: ${java.time.YearMonth.now()}")
            if (relatedYaml.isNotEmpty()) {
                appendLine("related:")
                appendLine(relatedYaml)
            }
            appendLine("---")
        }

        val fullContent = frontmatter + "\n" + params.content + relatedSection
        Files.writeString(filePath, fullContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

        return Json.encodeToString(mapOf("status" to "created", "path" to vaultPath.relativize(filePath).toString()))
    }
}
