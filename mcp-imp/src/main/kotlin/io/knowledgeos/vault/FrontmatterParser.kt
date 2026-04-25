package io.knowledgeos.vault

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.decodeFromString

object FrontmatterParser {

    private val yaml = Yaml.default
    private val frontmatterRegex = Regex("""^---\s*\n(.*?)\n---\s*\n(.*)""", RegexOption.DOT_MATCHES_ALL)

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
            Pair(frontmatter, body)
        } else {
            Pair(Frontmatter(), content.trimStart())
        }
    }
}
