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

Game initialization **complete** — one `InitializeGame` use case (world + player), seeded at startup end-to-end:

- **Domain** — `Scene` aggregate with `Exit`/`SceneId` (`core/model/scene/`); ArchUnit hexagonal guard.
- **Use case** — `InitializeGame` (`core/usecase/initialize/`): input port
  `initialize(List<SceneEntry>, String startingSceneId)`, `InitializeGameUseCase`, co-located presenter
  port, and the `*Entry` input DTOs (the input-port contract). Base presenter
  `ErrorHandlingPresenterOutputPort` in `core/port/`. Two **phases** as private subfunctions: world
  construction (two-pass build-then-resolve outside a tx; seed-if-empty + per-scene save in one
  `doInTransaction`) then player placement (validity gate + starting-scene resolve outside a tx;
  create-if-absent in one `doInTransaction`); present after each commit; a world that fails to construct
  stops before any player is created.
- **Ports** — `SceneRepositoryOperationsOutputPort` (persistence), `TransactionOperationsOutputPort`
  (tx); both unchecked errors.
- **Persistence** — Flyway schema (`scene`/`exit`), Spring Data JDBC `*DbEntity`s, MapStruct mapper, and
  `SpringSceneRepositoryAdapter` implementing the port (`infrastructure/persistence/scene/`). Local
  Postgres service + schema-only read-only MCP.
- **Transactions** — `SpringTransactionAdapter` + `TransactionConfig` (`infrastructure/transaction/`).
- **Game seeding** — `GameSeeder` (`infrastructure/world/`, plain singleton) reads `world/scenes.yaml`
  via `SceneYamlReader` and the configured starting scene, and fires `InitializeGame` through
  `ApplicationContext.getBean` (cargo-clean idiom) — replacing the former `WorldSeeder` + `PlayerSeeder`
  pair. Presenter `LoggingInitializeGamePresenter` logs both phases.
- **Composition root** — `UseCaseConfig` (`infrastructure/`).

Interactive terminal shell **complete** (issue #6) — one process, JLine owning the console:

- **Boot orchestration** — `BootSequence` (the *sole* `ApplicationRunner`, `infrastructure/`) orders two
  driving adapters: `gameSeeder.seed()` then `consoleSession.start()`. Both injected as singletons. Gated
  (with the terminal beans) by `game.terminal.enabled`. The world→player business sequence lives inside
  the use case, not here (design-notes §6).
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
- **Player placement** — folded into `InitializeGame` as its second phase (creates the configured player
  `game.player.id` at `game.player.starting-scene-id`, once if absent, through the domain in a
  transaction). Was briefly a separate `CreatePlayer` use case / `PlayerSeeder`; merged in issue #10 —
  see design-notes §4/§6.
- **Command parsing** — `CommandParser` (tokenizer + verb registry) → `Command` intents
  (`LookCommand`/`QuitCommand`/`UnknownCommand`) in `infrastructure/terminal/`. `ConsoleSession` is now a
  thin controller (parse → dispatch → pull prototype `LookInputPort`); the use case drives the presenter.
  ANTLR deferred (design-notes §9).
- **Config** — `game.player.id` (`plr1`), `game.player.starting-scene-id` (`scn1`) in `GameConfigurationProperties`.

Tests: 66 unit (Surefire, DB-free) + 10 integration (`*IT`, Failsafe, real Postgres). Not yet: anything
beyond scenes + player (NPCs/items), the `look <target>` / `move` use cases, async/event processing.
