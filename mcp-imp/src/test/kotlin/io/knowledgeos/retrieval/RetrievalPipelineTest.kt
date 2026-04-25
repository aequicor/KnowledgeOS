package io.knowledgeos.retrieval

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RrfFusionExtendedTest {

    private fun chunk(id: String) = ScoredChunk(chunkId = id, docPath = "$id.md", text = "text", score = 0.5)

    @Test
    fun `common items ranked higher than unique ones`() {
        val bm25 = listOf(chunk("shared"), chunk("bm25_only"))
        val vector = listOf(chunk("shared"), chunk("vec_only"))
        val fused = RrfFusion.fuse(bm25, vector, topK = 10)

        fused[0].chunkId shouldBe "shared"
        fused.size shouldBe 3
    }

    @Test
    fun `empty vector results return only bm25`() {
        val bm25 = listOf(chunk("a"), chunk("b"))
        val fused = RrfFusion.fuse(bm25, emptyList(), topK = 10)

        fused.shouldHaveSize(2)
        fused.map { it.chunkId } shouldBe listOf("a", "b")
    }

    @Test
    fun `empty bm25 returns only vector`() {
        val vector = listOf(chunk("x"), chunk("y"))
        val fused = RrfFusion.fuse(emptyList(), vector, topK = 10)

        fused.shouldHaveSize(2)
    }
}
