# KnowledgeOS

> Система знаний для AI-агентов, которая умнеет по мере работы.

Документация — первична. Код — артефакт.

---

## Идея

Традиционная разработка выглядит так:

```
Мысль → Код
```

KnowledgeOS меняет этот порядок:

```
Мысль → Документ (источник истины) → AI Agent → Код
```

AI-агент не просто выполняет задачи — он читает проектную документацию перед каждым действием и обновляет её после выполнения задачи. Столкнулся с нестандартным кейсом — написал гайдлайн. В следующий раз найдёт его сам.

Хочешь изменить поведение — правь документ, не код.

---

## Архитектура

```
┌──────────────────────────────────────────────────────────┐
│               Оператор ИИ-агента                         │
│          ставит задачи · ревьюит · правит доки           │
└──────────┬──────────────────────────────┬────────────────┘
           │ задача                       │ ревью
           ▼                             ▼
┌──────────────────┐             ┌──────────────────┐
│   Markdown Vault │◄────────────│    AI Agent      │
│                  │  читает /   │    (OpenCode)    │
│  любые .md       │  редактирует│                  │
│  любая структура │             │  1. search_docs  │
│  любой YAML      │             │  2. читает доки  │
│  frontmatter     │             │  3. пишет код    │
│                  │             │  4. write_doc    │
│  [[wikilinks]]   │             │  5. update_doc   │
└────────┬─────────┘             └────────┬─────────┘
         │ файлы                          │ search_docs()
         ▼                               ▼
┌──────────────────────────────────────────────────────────┐
│                  MCP Server (Kotlin)                     │
│                                                          │
│   [Metadata filter]                                      │
│   BM25 ──────┐                                          │
│   Vector ────┼──► RRF Fusion ──► Wikilinks ──► Rerank ──► chunks │
│              │                                          │
└──────────────────────────────────────────────────────────┘
                                          │
                                          ▼
                                   ┌─────────────┐
                                   │  Codebase   │
                                   │  (артефакт) │
                                   └─────────────┘
```

### Три компонента

**Vault** — мозг системы. Единственный источник истины. Произвольная иерархия `.md`-файлов с произвольным YAML-frontmatter, связанная через `[[wikilinks]]`. KnowledgeOS не предписывает ни структуру папок, ни схему frontmatter. Опциональные рекомендации — в [docs/STRUCTURE-RECOMMENDATIONS.md](docs/STRUCTURE-RECOMMENDATIONS.md).

**MCP Server** — нервная система. Индексирует vault, обрабатывает запросы агента. BM25 индекс персистируется на локальный диск, векторный индекс — в ChromaDB. Сервер можно перезапустить без полной переиндексации. Граф wikilinks поддерживается в памяти и обновляется инкрементально через file watcher; wikilinks разворачиваются on-demand при получении документа — если в чанке есть [[ссылки]], сервер автоматически загружает связанные документы. Реализован на Kotlin.

**AI Agent (OpenCode)** — исполнитель. Единственный кто пишет код. Перед каждым действием читает документацию. После — обновляет документы и создаёт новые через инструменты: `search_docs`, `write_doc`, `update_doc`, `get_doc`, `list_docs`.

---

## Структура Vault

KnowledgeOS не предписывает структуру vault — индексируется произвольная иерархия `.md`-файлов с любым YAML-frontmatter. Любые поля frontmatter попадают в индекс как фильтруемые метаданные (`fm.<key>`), любая папка является валидным расположением.

### Опциональная рекомендованная структура

Если вы хотите готовый шаблон, оптимизированный под AI-агентов, см. [docs/STRUCTURE-RECOMMENDATIONS.md](docs/STRUCTURE-RECOMMENDATIONS.md). Краткий пример:

```
vault/
├── _index.md                # точка входа (опционально)
├── rules/                   # короткие правила: "агент ДОЛЖЕН ..."
├── patterns/                # переиспользуемые how-to с примерами кода
├── decisions/               # ADR — почему выбрали X
├── reference/               # API/схемы/конфиги
└── domain/                  # бизнес-знания
```

Минимальный полезный frontmatter:

```yaml
---
title: Database transaction rules
description: When and how to wrap DB mutations in transactions.
kind: rule
tags: [db, sql, transactions]
updated: 2026-05
---
```

