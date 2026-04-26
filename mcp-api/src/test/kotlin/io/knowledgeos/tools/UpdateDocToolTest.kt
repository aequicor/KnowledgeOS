package io.knowledgeos.tools

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class UpdateDocToolTest {

    private val vaultDir = Files.createTempDirectory("vault")
    private val tool = UpdateDocTool(vaultDir)

    @BeforeEach
    fun setUp() {
        vaultDir.resolve("doc.md").toFile().writeText(
            "---\ngenre: guideline\ntitle: My Doc\n---\n\nOriginal body content."
        )
    }

    @AfterEach
    fun tearDown() {
        vaultDir.toFile().deleteRecursively()
    }

    @Test
    fun `updates document content`() {
        runBlocking {
            val result = tool.execute(UpdateDocTool.UpdateParams("doc.md", "---\ngenre: guideline\ntitle: My Doc\n---\n\nNew body."))
            result shouldContain "updated"
            vaultDir.resolve("doc.md").toFile().readText() shouldContain "New body."
        }
    }

    @Test
    fun `preserve_frontmatter keeps original frontmatter when content has no frontmatter`() {
        runBlocking {
            val result = tool.execute(UpdateDocTool.UpdateParams("doc.md", "Just the new body.", preserve_frontmatter = true))
            result shouldContain "updated"

            val saved = vaultDir.resolve("doc.md").toFile().readText()
            saved shouldStartWith "---"
            saved shouldContain "genre: guideline"
            saved shouldContain "Just the new body."
        }
    }

    @Test
    fun `preserve_frontmatter does not duplicate frontmatter when content already has it`() {
        runBlocking {
            val contentWithFm = "---\ngenre: how-to\ntitle: Updated\n---\n\nNew body."
            tool.execute(UpdateDocTool.UpdateParams("doc.md", contentWithFm, preserve_frontmatter = true))

            val saved = vaultDir.resolve("doc.md").toFile().readText()
            saved shouldContain "genre: how-to"
            saved shouldContain "New body."
        }
    }

    @Test
    fun `returns error for non-existent document`() {
        runBlocking {
            val result = tool.execute(UpdateDocTool.UpdateParams("missing.md", "content"))
            result shouldContain "error"
            result shouldContain "not found"
        }
    }

    @Test
    fun `blocks path traversal`() {
        runBlocking {
            val result = tool.execute(UpdateDocTool.UpdateParams("../etc/passwd", "evil"))
            result shouldContain "error"
            result shouldContain "traversal"
        }
    }
}
