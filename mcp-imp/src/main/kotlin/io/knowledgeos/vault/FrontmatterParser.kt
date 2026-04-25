package io.knowledgeos.vault

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.decodeFromString

object FrontmatterParser {

    private val yaml = Yaml.default
    private val frontmatterRegex = Regex("""^---\s*\n(.*?)\n---\s*\n(.*)""", RegexOption.DOT_MATCHES_ALL)
    private val relatedSectionRegex = Regex(
        """##\s*Связанные документы\s*\n((?:-\s*\[\[[^]]+\]\]\s*\n?)*)""",
        RegexOption.DOT_MATCHES_ALL
    )
    private val wikilinkItemRegex = Regex("""-\s*\[\[([^]]+)\]\]""")

    fun parse(content: String): Pair<Frontmatter, String> {
        val match = frontmatterRegex.find(content.trimStart())
        return if (match != null) {
            val yamlBlock = match.groupValues[1]
            val body = match.groupValues[2].trimStart()
            val frontmatter = try {
                yaml.decodeFromString<Frontmatter>(yamlBlock)
            } catch (e: Exception) {
                Frontmatter()
            }

            val bodyRelated = relatedSectionRegex.find(body)
                ?.let { section ->
                    wikilinkItemRegex.findAll(section.value)
                        .map { it.groupValues[1].trim() }
                        .filter { it.isNotBlank() }
                        .toList()
                } ?: emptyList()

            val mergedRelated = (frontmatter.related + bodyRelated).distinct()
            val merged = frontmatter.copy(related = mergedRelated)

            Pair(merged, body)
        } else {
            Pair(Frontmatter(), content.trimStart())
        }
    }
}
