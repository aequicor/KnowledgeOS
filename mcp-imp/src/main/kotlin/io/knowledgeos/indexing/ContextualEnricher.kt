package io.knowledgeos.indexing

import io.knowledgeos.vault.Document

interface ContextualEnricher {
    suspend fun enrich(chunks: List<Chunk>, document: Document): List<Chunk>
}

class DeepSeekContextualEnricher(
    private val client: DeepSeekClient,
    private val model: String,
    private val enabled: Boolean = true
) : ContextualEnricher {

    override suspend fun enrich(chunks: List<Chunk>, document: Document): List<Chunk> {
        if (!enabled || chunks.isEmpty()) return chunks
        val title = document.frontmatter.title
        val prefix = if (title.isNotBlank()) "[$title] " else ""
        return chunks.map { chunk ->
            try {
                val context = client.complete(
                    prompt = buildContextPrompt(chunk.text, title),
                    model = model
                )
                chunk.copy(
                    contextualizedText = "$prefix$context\n\n${chunk.text}"
                )
            } catch (e: Exception) {
                chunk
            }
        }
    }

    private fun buildContextPrompt(chunkText: String, docTitle: String): String =
        "Document title: \"$docTitle\"\n\n" +
        "Chunk content:\n$chunkText\n\n" +
        "Provide 1-2 sentences of context explaining what this chunk is about relative to the document."
}

class PassThroughEnricher : ContextualEnricher {
    override suspend fun enrich(chunks: List<Chunk>, document: Document): List<Chunk> = chunks
}
