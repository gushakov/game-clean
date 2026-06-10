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
