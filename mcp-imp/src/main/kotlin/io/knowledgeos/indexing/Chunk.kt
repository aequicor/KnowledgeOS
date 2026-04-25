package io.knowledgeos.indexing

import io.knowledgeos.vault.Frontmatter

data class Chunk(
    val id: String,
    val docPath: String,
    val text: String,
    val contextualizedText: String,
    val frontmatter: Frontmatter,
    val position: Int
)
