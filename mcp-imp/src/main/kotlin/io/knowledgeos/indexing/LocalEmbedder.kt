package io.knowledgeos.indexing

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.LongBuffer

private val embedderLog = KotlinLogging.logger {}

class LocalEmbedder(
    private val modelPath: String,
    private val tokenizer: BertTokenizer
) {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
        }
        session = env.createSession(modelPath, opts)
        embedderLog.info { "Local embedder loaded: $modelPath (${session.numInputs} inputs, ${session.numOutputs} outputs)" }
    }

    fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()

        val tokenized = texts.map { tokenizer.tokenize(it) }
        val maxLen = tokenized.maxOf { it.size }

        val batchSize = tokenized.size
        val flatInputIds = LongArray(batchSize * maxLen)
        val flatAttentionMask = LongArray(batchSize * maxLen)
        val flatTokenTypeIds = LongArray(batchSize * maxLen)

        for (i in tokenized.indices) {
            val tokens = tokenized[i]
            val offset = i * maxLen
            for (j in tokens.indices) {
                flatInputIds[offset + j] = tokens[j].toLong()
                flatAttentionMask[offset + j] = 1L
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
            val result: List<FloatArray>
            session.run(inputs).use { out ->
                val tensor = out.get(0) as OnnxTensor
                val raw = tensor.value
                result = extractEmbeddings(raw)
            }
            result
        } finally {
            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractEmbeddings(raw: Any): List<FloatArray> {
        if (raw is Array<*> && raw.isNotEmpty() && raw[0] is FloatArray) {
            return (raw as Array<FloatArray>).toList()
        }
        if (raw is Array<*> && raw.isNotEmpty() && raw[0] is Array<*> && (raw[0] as Array<*>).isNotEmpty() && (raw[0] as Array<*>)[0] is FloatArray) {
            val arr3d = raw as Array<Array<FloatArray>>
            return arr3d.map { it[0] }
        }
        return emptyList()
    }

    fun embeddingDim(): Int {
        val test = embed(listOf("test")).first()
        return test.size
    }

    fun close() {
        session.close()
    }
}
