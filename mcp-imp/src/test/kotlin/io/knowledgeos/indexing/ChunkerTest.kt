package io.knowledgeos.indexing

import io.knowledgeos.vault.Document
import io.knowledgeos.vault.Frontmatter
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.io.path.Path as KPath

class ChunkerTest {

    private fun document(body: String) = Document(
        path = KPath("test.md"),
        frontmatter = Frontmatter.ofStrings("title" to "Test"),
        body = body,
        wikilinks = emptyList()
    )

    @Test
    fun `chunks empty body to empty list`() {
        val chunks = Chunker().chunk(document(""))
        chunks.shouldHaveSize(0)
    }

    @Test
    fun `chunks short text into single chunk`() {
        val chunks = Chunker(chunkSize = 100).chunk(document("Short text"))
        chunks.shouldHaveSize(1)
        chunks[0].id shouldBe "test_0"
        chunks[0].docPath shouldBe "test.md"
    }

    @Test
    fun `chunks long text into multiple chunks`() {
        val words = (1..600).joinToString(" ") { "word$it" }
        val chunks = Chunker(chunkSize = 200, chunkOverlap = 20).chunk(document(words))
        chunks.shouldNotBeNull()
        chunks.size shouldBe 4
    }

    @Test
    fun `preserves heading-based boundaries`() {
        val text = "# Section 1\nContent A\n## Section 2\nContent B\n"
        val chunks = Chunker(chunkSize = 500).chunk(document(text))
        chunks.size shouldBe 2
        chunks[0].text shouldBe "# Section 1\nContent A"
        chunks[1].text shouldBe "## Section 2\nContent B"
    }
}
