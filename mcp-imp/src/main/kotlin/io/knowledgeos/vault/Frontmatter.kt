package io.knowledgeos.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class Frontmatter(val raw: Map<String, JsonElement> = emptyMap()) {

    fun string(key: String): String? = (raw[key] as? JsonPrimitive)?.contentOrNull

    fun stringList(key: String): List<String> {
        val node = raw[key] ?: return emptyList()
        return when (node) {
            is JsonArray -> node.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> node.contentOrNull?.let { listOf(it) } ?: emptyList()
            else -> emptyList()
        }
    }

    val title: String get() = string("title") ?: ""

    val related: List<String> get() = stringList("related")

    fun withRelated(values: List<String>): Frontmatter {
        if (values.isEmpty() && "related" !in raw) return this
        val merged = raw.toMutableMap()
        merged["related"] = JsonArray(values.map { JsonPrimitive(it) })
        return Frontmatter(merged)
    }

    fun isEmpty(): Boolean = raw.isEmpty()

    companion object {
        val EMPTY = Frontmatter()

        fun of(vararg pairs: Pair<String, JsonElement>): Frontmatter =
            Frontmatter(pairs.toMap())

        fun ofStrings(vararg pairs: Pair<String, String>): Frontmatter =
            Frontmatter(pairs.associate { (k, v) -> k to JsonPrimitive(v) })
    }
}

fun JsonObject.toFrontmatter(): Frontmatter = Frontmatter(this.toMap())
