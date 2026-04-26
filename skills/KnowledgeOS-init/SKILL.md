---
name: KnowledgeOS-init
description: Initialize documentation vault structure for a new project
---

# Initialize KnowledgeOS Project Documentation

Check if project documentation exists. If not, create the vault structure and bootstrap documents following the Diátaxis framework.

## Before you start

1. You have access to the `write_guideline` MCP tool
2. The vault directory exists (or will be created by the MCP server)
3. You're initializing a NEW project (check if `vault/_INDEX.md` already exists)

## Steps

### 1. Check if documentation exists

Use `search_docs` to see if `_INDEX.md` exists:

```
Call search_docs with query "карта знаний" or "_INDEX"
```

**If found:** Documentation already exists. Stop here.

**If not found:** Proceed to initialization.

### 2. Create vault structure

Create the following directory structure (if using local filesystem):

```
vault/
├── _INDEX.md
├── concepts/
│   └── architecture.md
├── guidelines/
│   └── documentation-structure.md
├── how-to/
│   └── (empty, users add later)
└── reference/
    └── env-vars.md
```

If using MCP tools, write `_INDEX.md` first (it's the entry point).

### 3. Initialize core documents in order

#### A. `_INDEX.md` — Knowledge base index

Use `write_guideline` with:

```
path: _INDEX.md
content: (see Template A below)
```

#### B. `concepts/architecture.md` — System overview

Use `write_guideline` with:

```
path: concepts/architecture.md
content: (see Template B below)
```

#### C. `guidelines/documentation-structure.md` — Documentation rules

Use `write_guideline` with:

```
path: guidelines/documentation-structure.md
content: (see Template C below)
```

#### D. `reference/env-vars.md` — Configuration reference

Use `write_guideline` with:

```
path: reference/env-vars.md
content: (see Template D below)
```

### 4. Verify initialization

Search for each document:
- Search "карта знаний" → should find `_INDEX.md`
- Search "архитектура" → should find `concepts/architecture.md`
- Search "документация" → should find `guidelines/documentation-structure.md`
- Search "переменные окружения" → should find `reference/env-vars.md`

If all found, initialization is complete.

## Templates

### Template A: `_INDEX.md`

```markdown
---
genre: reference
title: Карта знаний [Project Name]
topic: index
confidence: high
source: human
updated: 2026-04
---

# [Project Name] Knowledge Base

Добро пожаловать в базу знаний. Используй `search_docs` для поиска.

## Структура

- [[concepts/architecture]] — как устроена система
- [[guidelines/documentation-structure]] — как организована документация
- [[reference/env-vars]] — переменные окружения

## Как добавить документ

1. Выбери жанр: `concepts/` (объяснения), `guidelines/` (правила), `how-to/` (инструкции), `reference/` (факты)
2. Напиши документ в kebab-case.md с обязательным frontmatter (см. `guidelines/documentation-structure`)
3. Добавь [[wikilink]] в этот индекс и в связанные документы
4. Проверь поиск: `search_docs` должна найти твой документ
```

### Template B: `concepts/architecture.md`

```markdown
---
genre: concept
title: Архитектура системы
topic: architecture
confidence: high
source: human
updated: 2026-04
---

# Архитектура [Project Name]

## Обзор

[Краткое описание того, что делает система и как она устроена]

## Компоненты

[Основные модули/сервисы]

## Поток данных

[Как информация движется через систему]

## Связанные документы

- [[guidelines/documentation-structure]]
- [[reference/env-vars]]
```

### Template C: `guidelines/documentation-structure.md`

```markdown
---
genre: guideline
title: Структура документации
topic: documentation
confidence: high
source: human
updated: 2026-04
triggers:
  - "структура документации"
  - "как создать документ"
  - "жанры документов"
  - "frontmatter"
related:
  - [[concepts/architecture]]
---

# Структура документации

## Жанры Diátaxis

### concepts/
- Объяснения, архитектура, дизайн-решения
- Когда: контекст, "почему?" вопросы

### guidelines/
- Правила для агента
- **Обязателен** массив `triggers` в frontmatter
- Когда: нужно задать стиль, полицу, правило

### how-to/
- Пошаговые инструкции
- Структура: Предусловия → Шаги → Проверка
- Когда: "как сделать X?"

### reference/
- Факты, API, конфигурация
- Только факты, без объяснений
- Когда: нужен справочник

## Правила именования

- Файлы: `kebab-case.md`
- Wikilinks: совпадают с именем файла без расширения
- ADR в concepts: `001-name.md`, `002-name.md`, ...

## Обязательный frontmatter

```yaml
---
genre: concept | guideline | how-to | reference
title: Человекочитаемый заголовок
topic: kebab-case-тема
confidence: high | medium | low
source: human | agent
updated: YYYY-MM
triggers: [для guidelines]
related: [для всех]
---
```

## Workflow

1. Создай файл в правильной папке жанра
2. Добавь frontmatter
3. Напиши содержимое
4. Обнови `_INDEX.md` с wikilink
5. Добавь двусторонние [[wikilinks]] на связанные документы
```

### Template D: `reference/env-vars.md`

```markdown
---
genre: reference
title: Переменные окружения
topic: env-vars
confidence: high
source: human
updated: 2026-04
---

# Переменные окружения

Конфигурация через `.env` файл.

## Основные переменные

| Переменная | По умолчанию | Описание |
|---|---|---|
| `VAULT_PATH` | `vault` | Путь к папке с документацией |
| `CHROMA_URL` | `http://localhost:8000` | URL ChromaDB |
| `LLM_API_KEY` | — | API ключ для LLM (OpenRouter, DeepSeek, OpenAI) |
| `LLM_BASE_URL` | `https://openrouter.ai/api/v1` | Базовый URL LLM |

## Безопасность

- **НИКОГДА** не коммитьте `.env` файл в git
- Добавьте `.env` в `.gitignore`
- Храните ключи локально или в переменных окружения CI/CD
```

## Troubleshooting

**Problem:** "write_guideline tool not found"
- Check that KnowledgeOS MCP server is running
- Verify the tool is exposed in the MCP server config

**Problem:** Wikilinks not working after creation
- Make sure document paths match exactly (case-sensitive on Linux)
- Run `search_docs` to verify documents are indexed
- Check that frontmatter is valid YAML

**Problem:** "File already exists" error
- Documentation was already initialized
- Use `search_docs` to find and read existing documents
- Avoid overwriting existing vault structure

## Verification checklist

- [ ] `_INDEX.md` exists and is searchable
- [ ] `concepts/architecture.md` created
- [ ] `guidelines/documentation-structure.md` created
- [ ] `reference/env-vars.md` created
- [ ] All documents have correct frontmatter
- [ ] Wikilinks are bidirectional where appropriate
- [ ] `search_docs` finds all documents by keywords
- [ ] Vault is ready for agents to add new documents
