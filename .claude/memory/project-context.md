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
| Id generation | Domain `Dice` rolls via `core/model/id/Ids` (`ItemId.mint(Dice)`) — no library, no port (was NanoID behind `IdGeneratorOperationsOutputPort`; reclassified to a domain capability, #53) |
| UX | JLine (terminal), `org.jline:jline` 4.1.3 aggregate jar — **decided** (see design-notes) |
| IT isolation | Testcontainers (`org.testcontainers:testcontainers-postgresql`, BOM-managed at 2.0.5 by Boot 4) — ephemeral Postgres per `mvn verify` |

## Repo workflow

- Public GitHub, remote `origin`, default branch `main`, integration branch `dev`.
- `.claude/` is versioned (machine-local `settings.local.json` excluded); keep
  committed content institution-neutral and free of machine setup.
- Standard flow otherwise: `claude/<issue>_*` branch from `dev`, PR `--base dev`.

## Package layout (Clean DDD)

- `core/` — framework-free. `model/{aggregate}/` (aggregate roots + VOs, shared — `scene/`, `player/`,
  `item/`, `npc/` (the `Npc` aggregate + `NpcId`/`NpcTemplate`), `spawn/` (the shared `SpawnRule` VO, used by item and npc templates), `calendar/`, `clock/`, `daytime/` (`DayPhase`/`DayPhaseSchedule` VOs + the `DayPhaseLog` singleton aggregate),
  `dice/` (the `Dice` domain capability — interface + `AbstractDice`/`SystemDice`/`SeededDice` impls — and the `Chance` VO it rolls; design-notes §4),
  `designation/` (the `Designatable` capability interface — the two facts the `select` dialogue asks of a candidate, implemented by `Item`; #67),
  `blackjack/` (the cards **generic subdomain** as pure VOs — `Suit`/`Rank`/`Card`/`Hand`/`Deck`/`BlackjackRound`/`RoundOutcome`; ArchUnit-confined to itself + `dice/` + the `model/` root + JDK/Lombok — a simulated module boundary, so the package can never name a `PlayerId`; #72),
  `id/` (the `Ids` helper — the model's single knower of the generated-id-body alphabet+length; `ItemId.mint(Dice)` rolls bodies through it, design-notes §2/§4/#53)) plus the `model/` root holding the always-valid construction gate's failure type
  `InvalidDomainObjectError` + the `DomainValidation` helper (constructors/factories throw it; behaviour-method
  arg guards stay plain `Objects.requireNonNull`/NPE — design-notes §2), `port/{operation}/` (output ports — `port/persistence/`, `port/transaction/`, `port/player/`,
  `port/seed/`, `port/calendar/` (calendar-source port + error), `port/daytime/`
  (day-phase-schedule source port + error), `port/clock/`
  (time-source port) — the seed package holds the seed-source port and the
  `GameSeed`/`*Entry` carriers it returns; the day-phase-log repository port lives in `port/persistence/` with the other repos), `usecase/{summarygoal}/` (use-case class + its input and presenter ports;
  a reusable **subcase** gets its own peer package, e.g. `usecase/orient/` and `usecase/select/` (the `AbstractSelectTargetSubcase<C, T>` Template-Method base — generic in coordinate *and* candidate, `T extends Designatable` — + its `SelectSceneItemSubcase`/`SelectInventoryItemSubcase` concretes); `usecase/clock/` holds `AskForTime` + `SuspendGame` + `AnnounceTimeOfDay`; `usecase/guidance/` holds the presenter-only `Guidance` use case; `usecase/inventory/` holds `Take` + `Drop` (move an item between the ground and the player's keeping) + `Inventory` (list the keeping); `usecase/npc/` holds `AnimateNpcs` (system-actor autonomous NPC movement); `usecase/blackjack/` holds `PlayBlackjack` (play a hand against the dealer persona — the ephemeral conversation, #72)).
- `infrastructure/` — adapters, Spring wiring. At the **root**: `GameCleanApplication` (entry point;
  here so component scanning never reaches `core`), `UseCaseConfig` (composition root), `BootSequence`
  (boot orchestrator), `GameConfigurationProperties` (single `game.*` config catalog — nested `World`,
  `Terminal`, `Player`, `Time`). Sub-packages:
  `infrastructure/persistence/{aggregate}/` (incl. `clock/`, `daytime/`), `infrastructure/mapping/` (layer-neutral MapStruct support — `ScalarConverter`, the shared VO-ID↔`String` `default` converters every DB-entity mapper `extends`; a sibling of `persistence` because id↔`String` is generic scalar mapping, reusable by any future mapper family, #77), `infrastructure/world/` (`GameSeedYamlReader` + `YamlGameSeedSource` + `GameSeeder`),
  `infrastructure/calendar/` (`CalendarYamlReader` + `YamlCalendarSource` — the latter implements **both** the calendar-source and day-phase-schedule-source ports over `calendar.yaml`), `infrastructure/clock/` (`SystemGameTimeSource`),
  `infrastructure/time/` (`GameClockTicker` — the scheduler-driven background metronome (a `SchedulingConfigurer`) driving `AnnounceTimeOfDay`; scheduling enabled on `BootSequence`), `infrastructure/npc/` (`NpcActivityTicker` — the second background metronome, driving `AnimateNpcs`),
  `infrastructure/transaction/` (Spring tx adapter + config), `infrastructure/terminal/` (JLine; sub-packaged
  by concern — root holds `ConsoleSession` driving loop + `TerminalConfig` resource wiring + `AffordanceContext`
  (session-lifetime conversational buffer holding one armed `Affordance`, #72/#79) + the **sealed** `Affordance`
  value (`SelectionAffordance` = kind + selection tokens; `EphemeralAffordance` = kind + opaque state envelope)
  + the `AffordanceKind` enum;
  `command/` the sealed `Command` + `CommandParser`; `conversation/` the kind-routed dispatcher (`Conversation`
  with its per-dialogue `continuedBy(Command)` predicate + `AbstractSelectionConversation` Template-Method base +
  `Examine`/`Take`/`Drop`/`Hit`/`BlackjackConversation`); `presenter/` the driven
  `Terminal*Presenter`s; `render/`
  `Console` (now with `printAbove` for async writes)/`CurrentSceneRenderer`/`OrientRenderer`/`ItemRenderer`/`NpcRenderer`/`BlackjackRenderer`/`CalendarRenderer`/`English`). No `infrastructure/id/` any more — id generation is a domain capability (`core/model/id/Ids` + `Dice`), the NanoID adapter and JNanoID dependency deleted (#53).
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
  (tx), `GameSeedSourceOperationsOutputPort` (`core/port/seed/`, pulls the authored seed); each port owns an
  unchecked boundary error (`PersistenceOperationsError`, `TransactionOperationsError`,
  `GameSeedSourceOperationsError`) — every driven adapter wraps its framework's technical exceptions into one,
  so a use case never catches a raw Spring/JDBC/YAML type (design-notes §2/§3/§5).
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
  use case, not here (design-notes §6). Also carries `@EnableScheduling` (since #36): the enablement for the
  async `GameClockTicker`, gated with the rest of the interactive runtime — recurring driving adapters are
  not one-shot runners, so only their scheduling-enablement lives here.
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
- **Shared presenter capability** — the two not-found outcomes (`presentPlayerNotFound` +
  `presentCurrentSceneNotFound`) live on `OrientPlayerPresenterOutputPort` (the `orient` subcase's port — see
  below; introduced here as `CurrentScenePresenterOutputPort`, since renamed/moved), which the concrete
  presenters implement **flat** beside each use case's own port (#81); `LookPresenterOutputPort` declares
  `presentScene`, `MovePresenterOutputPort` declares `presentSceneEntered` (distinct stripes, one shared
  `CurrentSceneRenderer`) + `presentNoSuchExit`/`presentTargetSceneNotFound` (design-notes §4: three axes of
  sharing + flat presenter ports).
- **Terminal** — `Console` styled-writer resource (§7 facade, declared in `TerminalConfig`) + shared
  `CurrentSceneRenderer`; two thin presenter beans `TerminalLookPresenter` / `TerminalMovePresenter`
  (replacing `TerminalScenePresenter`). `MoveCommand` + `move`/`go` verbs in `CommandParser`; `ConsoleSession`
  dispatches to a pulled prototype `MoveInputPort`.
- **Persistence** — `savePlayer` is now an **upsert** (`existsById ? update : insert`); `@Version`/optimistic
  locking deferred to the concurrency thread (design-notes §5).

`Examine` vertical **complete** (issue #43) — the project's first **multi-interaction** use case (`look <target>` /
`examine <target>` describe one item on the ground; ambiguous descriptions disambiguate via a numbered menu):

- **Domain** — `Item.matches(String)` SEFF (case-insensitive substring on short description; first behaviour on
  `Item`, plain-NPE arg guard per design-notes §2). Items only; `look <exit>` deferred until `Exit` grows a
  description.
- **Use case** — `Examine` (`core/usecase/explore/`): two interactions converging on one outcome
  (`presentItemDescription`) — `playerExaminesTarget(String)` (designate by description: orient → match → 0/1/N
  branch) and `playerExaminesChosenCandidate(int ordinal, List<String> offeredTokens)` (designate by choosing
  from the offer, the disambiguation completion). The selection interaction is **handed the offered tokens as a
  value** by the controller (dependency rejection) and **presents every selection outcome itself**
  (`presentNoSuchOption` / `presentItemNoLongerAvailable` / `presentItemDescription`; the empty-offer case
  became a wiring-precondition throw when the conversation dispatcher arrived — see the Take vertical),
  re-validating the chosen token against live scene state. By-identity resolution is **inlined** into that
  interaction (not a helper — keeps each checkpoint's present-and-return visible in one method); no driver
  designates by raw id yet (a future GUI row-click would get its own interaction). Read-only, no tx, like
  `look`. Disambiguation is a Cockburn **extension**; choosing-by-number is a **variation** of target
  designation (design-notes §4).
- **Conversational state** — `AffordanceContext` (`infrastructure/terminal/`, a session-lifetime resource
  declared in `TerminalConfig`): remembers the offered candidate **id tokens** in display order. It trades in
  **raw `String` tokens, not the `ItemId` model VO** — the driven presenter does the `getId().asString()` flatten
  when it arms it; the primary console adapter stays model-free per "primitives inward" (§6) (design-notes §4).
  Surface is `offer` / `currentOffer` / `clear` — a dumb store that resolves nothing and presents nothing. The
  **presenter** arms it as it renders the menu; the **controller** (`ConsoleSession`) only detects the selection
  intent, hands `currentOffer()` to the use case as a value, and abandons (clears) the offer on any other
  command. The use case owns the conversation — it resolves the pick and presents all outcomes; the controller
  decides and renders nothing (design-notes §4).
- **Presenter port re-split (ISP)** — `OrientPlayerPresenterOutputPort` shrank to the two not-found outcomes the
  subcase presents; `presentScene` moved down into a `CurrentScenePresenterOutputPort` (look/move) — since
  **deleted** under the flat-port rule (#81): each port declares its own scene outcome. Renderers mirror the
  split: `OrientRenderer` (not-founds, shared), `CurrentSceneRenderer` (scene only), `ItemRenderer` (examine
  outcomes) (design-notes §4).
- **Parsing** — `CommandParser` generalized to one factory per verb (returns command-or-null); `look`/`examine`/`x`
  take the line remainder as a multi-word target; a bare positive integer → `SelectCommand`. New `ExamineCommand`
  / `SelectCommand` in the sealed `Command` set.

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
  `fromGeneratedBody` + `mint(Dice)` — the model mints its own ids by rolling dice, no id port), `location`
  (`SceneId` reference — items are their own aggregate, not held by `Scene`),
  short/full descriptions. Plus VOs `SpawnRule` (chance + maxTries + ≥1 candidate scene;
  `rollPlacements(Dice)`/`candidateScenesNotIn`), `ItemTemplate` (descriptions + rule; `instanceAt`,
  `spawnInto(Dice)`, `candidateScenesNotIn`). The probability VO `Chance`, the entropy source `Dice`, and the
  id-body minter `Ids` all live in the model (`core/model/dice/`, `core/model/id/`) — reclassified from output
  ports (#52 randomness, #53 id generation; design-notes §2/§4), so `SpawnRule`/`ItemTemplate` roll/pick/mint
  through a `Dice` rather than a `DoubleSupplier` and a `Supplier<ItemId>`.
  `Scene.exitsWithTargetNotIn` added for symmetry. (design-notes §2, §4, §10.)
- **Use case** — folded into `InitializeGame` as a **third phase** (world → player → items): input **pulled**
  as a `GameSeed` carrier (`core/port/seed/`: `SceneEntry`/`ItemEntry`/`SpawnEntry`) via
  `GameSeedSourceOperationsOutputPort`; spawn rolls (chance per try, uniform scene
  pick, semantics (a) = up to `max` tries) run outside the tx; a third `itemsAlreadySpawned` guard joins
  seed-if-empty/create-if-absent in the single transaction. `presentGameInitialized(scenes, playerId, items)`
  reports items spawned *this run*; new `presentItemSpawnSceneUnknown` stripe. (design-notes §4.)
- **Ports** — `ItemRepositoryOperationsOutputPort` (`findItemsInScene`/`saveItem`/`itemsAlreadySpawned`). Two
  ports that once lived here are gone: the spawn entropy `RandomnessOperationsOutputPort` (→ domain `Dice`, #52)
  and `IdGeneratorOperationsOutputPort.generateItemId()` (→ `ItemId.mint(Dice)` + `Ids`, #53). Both were the
  same realisation — once dice are domain, minting odds *and* identities reaches nowhere outside the hexagon.
- **Presentation** — items belong to the *presented* scene, fetched per use case (`look` current, `move`
  target), **not** in `orient`; `presentScene(Scene, List<Item>)` on the shared port, `CurrentSceneRenderer`
  appends an "On the ground:" list. (design-notes §4.)
- **Persistence / infra** — Flyway `V3__create_item.sql` (`item`; no FK on `scene_id`), `ItemDbEntity` +
  MapStruct mapper + Spring Data repo (`findBySceneId`) + `SpringItemRepositoryAdapter`. (The `JNanoIdGenerator`
  adapter and the JNanoID dependency that once minted ids were deleted in #53 — ids are now rolled in-model.)
  `SceneYamlReader` renamed `GameSeedYamlReader`
  (parses scenes + items, assembles `GameSeed`), now invoked by `YamlGameSeedSource` behind the seed port.

Calendar core model **complete** (issue #31) — time/date value objects + arithmetic only, no ports or
persistence (those arrived with the `now`/`time` vertical, #33):

- **Domain** — `core/model/calendar/`: `GameCalendar` (authored uniform radices — seconds/hour, hours/day,
  days/month — plus ordered `Weekday`/`Month` cycles; always-valid: positive radices, non-empty cycles,
  unique names), owning the mixed-radix `placeInstant(long)`, `monthOf(GameDate)`, the **continuous-week**
  `weekdayOf(GameDate)`, and `absoluteHourOf(long)` (the monotonic hour-since-epoch — un-wrapped companion to
  `placeInstant`'s cyclic `hourIndex`, used by the day-phase dedup) SEFFs. `GameDate` — pure 0-based positional tuple {year, monthIndex, dayIndex,
  hourIndex, secondOfHour}, non-negativity-only gate (`GameCalendar` is its sole factory — shape A).
  `Weekday`/`Month` — named VOs (non-blank name + description). Indices 0-based; ordinal/clock labeling left
  to a future renderer; `EPOCH_YEAR = 1000`. (design-notes §11.)
- **Deferred-then-realized in #33** (not as originally sketched — see design-notes §11): the originally
  wall-clock-leaning shape was reversed to **Model B**, so there is no persisted *real epoch*; instead a
  persisted accumulator (`GameClock`) plus a *session-elapsed* time-source port. The calendar is **not
  persisted** — loaded each boot via a port — so no `calendar:` seed carrier was added.

`now`/`time` command + Model B clock **complete** (issue #33) — the first time-facing interaction; game time
is **accumulated play-time** (advances only while playing; banked on `bye`, no ticker):

- **Domain** — `GameClock` aggregate (`core/model/clock/`): `accumulatedGameSeconds`, `elapsedWith(session)` =
  banked + session, `accumulate(session)` → new clock, `initial()` = 0 (design-notes §11; Model B).
- **Use cases** (`core/usecase/clock/`) — `AskForTime` (`playerChecksTheTime()`, read-only, no tx: load
  calendar + clock + session-elapsed → `placeInstant` → present) and `SuspendGame` (`playerLeavesTheGame()`,
  one `doInTransaction` banks the session, presents after commit). `InitializeGame` gained a fourth
  create-if-absent guard: **create-clock-at-zero** (no ordering precondition; design-notes §4/§11).
- **Ports** — `CalendarSourceOperationsOutputPort.loadCalendar()` (`core/port/calendar/`, returns a **valid**
  `GameCalendar` — load-each-boot, the §3 deviation + `CalendarSourceOperationsError`),
  `GameTimeSourceOutputPort.elapsedSessionSeconds()` (`core/port/clock/`),
  `GameClockRepositoryOperationsOutputPort` (`core/port/persistence/`, find/save, upsert).
- **Adapters** — `YamlCalendarSource` (`infrastructure/calendar/`, constructs+caches the calendar at boot, fail-fast)
  + `CalendarYamlReader`; `SystemGameTimeSource` (`infrastructure/clock/`, wall-clock since session start,
  injectable `Clock`); Flyway `V4__create_game_clock.sql` (singleton row) + `GameClockDbEntity` + MapStruct
  mapper + repo + `SpringGameClockRepositoryAdapter`.
- **Terminal** — `CalendarRenderer` (domain-aware) composing `Console` (styling) + `English` (pure
  ordinals/plurals grammar) — the three-layer presentation composition (design-notes §7); `now`/`time` verbs →
  `TimeCommand`; `bye` dispatches `SuspendGame` before breaking the loop. Presenters `new`ed by `UseCaseConfig`.
- **Config** — `game.time.calendar-location` (default `classpath:world/calendar.yaml`); authored
  `world/calendar.yaml` (radices + named cycles, **world content** not `game.*` config — design-notes §11).

Dawn/dusk announcements + background time ticker **complete** (issue #36) — the project's first asynchronous,
time-driven interaction and first parallel actor (Package B: "dumb metronome, smart use case"):

- **Domain** — `core/model/daytime/`: `DayPhase` (name + 0-based `hourOfDay` + ≥1 non-blank messages; owns the
  uniform `pickMessage(Dice)`, delegating to `Dice.pick` like `SpawnRule` rolls with a `Dice`), `DayPhaseSchedule` (distinct phase
  hours; `phaseBeginningAt(hourIndex)`), and `DayPhaseLog` — a **world-singleton aggregate** holding
  `announcedThroughHour` (the dedup watermark; `-1` = none, monotonic `announceThrough`/`isPending`) plus an
  optimistic-locking `version` carried **on the model** (opaque, excluded from value equality, carried through
  `announceThrough`; design-notes §5).
- **Use case** — `AnnounceTimeOfDay` (`core/usecase/clock/`): system-actor input port `systemObservesTimeOfDay()`,
  presenter port declares `presentDayPhaseBegan` + `presentNothingToAnnounce` + `presentGameNotInitialized`
  (per-port readiness declaration since the flat-port rule deleted `ClockReadinessPresenterOutputPort`, #81).
  Derives "now" like `AskForTime`; reads the log once (capturing its version) + random message pick **outside**
  the tx; one `doInTransaction(action, onLockDetected)` holds the single **version-checked** save (no inside-tx
  re-read), presents after commit. A concurrent loss surfaces as `OptimisticLockingError` → the tx-port
  `onLockDetected` handler → `presentNothingToAnnounce`; a quiet observation also presents
  `presentNothingToAnnounce` (no tx); missing clock → `presentGameNotInitialized`
  (design-notes §5/§8 thread #3). `InitializeGame` gained a **5th create-if-absent guard** (seed
  `DayPhaseLog.initial()` beside the clock).
- **Ports** — `DayPhaseScheduleSourceOperationsOutputPort.loadDayPhases()` (`core/port/daytime/`, returns a
  **valid** `DayPhaseSchedule` — the §3 load-each-boot deviation, + `DayPhaseScheduleSourceOperationsError`);
  `DayPhaseLogRepositoryOperationsOutputPort` (`core/port/persistence/`, find/save); `OptimisticLockingError`
  (`core/port/concurrency/`, its **own neutral package** — raised by persistence, reacted to by the transaction
  port / use case, owned conceptually by the aggregate; not a subtype of either port error — a concurrent loss
  is an outcome, not a fault. Also reacted to via the new `doInTransaction(action, onLockDetected)` overload).
- **Adapters / infra** — `YamlCalendarSource` now implements **both** source ports (reads a `dayPhases:` block in
  `calendar.yaml` via `CalendarYamlReader.readDayPhases`, reconciles each phase hour against `hoursPerDay`
  fail-fast at boot); Flyway `V5__create_day_phase_log.sql` (singleton row, with a `version` column) +
  `DayPhaseLogDbEntity` (`@Version`) + MapStruct mapper + repo + `SpringDayPhaseLogRepositoryAdapter` (version-driven
  `repository.save` upsert — **no** `existsById`/`JdbcAggregateTemplate`, unlike the version-less clock adapter;
  wraps `OptimisticLockingFailureException` → `OptimisticLockingError`); `GameClockTicker` (`infrastructure/time/`, a
  `SchedulingConfigurer` registering a fixed-delay task that fires `AnnounceTimeOfDay`, pulling a fresh prototype
  per tick — carries **no** domain knowledge; reads the **bound, typed** `game.time.ticker.interval` `Duration`
  from the catalog, not a `${}` placeholder — `@Scheduled` placeholders don't see `@DefaultValue`s and reject
  the `5s` form; `@EnableScheduling` sits on the gated `BootSequence`, and Spring's lifecycle cancels scheduled
  tasks before the `Terminal` is destroyed, so `printAbove` never hits a closed terminal — no hand-rolled
  `SmartLifecycle`); `TerminalAnnounceTimeOfDayPresenter` announces via the new
  `Console.printAbove` (first async console write) and trace/warn-logs the quiet/not-ready/error outcomes.
- **Config** — `game.time.ticker.interval` (default `5s`, a `Duration`); authored `dayPhases:` (Dawn@6, Dusk@18)
  in `world/calendar.yaml`. Deferred: catch-up across boundaries skipped when the interval exceeds a game hour
  (detects only a phase at the *current* hour).

`Guidance` vertical **complete** (issues #45, #47) — player-orientation output; the console's last direct
write removed, so `ConsoleSession` now presents nothing itself:

- **Use case** — `Guidance` (`core/usecase/guidance/`): **presenter-only, no domain ports** (the project's
  thinnest). Two interactions, one player audience: player-actor `playerIssuesUnrecognizedCommand(String)` →
  `presentUnrecognizedCommand`, and system-actor `systemGreetsPlayer()` → `presentWelcome`. Abstract outcomes —
  no command vocabulary crosses into core. Near-empty use case is intentional ("controllers never present" +
  "a presenter port is mandated even with no human audience"); multi-actor in one use case is fine
  (design-notes §4, §9).
- **Presenter** — `TerminalGuidancePresenter` (`infrastructure/terminal/presenter/`) owns the curated command
  list in one shared constant (welcome + unrecognized-nudge can't drift); welcome cyan, nudge yellow.
- **Console as request-dispatcher** — `ConsoleSession.start()` no longer has `printLine` (deleted). The welcome
  is the loop's **turn-1 system request** (`greetPlayer(); continue;`), dispatched directly — **not** a
  `WelcomeCommand` in the parsed-intent `Command` set (the `Command` set stays = parser output; the parser
  never produces a welcome). `bye` is intercepted before the dispatch switch (it must `break` the loop). The
  loop is the internalized request-dispatcher; per turn, fire-and-forget holds with no exception
  (design-notes §9).

`Take` vertical **complete** (issue #55, PR1) — pick an item off the ground into the player's keeping; the
project's first contested-resource write and first multi-conversation terminal dispatch:

- **Domain** — sealed `Location` VO (`core/model/item/`): `OnGround(SceneId)` / `HeldBy(PlayerId)` cases
  (`@Value`), generalizing `Item.location` from a bare `SceneId` so location is mobile (ground ↔ held). `Item`
  gains `@With`, an opaque optimistic-locking `version` (excluded from id-equality, carried through copy-on-write),
  and `takenBy(PlayerId)` → `withLocation(new HeldBy(holder))` (plain-NPE guard). `ItemTemplate.instanceAt` builds
  `OnGround`. Holder is `PlayerId` until NPCs force a generalization (emergence). (design-notes §2.)
- **Use case** — `Take` (`core/usecase/inventory/` — a new summary goal, acquisition vs. `explore`'s perceive):
  two interactions converging on `presentItemTaken` — `playerTakesTarget(String)` and
  `playerTakesChosenCandidate(int, List<String>)`. Pure orchestration: `orient` → `select` (the scene-ground
  `SelectSceneItemSubcase`, reused) → `item.takenBy(player)` → one
  `doInTransaction(action, onLockDetected)` holding only `saveItem`, present after commit. No
  value-object-construction checkpoint (orient/select hand it valid objects). `TakePresenterOutputPort` extends
  `ErrorHandlingPresenterOutputPort` + `presentItemTaken(Item)` + `presentItemGotAway(ItemId)` (the distinct
  write-side lock-loss outcome; concrete presenter also implements the orient + select ports). (design-notes §4/§5.)
- **Concurrency** — `@Version` on `Item` (first contested-resource write): the lock-loss in `onLockDetected` is the
  write-side twin of `select`'s read-side `presentItemNoLongerAvailable`. Mirrors `DayPhaseLog`: version on model +
  `@Version` on `ItemDbEntity`, version-driven `saveItem` (null/0 → insert so spawn still inserts; >0 →
  update-with-check), wrapping `OptimisticLockingFailureException → OptimisticLockingError`. (design-notes §5.)
- **Select ripple** — `presentNoPendingSelection` removed from `SelectTargetPresenterOutputPort`; the empty-offer
  branch becomes a throwing precondition guard (the dispatcher resumes only an armed conversation, so an empty
  offer reaching the subcase is a wiring fault, not a player outcome).
- **Persistence** — Flyway `V6__item_mobile_location_and_version.sql`: `scene_id → (location_kind, location_ref)`,
  backfill existing rows `GROUND`/version 1, add `version`. `ItemLocationKind` enum (infra), MapStruct
  `Location ↔ (kind, ref)` converter (exhaustive `switch`), repo `findByLocationKindAndLocationRef`, adapter
  version-driven save.
- **Terminal — conversation dispatcher** (`take` is conversation #2, so it forces kind-routing — corrects the
  issue's "drop forces it"): `AffordanceKind{EXAMINE,TAKE}` enum; `AffordanceContext` now carries `(kind, tokens)`;
  `infrastructure/terminal/conversation/` holds `Conversation{kind(); resume(Command, offer)}` +
  `AbstractSelectionConversation` (Template Method, factors the `SelectCommand→ordinal` cast) +
  `ExamineConversation`/`TakeConversation`. `ConsoleSession` injects `List<Conversation>` (the container *is* the
  resumer map — no hand-maintained `kind→useCase` table), routes a `SelectCommand` to the conversation matching
  the armed kind (else folds into `guide`), and asserts at startup (`@PostConstruct`) that every `AffordanceKind`
  has a handler. New `TakeCommand` + `take`/`get` verbs; `TerminalTakePresenter` (arms kind `TAKE`); `ItemRenderer`
  gains `renderItemTaken`/`renderItemGotAway`. (design-notes §4/§9.)
- **Composition root** — `takeUseCase` prototype (shared presenter, as examine); singleton
  `examineConversation`/`takeConversation` beans.

`Drop` vertical **complete** (issue #55, PR2) — put a carried item down onto the current scene's ground; the
second inventory goal, and the second `select` provisioner that extracted the Template-Method base:

- **Domain** — `Item.droppedAt(SceneId)` → `withLocation(new OnGround(scene))`: the copy-on-write twin of
  `takenBy` (plain-NPE guard, version carried).
- **Select generalization** — `SelectTargetSubcaseInputPort<C>` is now **generic in its coordinate** (issue #55
  decision #6: one Cockburn goal = one port; provenance is a parameter, not a different goal);
  `AbstractSelectTargetSubcase<C>` holds the whole dialogue skeleton (`final` template methods) with one
  `provisionCandidates(C)` hook; concretes `SelectSceneItemSubcase` (`SceneId` → scene ground, `examine`/`take`)
  and `SelectInventoryItemSubcase` (`PlayerId` → player's keeping, `drop`). Candidate type stayed `Item` at
  this point (`<C, T>` deferred until a non-item selection exists — since cashed, #67 below). Shared outcome
  renamed provenance-neutral: `presentItemNoLongerHere` → `presentItemNoLongerAvailable` (presenters render
  ground- vs carry-flavored English; since renamed again type-neutral, #67). (design-notes §4.)
- **Use case** — `Drop` (`core/usecase/inventory/`): `playerDropsTarget(String)` +
  `playerDropsChosenCandidate(int, List<String>)` converging on `presentItemDropped(Item)`. The criss-cross
  mirror of take — selects by player, mutates by scene — over the same `orient`+`select` opening; no
  construction checkpoint. **Plain** `doInTransaction` (no `onLockDetected`): a held item is single-writer, so
  a lock loss is unreachable and propagates to the catch-all — the contested→handler (`take`) vs
  single-writer→propagate (`drop`) contrast, pinned by a unit test (design-notes §5).
- **Ports / persistence** — `findItemsHeldBy(PlayerId)` on the item port; the adapter reuses the existing
  `findByLocationKindAndLocationRef` derived query with kind `HELD`. **No Flyway migration** (V6 already carries
  location + version).
- **Terminal** — `DropCommand` + `drop`/`put` verbs (remainder-as-target); `AffordanceKind.DROP`;
  `TerminalDropPresenter` (arms DROP; carry-flavored English via new `ItemRenderer` variants);
  `DropConversation` — conversation #3, which *confirms* the kind-routed dispatcher (dispatcher and startup
  completeness assertion untouched). Composition root: `dropUseCase` prototype + `dropConversation` singleton.

`Inventory` vertical **complete** (issue #60) — the summary goal's one read (`inventory`/`i` lists the
player's keeping):

- **Use case** — `Inventory` (`core/usecase/inventory/`): `playerReviewsBelongings()` (no args, ambient
  player), read-only, no tx, no subcases. Opens by resolving the **player inline, not via `orient`** —
  inventory is grounded in the player alone, so the scene would be over-fetch and a dangling current-scene
  reference must not block it (outcome-sharing tracks the shared prologue, which inventory doesn't share).
  `InventoryPresenterOutputPort`: one success `presentCarriedItems(List<Item>)` (empty keeping = same
  outcome, phrased by the renderer — the `presentScene` empty-ground precedent) + its **own**
  `presentPlayerNotFound` (rendered via the shared `OrientRenderer` — shared rendering by composition, not
  port reuse). No new ports (`findItemsHeldBy` from drop).
- **Terminal** — `InventoryCommand` (marker) + `inventory`/`i` verbs; `TerminalInventoryPresenter` (one flat
  port, no `AffordanceContext`); `ItemRenderer.renderCarriedItems` (sorted list / "You are carrying
  nothing."); prototype bean in `UseCaseConfig`. Guidance's curated `AVAILABLE_COMMANDS` also caught up
  (`take`/`drop` had been missing) and gained `inventory`.

`NPC` step 1 vertical **complete** (issue #63) — authored NPCs: spawned at init, listed in the room,
autonomously wandering; the first realization of the `[thread #3]` "Player and NPCs act in parallel" premise:

- **Domain** — `Npc` aggregate (`core/model/npc/`): `NpcId` (prefix `npc`, `mint(Dice)`), `currentScene`
  (`SceneId` reference), short/full descriptions, `moveChance` (`Chance`), `moveTo(SceneId)` copy-on-write.
  **No `@Version`** — single-writer (only the ticker writes), deferred until the player can affect an NPC
  (design-notes §5). `NpcTemplate` (descriptions + `SpawnRule` + `moveChance`, `spawnInto(Dice)`). `SpawnRule`
  **relocated** `item/` → **`core/model/spawn/`** (its second consumer is NPC spawning; a neutral package
  avoids a bogus `npc → item` edge).
- **Use case — `AnimateNpcs`** (new summary goal `core/usecase/npc/`): system-actor `systemAdvancesNpcs()`,
  no security assertion, no `orient` reuse. Loads all NPCs; per NPC rolls `moveChance`; on a hit picks a
  random exit (`Dice.pick`) and `moveTo` the target (skips silently on no-exits / dangling target). Resolves
  the player's scene inline to filter *perceptible* movements (`PerceivedNpcMovement` + `MovementKind`
  DEPARTED/ARRIVED); one **plain** transaction saving moved NPCs; `presentNpcMovements`/`presentNothingHappened`
  after commit (quiet tick, empty world, off-stage move all present the quiet outcome). Co-located presenter.
- **InitializeGame** gained a fourth spawn phase (build → resolve-scenes → roll → `npcsAlreadySpawned`
  spawn-if-none guard, folded into the one transaction and `presentGameInitialized(scenes, playerId, items, npcs)`);
  new `presentNpcSpawnSceneUnknown` stripe.
- **Ports / persistence** — `NpcRepositoryOperationsOutputPort` (`findAllNpcs`/`findNpcsInScene`/`saveNpc`/
  `npcsAlreadySpawned`); Flyway `V7__create_npc.sql` (no FK, no version), `NpcDbEntity`, MapStruct mapper
  (`Chance ↔ (num,den)`), `findByCurrentSceneId` repo, `SpringNpcRepositoryAdapter` (version-less `existsById`
  upsert, mirroring the player adapter).
- **Room-listing ripple** — `presentScene(Scene, items, npcs)`; `look`/`move` fetch `findNpcsInScene` for the
  presented scene (current / target); `CurrentSceneRenderer` "Also here:" block.
- **Ticker / infra** — `NpcActivityTicker` (`infrastructure/npc/`, blind `SchedulingConfigurer`, second async
  writer/metronome; reads `game.npc.ticker.interval` default `10s`); `TerminalAnimateNpcsPresenter` +
  `NpcRenderer` (async `printAbove` narration); `game.npc.*` on `GameConfigurationProperties`.

`select` candidate-type generalization **complete** (issue #67) — the deferred `<C, T>` step, cashed as a pure
behavior-preserving refactor ahead of the `hit` vertical (#66, whose combat targets are the second candidate
kind), so `hit` lands with full disambiguation from day one:

- **Domain** — `Designatable` capability interface (`core/model/designation/`, the neutral-package precedent
  of `spawn/`): `matches(String fragment)` + `hasIdToken(String idToken)` (pure comparison against the id
  flatten). `Item` implements it (its `matches` promoted to the interface; `hasIdToken` = value-compare, no
  reconstitution).
- **Select** — `SelectTargetSubcaseInputPort<C, T extends Designatable>` and
  `AbstractSelectTargetSubcase<C, T>`: the skeleton is type-blind, with **two** type-bound hooks —
  `provisionCandidates(C)` (provenance) and the new eager `requireWellFormedToken(String)` (token shape: the
  concrete reconstitutes its candidate's id VO as a pure validity gate, preserving malformed-token-is-an-
  internal-fault *before any read*; folding it into per-candidate `hasIdToken` would mislabel the
  empty-candidates+malformed corner as a presented outcome). `SelectTargetPresenterOutputPort<T>` goes
  generic-in-T (deliberately unbounded — it demands nothing of `T`);
  `presentItemNoLongerAvailable(ItemId)` renamed type-neutral `presentTargetNoLongerAvailable(String idToken)`
  (carries the raw token — all the type-blind skeleton holds on that branch; renderers ignore it). Concretes
  re-typed `<SceneId, Item>` / `<PlayerId, Item>`; parents (`Examine`/`Take`/`Drop`) and presenters bind
  `Item`; terminal side (AffordanceContext, SelectCommand, conversation dispatcher) untouched — already
  string-token-based ("primitives inward" payoff). (design-notes §4.)

`Blackjack` mini-game vertical **complete** (issue #72) — the first **ephemeral conversation**: a
multi-interaction use case whose between-interaction state is a pure value riding the terminal session,
deliberately never persisted (abandonment = forfeit; the dealer sweeps the cards):

- **Domain** — `core/model/blackjack/` (see package layout above): `Hand.bestValue()` owns the soft-ace
  rule; `BlackjackRound` is a *position, not an entity* — `deal(Dice)` captures the shuffled deck so
  `playerDraws()`/`dealerPlaysOut()`/`settle()` are pure and the stand playout consumes **zero entropy**
  (capture-at-offer in a VO). `Dice` gained `shuffle(List)` (Fisher–Yates over `nextDraw()` in
  `AbstractDice`; `ScriptedDice` shuffles identity + scriptable `willShuffleToFront`). `Scene` gained
  `Set<MiniGame> miniGames` + `offers(MiniGame)` (`MiniGame` enum, `fromAuthoredName` gate — unknown
  authored name fails init as invalid parameters), authored per scene as a `mini-games:` comma list.
- **Use case** — `PlayBlackjack` (`core/usecase/blackjack/`, summary goal *amusement*; dealer is a persona
  executed by the system — no NPC): `playerSitsDownToPlay()` (orient prologue + `scene.offers(BLACKJACK)`
  grounding → deal; player natural settles immediately), `playerAsksDealerForHitCard(round)`,
  `playerRequestsToStand(round)` (synchronous dealer playout → dealer-busted/player-wins/dealer-wins/push),
  `playerExaminesGame(round)` (Cockburn **anytime extension** `*a`; also where an armed `play` folds). The
  round crosses the input port **as a value** relayed by the shell; a null/busted round is a
  wiring-precondition throw to the catch-all. **No persistence or transaction ports** — the thinnest
  orchestration after `Guidance`; unit tests need no repository mocks at all.
- **Terminal** — new commands: `PlayCommand` (`play`), `HitCardCommand` (bare `hit` and `hit me` — the
  parser splits the `hit` verb by token shape; `hit <target>` stays combat), `StandCommand`
  (`stand`/`stay`), `GameStandingCommand` (`game`/`table`). `AffordanceContext` holds one armed
  `Affordance` — an **opaque envelope** (`EphemeralAffordance`) the shell never reads, or a token offer
  (`SelectionAffordance`); the buffer is a pure holder `offer`/`arm`/`current`/`clear` (sealed split + slim,
  #79). `Conversation` gained
  `default continuedBy(Command)` (bare-number default) and `resume(Command, Affordance)`; the dispatcher
  gives the armed conversation **first crack** at each parsed line, else clears (abandonment — the forfeit
  for an ephemeral dialogue) and dispatches; stray table-talk verbs fold to guidance.
  `BlackjackConversation` (kind `BLACKJACK`) is the first verb-continued handler; the presenter owns the
  arming transcription (live outcomes arm/re-arm the round, terminal outcomes disarm — completion-disarm);
  `BlackjackRenderer` renders cards as plain English words (encoding-proof). Guidance's curated list
  gained `hit <target>` and `play`.
- **Persistence** — only the scene attribute: Flyway `V9__create_scene_mini_game.sql` (child table, the
  `exit` pattern), `MiniGameDbEntity` + second `@MappedCollection` on `SceneDbEntity`, MapStruct
  enum↔string through the `fromAuthoredName` gate (a corrupt stored name = wrapped integrity fault). The
  round itself is never stored. A pre-existing play DB never gains the authored mini-game
  (seed-if-empty) — reset the docker volume to re-seed.
- **Composition root / ArchUnit** — `playBlackjackUseCase` prototype (presenter shared with orient,
  `SystemDice`) + singleton `blackjackConversation`; new `BlackjackSubdomainArchitectureTest` (a
  *positive* dependency rule, so it analyzes production classes only via `ImportOption.DoNotIncludeTests`).

Tests: 441 unit (Surefire, DB-free) + 24 integration (`*IT`, Failsafe, **ephemeral Testcontainers
Postgres** via `AbstractPostgresIT` + `@ServiceConnection` — isolated from the `docker-compose` play DB
and from prior runs; issue #17). Not yet: `look <exit>` (awaits an `Exit` description), `examine`
over carried items (needs a composite ground∪keeping provisioner), NPCs *reacting* to the player and
async/event processing (both tickers poll; the outbox event spine is still ahead), blackjack stakes
(the trigger that would mint the round aggregate — see design-notes §9).
