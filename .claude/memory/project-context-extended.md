# game-clean — extended context

> **Charter — deep reference for current facts, recipes, conventions.**
> This file owns the **on-demand deep reference** for what exists today: domain
> model overview, use-case inventory, infrastructure adapters, persistence /
> security / frontend / test specifics, code conventions (Lombok shape, mapper
> wiring, naming patterns), end-to-end recipes, and recorded methodology
> deviations.
>
> - Update when a convention changes, a pattern is added, a new adapter family
>   appears, or a recipe needs revising.
> - Facts only. Rationale ("why we chose X over Y") belongs in `design-notes.md`.
> - Quick-reference one-liners belong in `project-context.md`, not here.

## Persistence (Spring Data JDBC + MapStruct + Flyway, no ORM)

Established by the scenes persistence spike, repeated verbatim for the player aggregate. Lives under
`infrastructure/persistence/{aggregate}/` (`scene/`, `player/`).

- **DB entities** — `*DbEntity` (e.g. `SceneDbEntity`, `ExitDbEntity`): plain Lombok
  `@Data` holders, single no-arg constructor (so Spring Data JDBC + MapStruct both bind
  cleanly). Spring Data JDBC annotations: `@Table`, `@Id`, explicit `@Column("snake_case")`
  (the default naming leaves camelCase unquoted → folded lowercase, so be explicit). Owned
  children map via `@MappedCollection(idColumn = "...")` on the parent (the child carries no
  back-reference field).
- **Repository** — `*SpringDataRepository extends CrudRepository<*DbEntity, String>`. Infra
  plumbing, *not* a domain port; the use case depends on the `*OutputPort` whose adapter (added
  with the use case) delegates here.
- **Mapper** — `*DbEntityMapper` (MapStruct, `componentModel = "spring"`). Domain ↔ DbEntity.
  Value-object id wrappers convert via `default` methods (`SceneId ↔ String`); re-wrapping runs
  the VO's own validation. Builds the domain aggregate through its Lombok builder — requires
  `lombok-mapstruct-binding` in the annotation-processor path (order: lombok → binding →
  mapstruct).
- **Writes vs reads** — writes are inserts via `JdbcAggregateTemplate.insert` (assigned String
  ids would otherwise be treated as updates); reads via the repository.
- **Schema (Flyway)** — migrations in `src/main/resources/db/migration/` (`V1` = `scene` + `exit`,
  `V2` = `player`). A composite PK on an owned child `(scene_id, name)` enforces a domain uniqueness
  invariant at the DB level. Cross-aggregate references (`exit.target_scene_id`, `player.current_scene_id`)
  carry **no FK** — resolution is a use-case rule yielding a domain outcome, not an FK violation.
- **Player family** (`infrastructure/persistence/player/`) — mirrors the scene family exactly:
  `PlayerDbEntity` (`@Table("player")`, `@Column("current_scene_id")`, no owned children),
  `PlayerDbEntityMapper` (`PlayerId`/`SceneId` ↔ String converters), `PlayerSpringDataRepository`,
  `SpringPlayerRepositoryAdapter` implementing `PlayerRepositoryOperationsOutputPort` (`findPlayer` via
  `findById().map(toDomain)`, `savePlayer` via `aggregateTemplate.insert`).

### Test layering — Surefire (unit) vs Failsafe (integration)

- **Unit tests** = `*Test`, run by Surefire in the `test` phase, **DB-free** (domain + use cases
  with mocked ports). The 27 model/arch tests are here.
- **Integration tests** = `*IT`, run by Failsafe in the `verify` phase (Failsafe execution is
  wired explicitly in `pom.xml`), against the **real Dockerized Postgres** — `@DataJdbcTest`
  + `@AutoConfigureTestDatabase(replace = NONE)`, datasource in `src/test/resources/application.yaml`
  (superuser role, since it writes). `@DataJdbcTest` rolls back each test; Flyway DDL still commits.
  Slice tests live under `infrastructure/...` so they find `@SpringBootApplication`.
- So `mvn test` stays DB-free; `mvn verify` needs the container up.

## Transactions (explicit demarcation, no blanket `@Transactional`)

