package io.knowledgeos.retrieval

interface Reranker {
    suspend fun rerank(query: String, chunks: List<ScoredChunk>, topK: Int): List<ScoredChunk>
}

class IdentityReranker : Reranker {
    override suspend fun rerank(query: String, chunks: List<ScoredChunk>, topK: Int): List<ScoredChunk> {
        return chunks.sortedByDescending { it.score }.take(topK)
    }
}

class OnnxReranker(
    private val modelPath: String,
    private val enabled: Boolean = true
) : Reranker {
    override suspend fun rerank(query: String, chunks: List<ScoredChunk>, topK: Int): List<ScoredChunk> {
        if (!enabled || chunks.isEmpty()) {
            return chunks.sortedByDescending { it.score }.take(topK)
        }
        // TODO: Implement ONNX cross-encoder reranking
        // For now, fall back to score-based ranking
        return chunks.sortedByDescending { it.score }.take(topK)
    }
}
