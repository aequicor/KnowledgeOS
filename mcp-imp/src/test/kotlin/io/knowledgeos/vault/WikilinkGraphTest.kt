package io.knowledgeos.vault

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path as KPath

class WikilinkGraphTest {

    private fun doc(id: String, vararg links: String) = Document(
        path = KPath("$id.md"),
        frontmatter = Frontmatter(),
        body = "",
        wikilinks = links.toList()
    )

    @Test
    fun `expand returns doc itself and linked docs at hop 1`() {
        val graph = WikilinkGraph()
        graph.upsert(doc("a", "b", "c"))
        graph.upsert(doc("b", "d"))
        graph.upsert(doc("c"))
        graph.upsert(doc("d"))

        val result = graph.expand("a", hops = 1)
        result shouldContainExactlyInAnyOrder listOf("a", "b", "c")
    }

    @Test
    fun `expand with 2 hops includes transitive links`() {
        val graph = WikilinkGraph()
        graph.upsert(doc("a", "b"))
        graph.upsert(doc("b", "c"))
        graph.upsert(doc("c"))

        val result = graph.expand("a", hops = 2)
        result shouldContainExactlyInAnyOrder listOf("a", "b", "c")
    }

    @Test
    fun `remove cleans up outgoing and incoming edges`() {
        val graph = WikilinkGraph()
        graph.upsert(doc("a", "b"))
        graph.upsert(doc("b"))

        graph.remove("a")
        val result = graph.expand("b", hops = 1)
        result shouldContainExactlyInAnyOrder listOf("b")
    }
}
