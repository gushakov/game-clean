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

## Baseline jumps **[doc]**

- **Java 17 minimum**, compatible through **Java 26**.
- **Spring Framework 7** underneath.
- **Jakarta EE 11** — Servlet 6.1, **Tomcat 11.0.x** (3.x was EE 9/10, Tomcat 10).

## Null-safety switched to JSpecify **[doc]**

Boot 4 / Framework 7 annotate APIs with **`org.jspecify.annotations.@Nullable`**
instead of Spring's own `@Nullable`. Mostly transparent, but strict null analysis
(IntelliJ, NullAway) sees a different annotation source — relevant if the IDE
starts flagging nullability differently.

## Unchanged / carried over from 3.x (so you don't misattribute)

- `@AutoConfiguration` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  as the autoconfig registration mechanism — this was the **3.0** change (replacing
  `spring.factories`); 4.0 keeps it, just one `.imports` per module now.
