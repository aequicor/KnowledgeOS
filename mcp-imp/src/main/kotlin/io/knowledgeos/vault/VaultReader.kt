package io.knowledgeos.vault

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

class VaultReader(private val vaultPath: Path) {

    fun readAll(): List<Document> {
        if (!vaultPath.exists()) return emptyList()
        return Files.walk(vaultPath)
            .filter { it.isRegularFile() && it.name.endsWith(".md") }
            .map { readFile(it) }
            .toList()
    }

    fun readFile(path: Path): Document {
        val content = Files.readString(path)
        val (frontmatter, body) = FrontmatterParser.parse(content)
        val wikilinks = WikilinkParser.extract(body)
        return Document(
            path = vaultPath.relativize(path),
            frontmatter = frontmatter,
            body = body,
            wikilinks = wikilinks
        )
    }
}
