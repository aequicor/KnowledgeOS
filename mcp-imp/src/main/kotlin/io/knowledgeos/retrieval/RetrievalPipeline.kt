package io.knowledgeos.retrieval

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class RetrievalPipeline(
    private val bm25Retriever: Bm25Retriever,
    private val vectorRetriever: VectorRetriever,
    private val rrfFusion: RrfFusion = RrfFusion,
    private val wikilinkRetriever: WikilinkRetriever,
    private val reranker: Reranker,
    private val defaultTopK: Int = 5,
    private val wikilinksHops: Int = 1
) {
    suspend fun retrieve(
        query: String,
        topK: Int = defaultTopK,
        filter: MetadataFilter = MetadataFilter.EMPTY
    ): List<ScoredChunk> = coroutineScope {
        val bm25Deferred = async { bm25Retriever.retrieve(query, topK, filter) }
        val vectorDeferred = async { vectorRetriever.retrieve(query, topK, filter) }

        val bm25Results = bm25Deferred.await()
        val vectorResults = vectorDeferred.await()

        val fused = rrfFusion.fuse(bm25Results, vectorResults, topK * 4)
        val expanded = wikilinkRetriever.expand(fused, wikilinksHops)
        reranker.rerank(query, expanded, topK)
    }
}
