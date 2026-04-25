package io.knowledgeos.vault

import java.nio.file.Path

data class Document(
    val path: Path,
    val frontmatter: Frontmatter,
    val body: String,
    val wikilinks: List<String>
) {
    val id: String get() = path.fileName.toString().removeSuffix(".md")
}
