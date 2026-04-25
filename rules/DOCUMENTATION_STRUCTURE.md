# Documentation Structure Rule

All documentation for this project must follow the [Diátaxis](https://diataxis.fr/) framework structure. The vault is organized into four genres:

## Directory Structure

```
vault/
├── _INDEX.md                    # Main index with wikilinks to all important docs
├── concepts/                    # What the system is (explanations, ADRs)
│   ├── architecture.md          # System architecture overview
│   └── decisions/               # Architecture Decision Records
│       ├── 001-name.md          # Format: NNN-short-descriptive-name
│       └── 002-name.md
├── guidelines/                  # Rules to follow (action-oriented)
│   └── name-of-guideline.md
├── how-to/                      # Step-by-step tutorials
│   └── action-to-perform.md
└── reference/                   # Facts, APIs, configuration
    └── topic-of-reference.md
```

## Genre Rules

### concepts/

- Contains: Explanations of architecture, design decisions, background knowledge
- ADR files: Number sequentially (`001-`, `002-`, ...), use kebab-case after number
- Frontmatter: `genre: concept`, include `title`, `topic`, `confidence`, `source`, `updated`
- ADR structure: Context → Decision → Reasons → Consequences
- No step-by-step instructions (put in `how-to/`)

### guidelines/

- Contains: Rules the AI agent must follow
- Frontmatter: `genre: guideline`, `triggers` array (mandatory), `tags`, `related` with wikilinks
- One guideline = one topic
- Include specific triggers so agents can find them via search
- No explanations (put context in `concepts/`)

### how-to/

- Contains: Step-by-step tutorials, onboarding guides
- Frontmatter: `genre: how-to`, ordered `steps` array
- File name: action-oriented (`add-mcp-tool.md`, not `mcp-tool-creation`)
- Start with prerequisites, end with verification steps
- No conceptual explanations (put in `concepts/`)

### reference/

- Contains: Facts, APIs, configuration, data structures
- Frontmatter: `genre: reference`, include `version`
- No opinions or explanations — just facts
- Keep minimal; reference should be copy-paste friendly

## Naming Conventions

1. **Files:** kebab-case, descriptive, `.md` extension
2. **Wikilinks:** Match file names without extension: `[[concepts/architecture]]`
3. **Index:** Always use `[[path/to/file]]` in `_INDEX.md`
4. **ADRs:** Sequential numbers with descriptive suffixes

## Required Frontmatter

Every document MUST include:

```yaml
---
genre: concept | guideline | how-to | reference
title: Human-readable title in Russian
topic: kebab-case-topic
confidence: high | medium | low
source: human | agent
updated: YYYY-MM
---
```

## Workflow

1. New knowledge → create in appropriate genre folder
2. Update `_INDEX.md` with wikilink to new document
3. Add wikilinks to related documents in both directions
4. Run search to verify document is discoverable via triggers/topic
