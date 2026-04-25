package io.knowledgeos.indexing

import io.knowledgeos.retrieval.ScoredChunk
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document as LuceneDoc
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.*
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path

class Bm25Index(indexPath: Path) {

    private val analyzer = StandardAnalyzer()
    private val directory = FSDirectory.open(indexPath)
    private val writer = IndexWriter(directory, IndexWriterConfig(analyzer).apply {
        openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
    })
    private var manager = DirectoryReader.open(writer, true, true)
    private var searcher = IndexSearcher(manager)

    fun index(chunks: List<Chunk>) {
        for (chunk in chunks) {
            deleteByChunkId(chunk.id)
            val doc = LuceneDoc()
            doc.add(StringField("id", chunk.id, Field.Store.YES))
            doc.add(StringField("docPath", chunk.docPath, Field.Store.YES))
            doc.add(TextField("text", chunk.contextualizedText, Field.Store.YES))
            doc.add(StringField("genre", chunk.frontmatter.genre, Field.Store.YES))
            doc.add(StringField("topic", chunk.frontmatter.topic, Field.Store.YES))
            doc.add(StringField("library", chunk.frontmatter.library ?: "", Field.Store.YES))
            doc.add(TextField("title", chunk.frontmatter.title, Field.Store.YES))
            writer.addDocument(doc)
        }
        writer.commit()
        refreshSearcher()
    }

    fun search(query: String, topK: Int, genre: String? = null, topic: String? = null): List<ScoredChunk> {
        val qp = QueryParser("text", analyzer)
        val queryObj = qp.parse(query)

        val booleanQuery = BooleanQuery.Builder().add(queryObj, BooleanClause.Occur.MUST)
        genre?.takeIf { it.isNotBlank() }?.let {
            booleanQuery.add(TermQuery(Term("genre", it)), BooleanClause.Occur.FILTER)
        }
        topic?.takeIf { it.isNotBlank() }?.let {
            booleanQuery.add(TermQuery(Term("topic", it)), BooleanClause.Occur.FILTER)
        }

        return executeQuery(booleanQuery.build(), topK)
    }

    fun searchAll(topK: Int): List<ScoredChunk> = executeQuery(MatchAllDocsQuery(), topK)

    private fun executeQuery(query: org.apache.lucene.search.Query, topK: Int): List<ScoredChunk> {
        val topDocs = searcher.search(query, topK)
        return topDocs.scoreDocs.map { hit ->
            val luceneDoc = searcher.storedFields().document(hit.doc)
            ScoredChunk(
                chunkId = luceneDoc.get("id") ?: "",
                docPath = luceneDoc.get("docPath") ?: "",
                text = luceneDoc.get("text") ?: "",
                score = hit.score.toDouble()
            )
        }
    }

    fun deleteByDocPath(docPath: String) {
        writer.deleteDocuments(Term("docPath", docPath))
        writer.commit()
        refreshSearcher()
    }

    private fun deleteByChunkId(id: String) {
        writer.deleteDocuments(Term("id", id))
    }

    private fun refreshSearcher() {
        val oldReader = manager
        val newReader = DirectoryReader.openIfChanged(oldReader)
        if (newReader != null) {
            oldReader.close()
            manager = newReader
            searcher = IndexSearcher(newReader)
        }
    }

    fun isEmpty(): Boolean = manager.numDocs() == 0

    fun close() {
        manager.close()
        writer.close()
        directory.close()
    }
}
