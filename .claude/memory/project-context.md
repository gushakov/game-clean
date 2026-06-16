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
| Id generation | NanoID (`com.aventrix.jnanoid:jnanoid` 2.0.0) behind `IdGeneratorOperationsOutputPort` — not in the Boot BOM, version pinned |
| UX | JLine (terminal), `org.jline:jline` 4.1.3 aggregate jar — **decided** (see design-notes) |
| IT isolation | Testcontainers (`org.testcontainers:testcontainers-postgresql`, BOM-managed at 2.0.5 by Boot 4) — ephemeral Postgres per `mvn verify` |

## Repo workflow

- Public GitHub, remote `origin`, default branch `main`, integration branch `dev`.
- `.claude/` is versioned (machine-local `settings.local.json` excluded); keep
  committed content institution-neutral and free of machine setup.
- Standard flow otherwise: `claude/<issue>_*` branch from `dev`, PR `--base dev`.

## Package layout (Clean DDD)

- `core/` — framework-free. `model/{aggregate}/` (aggregate roots + VOs, shared — `scene/`, `player/`,
  `item/`), `port/{operation}/` (output ports — `port/persistence/`, `port/transaction/`, `port/player/`,
  `port/id/`, `port/randomness/`, `port/seed/` — the latter holds the seed-source port and the
  `GameSeed`/`*Entry` carriers it returns), `usecase/{summarygoal}/` (use-case class + its input and presenter ports;
  a reusable **subcase** gets its own peer package, e.g. `usecase/orient/`).
- `infrastructure/` — adapters, Spring wiring. At the **root**: `GameCleanApplication` (entry point;
  here so component scanning never reaches `core`), `UseCaseConfig` (composition root), `BootSequence`
  (boot orchestrator), `GameConfigurationProperties` (single `game.*` config catalog). Sub-packages:
  `infrastructure/persistence/{aggregate}/`, `infrastructure/world/` (`GameSeedYamlReader` + `YamlGameSeedSource` + `GameSeeder`),
  `infrastructure/transaction/` (Spring tx adapter + config), `infrastructure/terminal/` (JLine
  `TerminalConfig` + `ConsoleSession` + presenters), `infrastructure/id/` (NanoID generator adapter),
  `infrastructure/randomness/` (JDK randomness adapter).
- Enforced by four ArchUnit guards: `core ↛ infrastructure`, `core.model ↛ core.port`,
  `@SpringBootApplication` resides in `infrastructure`, and `core` carries no Spring stereotypes.

## Status

Game initialization **complete** — one `InitializeGame` use case (world + player), seeded at startup end-to-end:

- **Domain** — `Scene` aggregate with `Exit`/`SceneId` (`core/model/scene/`); ArchUnit hexagonal guard.
- **Use case** — `InitializeGame` (`core/usecase/initialize/`): **no-arg** input port
  `systemInitializesGame()`, `InitializeGameUseCase`, co-located presenter port. It **pulls** the authored
  seed as Checkpoint 1 via `GameSeedSourceOperationsOutputPort`; the `GameSeed` + `*Entry` carriers it
  returns live in `core/port/seed/` (the output-port contract). Base presenter
  `ErrorHandlingPresenterOutputPort` in `core/port/`. Three **phases** (world → player → items) converging
  on one `presentGameInitialized`; a seed/world that fails stops the interaction before any write, and a
  seed-source failure is presented like a persistence fault (design-notes §3/§4/§6).
- **Ports** — `SceneRepositoryOperationsOutputPort` (persistence), `TransactionOperationsOutputPort`
  (tx), `GameSeedSourceOperationsOutputPort` (`core/port/seed/`, pulls the authored seed); all unchecked
  errors (`PersistenceOperationsError`, `GameSeedSourceOperationsError`).
- **Persistence** — Flyway schema (`scene`/`exit`), Spring Data JDBC `*DbEntity`s, MapStruct mapper, and
  `SpringSceneRepositoryAdapter` implementing the port (`infrastructure/persistence/scene/`). Local
  Postgres service + schema-only read-only MCP.
- **Transactions** — `SpringTransactionAdapter` + `TransactionConfig` (`infrastructure/transaction/`).
- **Game seeding** — `YamlGameSeedSource` (`infrastructure/world/`, driven adapter behind
  `GameSeedSourceOperationsOutputPort`) reads `world/scenes.yaml` via `GameSeedYamlReader` + the configured
  starting scene and returns the `GameSeed`. `GameSeeder` is now a **thin driving adapter**: pull the
  prototype `InitializeGameInputPort` (cargo-clean `getBean` idiom) and fire `systemInitializesGame()`, no
  return used. Presenter `LoggingInitializeGamePresenter` logs the outcome.
