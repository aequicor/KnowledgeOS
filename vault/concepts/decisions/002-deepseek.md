---
genre: concept
title: ADR-002: DeepSeek для embeddings и enrichment
topic: architecture
confidence: high
source: human
updated: 2026-04
---

# ADR-002: DeepSeek для embeddings и enrichment

## Контекст

Нужна модель для генерации embeddings (векторизация чанков и запросов) и contextual enrichment (обогащение чанков контекстом документа).

## Решение

Использовать DeepSeek v4 через OpenRouter API.

## Причины

1. Единый провайдер для embeddings и LLM
2. Доступ через OpenRouter без отдельной инфраструктуры
3. Качественные embeddings для русского и английского языков
4. Контекстное окно позволяет enrichment целых чанков

## Последствия

- Зависимость от внешнего API (OpenRouter)
- Стоимость за токены
- Возможность переключения через переменные окружения
