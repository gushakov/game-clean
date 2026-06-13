# game-clean — design notes

> **Charter — a Clean DDD doctrine reference, taught through game-clean.**
> This file is the project's *textbook*: the architectural and doctrinal theory of
> Clean DDD, worked out against a real codebase. The accent is on **trade-offs,
> decision rationale, and pragmatic deviations from doctrine** — the *why* and the
> *why-not*, never the *what* and never the *when*.
>
> - **Organize by doctrinal theme, not by date.** There is no decision log here, and
>   no dated entries. When a session produces new reasoning, fold it into the
>   relevant theme below (or open a new one) — do **not** append a `YYYY-MM-DD —`
>   bullet. Git owns history; this file owns the distilled doctrine.
> - **Reasoning, not record.** Cut transcripts, build-cycle war stories, test counts,
>   and "this session we…" framing. For each point state: the principle, the tension
>   it creates, how game-clean resolves it, and any deviation with its justification.
> - **Facts live elsewhere.** What something is, where it lives, which class owns it →
>   `project-context.md` / `project-context-extended.md`. If you catch yourself
>   writing a fact table or a file inventory here, move it and cross-link instead.
> - **Tie back to the open threads.** The four research questions below are the
>   project's reason for existing; mark where a doctrine point feeds one with
>   `[thread #N]`.
> - Boot-4-vs-3 gotchas → `spring-boot-4-notes.md`. Recipes / conventions →
>   `project-context-extended.md`.

## Why this project exists

A rich, playful domain (RPG) chosen specifically to stress-test Clean DDD's
interaction-first design beyond the usual business-app examples. A text game's
turn-by-turn loop of a Player and NPCs acting on shared state maps naturally onto
use cases as first-class interaction artifacts. The project is a teaching/showcase
vehicle for the community, not a product — which is *why* this file is written as
doctrine rather than as a worklog.

## Open threads (the research questions driving the project)

Open questions, not decisions — revisited as the model grows. The doctrine sections
below cite these where they produce new evidence.

1. **Emergence of value objects and aggregates.** How do VOs and aggregate
   boundaries actually *surface* from the interactions, rather than being designed up
   front?
2. **Granularity of output ports.** How does output-port granularity evolve as the
   interactions get richer — when does one port split into several?
3. **Transactional demarcation under concurrency.** How does transaction-boundary
   design take form when Player and NPCs act in parallel on shared state? The thread
   the author is most curious about.
4. **Subcases for shared logic.** When and how must subcases (helpers vs terminal
   subcases) be introduced to factor logic shared across use cases?

---

# Doctrine, as applied in game-clean

Each section states a Clean DDD principle and then shows the trade-off game-clean
hit and how it was resolved — including where we knowingly departed from the
"textbook" answer and why.

## 1. Layering and the dependency rule

**Principle.** Two rings: `core/` (model, ports, use cases) is framework-free;
`infrastructure/` (adapters, Spring wiring, composition root) depends inward on the
core and never the reverse. The Cockburn vocabulary maps onto code: a *summary goal*
is a sub-package under `core/usecase/`, a *user goal* is a use-case **class** (not a
sub-package), an *interaction* is a method. Three distinct homes — aggregates/VOs in
`core/model/{aggregate}/` (shared by all use cases, never nested under the usecase
tree), output ports in `core/port/{operation}/`, and the presenter port co-located
with its use case.

**Make the boundary structural, not a matter of discipline.** The dependency rule is
enforced by ArchUnit, so a violation fails the build rather than relying on
reviewer vigilance: `core ↛ infrastructure` (the headline boundary) and
`core.model ↛ core.port` (the model depends on *no* other layer, not even ports — see
§2).

**The framework must never scan the core.** `@SpringBootApplication` roots component
scanning at its *own* package, so the entry point's location is load-bearing: placed
at the project root it would scan `core` too. It therefore lives in
`infrastructure`, confining the scan and all autoconfiguration to the outer ring.
Because "where the file sits" is too fragile a guarantee for something this
important, two further ArchUnit rules make it an invariant: the
`@SpringBootApplication` class must reside in `infrastructure`, and `core` must carry
no Spring stereotype annotations (`@Component` & everything meta-annotated with it).
The entry point's javadoc also points at the boot orchestrator (§6) — the first place
a developer looks should not hide where startup actually happens.

