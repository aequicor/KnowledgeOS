package io.knowledgeos.retrieval

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private val log = KotlinLogging.logger {}

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
        val t0 = System.currentTimeMillis()

        val bm25Deferred = async { bm25Retriever.retrieve(query, topK, filter) }
        val vectorDeferred = async { vectorRetriever.retrieve(query, topK, filter) }
        val bm25Results = bm25Deferred.await()
        val vectorResults = vectorDeferred.await()
        val t1 = System.currentTimeMillis()
        log.debug { "retrieve query='${query.take(60)}' bm25=${bm25Results.size} vector=${vectorResults.size} retrieval=${t1 - t0}ms" }

        val fused = rrfFusion.fuse(bm25Results, vectorResults, topK * 4)
        log.debug { "retrieve fused=${fused.size}" }

        val expanded = wikilinkRetriever.expand(fused, wikilinksHops)
        log.debug { "retrieve expanded=${expanded.size} (+${expanded.size - fused.size} wikilink)" }

        val ranked = reranker.rerank(query, expanded, topK)
        val t2 = System.currentTimeMillis()
        log.debug { "retrieve final=${ranked.size} total=${t2 - t0}ms top=${ranked.firstOrNull()?.score?.let { "%.4f".format(it) }}" }

        ranked
    }
}
