package io.knowledgeos.tools

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class WriteDocToolTest {

    private lateinit var vaultDir: java.nio.file.Path
    private lateinit var tool: WriteDocTool

    @BeforeEach
    fun setUp() {
        vaultDir = Files.createTempDirectory("vault-write-doc")
        tool = WriteDocTool(vaultDir)
    }

    @AfterEach
    fun tearDown() {
        vaultDir.toFile().deleteRecursively()
    }

    @Test
    fun `writes document with arbitrary frontmatter`() {
        runBlocking {
            val frontmatter: Map<String, JsonElement> = mapOf(
                "title" to JsonPrimitive("Hello"),
                "kind" to JsonPrimitive("rule"),
                "tags" to JsonArray(listOf(JsonPrimitive("db"), JsonPrimitive("sql")))
            )
            val result = tool.execute(WriteDocTool.WriteParams(
                path = "rules/db.md",
                content = "Body content here.",
                frontmatter = frontmatter
            ))

            result shouldContain "\"status\":\"created\""
            result shouldContain "rules/db.md"

            val written = vaultDir.resolve("rules/db.md").toFile().readText()
            written shouldContain "title: Hello"
            written shouldContain "kind: rule"
            written shouldContain "tags:"
            written shouldContain "  - db"
            written shouldContain "  - sql"
            written shouldContain "Body content here."
        }
    }

    @Test
    fun `writes document without frontmatter`() {
        runBlocking {
            val result = tool.execute(WriteDocTool.WriteParams(
                path = "notes/plain.md",
                content = "Just markdown.",
                frontmatter = null
            ))

            result shouldContain "\"status\":\"created\""
            val written = vaultDir.resolve("notes/plain.md").toFile().readText()
            written shouldNotContain "---"
            written shouldContain "Just markdown."
        }
    }

    @Test
    fun `creates parent directories`() {
        runBlocking {
            tool.execute(WriteDocTool.WriteParams(
                path = "a/b/c/deep.md",
                content = "deep",
                frontmatter = null
            ))

            vaultDir.resolve("a/b/c/deep.md").toFile().exists() shouldBe true
        }
    }

    @Test
    fun `blocks path traversal`() {
        runBlocking {
            val result = tool.execute(WriteDocTool.WriteParams(
                path = "../escape.md",
                content = "evil",
                frontmatter = null
            ))

            result shouldContain "\"status\":\"error\""
            result shouldContain "traversal"
        }
    }

    @Test
    fun `rejects non-md path`() {
        runBlocking {
            val result = tool.execute(WriteDocTool.WriteParams(
                path = "notes/file.txt",
                content = "x",
                frontmatter = null
            ))

            result shouldContain "\"status\":\"error\""
            result shouldContain ".md"
        }
    }

    @Test
    fun `rejects blank path`() {
        runBlocking {
            val result = tool.execute(WriteDocTool.WriteParams(
                path = "",
                content = "x",
                frontmatter = null
            ))

            result shouldContain "\"status\":\"error\""
        }
    }

    @Test
    fun `quotes values that need quoting`() {
        runBlocking {
            val frontmatter: Map<String, JsonElement> = mapOf(
                "title" to JsonPrimitive("Has: colon"),
                "literal" to JsonPrimitive("true"),
                "number_like" to JsonPrimitive("42")
            )
            tool.execute(WriteDocTool.WriteParams(
                path = "edge.md",
                content = "body",
                frontmatter = frontmatter
            ))

            val written = vaultDir.resolve("edge.md").toFile().readText()
            written shouldContain "\"Has: colon\""
            written shouldContain "\"true\""
            written shouldContain "\"42\""
        }
    }
}
