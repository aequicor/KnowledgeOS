package io.knowledgeos.vault

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
            ?: return Pair(Frontmatter.EMPTY, content.trimStart())

        val yamlBlock = match.groupValues[1]
        val body = match.groupValues[2].trimStart()

        val frontmatter = parseYaml(yamlBlock)

        val bodyRelated = relatedSectionRegex.find(body)
            ?.let { section ->
                wikilinkItemRegex.findAll(section.value)
                    .map { it.groupValues[1].trim() }
                    .filter { it.isNotBlank() }
                    .toList()
            } ?: emptyList()

        val mergedRelated = (frontmatter.related + bodyRelated).distinct()
        val merged = if (mergedRelated.isEmpty()) frontmatter else frontmatter.withRelated(mergedRelated)

        return Pair(merged, body)
    }

    private fun parseYaml(yamlBlock: String): Frontmatter {
        if (yamlBlock.isBlank()) return Frontmatter.EMPTY
        return try {
            val node = yaml.parseToYamlNode(yamlBlock)
            val obj = node.toJsonElement() as? JsonObject ?: return Frontmatter.EMPTY
            Frontmatter(obj.toMap())
        } catch (e: Exception) {
            Frontmatter.EMPTY
        }
    }

    private fun YamlNode.toJsonElement(): JsonElement = when (this) {
        is YamlScalar -> JsonPrimitive(content)
        is YamlNull -> JsonNull
        is YamlList -> JsonArray(items.map { it.toJsonElement() })
        is YamlMap -> JsonObject(
            entries.entries.associate { (key, value) -> key.content to value.toJsonElement() }
        )
        else -> JsonNull
    }
}
