---
genre: concept
title: Архитектура KnowledgeOS
topic: architecture
confidence: high
source: human
updated: 2026-04
---

# Архитектура KnowledgeOS

KnowledgeOS — MCP-сервер, дающий AI-агенту доступ к документационному vault через гибридный retrieval.

## Компоненты

### Obsidian Vault
Мозг системы. Единственный источник истины. Хранит документацию по жанрам Diátaxis.

### MCP Server
Нервная система. Индексирует vault, обрабатывает запросы агента. BM25 индекс на диске, векторный в ChromaDB.

### AI Agent (OpenCode)
Исполнитель. Пишет код. Читает документы перед действием, обновляет после.

## Retrieval pipeline

```
query → Metadata filter → BM25 + Vector → RRF Fusion → Wikilinks → Rerank → top-K chunks
```

## Технологии

| Компонент | Технология |
|---|---|
| MCP Server | Kotlin + kotlin-sdk |
| BM25 | Apache Lucene |
| Vector | ChromaDB |
| Embeddings | DeepSeek v4 |
| Reranker | ONNX cross-encoder |
| Transport | Ktor + JSON-RPC |
