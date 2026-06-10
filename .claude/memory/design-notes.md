# game-clean — design notes

> **Charter — rationale, history, open threads. The *why*, not the *what*.**
> This file owns the narrative: design decisions and their motivations,
> methodology assessment, open architectural threads, Q&A captured from
> sessions, comparisons to reference projects, and pointers to ADRs (if the
> project maintains `doc/decisions/`).
>
> - Update when a decision is made and its *why* is worth remembering — or
>   when an open thread is opened, closed, or revised.
> - Current facts (what something is, where it lives, which class owns it)
>   belong in `project-context.md` or `project-context-extended.md`. If you
>   catch yourself writing a fact table here, move it.
> - Pointers to reference files (§-ref style) are welcome so this file can
>   stay focused on reasoning while still being navigable.

## Why this project exists

A rich, playful domain (RPG) chosen specifically to stress-test Clean DDD's
interaction-first design beyond the usual business-app examples. A text game's
turn-by-turn loop of a Player and NPCs acting on shared state maps naturally onto
use cases as first-class interaction artifacts. The project is a teaching/showcase
vehicle for the community, not a product.

## Open threads to explore (the questions driving the project)

These are the methodology aspects the user explicitly wants to investigate. They
are open questions, not decisions — revisit and refine as the model grows.

1. **Emergence of value objects and aggregates.** How do VOs and aggregate
   boundaries actually surface out of the interactions, rather than being designed
   up front?
2. **Granularity of output ports.** How does output-port granularity evolve as the
   interactions get richer — when does one port split into several?
3. **Transactional demarcation under concurrency.** How does transaction boundary
   design take form in use cases when Player and NPCs act in parallel on shared
   state? This is the thread the user is most curious about.
4. **Subcases for shared logic.** When and how must subcases (helpers vs terminal
   subcases) be introduced to factor logic shared across use cases?

## Decisions so far

- **2026-06-10 — `.claude/` is versioned (reverses the 2026-06-09 ignore decision).**
  The memory files capture the Clean DDD design *reasoning*, which is itself part of
  what the project shares with the community — and the author wants `.claude/` backed
  up for reinstalls. Only `settings.local.json` stays git-ignored (machine-local
  paths, username, JDK location). Committed `.claude/` content is kept
  institution-neutral and free of machine setup; methodology `@`-refs remain as
  author-local provenance that will not resolve in a fresh clone.
- **2026-06-10 — UX = JLine (terminal), decided.** Chose JLine (currently 4.1.3,
  aggregate `org.jline:jline` jar) over: plain JDK (too thin — no line editing,
  history, or async-safe output); **Spring Shell** (its annotation command
  framework would compete with the use-case layer for ownership of the interaction
  — wrong default for a *boundaries* showcase); **Lanterna** (full TUI / widget
  tree — over-fancy). JLine is **right-sized**: it solves the *presentation-layer*
  version of our concurrent-writers-on-shared-state problem — one line editor +
  N async `printAbove` writers to one terminal buffer, arbitrated by an in-process
  lock + cursor math — and nothing more. That mirrors thread `#3` one layer out:
  the domain will solve the same problem *class* with transactions + outbox.
- **2026-06-10 — Loop-driven is the target; start terminal-driven.** End state: an
  independent game clock; player input and NPC/timer/combat actions are events on
  shared state; the terminal renders asynchronously via `printAbove` (validated in
  a throwaway spike this session). We deliberately start synchronous and let
  concurrency arrive only when the domain demands it. Rough sequencing (direction,
  not commitment): (1) world-creation/seed use case — its real early value is a
  *persistence round-trip harness*; seed **through the domain**, not via Flyway
  data (Flyway owns schema/DDL only); (2) synchronous single-player movement;
  (3) async event processing via a poor-man's **outbox** — event appended within
  the command's transaction, reaction runs as a *separate* use case driven by the
  outbox relay (itself a driving adapter, symmetric to the terminal). Phase 3 is
  the deliberate *bridge into concurrency*, not just more features. The outbox is
  chosen over a bare `@TransactionalEventListener(AFTER_COMMIT)` specifically to
  buy **crash-durable** hand-off between the two transactions; rule: relay marks
  the row processed in the *same* TX as the reaction (exactly-once effect).
- **2026-06-10 — Single-player, single interactive session.** One human console
  input; NPCs/clock/combat supply all asynchronous interaction. Consequence:
  output ports carry **no recipient identity** (one console, always); singleton
  `Terminal`/`LineReader` is the *endorsed* model, not merely tolerated. The
  "to whom is output addressed?" multi-audience question is explicitly out of
  scope for the showcase.

## UX wiring sketch (not yet implemented)

- `Terminal` and `LineReader` are **singleton infrastructure beans** in a *guarded*
  infra `@Configuration` (profile/property-conditional, so unit-test slices never
  try to grab a system terminal). Adapters — input controllers, output presenters —
  inject them; the application layer sees only input/output **ports**, so JLine
  never crosses inward. Topology constraint baked in by single-session: exactly
  one `readLine` thread + N `printAbove` callers (JLine supports precisely this;
  never two concurrent readers).
- **The driver evolves, the beans don't.** Phase 2 = an `ApplicationRunner`
  blocking on the read loop on the main thread; Phase 3+ = `SmartLifecycle`-managed
  threads (input loop, clock, outbox relay) with ordered start/stop. Shutdown
  hazard: `Terminal.close()` must run **last** — stop the async threads before the
  Terminal bean is destroyed, or `printAbove` throws on a closed terminal.
- A thin `Console` facade over JLine is **deferred**: ports are the real boundary;
  introduce a facade only when a *second* adapter needs the same JLine choreography
  (styling helpers, dumb-terminal handling, width-aware formatting) — let it emerge
  from a second use site, same discipline as thread `#4` (subcases).

## Open hazards / stances

- **Logging vs terminal.** Once the real app is a Spring Boot fat jar, console
  logging will scribble over the JLine screen and clash with `printAbove`. Route
  logs to a file (or off-console) so JLine owns the terminal. Corollary: run with
  `java -jar` (fat jar) — **never** `mvn spring-boot:run` / `exec:java`, which let
  Maven own stdin/stdout and force JLine into dumb-terminal mode.
- **Testability stance.** Keep game logic in unit-testable **use cases**, not in
  the terminal adapter. Reserve headless app-driving (see the recipe in
  `project-context-extended.md`) for adapter smoke checks and the wall-clock /
  concurrency exploration of `#3`, where unit tests are weak — it verifies output
  *content and ordering*, not visual rendering.
