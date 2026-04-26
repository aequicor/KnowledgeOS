package io.knowledgeos.retrieval

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.oshai.kotlinlogging.KotlinLogging
import io.knowledgeos.indexing.BertTokenizer
import java.nio.LongBuffer

interface Reranker {
    suspend fun rerank(query: String, chunks: List<ScoredChunk>, topK: Int): List<ScoredChunk>
}

class IdentityReranker : Reranker {
    override suspend fun rerank(query: String, chunks: List<ScoredChunk>, topK: Int): List<ScoredChunk> {
        return chunks.sortedByDescending { it.score }.take(topK)
    }
}

class OnnxReranker(
    modelPath: String,
    vocabPath: String,
) : Reranker {
    private val log = KotlinLogging.logger {}
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val tokenizer: BertTokenizer

    init {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
        }
        session = env.createSession(modelPath, opts)
        tokenizer = BertTokenizer(vocabPath)
        log.info { "OnnxReranker loaded: $modelPath" }
    }

    override suspend fun rerank(query: String, chunks: List<ScoredChunk>, topK: Int): List<ScoredChunk> {
        if (chunks.isEmpty()) return emptyList()
        return try {
            val scores = scoreAll(query, chunks)
            chunks.zip(scores)
                .sortedByDescending { (_, score) -> score }
                .take(topK)
                .map { (chunk, score) -> chunk.copy(score = score) }
        } catch (e: Exception) {
            log.warn(e) { "Reranker inference failed, falling back to score-based ranking" }
            chunks.sortedByDescending { it.score }.take(topK)
        }
    }

    private fun scoreAll(query: String, chunks: List<ScoredChunk>): List<Double> {
        val pairs = chunks.map { tokenizer.tokenizePair(query, it.text) }
        val maxLen = pairs.maxOf { (ids, _) -> ids.size }
        val batchSize = pairs.size

        val flatInputIds = LongArray(batchSize * maxLen)
        val flatAttentionMask = LongArray(batchSize * maxLen)
        val flatTokenTypeIds = LongArray(batchSize * maxLen)

        for (i in pairs.indices) {
            val (ids, typeIds) = pairs[i]
            val offset = i * maxLen
            for (j in ids.indices) {
                flatInputIds[offset + j] = ids[j].toLong()
                flatAttentionMask[offset + j] = 1L
                flatTokenTypeIds[offset + j] = typeIds[j].toLong()
            }
        }

        val shape = longArrayOf(batchSize.toLong(), maxLen.toLong())
        val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(flatInputIds), shape)
        val attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(flatAttentionMask), shape)
        val tokenTypeIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(flatTokenTypeIds), shape)

        return try {
            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeIdsTensor
            )
            session.run(inputs).use { out ->
                extractScores(out.get(0).value, batchSize)
            }
        } finally {
            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractScores(raw: Any, batchSize: Int): List<Double> {
        // [batch_size, 1] — standard cross-encoder output
        if (raw is Array<*> && raw.isNotEmpty() && raw[0] is FloatArray) {
            return (raw as Array<FloatArray>).map { it[0].toDouble() }
        }
        // [batch_size] — flat logits
        if (raw is FloatArray) return raw.map { it.toDouble() }
        return List(batchSize) { 0.0 }
    }

    fun close() {
        session.close()
    }
}