**Build inside-out.** Domain → ports → use case (unit-tested against mocked ports) →
persistence adapter → driving adapter → composition root. The core is provable before
any database exists, which is the whole point of the dependency rule.

## 2. The always-valid model, and where validation lives

**Principle.** Domain entities and VOs are immutable and *always-valid*: a constructor
either yields a fully-legal object or throws. Validation is JDK-only (`requireNonNull`
+ explicit `IllegalArgumentException`); equality is by id for aggregate roots, by value
for VOs. (Exact Lombok shape → `project-context-extended.md`.)

**The "single source of truth" conundrum — and its resolution.** An always-valid id VO
appears to need to validate the very character pattern that the *infrastructure* id
generator emits — yet the dependency rule forbids the model from knowing the generator,
and two drifting copies of the pattern are unacceptable. The resolution is to **untangle
the two concerns the pattern conflates.** The *prefix* (e.g. `scn`) is a domain concern
— it says which kind of identity this is — so the model owns it. The *body alphabet and
length* are an encoding artifact of the generation scheme, **not** a domain invariant, so
the generator owns them privately. The model validates **prefix + structure only**
(non-empty, single token, no whitespace) and never the charset, because "always-valid"
constrains *domain* state and the charset is not a domain rule. The conundrum dissolves:
there is exactly one knower of the alphabet, so nothing can drift. Reinforced by authored
seed ids being *logical short keys* (`scn1`), deliberately distinct in body from generated
ids — the model could not own a single body-format even if it wanted to. Parked
alternatives: (a) a shared `IdFormat` type in `core` consumed inward by the generator —
coherent only if authored ids were full generated-shape ids, which they are not;
(b) validating authored-id format in the YAML driving adapter against the generator's
pattern — held in reserve for rejecting malformed authored ids at parse time *without the
model knowing the alphabet*; (c) a duplicate-plus-drift-guard test as last resort. This is
the **correct boundary**, not a shortcut — and `core.model ↛ core.port` makes it
structural.

**Emergence over speculation.** `[thread #1]` Aggregates and VOs are taken only as far
as the *current* interactions demand. `Scene`/`Exit`/`SceneId` were modelled to the
depth `look`/`move` require and no further; a speculative exit-visibility (`show`) field
and a speculative probability VO were both resisted. The model grows when an interaction
forces it to, not when we imagine it might.

## 3. Boundary currency: invalid-capable carrier in, valid model out

