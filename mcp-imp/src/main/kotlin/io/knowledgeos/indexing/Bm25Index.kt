package io.knowledgeos.indexing

import io.knowledgeos.retrieval.ScoredChunk
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
            doc.add(StringField("docId", chunk.id.substringBeforeLast("_"), Field.Store.NO))
            doc.add(StringField("docPath", chunk.docPath, Field.Store.YES))
            doc.add(TextField("text", chunk.contextualizedText, Field.Store.YES))

            val title = chunk.frontmatter.title
            if (title.isNotBlank()) {
                doc.add(TextField("title", title, Field.Store.YES))
            }

            for ((key, value) in chunk.frontmatter.raw) {
                addFrontmatterField(doc, key, value)
            }

            writer.addDocument(doc)
        }
        writer.commit()
        refreshSearcher()
    }

    private fun addFrontmatterField(doc: LuceneDoc, key: String, value: JsonElement) {
        val fieldName = "fm.$key"
        when (value) {
            is JsonPrimitive -> addPrimitive(doc, fieldName, value)
            is JsonArray -> value.forEach { item ->
                if (item is JsonPrimitive) addPrimitive(doc, fieldName, item)
            }
            is JsonObject -> Unit
        }
    }

    private fun addPrimitive(doc: LuceneDoc, fieldName: String, value: JsonPrimitive) {
        val str = value.content
        if (str.isNotBlank()) {
            doc.add(StringField(fieldName, str, Field.Store.YES))
        }
    }

    fun search(query: String, topK: Int, filters: Map<String, String> = emptyMap()): List<ScoredChunk> {
        val qp = QueryParser("text", analyzer)
        val queryObj = qp.parse(QueryParser.escape(query))

        val booleanQuery = BooleanQuery.Builder().add(queryObj, BooleanClause.Occur.MUST)
        for ((key, value) in filters) {
            if (key.isBlank() || value.isBlank()) continue
            booleanQuery.add(TermQuery(Term("fm.$key", value)), BooleanClause.Occur.FILTER)
        }

        return executeQuery(booleanQuery.build(), topK)
    }

    fun searchAll(topK: Int): List<ScoredChunk> = executeQuery(MatchAllDocsQuery(), topK)

    fun searchByDocId(docId: String, topK: Int): List<ScoredChunk> =
        executeQuery(TermQuery(Term("docId", docId)), topK)

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

    fun allDocPaths(): Set<String> {
        val docPaths = mutableSetOf<String>()
        for (ctx in manager.leaves()) {
            val leafReader = ctx.reader()
            for (i in 0 until leafReader.maxDoc()) {
                val doc = leafReader.storedFields().document(i)
                val path = doc.get("docPath")
                if (path != null) docPaths.add(path)
            }
        }
        return docPaths
    }

    fun close() {
        manager.close()
        writer.close()
        directory.close()
    }
}
