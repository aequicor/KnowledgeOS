package io.knowledgeos.indexing

import io.knowledgeos.retrieval.ScoredChunk
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class VectorIndex(
    private val chromaUrl: String,
    private val collectionName: String,
    private val client: HttpClient,
    private val tenant: String = "default_tenant",
    private val database: String = "default_database"
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var collectionId: String? = null

    private fun collectionsBase() = "$chromaUrl/api/v2/tenants/$tenant/databases/$database/collections"

    suspend fun ensureCollection() {
        collectionId = try {
            val resp: ChromaCollectionResponse = client.get("${collectionsBase()}/$collectionName").body()
            resp.id
        } catch (e: Exception) {
            val resp: ChromaCollectionResponse = client.post(collectionsBase()) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ChromaCreateRequest(name = collectionName)))
            }.body()
            resp.id
        }
    }

    private suspend fun collectionId(): String =
        collectionId ?: run { ensureCollection(); collectionId!! }

    suspend fun upsert(chunks: List<Chunk>, embeddings: List<FloatArray>) {
        if (chunks.isEmpty()) return
        val id = collectionId()
        val ids = chunks.map { it.id }
        val metadatas = chunks.map { chunk ->
            MetadataEntry(
                docPath = chunk.docPath,
                genre = chunk.frontmatter.genre,
                topic = chunk.frontmatter.topic
            )
        }
        val documents = chunks.map { it.contextualizedText }
        val embedList = embeddings.map { it.toList() }

        client.post("${collectionsBase()}/$id/upsert") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ChromaUpsertRequest(
                ids = ids, embeddings = embedList, metadatas = metadatas, documents = documents
            )))
        }
    }

    suspend fun query(embedding: FloatArray, topK: Int, genre: String? = null, topic: String? = null): List<ScoredChunk> {
        val id = collectionId()
        val embedList = embedding.toList()
        val where = buildMap<String, String> {
            genre?.takeIf { it.isNotBlank() }?.let { put("genre", it) }
            topic?.takeIf { it.isNotBlank() }?.let { put("topic", it) }
        }.takeIf { it.isNotEmpty() }

        val response: ChromaQueryResponse = client.post("${collectionsBase()}/$id/query") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ChromaQueryRequest(
                queryEmbeddings = listOf(embedList),
                nResults = topK,
                where = where,
                include = listOf("documents", "metadatas", "distances")
            )))
        }.body()

        val ids = response.ids.firstOrNull() ?: emptyList()
        val distances = response.distances?.firstOrNull() ?: emptyList()
        val documents = response.documents?.firstOrNull() ?: emptyList<String?>()
        val metadatas = response.metadatas?.firstOrNull() ?: emptyList<MetadataEntry?>()

        return ids.indices.map { i ->
            ScoredChunk(
                chunkId = ids[i],
                docPath = metadatas[i]?.docPath ?: "",
                text = documents[i] ?: "",
                score = 1.0 / (1.0 + (distances.getOrElse(i) { 0.0 }))
            )
        }
    }

    suspend fun deleteByDocPath(docPath: String) {
        try {
            val id = collectionId()
            client.post("${collectionsBase()}/$id/delete") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(mapOf("where" to mapOf("docPath" to docPath))))
            }
        } catch (e: Exception) { }
    }

    @Serializable
    private data class ChromaCreateRequest(val name: String)

    @Serializable
    private data class ChromaCollectionResponse(val id: String, val name: String)

    @Serializable
    private data class MetadataEntry(
        val docPath: String,
        val genre: String,
        val topic: String
    )

    @Serializable
    private data class ChromaUpsertRequest(
        val ids: List<String>,
        val embeddings: List<List<Float>>,
        val metadatas: List<MetadataEntry>,
        val documents: List<String>
    )

    @Serializable
    private data class ChromaQueryRequest(
        @SerialName("query_embeddings") val queryEmbeddings: List<List<Float>>,
        @SerialName("n_results") val nResults: Int,
        val where: Map<String, String>? = null,
        val include: List<String>? = null
    )

    @Serializable
    private data class ChromaQueryResponse(
        val ids: List<List<String>>,
        val distances: List<List<Double>>? = null,
        val documents: List<List<String?>>? = null,
        val metadatas: List<List<MetadataEntry?>>? = null
    )
}
