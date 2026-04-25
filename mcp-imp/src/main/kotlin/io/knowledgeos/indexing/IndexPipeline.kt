package io.knowledgeos.indexing

import io.knowledgeos.vault.Document
import io.knowledgeos.vault.VaultReader
import io.knowledgeos.vault.VaultWatcher
import io.knowledgeos.vault.WikilinkGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

class IndexPipeline(
    private val vaultReader: VaultReader,
    private val chunker: Chunker,
    private val enricher: ContextualEnricher,
    private val deepSeekClient: DeepSeekClient?,
    private val embeddingsModel: String,
    private val bm25Index: Bm25Index,
    private val vectorIndex: VectorIndex,
    private val wikilinkGraph: WikilinkGraph,
    private val watcher: VaultWatcher,
    private val localEmbedder: LocalEmbedder? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start(vaultPath: Path) {
        scope.launch {
            if (bm25Index.isEmpty()) {
                logger.info { "BM25 index empty, performing full reindex" }
                reindex(vaultPath)
            } else {
                val indexedPaths = bm25Index.allDocPaths()
                val allDocs = vaultReader.readAll()
                val missing = allDocs.filter { it.path.toString() !in indexedPaths }
                if (missing.isNotEmpty()) {
                    logger.info { "Found ${missing.size} new documents not in index, indexing..." }
                    for (doc in missing) {
                        indexDocument(doc)
                    }
                }
            }
        }

        watcher.onCreated = { doc -> handleCreate(doc) }
        watcher.onModified = { doc -> handleModify(doc) }
        watcher.onDeleted = { path -> handleDelete(path) }
        watcher.start()
        logger.info { "Index pipeline started, watching $vaultPath" }
    }

    fun stop() {
        watcher.stop()
    }

    private suspend fun reindex(vaultPath: Path) {
        val documents = vaultReader.readAll()
        logger.info { "Indexing ${documents.size} documents" }
        for (doc in documents) {
            indexDocument(doc)
        }
    }

    private fun handleCreate(doc: Document) {
        scope.launch {
            logger.debug { "Indexing created document: ${doc.path}" }
            indexDocument(doc)
        }
    }

    private fun handleModify(doc: Document) {
        scope.launch {
            logger.debug { "Re-indexing modified document: ${doc.path}" }
            deleteFromIndexes(doc.path.toString())
            indexDocument(doc)
        }
    }

    private fun handleDelete(path: Path) {
        scope.launch {
            val docPath = path.toString()
            logger.debug { "Deleting from indexes: $docPath" }
            deleteFromIndexes(docPath)
            val docId = path.fileName.toString().removeSuffix(".md")
            wikilinkGraph.remove(docId)
        }
    }

    private suspend fun indexDocument(doc: Document) {
        val chunks = chunker.chunk(doc)
        if (chunks.isEmpty()) return

        val enriched = enricher.enrich(chunks, doc)

        val texts = enriched.map { it.contextualizedText }
        val embeddings = try {
            if (localEmbedder != null) {
                localEmbedder.embed(texts)
            } else if (deepSeekClient != null) {
                deepSeekClient.embed(texts, embeddingsModel)
            } else {
                logger.warn { "No embedder configured, indexing via BM25 only" }
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get embeddings for ${doc.path}, indexing via BM25 only" }
            null
        }

        bm25Index.index(enriched)
        if (embeddings != null) {
            vectorIndex.upsert(enriched, embeddings)
        }
        wikilinkGraph.upsert(doc)
    }

    private suspend fun deleteFromIndexes(docPath: String) {
        bm25Index.deleteByDocPath(docPath)
        vectorIndex.deleteByDocPath(docPath)
    }
}
