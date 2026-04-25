package io.knowledgeos.tools

import io.knowledgeos.Config
import io.knowledgeos.retrieval.MetadataFilter
import io.knowledgeos.retrieval.RetrievalPipeline
import io.knowledgeos.retrieval.ScoredChunk
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SearchDocsTool(private val pipeline: RetrievalPipeline) {

    @Serializable
    data class SearchParams(
        val query: String,
        val genre: String? = null,
        val topic: String? = null
    )

    @Serializable
    data class SearchResult(
        val chunkId: String,
        val docPath: String,
        val text: String,
        val score: Double
    )

    suspend fun execute(params: SearchParams): String {
        val filter = MetadataFilter(genre = params.genre, topic = params.topic)
        val chunks = pipeline.retrieve(
            query = params.query,
            topK = Config.retrievalTopK,
            filter = filter
        )
        val results = chunks.map { SearchResult(it.chunkId, it.docPath, it.text, it.score) }
        return Json.encodeToString(results)
    }
}
