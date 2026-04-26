package io.knowledgeos.retrieval

import io.knowledgeos.indexing.Bm25Index
import io.knowledgeos.vault.WikilinkGraph

class WikilinkRetriever(
    private val wikilinkGraph: WikilinkGraph,
    private val bm25Index: Bm25Index
) {
    fun expand(chunks: List<ScoredChunk>, hops: Int = 1): List<ScoredChunk> {
        val seen = chunks.map { it.chunkId }.toMutableSet()
        val expanded = mutableListOf<ScoredChunk>()
        expanded.addAll(chunks)

        val docIds = chunks.map { extractDocId(it.chunkId) }.distinct()
        val sourceDocIds = docIds.toSet()
        val relatedDocs = docIds.flatMap { wikilinkGraph.expand(it, hops) }.toSet() - sourceDocIds

        for (relatedDoc in relatedDocs) {
            val relatedChunks = bm25Index.searchByDocId(relatedDoc, topK = 5)
            for (chunk in relatedChunks) {
                if (chunk.chunkId !in seen) {
                    seen.add(chunk.chunkId)
                    expanded.add(chunk.copy(score = chunk.score * 0.5))
                }
            }
        }
        return expanded
    }

    private fun extractDocId(chunkId: String): String = chunkId.substringBeforeLast("_")
}