`core/port/transaction/TransactionOperationsOutputPort` — lean 4-method canon over plain
`Runnable`/`Supplier`: `doInTransaction(readOnly, …)`, `doInTransactionWithResult(readOnly, …)`,
`doAfterCommit(…)`, `doAfterRollback(…)` (+ no-readOnly `default` overloads). Usage rule: validation
and reads run **outside** the transaction; only persistence (later, event dispatch) runs **inside**;
present in `doAfterCommit` (never before commit). Failure is expressed by **throwing** (unchecked) —
caught at the use case's outermost checkpoint; there is deliberately no `rollback()`.

- Adapter — `infrastructure/transaction/SpringTransactionAdapter` (plain Lombok class, not
  component-scanned), backed by Spring `TransactionTemplate` + `TransactionSynchronizationManager`.
  `doAfterCommit` runs immediately when no tx is active; `doAfterRollback` is a **no-op** when none
  is active. **No cache coupling** (project has no cache); the methodology's
  `CacheInvalidationOnRollback` seam is added only when a cache appears.
- Wiring — `infrastructure/transaction/TransactionConfig` declares both `TransactionTemplate` beans
  (read-write `@Primary` + `@Qualifier("read-only")`) and the adapter as an explicit `@Bean`.
- Deliberately **leaner than `cargo-clean`'s** legacy adapter: dropped its `*WithResult`
  after-commit/rollback variants (broken `AtomicReference` timing), its `rollback()`, and its
  `CacheManager` coupling. Rationale in design-notes §5 (Explicit transaction demarcation).
- Tested by `TransactionOperationsIT` (`@SpringBootTest`, real DB, no test-managed rollback) — the
  one IT that is **not** a `@DataJdbcTest` slice, because commit/rollback must be observable.

## Use cases, composition root, and the startup seeder

Established by the `ConstructWorld` vertical, now the `InitializeGame` use case (`core/usecase/initialize/`).

- **Use case shape** — input port (`{Name}InputPort`, all interactions `void`), framework-free
  `{Name}UseCase` (`@RequiredArgsConstructor`, `@FieldDefaults(makeFinal, PRIVATE)`, ports as
  `presenter` + `*Ops` fields), and a co-located `{Name}PresenterOutputPort extends
  ErrorHandlingPresenterOutputPort` (base lives in `core/port/`). Input crosses as primitives, never
  domain types: a *driving* command adapter pushes a bare value when it is a single value
  (`Look`/`look(String playerId)` — no DTO) or `*Entry` DTOs when it has structure; `InitializeGame`
  instead takes **no argument** and **pulls** its `GameSeed`/`*Entry` carriers from
  `GameSeedSourceOperationsOutputPort` (those DTOs live in `core/port/seed/`, the output-port contract,
  not the use-case package — design-notes §3). Either way, value objects are constructed inside the use
  case, never by adapters.
- **Read-only use cases take no transaction.** `Look` (`core/usecase/explore/`) reads through the
  persistence ports and presents directly — no `TransactionOperationsOutputPort` collaborator at all,
  no after-commit hook. Outcomes (missing player, dangling current scene) are reached by
  branch-and-present, the error catch-all by the outermost `try`. Summary-goal package `explore/`
  (looking around; later `look <target>` / `move`).
- **Subcase (shared, reusable interaction logic)** — `core/usecase/orient/` holds the project's first
  subcase: `OrientPlayerSubcaseInputPort.playerGetsBearings()` / `OrientPlayerSubcase`, a **guarded
  prologue** reused by `look`/`move` (resolve ambient player → resolve their current scene). Its own
  presenter port `OrientPlayerPresenterOutputPort` is extended by each parent's presenter port, and the
  parent is handed one presenter instance the subcase shares. On a missing player / dangling current scene
  it presents the outcome and throws the marker `SubcaseAlreadyPresented` (`core/port/`); on success it
  *returns* `OrientPlayerResult` (player + scene). Parents delegate their opening and swallow the marker in
  a dedicated `catch (SubcaseAlreadyPresented)` ahead of the catch-all. Tested directly
  (`OrientPlayerSubcaseTest`); parent unit tests mock the subcase (design-notes §4).
