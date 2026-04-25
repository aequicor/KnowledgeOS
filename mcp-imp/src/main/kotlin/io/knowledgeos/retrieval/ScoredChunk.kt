package io.knowledgeos.retrieval

data class ScoredChunk(
    val chunkId: String,
    val docPath: String,
    val text: String,
    val score: Double
)
