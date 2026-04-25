package io.knowledgeos.retrieval

data class MetadataFilter(
    val genre: String? = null,
    val topic: String? = null
) {
    companion object {
        val EMPTY = MetadataFilter()
    }
}
