# Spring Boot 4 — notes & gotchas (vs Boot 3.x)

> **Charter — a running, appendable reference of Spring Boot 4.0 behaviours that
> differ from 3.x.** This project runs on Boot 4.0.6; the author is new to Boot 4
> and wants every 4-vs-3 difference surfaced as it is encountered. Append new
> entries here as we hit them. Keep entries institution-neutral (public repo).
>
> Markers: **[hit]** = encountered and confirmed firsthand in this project;
> **[doc]** = from the official Boot 4.0 docs/source, not yet exercised here.

## The headline: `spring-boot-autoconfigure` was modularized

In 3.x a single `spring-boot-autoconfigure` jar held *every* autoconfiguration. In
4.0 each feature is its own module — `spring-boot-flyway`, `spring-boot-data-jdbc`,
`spring-boot-kafka`, `spring-boot-restclient`, … — and each module owns its
autoconfig classes. The entries below are all downstream consequences of this.

### 1. Autoconfig & `*Properties` classes moved packages **[doc]**

New pattern: `org.springframework.boot.<feature>.autoconfigure.*`.
- `…autoconfigure.kafka.KafkaProperties` → `…boot.kafka.autoconfigure.KafkaProperties`
- `…autoconfigure.flyway.FlywayAutoConfiguration` → `…boot.flyway.autoconfigure.FlywayAutoConfiguration`
- `RestTemplateBuilder` → `org.springframework.boot.restclient`

Any `@ImportAutoConfiguration(...)`, hand-written `AutoConfiguration.imports`, or
fully-qualified references to Boot internals will need updated imports.

### 2. A library on the classpath no longer autoconfigures itself **[hit]**

*The trap that cost us two build cycles during the persistence spike.* In 3.x,
`flyway-core` on the classpath was enough — `spring-boot-autoconfigure` carried
`FlywayAutoConfiguration` and it activated via `@ConditionalOnClass`. In 4.0 that
autoconfig lives in the separate **`spring-boot-flyway`** module. With only
`flyway-core`, **Flyway silently does nothing** — no migration, no error, just a
missing schema (`relation "scene" does not exist` at first write). Fix: add
`spring-boot-flyway` (it brings `flyway-core` transitively).

> Mental model for Boot 4: *"is the library on the classpath?"* and *"is Boot's
> integration module for it on the classpath?"* are now **two separate questions**.
> When something that should "just work" does nothing, look for the missing
> `spring-boot-<feature>` module first.

### 3. Test slices are modularized too **[hit]**

- `@DataJdbcTest`: `org.springframework.boot.test.autoconfigure.data.jdbc` →
  **`org.springframework.boot.data.jdbc.test.autoconfigure`**, shipped in its own
  **`spring-boot-data-jdbc-test`** artifact. **No longer transitive via
  `spring-boot-starter-test`** — add the slice module explicitly (test scope).
- `@AutoConfigureTestDatabase`: now `org.springframework.boot.jdbc.test.autoconfigure`
  (pulled transitively by `spring-boot-data-jdbc-test`).
- Boot 4 does add granular **test starters** for some slices (e.g.
  `spring-boot-starter-webmvc-test`), but not for every slice — for data-jdbc we
  pulled the module directly.
- Unchanged from 3.x: `@DataJdbcTest` is `@Transactional` and **rolls back by
  default**; for a real DB use `@AutoConfigureTestDatabase(replace = NONE)`. Flyway
  DDL still commits regardless of the test rollback.
- **The `@DataJdbcTest` slice includes `FlywayAutoConfiguration`** (and, with
  `spring-boot-testcontainers` present, `ServiceConnectionAutoConfiguration`) **[hit]**.
  Confirmed by reading the slice's `ImportsContextCustomizer` key list at runtime and by
  Flyway migrating a *pristine* Testcontainers Postgres from scratch
  (`flyway_schema_history … does not exist yet` then a clean migrate). This had been
  load-bearing-by-accident before issue #17: against the shared compose DB the schema
  already existed, so whether the slice ran Flyway never mattered. On an ephemeral
  container it does, and it works with no extra `@ImportAutoConfiguration`.

## Testcontainers 2.0 — Boot 4 pins it, and the artifact ids were all renamed **[hit]**

*Issue #17, the surprise that cost a build cycle.* In Boot 3.x the BOM imported
`testcontainers-bom` at a 1.x version, so `org.testcontainers:postgresql` and
`org.testcontainers:junit-jupiter` resolved unversioned. **Boot 4.0.6's
`spring-boot-dependencies` pins `testcontainers.version` to `2.0.5`** and imports the
2.0 BOM — and **Testcontainers 2.0 renamed every module artifactId with a
`testcontainers-` prefix**, keeping the `org.testcontainers` groupId:

- `org.testcontainers:postgresql` → **`org.testcontainers:testcontainers-postgresql`**
- `org.testcontainers:junit-jupiter` → **`org.testcontainers:testcontainers-junit-jupiter`**
- (same for `mysql`, `mongodb`, … — see the `testcontainers-bom` 2.0.5 module list)

