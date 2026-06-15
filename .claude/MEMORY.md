# game-clean — project memory index

Per-project memory index, read at session startup (per global `~/.claude/CLAUDE.md`).
Each entry points at a file under `.claude/memory/` with a one-line description of
what it owns. Keep this index institution-neutral — `.claude/` is versioned on a
public repo.

- [design-notes.md](memory/design-notes.md) — the project's *textbook*: Clean DDD doctrine, trade-offs, and decision rationale (the *why*/*why-not*), organized by theme, no dated log.
- [project-context.md](memory/project-context.md) — quick-reference current facts: stack, package layout, entry points, run/test commands, repo workflow, short fact tables.
- [project-context-extended.md](memory/project-context-extended.md) — on-demand deep reference: domain model, use-case inventory, infrastructure/persistence/security/test specifics, code conventions, recipes.
- [spring-boot-4-notes.md](memory/spring-boot-4-notes.md) — running, appendable log of Spring Boot 4.0 behaviours that differ from 3.x ([hit] = firsthand, [doc] = from docs).
