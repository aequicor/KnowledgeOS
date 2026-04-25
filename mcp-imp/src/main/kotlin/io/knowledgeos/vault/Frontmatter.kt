package io.knowledgeos.vault

import kotlinx.serialization.Serializable

@Serializable
data class Frontmatter(
    val genre: String = "guideline",
    val title: String = "",
    val topic: String = "",
    val library: String? = null,
    val triggers: List<String> = emptyList(),
    val related: List<String> = emptyList(),
    val confidence: String = "medium",
    val source: String = "agent",
    val updated: String = ""
)
