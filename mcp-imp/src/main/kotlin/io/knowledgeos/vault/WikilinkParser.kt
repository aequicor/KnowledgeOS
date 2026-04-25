package io.knowledgeos.vault

object WikilinkParser {
    private val wikilinkRegex = Regex("""\[\[([^]]+)\]\]""")

    fun extract(body: String): List<String> =
        wikilinkRegex.findAll(body).map { it.groupValues[1].trim() }.toList()
}
