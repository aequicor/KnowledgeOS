package io.knowledgeos.vault

import java.nio.file.Path
import kotlin.collections.component1
import kotlin.collections.component2

class WikilinkGraph {

    private val outgoing: MutableMap<String, MutableSet<String>> = mutableMapOf()
    private val incoming: MutableMap<String, MutableSet<String>> = mutableMapOf()

    fun upsert(doc: Document) {
        remove(doc.id)
        val targets = resolveTargets(doc.wikilinks)
        outgoing[doc.id] = targets.toMutableSet()
        for (target in targets) {
            incoming.getOrPut(target) { mutableSetOf() }.add(doc.id)
        }
    }

    fun remove(docId: String) {
        val oldTargets = outgoing.remove(docId) ?: emptySet()
        for (target in oldTargets) {
            incoming[target]?.remove(docId)
            if (incoming[target].isNullOrEmpty()) {
                incoming.remove(target)
            }
        }
        incoming.remove(docId)
    }

    fun expand(docId: String, hops: Int = 1): Set<String> {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(docId to 0)
        while (queue.isNotEmpty()) {
            val (current, depth) = queue.removeFirst()
            if (current in visited) continue
            visited.add(current)
            if (depth < hops) {
                for (neighbor in (outgoing[current] ?: emptySet()) + (incoming[current] ?: emptySet())) {
                    if (neighbor !in visited) {
                        queue.add(neighbor to depth + 1)
                    }
                }
            }
        }
        return visited
    }

    fun getOutgoing(docId: String): Set<String> = outgoing[docId]?.toSet() ?: emptySet()

    fun clear() {
        outgoing.clear()
        incoming.clear()
    }

    private fun resolveTargets(wikilinks: List<String>): Set<String> =
        wikilinks.map { it.replace(".md", "").trim() }.toSet()
}
