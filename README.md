# game-clean

[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Build](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![UI](https://img.shields.io/badge/UI-JLine%20terminal-303030)](https://github.com/jline/jline3)

A small, text-based single-player RPG you play in your terminal — and, more to the
point, a **working laboratory for the Clean DDD methodology** in a domain that is
rich enough to be interesting.

You walk through a connected graph of scenes (`look`, `move north`), items spawn
into the world at startup by chance, and the whole thing runs as one console
process. It's deliberately playful. The game is the excuse; the architecture is the
subject.

---

## Why this exists

Toy examples make any methodology look clean. The hard, honest questions —
*where does a shared opening between two use cases live? when is something a subcase
versus a duplicated branch? how do ports keep the domain framework-free without
turning into ceremony?* — only surface once a domain has enough moving parts to
push back.

An RPG universe is a good source of that pressure: a player and (eventually) NPCs
acting over shared, persistent state, with reads, writes, transactions,
randomness, and authored content all in play. As the game grows, it keeps surfacing
**interesting points and genuine gaps in the doctrine** — exactly what this project
wants to find and document, not paper over.

So this repo is two things at once:

- a real, runnable game built strictly **hexagonal / Clean DDD** — a
  framework-free `core` (domain model + ports + use cases) wrapped by an
  `infrastructure` layer of adapters and Spring wiring, with the boundaries
  enforced by ArchUnit;
- a **narrated design record** of how each vertical slice was reasoned about.

## The `.claude/` design journal is part of the project

This repository ships its `.claude/` memory directory **on purpose**. It isn't
incidental tooling clutter — it's the long-form reasoning behind the code:

- `.claude/memory/project-context.md` — the current map: stack, package layout,
  and a slice-by-slice status of what's been built.
- `.claude/memory/design-notes.md` — the *why*: the open threads, the trade-offs
  weighed, the doctrine questions each feature raised.
- `.claude/memory/spring-boot-4-notes.md` — a running log of Spring Boot 4-vs-3.x
  differences hit along the way.

If you came for the methodology rather than the gameplay, those files are the main
attraction. (The `@~/.claude/methodology/*` references inside them point at the
author's local methodology library and won't resolve in a fresh clone — they're
kept as provenance, not a runnable dependency.)

## How it was built

Effectively all of the code here is **vibe-coded in collaboration with Claude**
(Anthropic's Claude Opus 4.8, driven from Claude Desktop) — but under **close,
deliberate human supervision**. Every slice was designed in conversation first and reviewed
before it landed; nothing was merged on autopilot. That working style is itself
part of what the project demonstrates: a methodology-disciplined human-AI pairing,
where the doctrine in `.claude/` is what keeps the AI's output coherent across long
gaps between sessions.

Claude is also a co-author of the **world content** — the scene descriptions, item
flavour text, and the authored seed files under `src/main/resources/world/` are the
kind of thing it's happy to help expand. Want a new wing of the ruined keep or a
cursed amulet with a backstory? That's a fun thing to ask it for.

---

## Running the game

**Requirements:** JDK 21+, Maven, and Docker (for the PostgreSQL play database).

### 1. Start the database

The game persists its world to PostgreSQL, brought up via the project compose file:

```bash
docker compose up -d
```

### 2. Build the jar

```bash
mvn -DskipTests package
```

### 3. Launch the console

The UI is a **JLine terminal application**, and JLine needs a *real* terminal. The
launch helper discovers a JDK 21+ runtime and runs the fat jar for you:

```bash
.claude/scripts/run-app.sh
```

Then type `look` to see where you are, `move north` (or `go east`, …) to travel,
and `bye` to quit.

> **Run it from an interactive shell** (Git Bash, Windows Terminal, a system
> console). Do **not** launch through Maven (`mvn spring-boot:run` / `exec:java`):
> Maven takes ownership of stdin/stdout and forces JLine into dumb-terminal mode,
> losing line editing, history, and colour. For the same reason the script runs the
> built jar with `java -jar` directly.

> **Logging goes to a file, not the console.** Because JLine owns the terminal,
> Logback is configured (`logback-spring.xml`) to write to `./logs/game-clean.log`
> with **no console appender** — so logs never scribble over the game UI. If
> something goes wrong at startup and the console seems stuck or empty, that log
> file is where the story is.

### Choosing a world

Two authored worlds ship in `src/main/resources/world/`. Pass `--world` to pick one
(omit it for the default `scenes.yaml`):

```bash
.claude/scripts/run-app.sh --world scenes2.yaml
```

A bare name resolves to `classpath:world/<name>`; a value with a scheme
(`file:/abs/path.yaml`) is used verbatim. Any other `--game.*` arguments are
forwarded to the application.

---

## Tech stack

| Concern            | Choice                                                              |
|--------------------|---------------------------------------------------------------------|
| Runtime            | Spring Boot 4.0.6 on Java 21                                         |
| Build              | Maven                                                               |
| Database           | PostgreSQL (project `docker-compose.yaml`)                          |
| Migrations         | Flyway — **no ORM**                                                 |
| Persistence access | Spring Data JDBC + MapStruct                                        |
| UI                 | JLine terminal console                                              |
| Id generation      | NanoID, behind an output port                                       |
| Tests              | JUnit 5 unit tests + integration tests on **ephemeral Testcontainers Postgres** |
| Boundaries         | ArchUnit guards keeping `core` framework-free and hexagonal         |

## Project layout

```
core/                       framework-free: the heart of the application
  model/{aggregate}/        aggregate roots + value objects (scene, player, item)
  port/{operation}/         output ports (persistence, transaction, id, randomness, seed, …)
  usecase/{goal}/           use cases + their input/presenter ports; subcases as peers
infrastructure/             adapters + Spring wiring
  persistence/              Spring Data JDBC repos, DB entities, MapStruct mappers
  terminal/                 JLine config, console session, command parsing, presenters
  world/                    YAML seed reader + seeder (authored content → through the domain)
  transaction/  id/  randomness/
```

Built so far: game initialization, the interactive terminal shell, and the `look`,
`move`, and item-spawning verticals. Not yet: NPCs, `look <target>` / `take`, and
asynchronous/event processing. See `.claude/memory/project-context.md` for the live
status.

---

*A pet project, worked on sporadically and shared for the community. No delivery
pressure — the point is to experiment, learn, and demonstrate the methodology in
the open.*