- **Composition root** — `UseCaseConfig` (`infrastructure/`).

Interactive terminal shell **complete** (issue #6) — one process, JLine owning the console:

- **Boot orchestration** — `BootSequence` (`infrastructure/`, a `@Configuration`) declares two
  `@Order`-ed `ApplicationRunner` beans — `@Order(1)` `gameSeeder.seed()`, `@Order(2)`
  `consoleSession.start()` — each an independent inward fire (no imperative `seed(); start();` body, which
  removes the return-of-control affordance the unidirectional rule forbids). Class-level
  `@ConditionalOnProperty(game.terminal.enabled)` gates both runners. Business sequencing lives inside the
  use case, not here (design-notes §6).
- **Two adapters, one terminal** — `TerminalConfig` declares shared singleton `Terminal`/`LineReader`
  *resources*; the driving `ConsoleSession` (blocking `look`/`bye` loop; `look` loads `scn1` directly via
  the Spring Data repo + mapper — **spike**, bypassing the clean port) and the driven
  `TerminalScenePresenter` both inject them (`infrastructure/terminal/`).
- **Config catalog** — all `game.*` properties bound in `GameConfigurationProperties` (nested `World`,
  `Terminal`); enabled on `GameCleanApplication`.
- **Logging** — `logback-spring.xml` routes logs to `./logs` (no console appender) so JLine owns the
  terminal; banner off. `logback-test.xml` restores console logging for the build.
- **Run** — `.claude/scripts/run-app.sh` (discovers JDK 21+, runs the fat jar); `java -jar` only, never
  `spring-boot:run` (Maven would force a dumb terminal). Running the app seeds the `docker-compose` play
  DB, which is now **fully decoupled** from the ITs (Testcontainers — see below), so no volume reset is
  needed before `mvn verify`.

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

`Move` vertical **complete** (issue #14) — the first read-write player-facing use case (player moves through a
named exit into the target scene, then sees it):

- **Domain** — first behaviour on each aggregate (emergence): `Scene.exitNamed(String)` (case-insensitive,
  trimmed lookup) and `Player.moveTo(SceneId)` (immutable, returns a new `Player`).
- **Use case** — `Move` (`core/usecase/explore/`): input port `playerMovesThrough(String exitName)`,
  `MoveUseCase`, co-located `MovePresenterOutputPort`. Reads + validity checks outside a tx; one
  `doInTransaction` holds only the `savePlayer` write; the entered scene is presented in `doAfterCommit`.
  Branch-and-present for missing player / dangling current scene / no such exit / dangling exit target.
- **Shared presenter capability** — the current-scene **outcome cluster** (`presentScene` +
  `presentPlayerNotFound` + `presentCurrentSceneNotFound`) lives on `OrientPlayerPresenterOutputPort` (the
  `orient` subcase's port — see below; introduced here as `CurrentScenePresenterOutputPort`, since
  renamed/moved); `LookPresenterOutputPort` is an empty marker extending it, `MovePresenterOutputPort`
  extends it + `presentNoSuchExit`/`presentTargetSceneNotFound` (design-notes §4: three axes of sharing).
- **Terminal** — `Console` styled-writer resource (§7 facade, declared in `TerminalConfig`) + shared
  `CurrentSceneRenderer`; two thin presenter beans `TerminalLookPresenter` / `TerminalMovePresenter`
  (replacing `TerminalScenePresenter`). `MoveCommand` + `move`/`go` verbs in `CommandParser`; `ConsoleSession`
  dispatches to a pulled prototype `MoveInputPort`.
- **Persistence** — `savePlayer` is now an **upsert** (`existsById ? update : insert`); `@Version`/optimistic
  locking deferred to the concurrency thread (design-notes §5).

`orient` subcase + ad-hoc presenter wiring **complete** (issue #16) — the project's first **subcase**,
factoring the shared `look`/`move` opening (resolve ambient player → resolve their current scene):

- **Subcase** — `core/usecase/orient/`: `OrientPlayerSubcaseInputPort.playerGetsBearings()`,
  `OrientPlayerSubcase`, the renamed/moved shared presenter port `OrientPlayerPresenterOutputPort` (was
  `explore/CurrentScenePresenterOutputPort`), and the `OrientPlayerResult` (player + scene) it returns. A
  **guarded prologue**: on a missing player / dangling current scene it presents the outcome and throws
  the marker `SubcaseAlreadyPresented` (`core/port/`); on success it *returns* `OrientPlayerResult`.
  `LookUseCase`/`MoveUseCase` delegate their opening to it and swallow the marker in a dedicated
  `catch (SubcaseAlreadyPresented)` ahead of `catch (Exception)` (design-notes §4: third subcase shape,
  Path-2-with-returned-results — promotion candidate for `subcases.md`).
- **Wiring** — presenters and subcases are now **`new`ed ad-hoc in `UseCaseConfig`** (no presenter is a
  `@Component` any more): each parent factory `new`s its presenter and shares that one instance with the
  subcase; applied to `InitializeGame` too (`new LoggingInitializeGamePresenter()`).
  `ErrorHandlingPresenterOutputPort` stays a single humble `presentError` (design-notes §6).
- **Tests** — `OrientPlayerSubcaseTest` covers the prologue outcomes; `Look`/`Move` unit tests mock the
  subcase; `InitializeGameIT` asserts persisted state (the presenter is no longer a mockable bean).

`Item` seeding vertical **complete** (issue #19) — items spawned into scenes at init, shown on the ground when
`look`/`move` present a scene (the project's first stochastic interaction and first runtime-generated id):

- **Domain** — `Item` aggregate (`core/model/item/`): `ItemId` (prefix `itm`; reconstitution ctor +
  `fromGeneratedBody`), `location` (`SceneId` reference — items are their own aggregate, not held by `Scene`),
  short/full descriptions. Plus VOs `Chance` (probability), `SpawnRule` (chance + maxTries + ≥1 candidate
  scene; `isHitBy`/`pickScene`/`candidateScenesNotIn`), `ItemTemplate` (descriptions + rule; `instanceAt`,
  `candidateScenesNotIn`). `Scene.exitsWithTargetNotIn` added for symmetry. (design-notes §2, §10.)
- **Use case** — folded into `InitializeGame` as a **third phase** (world → player → items): input **pulled**
  as a `GameSeed` carrier (`core/port/seed/`: `SceneEntry`/`ItemEntry`/`SpawnEntry`) via
  `GameSeedSourceOperationsOutputPort`; spawn rolls (chance per try, uniform scene
  pick, semantics (a) = up to `max` tries) run outside the tx; a third `itemsAlreadySpawned` guard joins
  seed-if-empty/create-if-absent in the single transaction. `presentGameInitialized(scenes, playerId, items)`
  reports items spawned *this run*; new `presentItemSpawnSceneUnknown` stripe. (design-notes §4.)
- **Ports** — `ItemRepositoryOperationsOutputPort` (`findItemsInScene`/`saveItem`/`itemsAlreadySpawned`),
  `IdGeneratorOperationsOutputPort.generateItemId()` (returns valid `ItemId`), `RandomnessOperationsOutputPort.nextDouble()`.
- **Presentation** — items belong to the *presented* scene, fetched per use case (`look` current, `move`
  target), **not** in `orient`; `presentScene(Scene, List<Item>)` on the shared port, `CurrentSceneRenderer`
  appends an "On the ground:" list. (design-notes §4.)
- **Persistence / infra** — Flyway `V3__create_item.sql` (`item`; no FK on `scene_id`), `ItemDbEntity` +
  MapStruct mapper + Spring Data repo (`findBySceneId`) + `SpringItemRepositoryAdapter`; `JNanoIdGenerator`
  (`infrastructure/id/`, JNanoId `com.aventrix.jnanoid:jnanoid:2.0.0` — not in the Boot BOM, version pinned),
  `JdkRandomnessAdapter` (`infrastructure/randomness/`). `SceneYamlReader` renamed `GameSeedYamlReader`
  (parses scenes + items, assembles `GameSeed`), now invoked by `YamlGameSeedSource` behind the seed port.

Tests: 122 unit (Surefire, DB-free) + 10 integration (`*IT`, Failsafe, **ephemeral Testcontainers
Postgres** via `AbstractPostgresIT` + `@ServiceConnection` — isolated from the `docker-compose` play DB
and from prior runs; issue #17). Not yet: NPCs, the `look <target>` / `take` use cases, async/event
processing.
