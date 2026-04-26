package io.knowledgeos.retrieval

import io.knowledgeos.indexing.Bm25Index
import io.knowledgeos.indexing.Chunk
import io.knowledgeos.vault.Document
import io.knowledgeos.vault.Frontmatter
import io.knowledgeos.vault.WikilinkGraph
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.Path as KPath

class WikilinkRetrieverTest {

    private lateinit var bm25: Bm25Index
    private lateinit var graph: WikilinkGraph
    private lateinit var retriever: WikilinkRetriever
    private val tmpDir = Files.createTempDirectory("wikiretriever")

    @BeforeEach
    fun setUp() {
        bm25 = Bm25Index(tmpDir)
        graph = WikilinkGraph()
        retriever = WikilinkRetriever(graph, bm25)

        // Index chunks for two docs
        bm25.index(listOf(
            chunk("alpha_0", "alpha.md", "alpha content about kotlin"),
            chunk("beta_0", "beta.md", "beta content about coroutines"),
            chunk("beta_1", "beta.md", "beta second section"),
            chunk("gamma_0", "gamma.md", "gamma unrelated doc"),
        ))
        // alpha links to beta
        graph.upsert(doc("alpha", "beta"))
        graph.upsert(doc("beta"))
        graph.upsert(doc("gamma"))
    }

    @AfterEach
    fun tearDown() {
        bm25.close()
        tmpDir.toFile().deleteRecursively()
    }

    @Test
    fun `expand adds chunks from wikilinked docs`() {
        val seeds = listOf(scored("alpha_0", "alpha.md"))
        val result = retriever.expand(seeds, hops = 1)

        val ids = result.map { it.chunkId }
        ids shouldContain "alpha_0"
        ids shouldContain "beta_0"
        ids shouldContain "beta_1"
    }

    @Test
    fun `expand does not add chunks from unlinked docs`() {
        val seeds = listOf(scored("alpha_0", "alpha.md"))
        val result = retriever.expand(seeds, hops = 1)

        result.none { it.chunkId == "gamma_0" } shouldBe true
    }

    @Test
    fun `expanded chunks have halved score relative to bm25 result`() {
        val seeds = listOf(scored("alpha_0", "alpha.md", score = 1.0))
        val result = retriever.expand(seeds, hops = 1)

        val betaChunk = result.first { it.chunkId == "beta_0" }
        val rawScore = bm25.searchByDocId("beta", topK = 10).first { it.chunkId == "beta_0" }.score
        betaChunk.score shouldBe rawScore * 0.5
    }

    @Test
    fun `expand with no wikilinks returns original chunks only`() {
        graph.upsert(doc("gamma"))
        val seeds = listOf(scored("gamma_0", "gamma.md"))
        val result = retriever.expand(seeds, hops = 1)

        result.shouldHaveSize(1)
        result[0].chunkId shouldBe "gamma_0"
    }

    @Test
    fun `expand does not duplicate already-seen chunks`() {
        val seeds = listOf(
            scored("alpha_0", "alpha.md"),
            scored("beta_0", "beta.md")
        )
        val result = retriever.expand(seeds, hops = 1)

        result.map { it.chunkId }.distinct().size shouldBe result.size
    }

    private fun chunk(id: String, docPath: String, text: String) = Chunk(
        id = id,
        docPath = docPath,
        text = text,
        contextualizedText = text,
        frontmatter = Frontmatter(),
        position = id.substringAfterLast("_").toInt()
    )

    private fun doc(id: String, vararg links: String) = Document(
        path = KPath("$id.md"),
        frontmatter = Frontmatter(),
        body = "",
        wikilinks = links.toList()
    )

    private fun scored(chunkId: String, docPath: String, score: Double = 1.0) =
        ScoredChunk(chunkId = chunkId, docPath = docPath, text = "text", score = score)
}
