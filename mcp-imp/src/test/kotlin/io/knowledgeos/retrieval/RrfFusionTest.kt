package io.knowledgeos.retrieval

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.Test

class RrfFusionTest {

    private fun chunk(id: String, score: Double = 1.0) = ScoredChunk(
        chunkId = id, docPath = "doc.md", text = "text", score = score
    )

    @Test
    fun `fuses two result lists with union set`() {
        val bm25 = listOf(chunk("a"), chunk("b"), chunk("c"))
        val vector = listOf(chunk("b"), chunk("c"), chunk("d"))
        val fused = RrfFusion.fuse(bm25, vector, topK = 10)

        fused.map { it.chunkId } shouldContainExactly listOf("b", "c", "a", "d")
    }

    @Test
    fun `respects topK limit`() {
        val bm25 = (1..10).map { chunk("bm25_$it") }
        val vector = (1..10).map { chunk("vec_$it") }
        val fused = RrfFusion.fuse(bm25, vector, topK = 5)

        fused.shouldHaveSize(5)
    }

    @Test
    fun `handles empty lists`() {
        val fused = RrfFusion.fuse(emptyList(), emptyList())
        fused.shouldHaveSize(0)
    }
}
