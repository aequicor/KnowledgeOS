package io.knowledgeos.indexing

import io.knowledgeos.vault.Frontmatter
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class Bm25IndexTest {

    private lateinit var index: Bm25Index
    private val tmpDir = Files.createTempDirectory("bm25test")

    @BeforeEach
    fun setUp() {
        index = Bm25Index(tmpDir)
    }

    @AfterEach
    fun tearDown() {
        index.close()
        tmpDir.toFile().deleteRecursively()
    }

    private fun chunk(
        id: String,
        docPath: String,
        text: String,
        frontmatter: Frontmatter = Frontmatter.ofStrings("genre" to "guideline", "topic" to "test")
    ) = Chunk(
        id = id,
        docPath = docPath,
        text = text,
        contextualizedText = text,
        frontmatter = frontmatter,
        position = id.substringAfterLast("_").toInt()
    )

    @Test
    fun `searchByDocId returns only chunks for requested doc`() {
        index.index(listOf(
            chunk("alpha_0", "alpha.md", "alpha first chunk"),
            chunk("alpha_1", "alpha.md", "alpha second chunk"),
            chunk("beta_0", "beta.md", "beta content here"),
        ))

        val results = index.searchByDocId("alpha", topK = 10)
        results.shouldHaveSize(2)
        results.all { it.docPath == "alpha.md" } shouldBe true
    }

    @Test
    fun `searchByDocId returns empty for unknown doc`() {
        index.index(listOf(chunk("alpha_0", "alpha.md", "some text")))
        index.searchByDocId("unknown", topK = 10).shouldHaveSize(0)
    }

    @Test
    fun `searchByDocId does not leak chunks from other docs`() {
        index.index(listOf(
            chunk("foo_0", "foo.md", "foo content"),
            chunk("foobar_0", "foobar.md", "foobar content"),
        ))

        val results = index.searchByDocId("foo", topK = 10)
        results.shouldHaveSize(1)
        results[0].chunkId shouldBe "foo_0"
    }

    @Test
    fun `deleteByDocPath removes all chunks for that doc`() {
        index.index(listOf(
            chunk("alpha_0", "alpha.md", "alpha text"),
            chunk("beta_0", "beta.md", "beta text"),
        ))
        index.deleteByDocPath("alpha.md")

        index.searchByDocId("alpha", topK = 10).shouldHaveSize(0)
        index.searchByDocId("beta", topK = 10).shouldHaveSize(1)
    }

    @Test
    fun `search filters by arbitrary frontmatter field`() {
        index.index(listOf(
            chunk("a_0", "a.md", "shared keyword content",
                frontmatter = Frontmatter.ofStrings("category" to "research")),
            chunk("b_0", "b.md", "shared keyword content",
                frontmatter = Frontmatter.ofStrings("category" to "tutorial")),
        ))

        val researchOnly = index.search("shared", topK = 10, filters = mapOf("category" to "research"))
        researchOnly.shouldHaveSize(1)
        researchOnly[0].docPath shouldBe "a.md"

        val noFilter = index.search("shared", topK = 10)
        noFilter.size shouldBe 2
    }

    @Test
    fun `search with empty filter returns all matches`() {
        index.index(listOf(
            chunk("x_0", "x.md", "alpha"),
            chunk("y_0", "y.md", "alpha"),
        ))

        val results = index.search("alpha", topK = 10, filters = emptyMap())
        results.size shouldBe 2
    }
}
