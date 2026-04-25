package io.knowledgeos.retrieval

object RrfFusion {
    private const val K = 60.0

    fun fuse(bm25Results: List<ScoredChunk>, vectorResults: List<ScoredChunk>, topK: Int = 20): List<ScoredChunk> {
        val scores = mutableMapOf<String, Double>()
        val chunks = mutableMapOf<String, ScoredChunk>()

        addRanked(bm25Results, scores, chunks)
        addRanked(vectorResults, scores, chunks)

        return chunks.values
            .sortedByDescending { scores[it.chunkId] ?: 0.0 }
            .take(topK)
    }

    private fun addRanked(
        results: List<ScoredChunk>,
        scores: MutableMap<String, Double>,
        chunks: MutableMap<String, ScoredChunk>
    ) {
        for ((rank, chunk) in results.withIndex()) {
            val rrfScore = 1.0 / (K + rank + 1)
            scores.compute(chunk.chunkId) { _, old -> (old ?: 0.0) + rrfScore }
            if (chunk.chunkId !in chunks) {
                chunks[chunk.chunkId] = chunk
            }
        }
    }
}
