package io.knowledgeos.indexing

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

class DeepSeekClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val client: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun embed(texts: List<String>, model: String): List<FloatArray> {
        logger.trace { ">>> embed request: model=$model texts=${texts.size}" }
        return try {
            val response: HttpResponse = client.post("$baseUrl/embeddings") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(EmbedRequest(model = model, input = texts))
            }
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                logger.debug { "<<< embed error ${response.status.value}: $body" }
                throw DeepSeekException("Embedding API error ${response.status.value}: $body")
            }
            val result = response.body<EmbedResponse>()
            logger.trace { "<<< embed response: ${result.data.size} vectors" }
            result.data.map { it.embedding.toFloatArray() }
        } catch (e: DeepSeekException) {
            throw e
        } catch (e: Exception) {
            throw DeepSeekException("Embedding failed", e)
        }
    }

    suspend fun complete(prompt: String, model: String): String {
        logger.trace { ">>> complete request: model=$model prompt=${prompt.take(120).replace('\n', ' ')}…" }
        return try {
            val response: HttpResponse = client.post("$baseUrl/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(ChatRequest(
                    model = model,
                    messages = listOf(ChatMessage(role = "user", content = prompt))
                ))
            }
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                logger.debug { "<<< complete error ${response.status.value}: $body" }
                throw DeepSeekException("Completion API error ${response.status.value}: $body")
            }
            val result = response.body<ChatResponse>()
            val content = result.choices.firstOrNull()?.message?.content ?: ""
            logger.trace { "<<< complete response: ${content.take(200).replace('\n', ' ')}" }
            content
        } catch (e: DeepSeekException) {
            throw e
        } catch (e: Exception) {
            throw DeepSeekException("Completion failed", e)
        }
    }

    @Serializable
    private data class EmbedRequest(val model: String, val input: List<String>)

    @Serializable
    private data class EmbedResponse(val data: List<EmbeddingData>)

    @Serializable
    private data class EmbeddingData(val embedding: List<Float>)

    @Serializable
    private data class ChatRequest(val model: String, val messages: List<ChatMessage>)

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatResponse(val choices: List<Choice>)

    @Serializable
    private data class Choice(val message: ChatMessage)
}

class DeepSeekException(message: String, cause: Throwable? = null) : Exception(message, cause)
