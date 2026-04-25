# Search Rule

When searching for information, always follow this priority:

1. **KnowledgeOS MCP tools first** — always use `knowledgeos_search_docs` for any question about the project's documentation, concepts, guidelines, architecture, decisions, or conventions. The vault is the single source of truth.

2. **Then fall back to code search** — only if `search_docs` returns nothing useful, use `grep`, `glob`, or `read` to search the codebase.

3. **Document new findings** — if you discover something important that is not in the vault, use `knowledgeos_write_guideline` to document it.