- **Composition root** — `infrastructure/UseCaseConfig` declares each use case `@Bean
  @Scope(PROTOTYPE)`, return-typed to the **input port interface** (impl hidden from the container),
  assembled with explicit `new` (no Spring stereotype on core classes). **Presenters and subcases are
  `new`ed inside the factory, not injected as beans** (no presenter is a `@Component`): the factory `new`s
  its presenter and passes that one instance to both the use case and any subcase it drives — `InitializeGame`
  included (`new LoggingInitializeGamePresenter()`). Trade-off + testing corollary (a presenter is not
  bean-swappable → unit tests mock it, ITs assert persisted state) in design-notes §6.
- **Loading a prototype use case from an adapter** — inject the Spring `ApplicationContext` and call
  `appContext.getBean(XxxInputPort.class)` at the start of each interaction (cargo-clean
  `BookingController` idiom; rationale/trade-off in design-notes §6). A singleton adapter
  fetches per interaction, never holds the prototype (that would defeat the scope).
- **Persistence adapter** — `Spring{Aggregate}RepositoryAdapter` (`@Component`): emptiness via
  `repository.count() == 0`, writes via `JdbcAggregateTemplate.insert(mapper.toDbEntity(...))` (insert
  — ids are assigned); infra exceptions wrapped in `PersistenceOperationsError`.
- **Game seeding** — split into a *driven source adapter* and a *thin driving adapter*.
  `infrastructure/world/YamlGameSeedSource` implements `GameSeedSourceOperationsOutputPort`: it reads
  `world/scenes.yaml` via `GameSeedYamlReader` + the configured starting scene and returns the `GameSeed`,
  wrapping any `IOException` in `GameSeedSourceOperationsError` (presented, not fail-fast). The use case
  *pulls* that as Checkpoint 1. `infrastructure/world/GameSeeder` (plain singleton, *not* an
  `ApplicationRunner`) is now logic-free: it fires `InitializeGame` via `seed()` (one `getBean` prototype
  pull, no return used). Idempotent (the use case's seed-if-empty / create-if-absent / spawn-if-none
  guards), so it is invoked unconditionally by `BootSequence`'s ordered runner on every interactive boot.
  Replaces the former `WorldSeeder` + `PlayerSeeder` pair; the world→player→items order is internal to the
  use case (design-notes §3/§4/§6). Presenter `LoggingInitializeGamePresenter` logs the outcome to file.

## Boot orchestration, config catalog, and the terminal shell