Текущий vault в репозитории (`vault/`) — пример другого варианта структуры (Diátaxis с `concepts/`, `guidelines/`, и т.д.); он работает, но это не «единственно правильный» способ. Любая другая структура тоже работает.

### Wikilinks как граф знаний

Связи между документами прописываются явно через `[[название-документа]]`. Это и есть граф знаний — без LLM-извлечения, без погрешностей, с полным контролем.

```markdown
# Правила работы с транзакциями

Всегда оборачивай мутации в транзакцию. См. [[db-schema]].
При ошибках → [[error-handling]].
Паттерн репозитория: [[repository-pattern]].
```

При поиске по `guidelines/database.md` сервер автоматически подтягивает все прилинкованные документы — агент получает связанный кластер, а не изолированный чанк.

---

## MCP Server

### Инструменты агента

Агент видит пять инструментов:

```
search_docs(
  query:   String,                       // что ищем
  filters: Map<String, String>? = null,  // опц. фильтр по любым frontmatter-полям
                                          //   например {"kind": "rule", "tags": "db"}
) → List<Chunk>

write_doc(
  path:        String,                  // путь относительно vault, должен оканчиваться на .md
  content:     String,                  // markdown (без frontmatter)
  frontmatter: Map<String, JsonElement>? = null,  // опц. произвольный YAML
) → { status, path }

update_doc(
  path:                 String,         // путь к существующему документу
  content:              String,         // новое содержимое
  preserve_frontmatter: Boolean = false,// сохранить старый frontmatter, если новый отсутствует
) → { status, path }

get_doc(path: String) → markdown
list_docs(directory: String? = null) → List<String>
```

**Фильтры — произвольные.** `filters` сравнивает значения с любыми полями frontmatter ваших документов. Если у вас есть `kind: rule` — фильтруйте `{"kind": "rule"}`. Если `genre: concept` (старая схема) — `{"genre": "concept"}`. KnowledgeOS не знает заранее, какие у вас поля.

### Retrieval pipeline

```
query
  │
  ├─► [Metadata filter]     опц. сужение по любым frontmatter-полям
  │
  ├─► [BM25]                точные термины, имена функций, либ
  │
  ├─► [Vector search]       семантически похожие чанки
  │
  ├─► [RRF Fusion]          объединяем результаты оба ретривера
  │
  ├─► [Wikilink expansion]  подтягиваем [[связанные]] документы
  │
  └─► [Reranker]            финальный отбор top-K
```

**BM25** — exact match, работает без GPU, быстрый, хорош для кода.
**Vector** — семантика, ловит синонимы и перефразировки.
**Wikilinks** — граф расширения, подтягивает связанный контекст автоматически.
**Reranker** — cross-encoder переранжирует объединённый результат, оставляет только релевантное.

### Индексация

File watcher следит за vault. При изменении файла — инкрементальное обновление BM25, векторного индекса и графа wikilinks в памяти. Граф wikilinks не персистируется на диск: при каждом запросе к документу сервер on-demand разворачивает [[ссылки]] и загружает связанные документы. Contextual enrichment: перед индексацией каждый чанк обогащается контекстом через LLM — повышает точность retrieval.

---

## Как агент умнеет

```
1. Агент получает задачу
        ↓
2. search_docs("задача") → читает релевантные документы
        ↓
3. Выполняет задачу по правилам
        ↓
4. Столкнулся с чем-то новым или нестандартным
        ↓
5. write_doc("rules/new-rule.md", ...) → новый .md по выбранному пути
   update_doc(...) → обновляет существующий документ, добавляет [[ссылку]]
        ↓
6. Следующая похожая задача → агент найдёт это знание сам
```

Петля замкнута. Агент накапливает знания автоматически; человек фокусируется на концептах, архитектурных решениях и ревью.

---

## Технический стек

