package io.knowledgeos.vault

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test

class WikilinkParserTest {

    @Test
    fun `extracts wikilinks from text`() {
        val text = "See [[concepts/architecture]] and [[guidelines/writing-guidelines]]"
        val result = WikilinkParser.extract(text)
        result shouldContainExactly listOf("concepts/architecture", "guidelines/writing-guidelines")
    }

    @Test
    fun `returns empty list for text without wikilinks`() {
        val text = "Just regular text without links"
        WikilinkParser.extract(text).shouldBeEmpty()
    }

    @Test
    fun `handles multiple wikilinks on same line`() {
        val text = "[[a]] [[b]] [[c]]"
        val result = WikilinkParser.extract(text)
        result shouldContainExactly listOf("a", "b", "c")
    }

    @Test
    fun `trims whitespace around wikilink names`() {
        val text = "[[  spaced  ]] and [[normal]]"
        val result = WikilinkParser.extract(text)
        result shouldContainExactly listOf("spaced", "normal")
    }
}
