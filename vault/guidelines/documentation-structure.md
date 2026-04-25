---
genre: guideline
title: Структура документации
topic: documentation
confidence: high
source: human
updated: 2026-04
triggers:
  - "структура документации"
  - "как организовать документацию"
  - "как создать новый документ"
  - "diátaxis"
  - "жанры документов"
  - "frontmatter"
  - "wikilinks"
related:
  - [[guidelines/writing-guidelines]]
  - [[concepts/architecture]]
---

# Структура документации

## Структура vault

Документация организована по фреймворку Diátaxis с четырьмя жанрами:

```
vault/
├── _INDEX.md                    # Главный индекс с wikilinks
├── concepts/                    # Что есть система (объяснения, ADR)
│   ├── architecture.md
│   └── decisions/               # Architecture Decision Records
│       ├── 001-name.md
│       └── 002-name.md
├── guidelines/                  # Правила для AI-агента
│   ├── name-of-guideline.md
│   └── ...
├── how-to/                      # Пошаговые руководства
│   └── action.md
└── reference/                   # Факты, API, конфигурация
    └── topic.md
```

## Правила по жанрам

### concepts/

- Объяснения архитектуры, дизайн-решений, контекст
- ADR: нумерация по порядку (`001-`, `002-`, ...), kebab-case
- Структура ADR: Контекст → Решение → Причины → Следствия
- **Не** добавлять инструкции (это в `how-to/`)

### guidelines/

- Правила, которые должен соблюдать AI-агент
- **Триггеры обязательны** — массив `triggers` в frontmatter
- Один документ = одна тема
- Без объяснений контекста (это в `concepts/`)

### how-to/

- Пошаговые руководства
- Имя файла: глагол действия (`add-mcp-tool.md`)
- Структура: Предусловия → Шаги → Проверка
- Без концептуальных объяснений (это в `concepts/`)

### reference/

- Факты, API, конфигурация, структуры данных
- Только факты, без мнений
- Минималистично, готово к копированию

## Именование файлов

1. **Файлы:** kebab-case, описательные, с `.md`
2. **Wikilinks:** Совпадают с именем файла без расширения: `[[concepts/architecture]]`
3. **_INDEX.md:** Всегда содержит [[ссылку]] на новый документ
4. **ADR:** Последовательные номера с описанием

## Обязательный frontmatter

Каждый документ ДОЛЖЕН содержать:

```yaml
---
genre: concept | guideline | how-to | reference
title: Человекочитаемый заголовок
topic: kebab-case-тема
confidence: high | medium | low
source: human | agent
updated: YYYY-MM
---
```

## Workflow

1. Новое знание → создать файл в правильной папке жанра
2. Обновить `_INDEX.md` с wikilink на новый документ
3. Добавить [[wikilinks]] на связанные документы в обе стороны
4. Проверить через `search_docs`, что документ находится по триггерам