This is the sharpest boundary lesson the project has produced so far, and it touches
`[thread #2]` (it fixes a port's *type vocabulary*, independent of how many ports exist).

**The apparent asymmetry.** Two adapters look parallel but behave differently: the
persistence port serves and accepts domain `Scene` models (hiding its `*DbEntity` and
mapping inside the adapter), while the YAML seed reader returns `*Entry` DTOs of
primitives and defers model construction to the use case. Why isn't one of them "wrong"?

**It dissolves once each adapter sits on its correct side of the hexagon.** Persistence is
a **driven** (output) port. The YAML seed is a **driving** (input) adapter feeding the use
case's input port — a peer of the future terminal command adapter, *not* of the persistence
port. Compared like-for-like (driven↔driven, driving↔driving) the symmetry is intact.

**And the asymmetry is *required* by the always-valid invariant, not a stylistic wart.**
Input data *may be invalid* — human-authored YAML can carry a blank name, a dangling exit
target, a bad prefix — so it needs a carrier that *can hold invalid state* long enough to
reach the validation gate and be rejected there as a domain error. An always-valid `Scene`
cannot be that carrier: its constructor throws *before* the gate is reached. So a
possibly-invalid `*Entry` DTO is **forced** on the input side. Output data, by contrast, is
*valid by provenance* (the domain wrote it), so the model is the natural type; re-wrapping
through the VO constructors on read is a defensive corruption check, not a first-time gate.
The rule, in one line: **invalid-capable carrier inward, valid model outward.**

**Consequences.**
- Persistence is the **exemplar** of a driven port — reconstituting aggregates *is* the
  DDD repository's job — not a special case. "A driven adapter returns a valid model graph
  and hides its own DTO" is the default to strive for.
- The driving side carries primitives and constructs VOs *in the use case*, never in the
  adapter.
- All validation lands at **one gate**: intra-aggregate (VO / `Scene` constructors) and
  inter-aggregate (the two-pass exit-target resolution, §4) both inside the use case,
  rather than split across infra and core.

**Testability is the clincher.** With `*Entry` on the input port you can unit-test the use
case against a blank-named scene or a dangling target and assert the domain error, because
the DTO can *represent* that bad state. A `List<Scene>`-returning seed port couldn't even
construct the bad fixture — the gate would be untestable at the use-case level.

**Parked caveat.** The one place "driven ports return models" gets real tension is a driven
port sourcing *untrusted external* data (a future `WeatherOutputPort` over a third-party API
— external like YAML, yet pulled like persistence). DDD's answer is an **Anti-Corruption
Layer** inside that adapter, owning the foreign→domain translation and still returning
models. The seed sidesteps this by being *pushed* (driving), where primitives-inward is
already settled.

## 4. Use cases as first-class interactions

**Interaction shape (worked on `ConstructWorld`).** The actor is the *system at startup*.
The use case is **two-pass**: construct all scene aggregates, then validate that every exit
target resolves to a known scene. That second check is an *inter-aggregate* consistency rule,
so it lives in the **use case**, not on the `Scene` entity, and yields a meaningful domain
*outcome* rather than a foreign-key failure (the schema deliberately carries no FK on
`exit.target_scene_id` for exactly this reason).

**Seed through the domain.** Construction *is* the validity gate, so authored data flows in
through the domain model; Flyway owns schema/DDL only, never data. The seed-if-empty
idempotency guard lives *inside* the use case, so the guarantee holds regardless of which
adapter fires it.

**Express outcomes by presenting, not always by throwing.** A dangling exit target is
handled by **branch-and-present** — a checkpoint collects the unresolved targets and calls a
dedicated presenter method — rather than minting a thrown domain-error type. It is lighter,
and the inter-aggregate rule still yields a domain *outcome*. A genuine infrastructure
failure rides the catch-all `presentError`; carve out a specific presenter method only when a
real need appears.

**A presenter port is mandated even with no human audience.** `[thread #2]` The system-actor
seeding has no console to talk to, yet the methodology still requires a presenter port — so
its implementation *logs* (to file; see §7). The eventual player "welcome" is a *separate*
interaction with its *own* presentation port, which keeps output-port granularity honest
rather than overloading one port with two audiences.

## 5. Explicit transaction demarcation

**Principle.** Transactions are demarcated *explicitly* through a
`TransactionOperationsOutputPort`, never a blanket `@Transactional` over the interaction.
Validation and reads run **outside** the transaction; only persistence (and, later, event
dispatch) runs **inside** `doInTransaction`; presentation runs in `doAfterCommit`, so the
actor is never told "success" before the commit actually happens. The port is a lean
four-method canon over plain `Runnable`/`Supplier`; `doAfterCommit` runs immediately when no
transaction is active, while `doAfterRollback` is a no-op outside one (nothing rolled back to
react to) — an asymmetry that follows from the semantics, not an oversight.

**Departure from the reference (cargo-clean is the *legacy* shape).** Three deliberate
cuts: (1) no `*WithResult` after-commit/rollback variants — their `AtomicReference` is read
before the callback fills it, and presenter calls are `void` anyway; (2) no `rollback()`
method — failure is expressed by *throwing*, caught at the use case's single outermost
checkpoint, and an imperative `rollback()` would bypass that path; (3) no `CacheManager`
coupling — game-clean has no cache, so the `CacheInvalidationOnRollback` seam is introduced
only if/when one appears (YAGNI).

**A domain contract bent to fit the transaction mechanism — knowingly.** Spring's
`TransactionTemplate` callback cannot throw checked exceptions and rolls back only on
*unchecked* ones, so a checked persistence failure would let a half-built world **commit**.
The persistence error was therefore switched from checked to `extends RuntimeException`.
This reverses an earlier "checked on purpose" convention; the alternative (keep it checked,
bridge via `setRollbackOnly()` + wrap/unwrap in the adapter) was rejected as ceremony that
lets transaction convenience quietly reverse a domain contract. Unchecked also lets
persistence actions compose as plain `Runnable`/`Supplier` inside the port.

**The one read that belongs *inside* the transaction.** `[thread #3]` The seed-if-empty
`worldIsEmpty()` guard sits inside the same transaction as the writes, not outside with the
other reads — because it is a **read-then-write guard**, not a load. Keeping the emptiness
decision and its effect atomic is what stops two concurrent constructions from both deciding
"empty" and double-seeding. A first, small data point for how demarcation will have to
reason about concurrency.

**A consumer-less port built ahead of its use case — justified.** The transaction port was
built before its first consumer, which inverts the usual inside-out order. Acceptable here
because it is *stable cross-project infrastructure*, not an emergent domain artifact; the
emergence discipline (§2) applies to the model, not to plumbing the methodology already
considers settled.

## 6. The composition root — the framework held at arm's length

**Wiring is explicit and hidden from the core.** Use cases are declared as
`@Bean @Scope(PROTOTYPE)`, return-typed to the **input-port interface** so the implementation
is invisible to the container, and assembled with explicit `new` — no Spring stereotype ever
lands on a core class. Where Spring can't infer wiring (the two `TransactionTemplate`s,
read-write `@Primary` + `@Qualifier("read-only")`), it is declared by hand in one
`@Configuration` rather than scattered across component-scanned classes.

