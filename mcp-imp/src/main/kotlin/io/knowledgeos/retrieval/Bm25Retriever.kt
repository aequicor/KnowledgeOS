package io.knowledgeos.retrieval

import io.knowledgeos.indexing.Bm25Index

class Bm25Retriever(private val index: Bm25Index) {

    suspend fun retrieve(query: String, topK: Int, filter: MetadataFilter = MetadataFilter.EMPTY): List<ScoredChunk> {
        return index.search(
            query = query,
            topK = topK * 2,
            filters = filter.filters
        )
    }
}
