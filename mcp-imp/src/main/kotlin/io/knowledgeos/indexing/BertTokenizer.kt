package io.knowledgeos.indexing

import java.io.File
import java.io.InputStream

class BertTokenizer(vocabPath: String) {

    private val vocab: Map<String, Int>
    private val idToToken: Map<Int, String>
    private val unkId: Int
    private val clsId: Int
    private val sepId: Int
    private val padId: Int
    private val maxLength = 512

    init {
        val pairs = mutableMapOf<String, Int>()
        val reverse = mutableMapOf<Int, String>()
        File(vocabPath).forEachLine { line ->
            if (line.isNotBlank()) {
                pairs[line] = pairs.size
                reverse[pairs.size - 1] = line
            }
        }
        vocab = pairs
        idToToken = reverse
        unkId = vocab["[UNK]"] ?: 100
        clsId = vocab["[CLS]"] ?: 101
        sepId = vocab["[SEP]"] ?: 102
        padId = vocab["[PAD]"] ?: 0
    }

    fun tokenize(text: String): IntArray {
        val tokens = mutableListOf<Int>()
        tokens.add(clsId)

        val words = whitespaceTokenize(text.lowercase())
        for (word in words) {
            if (word.length > maxLength - 2) continue
            val subTokens = wordpieceTokenize(word)
            for (sub in subTokens) {
                if (tokens.size >= maxLength - 1) break
                tokens.add(sub)
            }
        }

        tokens.add(sepId)
        return tokens.toIntArray()
    }

    private fun whitespaceTokenize(text: String): List<String> {
        return text.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    private fun wordpieceTokenize(word: String): List<Int> {
        val chars = word.toCharArray().toMutableList()
        if (chars.isEmpty()) return listOf(unkId)
        if (chars.size > 100) return listOf(unkId)

        var start = 0
        val subTokens = mutableListOf<Int>()

        while (start < chars.size) {
            var end = chars.size
            var found = false
            while (start < end) {
                val sub = chars.subList(start, end).joinToString("")
                val key = if (start > 0) "##$sub" else sub
                val id = vocab[key]
                if (id != null) {
                    subTokens.add(id)
                    start = end
                    found = true
                    break
                }
                end--
            }
            if (!found) {
                subTokens.add(unkId)
                break
            }
        }
        return subTokens
    }

    fun getVocabSize(): Int = vocab.size
}
