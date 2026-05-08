package io.knowledgeos.retrieval

data class MetadataFilter(val filters: Map<String, String> = emptyMap()) {

    fun isEmpty(): Boolean = filters.isEmpty()

    companion object {
        val EMPTY = MetadataFilter()

        fun of(vararg pairs: Pair<String, String>): MetadataFilter =
            MetadataFilter(pairs.toMap())
    }
}
