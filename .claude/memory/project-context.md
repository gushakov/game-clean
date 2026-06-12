# game-clean — project context

> **Charter — current facts, scannable index.**
> This file owns the **quick-reference** view of the project as it exists *today*:
> stack, top-level package layout, entry points, run/test commands, repo workflow,
> and any short fact tables (glossary, use-case index, profile table, key files)
> that earn their place by being scannable.
>
> - Update when a stack version changes, a new top-level package appears, a use
>   case is added or renamed, or a run/test command changes.
> - Do **not** put rationale, history, or architectural discussion here. Those
>   belong in `design-notes.md`.
> - Do **not** put recipes or long-form conventions here. Those belong in
>   `project-context-extended.md`.
> - Do **not** keep a per-issue changelog or "Recent Changes" log here. Change
>   history is git's job; memory holds the distilled current state. See
>   `session-wrap-up.md` §"Memory is not a changelog".

## What this is

Text-based RPG that showcases Clean DDD. Public repo on `github.com`
(`gushakov/game-clean`). Personal pet project, worked on sporadically.

## Stack (intended; not all wired yet)

| Concern | Choice |
|---|---|
| Runtime | Spring Boot (Java) |
| Build | Maven |
| Database | PostgreSQL via project `docker-compose.yaml` |
| Migrations | Flyway — **no ORM** |
| Persistence access | Spring Data JDBC + MapStruct (per persistence module) |
| Mapping | MapStruct |
| UX | JLine (terminal), `org.jline:jline` 4.1.3 aggregate jar — **decided** (see design-notes) |

## Repo workflow

- Public GitHub, remote `origin`, default branch `main`, integration branch `dev`.
- `.claude/` is versioned (machine-local `settings.local.json` excluded); keep
  committed content institution-neutral and free of machine setup.
- Standard flow otherwise: `claude/<issue>_*` branch from `dev`, PR `--base dev`.

## Package layout (Clean DDD)

- `core/` — framework-free. `model/{aggregate}/` (aggregate roots + VOs, shared),
  `port/{operation}/` (output ports — `port/persistence/`, `port/transaction/`),
  `usecase/{summarygoal}/` (use-case class + its input and presenter ports).
- `infrastructure/` — adapters, Spring wiring, composition root (`UseCaseConfig`). Includes
  `infrastructure/persistence/{aggregate}/`, `infrastructure/world/` (YAML seed), and
  `infrastructure/transaction/` (Spring tx adapter + config).
- Enforced by an ArchUnit guard: `core ↛ infrastructure` and `core.model ↛ core.port`.

## Status

ConstructWorld vertical in progress (issue #3, **scenes only**). Implemented: `Scene` aggregate
with `Exit`/`SceneId` (`core/model/scene/`), the `SceneRepositoryOperationsOutputPort` persistence port,
the ArchUnit hexagonal guard, a local Postgres service + schema-only read-only MCP, and a **persistence
spike** — Flyway schema (`scene`/`exit`), Spring Data JDBC `*DbEntity`s, a MapStruct mapper, and a
`@DataJdbcTest` round-trip IT against the real DB (`infrastructure/persistence/scene/`). Also implemented:
a **YAML seed reader** (`infrastructure/world/`, SnakeYAML → `*Entry` DTOs) with a round-trip IT, and the
**transaction demarcation port** + Spring adapter (`core/port/transaction/`, `infrastructure/transaction/`,
unchecked errors, explicit `TransactionConfig` wiring). Not yet: the `ConstructWorld` use case (next — it
consumes the YAML entries, the persistence port, and the tx port), the persistence port adapter (deferred
until that use case), composition root. UX (JLine) is a spike only.
