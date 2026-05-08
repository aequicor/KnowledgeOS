package io.knowledgeos

import io.knowledgeos.indexing.*
import io.knowledgeos.retrieval.*
import io.knowledgeos.server.McpServer
import io.knowledgeos.tools.GetDocTool
import io.knowledgeos.tools.ListDocsTool
import io.knowledgeos.tools.SearchDocsTool
import io.knowledgeos.tools.UpdateDocTool
import io.knowledgeos.tools.WriteDocTool
import io.knowledgeos.vault.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Duration.Companion.milliseconds
import kotlin.io.path.Path as KPath

private val logger = KotlinLogging.logger {}

fun main() = runBlocking {
    logger.info { "KnowledgeOS starting..." }

    val vaultPath = KPath(Config.vaultPath)
    logger.info { "Vault path: $vaultPath" }

    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val deepSeekClient = if (Config.llmApiKey.isNotBlank() && Config.llmApiKey != "sk-...") {
        DeepSeekClient(Config.llmBaseUrl, Config.llmApiKey, httpClient)
    } else {
        logger.warn { "LLM_API_KEY not configured, enrichment disabled" }
        null
    }

    val localEmbedder = if (Config.embeddingsLocal) {
        try {
            val tokenizer = BertTokenizer(Config.embeddingsLocalVocabPath)
            val embedder = LocalEmbedder(Config.embeddingsLocalModelPath, tokenizer)
            logger.info { "Local embedder loaded, dim=${embedder.embeddingDim()}" }
            embedder
        } catch (e: Exception) {
            logger.error(e) { "Failed to load local embedder" }
            null
        }
    } else {
        logger.info { "Using remote embeddings: ${Config.embeddingsModel}" }
        null
    }

    val vaultReader = VaultReader(vaultPath)
    val chunker = Chunker(Config.retrievalChunkSize, Config.retrievalChunkOverlap)
    val enricher = if (deepSeekClient != null && Config.enrichmentEnabled) {
        DeepSeekContextualEnricher(deepSeekClient, Config.enrichmentModel, true)
    } else {
        PassThroughEnricher()
    }

    val bm25IndexPath = KPath(Config.bm25IndexPath)
    bm25IndexPath.parent?.let { java.nio.file.Files.createDirectories(it) }
    val bm25Index = Bm25Index(bm25IndexPath)
    val vectorIndex = VectorIndex(Config.chromaUrl, Config.chromaCollection, httpClient)

    val wikilinkGraph = WikilinkGraph()

    val watcher = VaultWatcher(vaultPath, vaultReader)

    val indexPipeline = IndexPipeline(
        vaultReader = vaultReader,
        chunker = chunker,
        enricher = enricher,
        deepSeekClient = if (!Config.embeddingsLocal) deepSeekClient else null,
        embeddingsModel = Config.embeddingsModel,
        bm25Index = bm25Index,
        vectorIndex = vectorIndex,
        wikilinkGraph = wikilinkGraph,
        watcher = watcher,
        localEmbedder = localEmbedder
    )

    vectorIndex.ensureCollection()

    if (Config.vaultWatch) {
        indexPipeline.start(vaultPath)
    } else {
        val documents = vaultReader.readAll()
        for (doc in documents) {
            wikilinkGraph.upsert(doc)
        }
    }

    val bm25Retriever = Bm25Retriever(bm25Index)
    val vectorRetriever = VectorRetriever(deepSeekClient, vectorIndex, Config.embeddingsModel, localEmbedder)

    val wikilinkRetriever = WikilinkRetriever(wikilinkGraph, bm25Index)
    val reranker: Reranker = if (Config.retrievalRerankerEnabled) {
        try {
            OnnxReranker(Config.rerankerLocalModelPath, Config.rerankerLocalVocabPath).also {
                logger.info { "OnnxReranker enabled: ${Config.rerankerLocalModelPath}" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load OnnxReranker, falling back to IdentityReranker" }
            IdentityReranker()
        }
    } else {
        logger.info { "Reranker disabled, using IdentityReranker" }
        IdentityReranker()
    }

    val retrievalPipeline = RetrievalPipeline(
        bm25Retriever = bm25Retriever,
        vectorRetriever = vectorRetriever,
        wikilinkRetriever = wikilinkRetriever,
        reranker = reranker,
        defaultTopK = Config.retrievalTopK,
        wikilinksHops = Config.retrievalWikilinksHops
    )

    val searchDocsTool = SearchDocsTool(retrievalPipeline)
    val writeDocTool = WriteDocTool(vaultPath)
    val updateDocTool = UpdateDocTool(vaultPath)
    val getDocTool = GetDocTool(vaultPath)
    val listDocsTool = ListDocsTool(vaultPath)

    val mcpServer = McpServer(
        port = Config.serverPort,
        searchDocsTool = searchDocsTool,
        writeDocTool = writeDocTool,
        updateDocTool = updateDocTool,
        getDocTool = getDocTool,
        listDocsTool = listDocsTool,
        enableDnsRebindingProtection = Config.mcpDnsRebindingProtection,
        allowedHosts = Config.mcpAllowedHosts,
        allowedOrigins = Config.mcpAllowedOrigins,
    )

    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            logger.info { "Shutting down..." }
            indexPipeline.stop()
            bm25Index.close()
            localEmbedder?.close()
            (reranker as? OnnxReranker)?.close()
            httpClient.close()
            mcpServer.stop()
        }
    })

    mcpServer.start()
    logger.info { "KnowledgeOS MCP Server ready on port ${Config.serverPort}" }

    while (true) {
        kotlinx.coroutines.delay(1000.milliseconds)
    }
}
