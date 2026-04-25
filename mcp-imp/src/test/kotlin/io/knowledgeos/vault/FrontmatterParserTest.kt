package io.knowledgeos.vault

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FrontmatterParserTest {

    @Test
    fun `parses frontmatter and body`() {
        val content = """
---
genre: concept
title: Test Document
topic: testing
confidence: high
source: human
---

This is the body text.
""".trimIndent()

        val (fm, body) = FrontmatterParser.parse(content)

        fm.genre shouldBe "concept"
        fm.title shouldBe "Test Document"
        fm.topic shouldBe "testing"
        fm.confidence shouldBe "high"
        fm.source shouldBe "human"
        body.trim() shouldBe "This is the body text."
    }

    @Test
    fun `handles missing frontmatter gracefully`() {
        val content = "Just a body without frontmatter."
        val (fm, body) = FrontmatterParser.parse(content)

        fm.genre shouldBe "guideline"
        fm.title shouldBe ""
        body.trim() shouldBe "Just a body without frontmatter."
    }

    @Test
    fun `parses frontmatter with library field`() {
        val content = """
---
genre: guideline
title: Rules
topic: database
library: exposed
---
Body
""".trimIndent()

        val (fm, _) = FrontmatterParser.parse(content)
        fm.library shouldBe "exposed"
    }
}
