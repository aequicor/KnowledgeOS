# Recommended vault structure for AI agents

KnowledgeOS itself does **not** enforce any vault structure or frontmatter schema. You can throw arbitrary `.md` files at it and search will work.

This document collects opinionated defaults for users who want a structure that *works well with LLM agents*. The recommendations are based on Anthropic's Claude Skills guidance, the Diátaxis framework, and recent RAG/agent literature. Treat them as a starting template, not a contract.

> **TL;DR.** Few short docs beat one big one. Use shallow folders by document *type*, put topics in `tags`. Keep `title` + `description` + `kind` as the only "required" frontmatter. Use `[[wikilinks]]` to wire docs together. Make every `## H2` self-contained because that's the chunk boundary.

---

## A. Folder layout — shallow, by document type

LLM agents retrieve by similarity, not by browsing. Deep hierarchies don't help retrieval; they only force every doc into a single home it may not belong in. Tags do that better.

That said, **type** (rule vs. tutorial vs. reference) does affect how the agent should *use* the doc, so it's worth segmenting on. A two-level layout is plenty:

```
vault/
├── _index.md           # optional Map of Content (top-level entry point)
├── rules/              # short, prescriptive ("agent MUST ...")
├── patterns/           # reusable how-tos with code examples
├── decisions/          # ADRs — why we chose X over Y
├── reference/          # API/schema/config — long, lookup-only
└── domain/             # business/product knowledge
```

Why these five? They map to Diátaxis (rules ≈ tutorials' guardrails, patterns ≈ how-to, decisions+reference ≈ explanation+reference, domain ≈ context). They also tell the agent how to weigh a hit: rules override patterns, decisions explain rather than prescribe.

A flat vault with only `kind:` in frontmatter is also fine — the value prop of KnowledgeOS is that the retrieval pipeline makes layout a stylistic choice, not a load-bearing one.

## B. Frontmatter — minimum viable schema

```yaml
---
title: Database transaction rules
description: When and how to wrap DB mutations in transactions; covers retries, isolation levels, and rollback handling.
kind: rule
tags: [db, sql, transactions]
updated: 2026-05
---
```

**Required (3 fields):**

| Field | Why it matters |
|---|---|
| `title` | Human-readable name; also a wikilink target. |
| `description` | 1–3 sentences, third-person, includes "what it covers AND when to use it." This is the highest-signal field for both metadata filtering and reranker scoring. |
| `kind` | Document type: `rule | pattern | decision | reference | domain`. Lets the agent pick filter strategy. |

**Recommended (high ROI):**

| Field | Notes |
|---|---|
| `tags` | Flat list of 3–7 lowercase keywords. Filterable axis. |
| `updated` | ISO date. Critical for tiebreaking stale forks. |

**Optional / power-user:**

| Field | Notes |
|---|---|
| `status` | `active | deprecated | draft` — agents skip deprecated content. |
| `applies_to` | Glob pattern (paths/modules/languages) when a rule is scoped. |
| `supersedes` / `superseded_by` | Wikilink targets, for ADR chains. |
| `related` | Wikilinks for graph context (also auto-extracted from a `## Связанные документы` section in the body). |

**Avoid:** keyword-stuffed metadata, prose summaries (those belong in the body's TL;DR), per-section metadata.

## C. Writing style — concise, third-person, example-heavy

- **Length:** target 150–500 lines. With chunk size ~512 tokens, that's ~6–10 retrievable chunks per doc — enough granularity, not so much it fragments meaning.
- **Tone:** third person, present tense ("The agent processes…", not "you can…"). First/second person breaks discoverability when the doc is injected into a system prompt.
- **Self-contained sections.** Every `## H2` should make sense in isolation — that's the chunk boundary. Avoid antecedents like "it" or "this" across section breaks; repeat the noun.
- **Consistent terminology.** One term per concept ("vault", not "knowledge base" / "repo" / "store"). LLMs get confused by synonyms during retrieval.
- **Code first, prose second.** Show 1–3 canonical examples; skip exhaustive edge-case walls.
- **No time-sensitive language.** Replace "as of August 2025" with versioned headings or a `<details>Legacy patterns</details>` block.
- **Triggers/keywords:** don't bolt on a "Keywords:" footer. Write the `description` so it naturally contains the trigger phrases an agent would search for. BM25 picks these up; the reranker scores them.

## D. Linking — wikilinks beat folders, but keep the graph shallow

- **Use wikilinks** (`[[doc-name]]`) for all cross-document references. Folders force a single home; wikilinks let a doc participate in many contexts.
- **Keep references one level deep.** Avoid nested chains (A → B → C); the agent partial-reads and loses information. Always link from the canonical/index doc, not transitively.
- **Index files (MOCs):** optional but valuable. One top-level `_index.md` gives the agent a navigable entrypoint when retrieval misses. Don't make it load-bearing — it's a fallback.
- **Tags vs. folders:** prefer tags. Tags are filterable axes (multi-membership); folders are containers (single-membership). Folders earn their keep when they segment by *type* (rules vs. reference) because that affects how the agent uses the result.

## E. Workflow — search → read → write → consolidate

The cycle KnowledgeOS envisions ("agent reads → acts → writes guideline") aligns with self-improving agent patterns:

1. **Search.** Issue specific queries, not browse. Filter by `kind` first (search rules, fall back to patterns), and by tags.
2. **Read.** Retrieve small chunks, expand `[[wikilinks]]` only on a hit. Don't pre-load the vault into context.
3. **Write.** When the agent learns something new:
   - **First search for an existing doc to update** (`update_doc`). Update beats create.
   - Only create a new file (`write_doc`) if no canonical home exists.
   - Always set/refresh `updated` on edit.
4. **Consolidate (periodic).** Treat the vault as an evolving playbook. Periodically detect duplicates (same `tags` + similar embeddings), stale docs (`updated` > N months + `status: active`), and orphans (no inbound wikilinks).

## F. Document size — many short focused docs

Prefer many short focused docs over few sprawling ones — but only when each has a clear single concern. ADRs especially: short ADRs that link forward/backward beat one giant decision log. A rule should be 50–200 lines; a reference doc can be longer (with a TOC at the top so the agent can preview-then-jump).

---

## Sources

- [Effective context engineering for AI agents](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents) — Anthropic
- [Skill authoring best practices](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices) — Claude API docs
- [How to write documentation that AI agents can actually use](https://alhena.ai/blog/write-documentation-ai-agents-can-use/) — Alhena
- [Markdown-first semantics: frontmatter and hidden context for RAG](https://blog.trysteakhouse.com/blog/markdown-first-semantics-frontmatter-rag-retrieval) — Steakhouse
- [Stop bloating your CLAUDE.md: progressive disclosure for AI coding tools](https://alexop.dev/posts/stop-bloating-your-claude-md-progressive-disclosure-ai-coding-tools/) — alexop.dev
- [How to write a great agents.md: lessons from 2,500+ repositories](https://github.blog/ai-and-ml/github-copilot/how-to-write-a-great-agents-md-lessons-from-over-2500-repositories/) — GitHub Blog
- [Best chunking strategies for RAG (and LLMs) in 2026](https://www.firecrawl.dev/blog/best-chunking-strategies-rag) — Firecrawl
- [Diátaxis Framework](https://diataxis.fr/)
- [Agentic Context Engineering (arXiv 2510.04618)](https://arxiv.org/abs/2510.04618)
