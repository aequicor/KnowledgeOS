# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Run tests for a specific module
./gradlew :mcp-api:test
./gradlew :mcp-imp:test

# Run the server locally (without Docker)
./gradlew :mcp-api:run

# Docker: start all services (requires docker-compose.local.yml)
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d

# Docker: build + restart all services
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build

# Docker: rebuild a specific MCP service
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build mcp-my-app
```

## Architecture

KnowledgeOS is an MCP server that gives AI agents access to a documentation vault. The agent reads docs before acting and writes new guidelines after learning something new — the vault is the single source of truth, code is a byproduct.

**Two Gradle modules:**

- `mcp-api` — entry point: MCP protocol handler, HTTP routes, three MCP tools (`search_docs`, `write_guideline`, `update_doc`)
- `mcp-imp` — implementation: vault file watching, frontmatter/wikilink parsing, chunking, contextual enrichment, BM25 + vector retrieval, RRF fusion, reranking

`mcp-api` depends on `mcp-imp`. Convention plugin `buildsrc.convention.kotlin-jvm` applies Kotlin JVM + toolchain 21 to both modules.

**Retrieval pipeline** (query → chunks):
`Metadata filter → BM25 (Lucene) → Vector (ChromaDB) → RRF Fusion → Wikilink expansion → Reranker (ONNX)`

**Key external dependencies:**
- MCP protocol: [modelcontextprotocol/kotlin-sdk](https://github.com/modelcontextprotocol/kotlin-sdk)
- Vector store: ChromaDB (Docker, REST)
- Embeddings + Enrichment: DeepSeek `deepseek/deepseek-v4-flash`
- Reranker: `cross-encoder/ms-marco-MiniLM-L-6-v2` via ONNX Runtime (no Python needed)
- BM25: Apache Lucene

**Vault** (`vaults/<project>/` directory) is bind-mounted into the container at `/vault`. File watcher keeps the in-memory wikilink graph and BM25/vector indexes incrementally updated. Wikilinks (`[[doc-name]]`) are expanded on-demand when a document is fetched. Multiple projects each get their own container, vault directory, and indexes.

## Configuration

All config via `.env` (copy from `.env.example`). Key variables: `DEEPSEEK_API_KEY`, `VAULT_PATH`, `CHROMA_URL`. See `.env.example` for the full list.

## Module registration

When adding a new Gradle module, register it in `settings.gradle.kts` (`include(":module-name")`) and apply the convention plugin in its `build.gradle.kts` (`id("buildsrc.convention.kotlin-jvm")`). Add new dependencies to `gradle/libs.versions.toml`.
