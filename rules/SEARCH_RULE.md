# Search Rule

When searching for information, always follow this priority:

1. **KnowledgeOS MCP tools first** — always use `knowledgeos_search_docs` for any question about the project's documentation, concepts, guidelines, architecture, decisions, or conventions. The vault is the single source of truth.

2. **Read the returned documents** — `knowledgeos_search_docs` returns chunkIds with `docPath`. Read each unique `docPath` from `vault/` to get the full content before deciding if more information is needed.

3. **Then fall back to code search** — only if `knowledgeos_search_docs` returned nothing useful AND reading the docs didn't help, use `grep`, `glob`, or `read` to search the codebase.

4. **Document new findings** — if you discover something important that is not in the vault, use `knowledgeos_write_guideline` to document it.