**Prototype use cases are pulled, not held.** A use case is a per-interaction subroutine
holding no conversational state, so a longer-lived driving adapter must fetch a *fresh*
instance each interaction; injecting a prototype into a singleton resolves it once and
silently defeats the scope. The mechanism is `ApplicationContext.getBean(XxxInputPort.class)`
at the start of each interaction — the cargo-clean idiom, chosen over `ObjectProvider`. The
reasoning: *every* prototype-pull couples to the container API (`ObjectProvider` is merely a
narrower handle, `@Lookup` is CGLIB magic), the coupling is unavoidable and confined to the
infrastructure ring (the core never sees Spring, so the dependency rule holds), and matching
the established reference idiom wins over `ObjectProvider`'s marginal testability edge. The
seam is covered by a full-boot IT rather than a stubbed-provider unit test — fine for a thin
adapter.

**Boot-time ordering of driving adapters is a composition-root concern.** This is the
sharpest lifecycle lesson so far. Two driving adapters must come up in a defined order —
*construct the world, then open it to the player* — and that order is a real application
invariant.

The tempting move is to push the sequence "up" into the application layer (a startup use
case over ports). **That is doctrinally wrong here, and the reason is sharp:** the second
step — the interactive loop — is a driving adapter taking the main thread. Driving adapters
call *inward*. If the application core sequenced them, the core would be reaching *out* to
start its own driving adapters — reversing the hexagon. The core must never know its driving
adapters exist, let alone order them.

So the conclusion is the interesting part: **boot-time ordering of driving adapters is a
composition-root / infrastructure responsibility.** It is a lifecycle concern that sits
*beside* the composition root, not inside `core`. Expressing it as two `@Order`-annotated
`ApplicationRunner`s dissolved that real responsibility into framework SPI — the sequence
became an emergent property of Spring discovering all runners and sorting them by
magic-constant precedence, reconstructable only by reading two files and knowing the SPI. We
pulled it back into one named, readable place: a single `ApplicationRunner` whose body *is*
the sequence (`worldSeeder.seed()` then `consoleSession.start()`). The two steps are injected
directly as the singletons they are — no `ObjectProvider`. The order is now a unit-testable
statement, not a sort.

A consequence worth noting: once the orchestrator owns the order, the per-adapter enable
flags can be simplified. The standalone "construct world on startup" flag became redundant —
seeding is triggered only by the orchestrator (gated, with the console, by one
`game.terminal.enabled` switch) and the seed operation is idempotent, so an extra knob earned
nothing. **Forward fit:** when Phase 3 adds an independent clock and an outbox relay, their
ordered start — and the *reverse-order* shutdown the design requires (`Terminal.close()` must
run last) — belong here too, as further explicit lines rather than scattered `SmartLifecycle`
phase numbers (which would only be `@Order` magic by another name).

## 7. The presentation layer (JLine), and one terminal for two adapters

**Why JLine, and why not the alternatives.** JLine is *right-sized* for a boundaries
showcase. **Spring Shell** was rejected because its annotation command framework would
compete with the use-case layer for ownership of the interaction — exactly the wrong default
when the *point* is to show use cases owning interactions. **Lanterna** (a full TUI/widget
tree) is over-fancy; **plain JDK** is too thin (no line editing, history, or async-safe
output). JLine solves precisely the *presentation-layer* version of the project's core
problem — concurrent writers on shared state: one line editor plus N async `printAbove`
writers to a single terminal buffer, arbitrated by an in-process lock and cursor math.
`[thread #3]` That mirrors the domain problem one layer out, where transactions + an outbox
will solve the same *class* of problem.

