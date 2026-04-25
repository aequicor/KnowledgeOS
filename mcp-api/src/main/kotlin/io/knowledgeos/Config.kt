package io.knowledgeos

object Config {
    val vaultPath: String get() = System.getenv("VAULT_PATH") ?: "vault"
    val vaultWatch: Boolean get() = System.getenv("VAULT_WATCH")?.toBooleanStrictOrNull() ?: true
    val serverPort: Int get() = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8080

    val retrievalTopK: Int get() = System.getenv("RETRIEVAL_TOP_K")?.toIntOrNull() ?: 5
    val retrievalChunkSize: Int get() = System.getenv("RETRIEVAL_CHUNK_SIZE")?.toIntOrNull() ?: 512
    val retrievalChunkOverlap: Int get() = System.getenv("RETRIEVAL_CHUNK_OVERLAP")?.toIntOrNull() ?: 50
    val retrievalWikilinksHops: Int get() = System.getenv("RETRIEVAL_WIKILINKS_HOPS")?.toIntOrNull() ?: 1
    val retrievalRerankerEnabled: Boolean get() = System.getenv("RETRIEVAL_RERANKER_ENABLED")?.toBooleanStrictOrNull() ?: true
    val retrievalRerankerModel: String get() = System.getenv("RETRIEVAL_RERANKER_MODEL") ?: "cross-encoder/ms-marco-MiniLM-L-6-v2"

    val deepseekApiKey: String get() = System.getenv("DEEPSEEK_API_KEY") ?: ""
    val deepseekBaseUrl: String get() = System.getenv("DEEPSEEK_BASE_URL") ?: "https://openrouter.ai/api/v1"
    val embeddingsModel: String get() = System.getenv("EMBEDDINGS_MODEL") ?: "deepseek/deepseek-v4-flash"
    val embeddingsLocal: Boolean get() = System.getenv("EMBEDDINGS_LOCAL")?.toBooleanStrictOrNull() ?: false
    val embeddingsLocalModelPath: String get() = System.getenv("EMBEDDINGS_LOCAL_MODEL_PATH") ?: "/app/model/onnx/model.onnx"
    val embeddingsLocalVocabPath: String get() = System.getenv("EMBEDDINGS_LOCAL_VOCAB_PATH") ?: "/app/model/onnx/vocab.txt"
    val enrichmentModel: String get() = System.getenv("ENRICHMENT_MODEL") ?: "deepseek/deepseek-v4-flash"
    val enrichmentEnabled: Boolean get() = System.getenv("ENRICHMENT_ENABLED")?.toBooleanStrictOrNull() ?: true

    val chromaUrl: String get() = System.getenv("CHROMA_URL") ?: "http://localhost:8000"
    val chromaCollection: String get() = System.getenv("CHROMA_COLLECTION") ?: "vault_chunks"

    val bm25IndexPath: String get() = System.getenv("BM25_INDEX_PATH") ?: "/app/index"
}
