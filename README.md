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
│  Obsidian Vault  │◄────────────│    AI Agent      │
│                  │  читает /   │    (OpenCode)    │
│  concepts/       │  редактирует│                  │
│  reference/      │             │  1. search_docs  │
│  how-to/         │             │  2. читает доки  │
│  tutorials/      │             │  3. пишет код    │
│  guidelines/     │             │  4. пишет гайд   │
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

**Obsidian Vault** — мозг системы. Единственный источник истины. Хранит документацию по жанрам [Diátaxis](https://diataxis.fr/), связанную через `[[wikilinks]]`. Редактируется людьми и агентом.

**MCP Server** — нервная система. Индексирует vault, обрабатывает запросы агента. BM25 индекс персистируется на локальный диск, векторный индекс — в ChromaDB. Сервер можно перезапустить без полной переиндексации. Граф wikilinks поддерживается в памяти и обновляется инкрементально через file watcher; wikilinks разворачиваются on-demand при получении документа — если в чанке есть [[ссылки]], сервер автоматически загружает связанные документы. Реализован на Kotlin.

**AI Agent (OpenCode)** — исполнитель. Единственный кто пишет код. Перед каждым действием читает документацию. После — обновляет гайдлайны и связанные документы через три инструмента: `search_docs`, `write_guideline`, `update_doc`.

---

## Структура Vault

```
vault/
├── _INDEX.md                        # карта vault — точка входа агента
│
├── concepts/                        # ПОЧЕМУ и КАК устроено
│   ├── architecture.md
│   ├── domain-model.md
│   └── decisions/                   # Architecture Decision Records
│       ├── 001-use-postgres.md
│       └── 002-no-orm.md
│
├── reference/                       # ЧТО ЕСТЬ — полные списки
│   ├── api-endpoints.md
│   ├── db-schema.md
│   └── env-vars.md
│
├── how-to/                          # КАК СДЕЛАТЬ конкретную задачу
│   ├── add-api-endpoint.md
│   ├── add-db-migration.md
│   └── write-tests.md
│
├── tutorials/                       # НАУЧИТЬСЯ — для новых людей
│   └── onboarding.md
│
└── guidelines/                      # ПРАВИЛА — накапливаются агентом
    ├── database.md
    ├── testing.md
    ├── error-handling.md
    └── libs/
        └── exposed.md               # правила работы с конкретными либами
```

Жанровое разделение по [Diátaxis](https://diataxis.fr/): каждый документ отвечает на один тип вопроса — агент знает заранее где искать инструкции, где факты, а где правила.

### Frontmatter документа

```markdown
---
genre: guideline           # concept | reference | how-to | tutorial | guideline
title: Правила работы с БД
topic: database
library: exposed           # опционально — конкретная библиотека
triggers:
  - "db query"
  - "transaction"
  - "migration"
related:
  - [[reference/db-schema]]
  - [[how-to/add-db-migration]]
confidence: high           # low | medium | high
source: agent              # agent | human
updated: 2024-01
---
```

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

Агент видит три инструмента:

```
search_docs(
  query:  String,           // что ищем
  genre:  String? = null,   // concept | how-to | reference | tutorial | guideline
  topic:  String? = null,   // database | auth | testing | ...
) → List<Chunk>

write_guideline(
  content: String,          // текст гайдлайна в markdown
  topic:   String,          // куда положить
  library: String? = null,  // если про конкретную либу
  related: List<String>? = null, // [[wikilinks]] на связанные документы
) → Unit

update_doc(
  path:    String,          // путь к существующему документу в vault
  content: String,          // новое содержимое (полная замена или patch)
) → Unit
```

### Retrieval pipeline

```
query
  │
  ├─► [Metadata filter]     сужаем по genre/topic до поиска
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
2. search_docs("задача") → читает релевантные гайдлайны
        ↓
3. Выполняет задачу по правилам
        ↓
4. Столкнулся с чем-то новым или нестандартным
        ↓
5. write_guideline("правило...") → новый .md в guidelines/
   update_doc([[existing-doc]], добавляет [[ссылку]] на новый гайдлайн)
        ↓
6. Следующая похожая задача → агент найдёт это правило сам
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
├── docker-compose.yml
├── .env
└── vault/              ← сюда кладёте документацию
```

**`docker-compose.yml`**

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
    depends_on:
      chromadb:
        condition: service_started
    restart: unless-stopped

volumes:
  chroma-data:
  bm25-index:
```

**`.env`**

```dotenv
DEEPSEEK_API_KEY=sk-...
```

### 2. Запустите

```bash
docker compose up -d
```

MCP-сервер будет доступен на `http://localhost:8081/mcp`.

### 3. Обновление до новой версии

```bash
docker compose pull && docker compose up -d
```

---

## Быстрый старт (один проект)

### Требования

- Docker Desktop
- OpenCode
- Obsidian (опционально, vault работает как обычная папка)

### Запуск

```bash
# 1. Клонируем репозиторий
git clone https://github.com/your-org/knowledgeos
cd knowledgeos

# 2. Копируем и заполняем переменные окружения
cp .env.example .env
# → указываем DEEPSEEK_API_KEY

# 3. Копируем и настраиваем локальный compose-файл
cp docker-compose.local.yml.example docker-compose.local.yml
# → правим под свой проект: имя сервиса, порт, путь к vault

# 4. Создаём папку vault (если ещё нет)
mkdir -p vaults/my-app

# 5. Поднимаем все сервисы
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d

# 6. Подключаем к OpenCode
# В opencode.json добавляем MCP сервер:
# "mcpServers": {
#   "knowledge": { "url": "http://localhost:8081/mcp" }
# }
```

### Ребилд и рестарт MCP сервера

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
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
    build: .
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
    depends_on:
      chromadb:
        condition: service_started
    restart: unless-stopped

volumes:
  bm25-new-project:
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
    "knowledge-my-app":        { "url": "http://localhost:8081/mcp" },
    "knowledge-another":       { "url": "http://localhost:8082/mcp" },
    "knowledge-new-project":   { "url": "http://localhost:8083/mcp" }
  }
}
```

---

## Конфигурация

### `.env` — общие настройки для всех проектов

```dotenv
# .env.example

# DeepSeek / OpenRouter (embeddings + enrichment)
DEEPSEEK_API_KEY=sk-...
DEEPSEEK_BASE_URL=https://openrouter.ai/api/v1
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

  chromadb:
    volumes:
      - chroma-data:/chroma/chroma          # named volume — все векторные индексы

volumes:
  bm25-my-app:
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
│           ├── WriteGuidelineTool.kt  # MCP tool: write_guideline
│           └── UpdateDocTool.kt       # MCP tool: update_doc
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