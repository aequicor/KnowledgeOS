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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
            val resp: ChromaCollectionResponse = client.post(collectionsBase()) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ChromaCreateRequest(name = collectionName)))
            }.body()
            resp.id
        } catch (e: Exception) {
            val resp: ChromaCollectionResponse = client.get("${collectionsBase()}/$collectionName").body()
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
            buildMetadata(chunk.docPath, chunk.frontmatter.raw)
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

    suspend fun query(embedding: FloatArray, topK: Int, filters: Map<String, String> = emptyMap()): List<ScoredChunk> {
        val id = collectionId()
        val embedList = embedding.toList()
        val whereMap = filters.filterValues { it.isNotBlank() }.filterKeys { it.isNotBlank() }
        val where = whereMap.takeIf { it.isNotEmpty() }

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
        val metadatas = response.metadatas?.firstOrNull() ?: emptyList<Map<String, JsonElement>?>()

        return ids.indices.map { i ->
            val docPath = (metadatas.getOrNull(i)?.get("docPath") as? JsonPrimitive)?.content ?: ""
            ScoredChunk(
                chunkId = ids[i],
                docPath = docPath,
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

    private fun buildMetadata(docPath: String, frontmatter: Map<String, JsonElement>): Map<String, JsonPrimitive> {
        val out = linkedMapOf<String, JsonPrimitive>()
        out["docPath"] = JsonPrimitive(docPath)
        for ((key, value) in frontmatter) {
            if (key == "docPath") continue
            when (value) {
                is JsonPrimitive -> {
                    val s = value.content
                    if (s.isNotBlank()) out[key] = JsonPrimitive(s)
                }
                is JsonArray -> {
                    val joined = value.mapNotNull { (it as? JsonPrimitive)?.content }
                        .filter { it.isNotBlank() }
                        .joinToString(",")
                    if (joined.isNotBlank()) out[key] = JsonPrimitive(joined)
                }
                is JsonObject -> Unit
            }
        }
        return out
    }

    @Serializable
    private data class ChromaCreateRequest(val name: String)

    @Serializable
    private data class ChromaCollectionResponse(val id: String, val name: String)

    @Serializable
    private data class ChromaUpsertRequest(
        val ids: List<String>,
        val embeddings: List<List<Float>>,
        val metadatas: List<Map<String, JsonPrimitive>>,
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
        val metadatas: List<List<Map<String, JsonElement>?>>? = null
    )
}