**Single-player, single interactive session.** One human console input; NPCs, clock, and
combat supply all asynchronous interaction. The consequence is a genuine simplification:
output ports carry **no recipient identity** (there is always exactly one console), so a
singleton `Terminal`/`LineReader` is the *endorsed* model, not merely tolerated. The
multi-audience "to whom is this output addressed?" question is explicitly out of scope.

**Two adapters, one terminal — a resource is shared, not an adapter.** The interactive loop
is a *driving* adapter; the scene presenter is a *driven* adapter; by doctrine they cannot be
the same bean. Yet there is only one physical console. The resolution: the JLine `Terminal`
and `LineReader` are singleton **infrastructure resources**, not adapters, and both adapters
*inject* them. Nothing in the hexagon is violated, because what is shared is a *resource*, not
an *adapter* — the two adapters remain distinct beans on opposite sides of the boundary. The
single-session topology (one `readLine` thread, N `printAbove` writers) falls straight out of
this.

**Logging must yield the console to JLine.** Because JLine owns the terminal, application
logging is routed to a **file** (a console appender would scribble over the prompt and clash
with `printAbove`), and the startup banner is off. The system-actor seeder's logging presenter
(§4) therefore lands in the log file, where it belongs. Corollary: run the app as a fat jar
via `java -jar` — **never** `mvn spring-boot:run` / `exec:java`, which let Maven own
stdin/stdout and force JLine into dumb-terminal mode.

**The driver evolves; the beans don't.** `[thread #4]` The terminal beans are guarded so
unit-test slices never grab a system terminal. The *driver* over them changes across phases —
a blocking runner now, `SmartLifecycle`-managed threads (input loop, clock, outbox relay)
later — while the bean topology stays put. A thin `Console` facade over JLine is deliberately
deferred until a *second* adapter needs the same choreography (styling, dumb-terminal
handling, width-aware formatting): let it emerge from a second use site, the same emergence
discipline the subcases thread will demand.

## 8. Trajectory: from synchronous to loop-driven concurrency

**Start synchronous; let concurrency arrive only when the domain demands it.** `[thread #3]`
The end state is loop-driven: an independent game clock, with player input and
NPC/timer/combat actions as events on shared state, rendered asynchronously to the terminal.
We deliberately do not build that up front. The rough sequencing (direction, not commitment):
(1) the world-seed use case, whose real early value is a *persistence round-trip harness*;
(2) synchronous single-player movement; (3) async event processing via a poor-man's
**outbox**.

**Why the outbox, specifically.** Phase 3 is the deliberate *bridge into concurrency*, not
just more features. The outbox is chosen over a bare
`@TransactionalEventListener(AFTER_COMMIT)` to buy **crash-durable** hand-off between two
transactions: the event is appended within the command's transaction, and a separate use case
— driven by the outbox relay, itself a driving adapter symmetric to the terminal — runs the
reaction. The rule that makes it exactly-once in effect: the relay marks the row processed in
the *same* transaction as the reaction.

---

## Open hazards / stances (genuinely unresolved)

- **Testability stance.** Game logic lives in unit-testable **use cases**, not in the
  terminal adapter. Headless app-driving (recipe in `project-context-extended.md`) is
  reserved for adapter smoke checks and the wall-clock / concurrency exploration of
  `[thread #3]`, where unit tests are weak — and it verifies output *content and ordering*,
  not visual rendering.
- **Shutdown ordering (Phase 3).** Async threads (clock, outbox relay) must be stopped
  *before* the `Terminal` bean is closed, or `printAbove` throws on a closed terminal. The
  boot orchestrator (§6) is the intended home for this reverse-order teardown.

## Non-doctrinal project decision

- **`.claude/` is versioned.** The memory files capture the Clean DDD reasoning, which is
  itself part of what the project shares with the community, and the author wants them backed
  up for reinstalls. Only `settings.local.json` stays git-ignored (machine-local paths,
  username, JDK location). Committed `.claude/` content is kept institution-neutral; the
  methodology `@`-refs in the project `CLAUDE.md` are author-local provenance that will not
  resolve in a fresh clone. (Not Clean DDD doctrine — recorded here only because it explains
  why these notes are public.)
