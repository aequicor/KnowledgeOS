package io.knowledgeos.indexing

import io.knowledgeos.vault.Document

class Chunker(
    private val chunkSize: Int = 512,
    private val chunkOverlap: Int = 50
) {
    fun chunk(document: Document): List<Chunk> {
        val text = document.body
        if (text.isBlank()) return emptyList()

        val sections = splitByHeadings(text)
        val chunks = mutableListOf<Chunk>()
        var position = 0

        for (section in sections) {
            if (section.length <= chunkSize) {
                chunks.add(createChunk(section, document, position++))
            } else {
                val words = section.split(Regex("\\s+"))
                var i = 0
                while (i < words.size) {
                    val end = minOf(i + chunkSize, words.size)
                    val chunkText = words.subList(i, end).joinToString(" ")
                    chunks.add(createChunk(chunkText, document, position++))
                    if (end >= words.size) break
                    i += chunkSize - chunkOverlap
                }
            }
        }
        return chunks
    }

    private fun createChunk(text: String, doc: Document, position: Int): Chunk = Chunk(
        id = "${doc.id}_$position",
        docPath = doc.path.toString(),
        text = text,
        contextualizedText = text,
        frontmatter = doc.frontmatter,
        position = position
    )

    private fun splitByHeadings(text: String): List<String> {
        val headingPattern = Regex("""^(#{1,6}\s.+)$""", RegexOption.MULTILINE)
        val splits = headingPattern.split(text)
        val headings = headingPattern.findAll(text).map { it.value }.toList()

        val sections = mutableListOf<String>()

        if (splits.isNotEmpty() && splits[0].isNotBlank()) {
            sections.add(splits[0].trim())
        }

        for (i in headings.indices) {
            val header = headings[i] + "\n"
            val content = if (i + 1 < splits.size) splits[i + 1].trim() else ""
            val section = header + content
            if (section.isNotBlank()) {
                sections.add(section)
            }
        }
        if (sections.isEmpty() && text.isNotBlank()) {
            sections.add(text.trim())
        }
        return sections
    }
}