With the *old* ids, the BOM no longer manages them, so Maven fails the POM parse with
`'dependencies.dependency.version' … is missing` (not a "not found" — the coordinates
simply aren't in the 2.0 BOM). Fix: use the new `testcontainers-` artifactIds; the
version stays BOM-managed, no pin needed. **Package names are unchanged** —
`org.testcontainers.containers.PostgreSQLContainer` and
`org.springframework.boot.testcontainers.service.connection.@ServiceConnection` still
import as before, so only the `pom.xml` coordinates move.

## Baseline jumps **[doc]**

- **Java 17 minimum**, compatible through **Java 26**.
- **Spring Framework 7** underneath.
- **Jakarta EE 11** — Servlet 6.1, **Tomcat 11.0.x** (3.x was EE 9/10, Tomcat 10).

## Null-safety switched to JSpecify **[doc]**

Boot 4 / Framework 7 annotate APIs with **`org.jspecify.annotations.@Nullable`**
instead of Spring's own `@Nullable`. Mostly transparent, but strict null analysis
(IntelliJ, NullAway) sees a different annotation source — relevant if the IDE
starts flagging nullability differently.

## Test mocking: `@MockBean`/`@SpyBean` removed → `@MockitoBean`/`@MockitoSpyBean` **[hit]**

Boot 4 **removes** `org.springframework.boot.test.mock.mockito.@MockBean` / `@SpyBean` (deprecated
since 3.4). The replacement is owned by **Spring Framework 7**, not Boot:
`org.springframework.test.context.bean.override.mockito.@MockitoBean` /
`@MockitoSpyBean`. Same role — replace a context bean with a Mockito mock/spy — different package and
a different owning project. Hit when writing `ConstructWorldIT`: a `@MockBean` import simply doesn't
resolve. Field-level usage is otherwise unchanged.

## Config-properties / condition annotations unchanged **[hit]**

`@ConfigurationProperties` + `@EnableConfigurationProperties` (`org.springframework.boot.context.properties`),
`@DefaultValue` (`…context.properties.bind`), and `@ConditionalOnProperty`
(`org.springframework.boot.autoconfigure.condition`) are all in their 3.x packages — they live in core
`spring-boot` / the slimmed `spring-boot-autoconfigure`, not a relocated feature module. `ApplicationRunner`
and `ObjectProvider` likewise unchanged. (Confirmed building `WorldSeedRunner`/`WorldSeedProperties`.)

## Ordering `ApplicationRunner` beans is unchanged — still `@Order`/`Ordered` **[doc]**

Boot 4.1 keeps the 3.x idiom verbatim: when several `ApplicationRunner`/`CommandLineRunner` beans must
run in a defined order, implement `org.springframework.core.Ordered` or annotate with
`org.springframework.core.annotation.@Order` (lower value first); `SpringApplication.callRunners()` sorts
by `AnnotationAwareOrderComparator`. **There is no new Boot 4 mechanism** for "declare a runnable bean and
run it in order" — no replacement abstraction was introduced. `@Order` works on `@Bean`-produced runner
methods too (the bean it produces is what gets ordered). Used in `BootSequence` to declare the two boot
runners (`@Order(1)` seed, `@Order(2)` console) in one config class. (Confirmed against the Boot 4.1
*Spring Application* reference; runner ordering exercised by `BootSequenceTest`.)

## Scheduling & lifecycle for an async driving adapter — not a 4-vs-3 delta (kept in design-notes)

The dawn/dusk `GameClockTicker` is a scheduler-driven background task (a `SchedulingConfigurer`) whose shutdown
must precede `Terminal.close()`. The relevant facts — Spring's scheduled tasks are lifecycle-managed (≥6.1, so
cancelled at context close, waiting for an in-flight run, **before** plain singletons are destroyed); scheduling
starts during context refresh, *before* `ApplicationRunner`s; `@Scheduled`'s `fixedDelayString` takes ISO-8601
(`PT5S`) or a millis number, **not** the simplified `5s` style (that style is for `@ConfigurationProperties`
binding); and a `@DefaultValue` is a binding-time default, not an `Environment` entry a `${}` placeholder can
resolve — are **Spring 6.1 / Framework 7 behaviours, not Boot-4-vs-3 differences**, so they live with the
architecture rationale in `design-notes.md` (§6/§7), not here. (Two earlier drafts — a hand-rolled
`SmartLifecycle` thread, then an `@Scheduled` placeholder that failed to boot — were both walked back to a
`SchedulingConfigurer` reading the bound, typed `Duration`.)

## `Duration` config binding + nested `@DefaultValue` group — works as in 3.x **[hit]**

`game.time.ticker.interval` binds to a `java.time.Duration` from a string like `5s` (Spring Boot's
`DurationStyle`), and a nested constructor-bound group with a bare `@DefaultValue` (`@DefaultValue Ticker ticker`)
instantiates the group with its own field defaults when the whole `game.time.ticker.*` subtree is absent.
Confirmed: `InitializeGameIT`'s full-context startup binds `GameConfigurationProperties` with the default
`Ticker(5s)` and comes up clean. (Same constructor-binding + `@DefaultValue` machinery as the existing
`World`/`Terminal`/`Player`/`Time` groups — no Boot 4 difference.)

## Unchanged / carried over from 3.x (so you don't misattribute)

- `@AutoConfiguration` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  as the autoconfig registration mechanism — this was the **3.0** change (replacing
  `spring.factories`); 4.0 keeps it, just one `.imports` per module now.
- **The Spring Framework exception hierarchies did not move.** `org.springframework.dao.DataAccessException`
  (spring-jdbc/spring-tx) and `org.springframework.transaction.TransactionException` /
  `CannotCreateTransactionException` are in their 3.x packages on Boot 4.0.6. The Boot-4 modularization split
  *Boot's own* autoconfigure & test-slice jars, not Spring Framework's core types — so driven-adapter
  exception wrapping (`catch (DataAccessException …)`, `catch (TransactionException)`) compiles against the
  same imports as 3.x. **[hit]** (confirmed by `mvn verify` while wrapping the persistence/transaction
  adapters into port error types, issue #29).
