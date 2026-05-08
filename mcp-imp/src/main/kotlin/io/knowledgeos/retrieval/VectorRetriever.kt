package io.knowledgeos.retrieval

import io.knowledgeos.indexing.DeepSeekClient
import io.knowledgeos.indexing.LocalEmbedder
import io.knowledgeos.indexing.VectorIndex

class VectorRetriever(
    private val deepSeekClient: DeepSeekClient?,
    private val vectorIndex: VectorIndex,
    private val embeddingsModel: String,
    private val localEmbedder: LocalEmbedder? = null
) {
    suspend fun retrieve(query: String, topK: Int, filter: MetadataFilter = MetadataFilter.EMPTY): List<ScoredChunk> {
        val embedding = try {
            if (localEmbedder != null) {
                localEmbedder.embed(listOf(query)).firstOrNull()
            } else if (deepSeekClient != null) {
                deepSeekClient.embed(listOf(query), embeddingsModel).firstOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
        if (embedding == null) return emptyList()
        return vectorIndex.query(
            embedding = embedding,
            topK = topK * 2,
            filters = filter.filters
        )
    }
}
