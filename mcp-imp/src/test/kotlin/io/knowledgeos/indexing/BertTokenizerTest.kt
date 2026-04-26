package io.knowledgeos.indexing

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class BertTokenizerPairTest {

    // Build vocab where [CLS]=101, [SEP]=102 to match BertTokenizer defaults
    private val vocabFile = run {
        // Pad vocab with empty entries 0..100 so [CLS] lands at index 101
        val entries = (0..100).map { "" }.toMutableList<String>()
        entries.add("[CLS]")  // index 101
        entries.add("[SEP]")  // index 102
        entries.add("[PAD]")  // index 103
        entries.add("[UNK]")  // index 104
        entries.add("hello")
        entries.add("world")
        entries.add("a")
        entries.add("b")
        val f = kotlin.io.path.createTempFile("vocab", ".txt")
        f.toFile().writeText(entries.joinToString("\n"))
        f.toString()
    }
    private val tokenizer = BertTokenizer(vocabFile)

    @Test
    fun `tokenizePair starts with CLS and ends with SEP`() {
        val single = tokenizer.tokenize("hello")
        val (ids, _) = tokenizer.tokenizePair("hello", "world")

        ids.first() shouldBe single.first()  // same CLS id
        ids.last() shouldBe single.last()    // same SEP id
    }

    @Test
    fun `tokenizePair segment 0 for query, segment 1 for passage`() {
        val (_, typeIds) = tokenizer.tokenizePair("a", "b")

        typeIds.first() shouldBe 0   // CLS is type 0
        typeIds.last() shouldBe 1    // final SEP is type 1
    }

    @Test
    fun `tokenizePair result is longer than single tokenize`() {
        val single = tokenizer.tokenize("hello world")
        val (pair, _) = tokenizer.tokenizePair("hello", "world")

        pair.size shouldNotBe single.size
        (pair.size > single.size) shouldBe true
    }
}
