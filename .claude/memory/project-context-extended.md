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

_Domain model, use cases, and adapters not implemented yet — those sections fill
in as the model takes shape. The recipe below is dev tooling, valid already._

## Recipe — driving the terminal app headlessly

The interactive terminal app reads from a TTY; to run it without one (Claude
sessions, CI smoke checks), **script and *pace* stdin**:

```
{ printf 'cmd1\n'; sleep 5; printf 'cmd2\n'; sleep 4; printf 'bye\n'; } \
  | <jdk21>/bin/java -cp "target/classes;<jline-jar>" <MainClass>
```

(For the real app, swap the `-cp` invocation for `java -jar target/<app>.jar`.)

- **Non-TTY ⇒ JLine dumb terminal.** ANSI styling is stripped and cursor
  choreography is absent, so this verifies output **content and ordering**, not
  visual rendering. The redraw-preserves-your-input nicety is *not* observable
  headless — that stays a human-at-a-real-terminal check.
- **Pace the input** (interleaved `sleep`) to let time-based / async `printAbove`
  events fire and be captured.
- **Always JDK 21** (class compiled at 21) and **never via Maven** — `mvn
  spring-boot:run` / `exec:java` make Maven own stdin/stdout ⇒ forced dumb mode.
- Use this for **adapter smoke checks** and the wall-clock / concurrency
  exploration of thread `#3`; test game *logic* via JUnit on the use-case layer.
  See the testability stance in `design-notes.md`.

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

`docker-compose.yaml` runs a single Postgres service (`gameclean-db`, db `gameclean`,
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
- Output ports throw a **checked** `*OperationsError`, caught at the use-case checkpoint.
- Input crosses the boundary as primitives / `*Entry` DTOs; Value Objects are constructed
  **inside** the use case, never by adapters.