Established by the JLine entry-point work (issue #6).

- **`BootSequence`** (`infrastructure/`, a `@Configuration`) declares two `@Order`-ed `ApplicationRunner`
  beans — `@Order(1)` fires `gameSeeder.seed()`, `@Order(2)` fires `consoleSession.start()` — co-located so
  the order is one readable declaration. **No imperative `seed(); start();` body**: each runner is an
  independent inward fire, which removes the return-of-control site an outcome-dependent branch could creep
  into (unidirectional flow > imperative visibility; design-notes §6). Boot-time *ordering* of driving
  adapters is a composition-root concern; *business sequencing* (world→player→items) is not — it lives
  inside `InitializeGame`. Class-level `@ConditionalOnProperty(game.terminal.enabled)` gates both runners,
  so test slices get neither — they never seed implicitly, block on a console, or grab a system terminal.
  (`@Order` on `ApplicationRunner` beans is unchanged on Boot 4 — see `spring-boot-4-notes.md`.)
- **Two adapters, one terminal** — `TerminalConfig` declares the JLine `Terminal` + `LineReader` as
  shared singleton *infrastructure resources* (`@Bean(destroyMethod = "close")` on the terminal), guarded
  by `game.terminal.enabled`. The driving `ConsoleSession` and the driven `TerminalScenePresenter` both
  inject them; sharing a *resource* (not an *adapter*) keeps the two on opposite hexagon sides without a
  doctrine breach (design-notes §7).
- **Command parsing + the controller seam** (issue #8, retiring the spike) — `CommandParser` (tokenizer +
  verb registry, `infrastructure/terminal/`) turns a line into a `Command` intent
  (`LookCommand`/`QuitCommand`/`UnknownCommand` — infrastructure-local, never crosses into core).
  `ConsoleSession` is now a thin **controller**: read → `parse` → dispatch (`QuitCommand` breaks the loop;
  `LookCommand` pulls a prototype `LookInputPort` via `getBean` and calls `look(game.player.id)`;
  `UnknownCommand` echoes a hint). The `Look` use case drives `TerminalScenePresenter` (now implementing
  `LookPresenterOutputPort`), so the console no longer touches the presenter. ANTLR deferred — rationale
  in design-notes §9. Adding a command/synonym = one `register(...)` line in `CommandParser`.
- **`GameConfigurationProperties`** (`infrastructure/`) — the single catalog of every `game.*` property,
  nested static classes per group (`World.seedLocation`, `Terminal.enabled`). Constructor-bound, Lombok,
  `@DefaultValue` (bare on nested groups). Enabled via `@EnableConfigurationProperties` on
  `GameCleanApplication`. Caveat: `@ConditionalOnProperty` guards still read `game.terminal.enabled` raw
  from the `Environment` (a condition can't consult a bound bean), so that key is bound here *and* named
  in the guards.
- **Logging** — `src/main/resources/logback-spring.xml` sends all logs to `./logs/game-clean.log` with
  **no console appender** (JLine owns the console); `spring.main.banner-mode=off`.
  `src/test/resources/logback-test.xml` restores console logging for the build (takes precedence on the
  test classpath). `logs/` is git-ignored.
- **Test seam** — `InitializeGameIT` (`@SpringBootTest`, terminal off) drives the `InitializeGame` use
  case through the composition root by firing the no-arg `systemInitializesGame()` — the use case now
  *pulls* the seed itself through the real `YamlGameSeedSource`, so the test no longer reads entries — and
  asserts the **persisted state** (the presenter is `new`ed, not a mockable bean) over two runs to prove
  idempotency. `BootSequenceTest` (unit) pins each `@Order`-ed runner firing only its own adapter and that
  seeding's `@Order` precedes the console's (read by reflection). `InitializeGameUseCaseTest` additionally
  covers the seed-source failure path (stubbed to throw → `presentError`).

## Recipe — running and driving the terminal app

Launch in a **real terminal** with `.claude/scripts/run-app.sh` (discovers a JDK 21+ via
`GAME_CLEAN_JAVA`, `JAVA_HOME`, an IntelliJ-managed JDK, or PATH, and runs the fat jar; it also
`unset`s any inherited `SPRING_CONFIG_ADDITIONAL_LOCATION`, whose value some shells path-mangle into a
non-existent dir and fail startup). Build the jar first if absent: `mvn -DskipTests package`.

The app reads from a TTY; to drive it without one (Claude sessions, CI smoke checks), **script and
*pace* stdin** into the launcher:

```
{ printf 'look\n'; sleep 3; printf 'bye\n'; } | .claude/scripts/run-app.sh 2>&1 | tail -n 20
```

- **Non-TTY ⇒ JLine dumb terminal.** ANSI styling is stripped and cursor choreography is absent, so
  this verifies output **content and ordering**, not visual rendering. The redraw-preserves-your-input
  nicety stays a human-at-a-real-terminal check.
- **Pace the input** (interleaved `sleep`) to let time-based / async `printAbove` events fire.
- **Never via Maven** — `mvn spring-boot:run` / `exec:java` make Maven own stdin/stdout ⇒ forced dumb mode.
- Use this for **adapter smoke checks** and the wall-clock / concurrency exploration of thread `#3`; test
  game *logic* via JUnit on the use-case layer. See the testability stance in `design-notes.md`.

> **Gotcha — running the app pollutes the IT database.** `run-app.sh` seeds `scn1`–`scn4` into the
> *same* Dockerized Postgres the `*IT`s use, and the seed **commits** (it is not test-rolled-back). A
> subsequent `mvn verify` then collides — e.g. `SceneRoundTripIT` hits a duplicate `scn1`,
> `ConstructWorldIT`'s "seeds then skips" no longer starts empty. **Reset the volume before verifying:**
> `docker compose down -v && docker compose up -d`.

> **Note — exit order.** Spring Data JDBC returns the `@MappedCollection` exits unordered (no `ORDER BY`).
> `TerminalScenePresenter` therefore **sorts exit names alphabetically at render time** — exit ordering is
> a *presentation* concern, kept out of the domain and the adapter. (Resolved the spike's
> non-determinism; `look scn1` now shows `Exits: east, north`.)

## Recipe — leak-scan `.claude/` before publishing

`.claude/` is versioned on a public repo; only `settings.local.json` is git-ignored
(it holds machine-local absolute paths, the username and the JDK location). Before
any commit/push that touches `.claude/`, confirm the publishable content is clean:

```
# The path/tooling fragments are literal and generic; replace each <placeholder>
# with your own machine/institution string. Do NOT write those real strings into
# this file — describe them as categories, or the scan would flag (and republish)
# its own recipe.
grep -rniE 'C:/Users|/c/Users|\.jdks|<local-username>|<institution>|<internal-host>|<internal-tooling>' \
  .claude --exclude=settings.local.json     # expect: no matches
git check-ignore .claude/settings.local.json  # must still print the path
```

- Clean scan **and** `settings.local.json` still ignored ⇒ safe to commit.
- The public repo handle (`<owner>/game-clean`) is fine — it is the repo's own
  identity, not a leak.

## Recipe — local Postgres + read-only MCP (schema inspection)

`docker-compose.yaml` runs a single Postgres service (`gameclean-db`, db `gamecleandb`,
`postgres`/`postgres`, port 5432). On *first* container creation it executes
`.claude/init-pg-readonly.sql`, minting a `claude_readonly` SELECT-only role.

```
docker compose up -d     # first run executes the init script (mints claude_readonly)
docker compose down -v   # drop the volume to re-run init on the next up
```

`.mcp.json` registers a project-scoped `postgres` MCP server using the read-only role. A global
PreToolUse hook restricts `mcp__postgres__query` to schema-only SELECTs (`information_schema` /
`pg_catalog` / `pg_*` / `flyway_schema_history`) and blocks all DDL/DML. Use it to inspect
Flyway migration results — never to read or mutate business data.

- The init script runs **only on first init** (empty data volume); recreate the volume to re-run.
- `claude_readonly` doesn't exist until that first init — bring the DB up before the MCP connects.

## Code conventions (as established)

- Domain entities/VOs: immutable, always-valid (validating constructors), **JDK-only**
  validation (`Objects.requireNonNull` + explicit `IllegalArgumentException`) — no
  commons-lang3. Equality by id for aggregate roots (`@EqualsAndHashCode(onlyExplicitlyIncluded
  = true)` + `@Include` on the id), by value for VOs. `@Builder` on the validating constructor,
  never on the class.
- Output ports throw an **unchecked** `*OperationsError` (`extends RuntimeException`), caught at the
  use case's single outermost checkpoint. Unchecked so persistence actions compose as plain
  `Runnable`/`Supplier` inside the transaction port and a thrown error triggers Spring's
  rollback-on-runtime (see Transactions below; rationale in design-notes §5).
- Input crosses the boundary as primitives / `*Entry` DTOs; Value Objects are constructed
  **inside** the use case, never by adapters.
- **Boundary currency (driven vs driving).** Driven (output) ports trade in **domain models** — the
  adapter hides its own `*DbEntity`/DTO and owns the mapping (persistence is the exemplar). Driving
  command adapters carry primitives / `*Entry` DTOs, leaving VO construction to the use case. **Third
  shape (the realized caveat):** an *untrusted-external source* pulled like persistence — the
  `GameSeedSourceOperationsOutputPort` over YAML — is a **driven port that returns an invalid-capable
  carrier** (`GameSeed`/`*Entry`), *not* a model, precisely because validation is deferred to the
  use-case gate; the ACL still owns the foreign→domain sourcing, but returns the carrier. The
  boundary-currency rule (invalid-capable carrier inward, valid model outward) is unchanged — the carrier
  just arrives as a return value. Rationale: design-notes §3 (boundary currency, *The parked caveat, realized*).
