---
genre: reference
title: Переменные окружения
topic: configuration
confidence: high
source: human
updated: 2026-04
---

# Переменные окружения

## Vault

| Переменная | По умолчанию | Описание |
|---|---|---|
| `VAULT_PATH` | `/vault` | Путь к vault внутри контейнера |
| `VAULT_WATCH` | `true` | Включить file watcher |

## Server

| Переменная | По умолчанию | Описание |
|---|---|---|
| `SERVER_PORT` | `8080` | Порт MCP-сервера |

## Retrieval

| Переменная | По умолчанию | Описание |
|---|---|---|
| `RETRIEVAL_TOP_K` | `5` | Число возвращаемых чанков |
| `RETRIEVAL_CHUNK_SIZE` | `512` | Размер чанка в словах |
| `RETRIEVAL_CHUNK_OVERLAP` | `50` | Перекрытие чанков |
| `RETRIEVAL_WIKILINKS_HOPS` | `1` | Глубина разворота wikilinks |
| `RETRIEVAL_RERANKER_ENABLED` | `true` | Включить reranker |
| `RETRIEVAL_RERANKER_MODEL` | `cross-encoder/ms-marco-MiniLM-L-6-v2` | Модель reranker'а |

## LLM (OpenAI-compatible)

| Переменная | По умолчанию | Описание |
|---|---|---|
| `LLM_API_KEY` | — | API ключ |
| `LLM_BASE_URL` | `https://openrouter.ai/api/v1` | Базовый URL |
| `EMBEDDINGS_MODEL` | `deepseek/deepseek-v4-flash` | Модель для embeddings |
| `ENRICHMENT_MODEL` | `deepseek/deepseek-v4-flash` | Модель для enrichment |
| `ENRICHMENT_ENABLED` | `true` | Включить enrichment |

## ChromaDB

| Переменная | По умолчанию | Описание |
|---|---|---|
| `CHROMA_URL` | `http://chromadb:8000` | URL ChromaDB |
| `CHROMA_COLLECTION` | `vault_chunks` | Название коллекции |

## BM25

| Переменная | По умолчанию | Описание |
|---|---|---|
| `BM25_INDEX_PATH` | `/app/index` | Путь к индексу |
