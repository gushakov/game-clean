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

ConstructWorld vertical **complete** (issue #3, **scenes only**), seeded at startup end-to-end:

- **Domain** — `Scene` aggregate with `Exit`/`SceneId` (`core/model/scene/`); ArchUnit hexagonal guard.
- **Use case** — `ConstructWorld` (`core/usecase/initialize/`): input port, `ConstructWorldUseCase`,
  presenter port, and the `*Entry` input DTOs (moved here from infrastructure — they are the input-port
  contract). Base presenter `ErrorHandlingPresenterOutputPort` in `core/port/`. Two-pass
  build-then-resolve outside a transaction; seed-if-empty guard + per-scene save inside one
  `doInTransaction`; present after commit.
- **Ports** — `SceneRepositoryOperationsOutputPort` (persistence), `TransactionOperationsOutputPort`
  (tx); both unchecked errors.
- **Persistence** — Flyway schema (`scene`/`exit`), Spring Data JDBC `*DbEntity`s, MapStruct mapper, and
  `SpringSceneRepositoryAdapter` implementing the port (`infrastructure/persistence/scene/`). Local
  Postgres service + schema-only read-only MCP.
- **Transactions** — `SpringTransactionAdapter` + `TransactionConfig` (`infrastructure/transaction/`).
- **Driving adapter** — `WorldSeedRunner` (`ApplicationRunner`, `infrastructure/world/`) reads
  `world/scenes.yaml` via `SceneYamlReader` and fires the use case at startup, guarded by the typed
  `game.world.construct-on-startup` property (`WorldSeedProperties`); loads the prototype use case via
  `ApplicationContext.getBean` (cargo-clean idiom).
- **Composition root** — `UseCaseConfig` (`infrastructure/`).
- **Bootable** — `application.yaml` now carries the local datasource; run via `java -jar` (never
  `spring-boot:run`).

Tests: 34 unit (Surefire, DB-free) + 9 integration (`*IT`, Failsafe, real Postgres). Not yet: anything
beyond scenes (NPCs/items), the terminal/JLine driving adapter (spike only), async/event processing.
