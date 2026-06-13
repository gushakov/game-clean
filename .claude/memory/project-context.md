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
- `infrastructure/` — adapters, Spring wiring. At the **root**: `GameCleanApplication` (entry point;
  here so component scanning never reaches `core`), `UseCaseConfig` (composition root), `BootSequence`
  (boot orchestrator), `GameConfigurationProperties` (single `game.*` config catalog). Sub-packages:
  `infrastructure/persistence/{aggregate}/`, `infrastructure/world/` (YAML seed + `WorldSeeder`),
  `infrastructure/transaction/` (Spring tx adapter + config), `infrastructure/terminal/` (JLine
  `TerminalConfig` + `ConsoleSession` + `TerminalScenePresenter`).
- Enforced by four ArchUnit guards: `core ↛ infrastructure`, `core.model ↛ core.port`,
  `@SpringBootApplication` resides in `infrastructure`, and `core` carries no Spring stereotypes.

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
- **World seeding** — `WorldSeeder` (`infrastructure/world/`, plain singleton) reads `world/scenes.yaml`
  via `SceneYamlReader` and fires the use case through `ApplicationContext.getBean` (cargo-clean idiom).
- **Composition root** — `UseCaseConfig` (`infrastructure/`).

Interactive terminal shell **complete** (issue #6) — one process, JLine owning the console:

- **Boot orchestration** — `BootSequence` (the *sole* `ApplicationRunner`, `infrastructure/`) states the
  startup order explicitly: `worldSeeder.seed()` then `consoleSession.start()`. Both injected as
  singletons. Gated (with the terminal beans) by `game.terminal.enabled`.
- **Two adapters, one terminal** — `TerminalConfig` declares shared singleton `Terminal`/`LineReader`
  *resources*; the driving `ConsoleSession` (blocking `look`/`bye` loop; `look` loads `scn1` directly via
  the Spring Data repo + mapper — **spike**, bypassing the clean port) and the driven
  `TerminalScenePresenter` both inject them (`infrastructure/terminal/`).
- **Config catalog** — all `game.*` properties bound in `GameConfigurationProperties` (nested `World`,
  `Terminal`); enabled on `GameCleanApplication`.
- **Logging** — `logback-spring.xml` routes logs to `./logs` (no console appender) so JLine owns the
  terminal; banner off. `logback-test.xml` restores console logging for the build.
- **Run** — `.claude/scripts/run-app.sh` (discovers JDK 21+, runs the fat jar); `java -jar` only, never
  `spring-boot:run` (Maven would force a dumb terminal). **Running the app seeds the shared IT DB** —
  reset the volume before `mvn verify` (see extended context).

`Look` vertical + `Player` aggregate **complete** (issue #8) — the first player-facing use case:

- **Domain** — `Player` aggregate (`core/model/player/`): `PlayerId` (prefix `plr`) + `currentScene`
  (`SceneId` reference). Minimal — position only.
- **Use case** — `Look` (`core/usecase/explore/`): input port `look(String playerId)`, `LookUseCase`,
  co-located `LookPresenterOutputPort`. The first **read-only** use case — reads player → its scene →
  presents, **no transaction**; branch-and-present for missing player / dangling scene.
- **Ports** — new `PlayerRepositoryOperationsOutputPort` (`findPlayer`/`savePlayer`); `findScene(SceneId)`
  added to `SceneRepositoryOperationsOutputPort` (retires the spike's direct repo read).
- **Persistence** — Flyway `V2__create_player.sql` (`player`; no FK on `current_scene_id`), `PlayerDbEntity`,
  MapStruct mapper, Spring Data repo, `SpringPlayerRepositoryAdapter` (`infrastructure/persistence/player/`).
- **Player seeding** — `PlayerSeeder` (`infrastructure/world/`, plain singleton) creates the configured
  player at its starting scene via the port **directly** — no use case, no tx (deliberate deviation; see
  design-notes §4). `BootSequence` order: world → player → console.
- **Command parsing** — `CommandParser` (tokenizer + verb registry) → `Command` intents
  (`LookCommand`/`QuitCommand`/`UnknownCommand`) in `infrastructure/terminal/`. `ConsoleSession` is now a
  thin controller (parse → dispatch → pull prototype `LookInputPort`); the use case drives the presenter.
  ANTLR deferred (design-notes §9).
- **Config** — `game.player.id` (`plr1`), `game.player.starting-scene-id` (`scn1`) in `GameConfigurationProperties`.

Tests: 61 unit (Surefire, DB-free) + 11 integration (`*IT`, Failsafe, real Postgres). Not yet: anything
beyond scenes + player (NPCs/items), the `look <target>` / `move` use cases, async/event processing.
