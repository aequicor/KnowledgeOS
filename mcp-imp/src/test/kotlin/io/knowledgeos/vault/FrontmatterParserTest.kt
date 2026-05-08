package io.knowledgeos.vault

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FrontmatterParserTest {

    @Test
    fun `parses arbitrary scalar fields and body`() {
        val content = """
---
genre: concept
title: Test Document
topic: testing
confidence: high
source: human
custom_field: anything
---

This is the body text.
""".trimIndent()

        val (fm, body) = FrontmatterParser.parse(content)

        fm.string("genre") shouldBe "concept"
        fm.string("title") shouldBe "Test Document"
        fm.string("topic") shouldBe "testing"
        fm.string("confidence") shouldBe "high"
        fm.string("source") shouldBe "human"
        fm.string("custom_field") shouldBe "anything"
        fm.title shouldBe "Test Document"
        body.trim() shouldBe "This is the body text."
    }

    @Test
    fun `handles missing frontmatter as empty`() {
        val content = "Just a body without frontmatter."
        val (fm, body) = FrontmatterParser.parse(content)

        fm.isEmpty() shouldBe true
        fm.string("genre") shouldBe null
        fm.title shouldBe ""
        body.trim() shouldBe "Just a body without frontmatter."
    }

    @Test
    fun `parses arbitrary user-defined field`() {
        val content = """
---
title: Rules
kind: rule
applies_to: backend
---
Body
""".trimIndent()

        val (fm, _) = FrontmatterParser.parse(content)
        fm.string("kind") shouldBe "rule"
        fm.string("applies_to") shouldBe "backend"
    }

    @Test
    fun `parses list field as string list`() {
        val content = """
---
title: Tagged
tags:
  - db
  - sql
  - performance
---
Body
""".trimIndent()

        val (fm, _) = FrontmatterParser.parse(content)
        fm.stringList("tags") shouldContainExactlyInAnyOrder listOf("db", "sql", "performance")
    }

    @Test
    fun `merges related from frontmatter and body section`() {
        val content = """
---
title: Doc
related:
  - foo
---

Some body.

## Связанные документы
- [[bar]]
- [[baz]]
""".trimIndent()

        val (fm, _) = FrontmatterParser.parse(content)
        fm.related shouldContainExactlyInAnyOrder listOf("foo", "bar", "baz")
    }

    @Test
    fun `handles malformed yaml as empty frontmatter`() {
        val content = """
---
this is :: not valid : yaml
---
Body content.
""".trimIndent()

        val (fm, body) = FrontmatterParser.parse(content)
        fm.isEmpty() shouldBe true
        body.trim() shouldBe "Body content."
    }
}
