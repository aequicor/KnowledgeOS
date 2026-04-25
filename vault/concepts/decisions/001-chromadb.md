---
genre: concept
title: ADR-001: ChromaDB как векторное хранилище
topic: architecture
confidence: high
source: human
updated: 2026-04
---

# ADR-001: ChromaDB как векторное хранилище

## Контекст

Нужно векторное хранилище для семантического поиска. Требования: Docker-развёртывание, REST API, open-source.

## Решение

Использовать ChromaDB вместо Qdrant, Milvus, Weaviate.

## Причины

1. Простой REST API без gRPC
2. Лёгкий Docker-образ
3. Поддержка фильтрации по метаданным
4. Бесплатный, open-source
5. Не требует отдельного облачного сервиса

## Последствия

- Хранилище внутри Docker Compose
- REST-клиент через Ktor
- Коллекция `vault_chunks` с метаданными genre/topic/path