| Компонент | Технология |
|---|---|
| MCP Server | [kotlin-sdk](https://github.com/modelcontextprotocol/kotlin-sdk) |
| BM25 | Apache Lucene (Kotlin DSL) |
| Vector store | ChromaDB |
| Развёртывание | Docker Compose / Docker Desktop |
| Embeddings + Enrichment | DeepSeek `deepseek/deepseek-v4-flash` |
| Reranker | `cross-encoder/ms-marco-MiniLM-L-6-v2` (ONNX) |
| File watching | Java WatchService |
| Frontmatter | Kaml (Kotlin YAML) |
| Wikilink parser | Regex + `[[...]]` |
| AI Agent | OpenCode |
| Vault | Obsidian |

---

## Быстрый запуск

Без клонирования репозитория — только Docker.

### 1. Создайте файлы

```
my-project/
├── knowledge-docker-compose.yml
├── .env
└── vault/              ← сюда кладёте документацию
```

**`knowledge-docker-compose.yml`**

```yaml
services:
  chromadb:
    image: chromadb/chroma
    volumes:
      - chroma-data:/chroma/chroma
    restart: unless-stopped
    environment:
      - ALLOW_RESET=true

  mcp:
    image: aequicor/knowledgeos:latest
    ports:
      - "8081:8080"
    env_file: .env
    environment:
      - CHROMA_URL=http://chromadb:8000
      - CHROMA_COLLECTION=vault_my_project
      - BM25_INDEX_PATH=/app/index
      - VAULT_PATH=/vault
    volumes:
      - ./vault:/vault
      - bm25-index:/app/index
      - bm25-models:/app/model         # ONNX models (download once, persist across rebuilds)
    depends_on:
      chromadb:
        condition: service_started
    restart: unless-stopped

volumes:
  chroma-data:
  bm25-index:
  bm25-models:
```

**`.env`**

```dotenv
LLM_API_KEY=sk-...
LLM_BASE_URL=https://openrouter.ai/api/v1
```

> **Важно:** добавьте `.env` в `.gitignore`, чтобы ключ не попал в репозиторий.

### 2. Запустите

```bash
docker compose -f knowledge-docker-compose.yml up -d
```

MCP-сервер будет доступен на `http://localhost:8081/mcp`.

### 3. Обновление до новой версии

```bash
docker compose -f knowledge-docker-compose.yml pull
docker compose -f knowledge-docker-compose.yml up -d
```

---

## Несколько проектов

Каждый проект — отдельный MCP-контейнер со своим vault, индексами и портом.
Общий только ChromaDB (один инстанс, разные коллекции).

### Структура

```
knowledgeos/
├── docker-compose.yml                  # в git — только ChromaDB
├── docker-compose.local.example.yml    # в git — шаблон для копирования
├── docker-compose.local.yml            # в .gitignore — ваша настройка проектов
├── .env                                # в .gitignore — общие переменные (API ключи, ...)
├── vaults/                             # vault каждого проекта
│   ├── my-app/                         # vault проекта "my-app"
│   │   ├── _INDEX.md
│   │   ├── concepts/
│   │   ├── guidelines/
│   │   └── ...
│   └── another-project/                # vault проекта "another-project"
│       └── ...
```

### Настройка нового проекта

Добавьте блок в `docker-compose.local.yml`:

```yaml
services:
  mcp-new-project:
    image: aequicor/knowledgeos:latest
    ports:
      - "8083:8080"                              # уникальный порт
    env_file: .env
    environment:
      - CHROMA_URL=http://chromadb:8000
      - CHROMA_COLLECTION=vault_new_project      # уникальная коллекция
      - BM25_INDEX_PATH=/app/index
      - VAULT_PATH=/vault
    volumes:
      - ./vaults/new-project:/vault              # уникальная папка vault
      - bm25-new-project:/app/index              # уникальный volume для BM25
      - bm25-models:/app/model                    # ONNX models
    depends_on:
      chromadb:
        condition: service_started
    restart: unless-stopped

volumes:
  bm25-new-project:
  bm25-models:
```

### Что должно быть уникальным для каждого проекта

| Параметр | Зачем |
|---|---|
| `ports` (host port) | Чтобы каждый MCP был доступен по своему порту |
| `CHROMA_COLLECTION` | Векторные индексы проектов не перемешиваются |
| `volumes` (vault path) | У каждого проекта свой vault |
| `volumes` (BM25 named volume) | BM25 индексы не пересекаются |
| `service name` | Уникальное имя Docker-сервиса |

### Подключение нескольких проектов в OpenCode

```json
{
  "mcpServers": {
    "knowledge-my-app": {
      "type": "remote",
      "url": "http://localhost:8081/mcp"
    }
  }
}
```

---

## Конфигурация

### `.env` — общие настройки для всех проектов

```dotenv
# .env.example

# OpenAI-compatible LLM (enrichment only — OpenRouter, DeepSeek, OpenAI, etc.)
LLM_API_KEY=sk-...
LLM_BASE_URL=https://openrouter.ai/api/v1
EMBEDDINGS_MODEL=deepseek/deepseek-v4-flash
ENRICHMENT_MODEL=deepseek/deepseek-v4-flash
ENRICHMENT_ENABLED=true

# Retrieval
RETRIEVAL_TOP_K=5
RETRIEVAL_CHUNK_SIZE=512
RETRIEVAL_CHUNK_OVERLAP=50
RETRIEVAL_WIKILINKS_HOPS=1
RETRIEVAL_RERANKER_ENABLED=true
RETRIEVAL_RERANKER_MODEL=cross-encoder/ms-marco-MiniLM-L-6-v2

# Vault watching
VAULT_WATCH=true

# ChromaDB
CHROMA_URL=http://chromadb:8000
CHROMA_PORT=8000
```

> **Важно:** добавьте `.env` в `.gitignore`, чтобы ключ `LLM_API_KEY` не попал в репозиторий.

### `docker-compose.local.yml` — параметры конкретного проекта

Переменные, уникальные для каждого проекта, задаются в блоке `environment`:

| Переменная | Описание |
|---|---|
| `CHROMA_COLLECTION` | Уникальное имя коллекции в ChromaDB (`vault_<project>`) |
| `VAULT_PATH` | Путь к vault внутри контейнера (обычно `/vault`) |
| `BM25_INDEX_PATH` | Путь к Lucene индексу внутри контейнера (`/app/index`) |

### Volumes и bind mounts

```yaml
# docker-compose.local.yml (фрагмент)
services:
  mcp-my-app:
    volumes:
      - ./vaults/my-app:/vault              # bind mount — vault проекта
      - bm25-my-app:/app/index              # named volume — персистентный BM25 индекс
      - bm25-models:/app/model              # named volume — ONNX модели (скачиваются один раз)

  chromadb:
    volumes:
      - chroma-data:/chroma/chroma          # named volume — все векторные индексы

volumes:
  bm25-my-app:
  bm25-models:
  chroma-data:
```

---

## Структура проекта

```
knowledgeos/
├── build.gradle.kts
├── settings.gradle.kts
├── Dockerfile
├── docker-compose.yml                 # ChromaDB (общий, в git)
├── docker-compose.local.example.yml   # шаблон для проектов (в git)
├── docker-compose.local.yml           # ваша настройка проектов (.gitignore)
├── .env.example
├── .env                               # общие переменные (.gitignore)
├── vaults/                            # vault'ы проектов
│   ├── my-app/                        # vault проекта "my-app"
│   └── another-project/               # vault проекта "another-project"
│
├── buildSrc/                          # convention plugins (kotlin-jvm)
│
├── mcp-api/                           # контракты и точка входа
│   └── src/main/kotlin/
│       ├── server/
│       │   ├── McpServer.kt           # MCP protocol handler
│       │   └── Routes.kt              # HTTP endpoints
│       │
│       └── tools/
│           ├── SearchDocsTool.kt      # MCP tool: search_docs
│           ├── WriteDocTool.kt        # MCP tool: write_doc
│           ├── UpdateDocTool.kt       # MCP tool: update_doc
│           ├── GetDocTool.kt          # MCP tool: get_doc
│           └── ListDocsTool.kt        # MCP tool: list_docs
│
└── mcp-imp/                           # реализация
    └── src/main/kotlin/
        ├── vault/
        │   ├── VaultWatcher.kt        # file watcher
        │   ├── FrontmatterParser.kt
        │   └── WikilinkParser.kt
        │
        ├── indexing/
        │   ├── Chunker.kt
        │   ├── ContextualEnricher.kt
        │   └── IndexPipeline.kt
        │
        └── retrieval/
            ├── Bm25Retriever.kt
            ├── VectorRetriever.kt
            ├── WikilinkRetriever.kt
            ├── RrfFusion.kt           # Reciprocal Rank Fusion
            └── Reranker.kt
```

---

## Философия

> Код перестаёт быть источником истины — он становится его следствием.

Разработчик больше не спорит над кодом — он спорит над формулировкой в документе. Ошибка агента — это ошибка в документации, а не в имплементации. Дебаггинг становится лингвистическим.

Новый навык — умение писать точные, недвусмысленные документы — ценнее знания синтаксиса.

---

## Лицензия

MIT
