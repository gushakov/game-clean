<!-- methodology-template: clean-ddd-core-only -->

# Project: game-clean

A text-based role-playing game built to **showcase the Clean DDD methodology in a
non-trivial setting**. The RPG universe is a deliberately rich domain: a Player and
NPCs interact over shared state, which makes it a natural laboratory for
interaction-first design.

This is a personal pet project, worked on sporadically with long breaks between
sessions. There is no delivery pressure — the goal is to experiment, learn, and
demonstrate the methodology for the community. Specific threads we want to explore
are recorded in `.claude/memory/design-notes.md`.

> **Public repository — `.claude/` is versioned on purpose.** This repo lives on
> public GitHub (`github.com`). The `.claude/` memory files document the Clean DDD
> design reasoning, which is itself part of what this project shares with the
> community, so they are committed. Keep all committed content **institution-neutral
> and free of machine-specific setup** — no absolute paths, usernames, or internal
> hostnames. Machine-local Claude settings stay out of git via `.gitignore`
> (`.claude/settings.local.json`). The `@~/.claude/methodology/*` references below
> point at the author's *local* methodology library; they will **not** resolve in a
> fresh clone and are kept as provenance, not as a runnable dependency.

## Methodology in scope

- @~/.claude/methodology/clean-ddd-core.md
- @~/.claude/methodology/subcases.md
- @~/.claude/methodology/persistence-and-transactions.md
- @~/.claude/methodology/testing.md
- @~/.claude/methodology/session-wrap-up.md

## Stack (current intent — see project-context.md for live state)

- Spring Boot, Maven
- PostgreSQL via a project `docker-compose.yaml`
- Flyway for schema migrations — **no ORM** (Spring Data JDBC + MapStruct per the
  persistence module)
- MapStruct for inter-layer mapping
- UX: JLine (terminal) — **decided** (`org.jline:jline`; see memory/design-notes.md)

## Working rules for this project

- **Discuss before implementing.** Do not implement features without explicit
  consent and only after the design has been examined together. Default to
  architectural discussion; treat "NO CODE" prompts as pure design conversation.
- **Flag Spring Boot 4 differences.** The author is new to Spring Boot 4 (this
  project runs on 4.x) and wants to learn it. Proactively call out any Boot
  4-vs-3.x difference, gotcha, or relocation as it comes up, and record it in
  `memory/spring-boot-4-notes.md` (append; mark `[hit]` for firsthand vs `[doc]`).
- Sessions are spaced out — re-read the memory files at the start of each one to
  reload where we left off.
- **Issues are plain.** Create issues with plain `gh issue create` — no assignee,
  no sprint, no external issue-tracker attribution. This is a personal pet project.
- **Leak-scan `.claude/` before publishing.** `.claude/` is versioned on a public
  repo (only `settings.local.json` is ignored). Before any commit or push that
  touches `.claude/`, scan the changed content for machine/institution leakage —
  absolute paths, the local username, internal hostnames, internal tooling names,
  JDK install paths — and confirm `settings.local.json` is still the lone ignored
  file. Recipe + exact command in `memory/project-context-extended.md`.

## Project-specific context

See `.claude/memory/project-context.md` and `.claude/memory/project-context-extended.md`
in this repository. Deep-dive narrative documents (domain context, onboarding notes,
architectural decisions) also live under `.claude/memory/` — including
`spring-boot-4-notes.md` (running log of Boot 4-vs-3.x differences, consulted when
touching Boot config/dependencies).

## On-demand resources

- `~/.claude/methodology/INDEX.md` — map of all methodology modules (the ones relevant to this project are loaded automatically via @-refs above; consult the INDEX only when a cross-cutting question arises that falls outside the loaded set).
- `~/.claude/methodology-log.md` — cross-project learning journal (maintainer-curated; read-only here).
