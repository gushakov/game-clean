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
either yields a fully-legal object or throws. The throw is a single named domain type,
`InvalidDomainObjectError` (in `core/model/`), raised through the small `DomainValidation`
helper rather than a raw `NullPointerException`/`IllegalArgumentException`; equality is by id
for aggregate roots, by value for VOs. (Exact Lombok shape → `project-context-extended.md`.)

**Why a *named* construction failure — and what it deliberately does not cover.** The
always-valid model makes construction *the* validity gate, so the gate's failure deserves a
domain-meaningful type, not a JDK exception each caller must remember to translate. This
**reverses an earlier "validation is JDK-only" stance**: every constructor and static factory
now throws `InvalidDomainObjectError`, and the use-case gate catches *that one type* —
`InitializeGame`'s three construction checkpoints went from
`catch (IllegalArgumentException | NullPointerException)` to `catch (InvalidDomainObjectError)`.
The payoff is the §6 principle applied to validation — *remove the affordance* (an untranslated
JDK exception leaking past the gate) rather than forbid the misuse — plus a sharper catch: a
stray `NullPointerException` bug inside a construction block now falls through to `presentError`
instead of being mislabelled "invalid parameters". The boundary is drawn on purpose:
**construction throws the named type; behaviour-method argument guards stay plain
`Objects.requireNonNull`/NPE** (`Scene.exitsWithTargetNotIn`, `SpawnRule.rollPlacements`,
`ItemTemplate.spawnInto`), because a null collaborator handed to a side-effect-free function is
a *caller* programming error, not invalid domain *input*. Two failure categories, two signals.

**Reconstitution shares the one gate — by choice, not accident.** `[thread #2]` The persistence
mappers rebuild VOs and aggregates through the same constructors, so a corrupt stored row now
throws `InvalidDomainObjectError` too, riding the use case's outermost `catch` to `presentError`
exactly as the old raw exception did. We weighed rewrapping reads into a distinct
`PersistenceOperationsError` — to keep "bad authored input" (a presented invalid-input outcome)
separate from "corrupt persisted data" (an integrity fault) — and **deferred it**: the read-path
failure is presented generically regardless, the aggregate rebuild is MapStruct-generated (so the
wrap is not a one-liner), and one construction gate is the simpler invariant until a real need to
tell the two apart appears. `InvalidDomainObjectError` is unchecked, like
`PersistenceOperationsError` (§5), so it composes through the transaction template and triggers
rollback on the rare construction that runs inside one.

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
forces it to, not when we imagine it might. The `Player` aggregate is the cleanest proof:
when `look` finally forced it into existence it was minted with a *single* field — its
current position (a `SceneId` reference) — because that is all `look` reads. No inventory,
stats, or name appeared on speculation; they wait for the interaction that demands them.

**Items: the speculation finally cashed in — three VOs and a generator, each pulled by one vertical.**
`[thread #1]` Adding *items found on the ground* forced exactly the model the earlier interactions had
refused to invent, and each piece arrived with a concrete demand behind it, not on a hunch. The **probability
VO** this section had parked became `Chance` the moment spawning needed odds. `SpawnRule` (chance + tries +
≥1 candidate scene) and `ItemTemplate` (descriptions + rule) followed — and `ItemTemplate` is the sharp
case: it exists not for tidiness but because a blank description must fail the validity gate *regardless of
how the random spawn rolls fall*; validating it on the template, up front, closes a gap where an invalid
item might otherwise never be exercised. The **id generator** the `SceneId`/`PlayerId` javadocs had long
promised ("owned solely by the generator adapter") had never actually existed, because authored/configured
ids (`scn1`, `plr1`) are never generated; item *instances* are the first identity the system itself mints —
one template spawns several — so the generator finally has a consumer, landing exactly where this section
predicted: the prefix in the VO (`ItemId.fromGeneratedBody`), the body alphabet (NanoID) private to the
adapter, one knower, no drift. That also retires parked alternative (a): authored ids are not
generated-shape, so a shared `IdFormat` still earns nothing.

**The item aggregate boundary — by identity, not containment.** `[thread #1]` `[thread #3]` An item is its
*own* aggregate, not a child of `Scene`. The test is invariants: there is no scene↔item consistency rule that
must hold inside one transaction (a scene is not made invalid by what lies on its floor), and an item's
location is mobile — on the ground now, in a player's or NPC's possession once `take` arrives. So `Item`
references *where it is* by `SceneId`, exactly as `Player` references `currentScene` and `Exit` its target;
"the items in a scene" is a *query* against that reference, never a collection the scene holds. Containing
items inside `Scene` would both invent a false invariant and force every pickup to rewrite the whole scene
aggregate — the contention seed of `[thread #3]`. Minted minimal, like `Player`: id, location, two
descriptions, nothing speculative.

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
(**Revised** — the seed's classification was later flipped from *driving* to *pulled driven*;
see *The parked caveat, realized* at the end of this section. The boundary-currency rule below
is unaffected: only the seed's hexagon side changed, not what crosses it.)

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
  adapter. A corollary surfaced with `look`: the carrier is a **DTO only when the input has
  structure to carry**. `ConstructWorld` takes `SceneEntry` because a scene is a record of
  fields; `look`'s entire input is one id, so it crosses as a bare `String playerId` and
  *no `*Entry` type exists*. The principle is "primitives inward," not "a DTO per use case" —
  the DTO is what you reach for when the primitives are plural and shaped.
- All validation lands at **one gate**: intra-aggregate (VO / `Scene` constructors) and
  inter-aggregate (the two-pass exit-target resolution, §4) both inside the use case,
  rather than split across infra and core.

**Testability is the clincher.** With the `*Entry` carrier crossing the boundary you can unit-test
the use case against a blank-named scene or a dangling target and assert the domain error, because the
DTO can *represent* that bad state. A `List<Scene>`-returning seed port couldn't even construct the bad
fixture — the gate would be untestable at the use-case level. (This leverage is independent of the
seed's hexagon side: after the flip to a pulled driven port, the same bad fixture is supplied by
*stubbing the source port* — see *The parked caveat, realized*.)

**The parked caveat, realized — the seed became a *pulled* driven port.** This caveat once read
"the one place 'driven ports return models' gets real tension is a driven port sourcing *untrusted
external* data (a future `WeatherOutputPort` — external like YAML, yet pulled like persistence); the
seed *sidesteps* this by being **pushed** (driving)." That sidestep was reversed, and the reversal is
*more* faithful, not less. The authored seed is now **pulled** by `InitializeGame` through
`GameSeedSourceOperationsOutputPort` as its first checkpoint, with the YAML machinery behind the
adapter. The tell that the original "seed is driving, peer of the terminal" reading was wrong: the
seed file does not *initiate* anything. The human at the terminal initiates (`look`); the seed is a
*resource the system fetches* to fulfil a startup goal it was already told to pursue — and "fetched,
not initiating" is exactly what a driven port models. This also makes the use case truly *drive* the
interaction: loading the world is the use case's own first step, not logic stranded in a seeder adapter
outside it.

What it realizes is a **third boundary shape**, distinct from both §3 poles: a *driven port that
returns an invalid-capable carrier* (`GameSeed` / the `*Entry` DTOs), **not** a valid model.
Persistence returns valid models because its data is valid *by provenance*; this port sources untrusted
authored data whose validation is *deliberately deferred* to the use-case gate, so an always-valid
model cannot be its return type — the constructor would throw before the gate. The boundary-currency
rule is untouched (**invalid-capable carrier inward, valid model outward**): the carrier merely arrives
as a *return value* now, not a pushed argument. DDD's ACL answer for untrusted sources still applies in
spirit — the adapter owns the foreign→domain *sourcing* — but it returns the carrier, not a model,
because rejecting bad authored input *as a presented outcome* is the whole point. Two §3 dividends
survive the flip intact: the **single validity gate** (VOs built only in the use case) and the
**testability clincher** — the bad-fixture test now *stubs the source port* to return a blank-named
scene instead of passing it as an argument, equally expressible. The cost: §3's clean
driven-returns-models / driving-pushes-DTOs symmetry now carries a documented exception — which is
exactly the kind of finding this showcase exists to surface. The decisive consequence elsewhere: with
the seed pulled *inside* the use case, a seed-source failure is **presented** (caught at the outermost
checkpoint), uniform with a persistence fault, rather than failing startup from an adapter — see §6.

## 4. Use cases as first-class interactions

**Interaction shape (the world-construction phase).** The actor is the *system at startup*.
World construction is **two-pass**: construct all scene aggregates, then validate that every exit
target resolves to a known scene. That second check is an *inter-aggregate* consistency rule,
so it lives in the **use case**, not on the `Scene` entity, and yields a meaningful domain
*outcome* rather than a foreign-key failure (the schema deliberately carries no FK on
`exit.target_scene_id` for exactly this reason).

**Seed through the domain.** Construction *is* the validity gate, so authored data flows in
through the domain model; Flyway owns schema/DDL only, never data. The seed-if-empty
idempotency guard lives *inside* the use case, so the guarantee holds regardless of which
adapter fires it.

**World and player are two phases of one system interaction — that present once.** `[thread #4]`
Initializing a fresh game is a *single* system-actor goal — `InitializeGame` — not two.
Constructing the authored world and placing the single player are *phases* of one `void`
interaction, because the world→player order is a **domain precondition** (a player needs a scene
to stand in), not two independent goals that merely happen to run at boot. Holding the sequence
inside one use case is what lets the precondition be enforced *where its outcome is visible*. The
decisive constraint is the rule stated next: a phase **must not present and then continue**. So
the world phase is *not* a sub-interaction that reports its own success — it is a pair of
**non-presenting checkpoints** (build the scene aggregates, resolve their exit targets) feeding
the interaction's *single* success, `presentGameInitialized`. "World already seeded" and "player
already present" fold into that one outcome, because the system actor's goal — a playable game —
is met identically whether the state was just written or already there. The phases are plain
inline checkpoints, not the heavier **subcase** machinery (a reusable procedure with its own
presenter) — that waited for a genuine cross-use-case reuse need, which has since arrived
*elsewhere* (the `orient` subcase, below); `InitializeGame`'s phases are single-use, so they stay
inline — and not, any longer, private helpers that returned a boolean gate. That boolean was the tell that a non-terminal phase was
being treated as a terminal interaction: it *presented* its outcome **and** *returned a
continue/stop flag*, straddling the helper/subcase split and doing the one thing the
single-presentation rule forbids. What remains is the validity gate, the inter-aggregate
resolution (every exit target *and* the starting scene resolve to an authored scene — checked
**in-memory** against the world being built, because on a first run the store is not seeded yet,
so a store lookup would be a false negative), and a now-*single* transaction-tight
seed-and-create — all converging on the one presentation.

**Presentation is terminal — and `InitializeGame` is what forced the invariant into the open.**
The general rule now lives canonically in `clean-ddd-core` → *Unidirectional flow of control* →
*Presentation is terminal* (exactly one `present*` per run, reached as the interaction's last act;
a `present*` relinquishes control completely rather than calling a subroutine; the use-case-side
twin of the controller rule, which never branches on a result or chains use cases). It was
**promoted out of this project**, so it is not restated in full here — this section keeps only
game-clean's own contribution. That contribution is the *evidence*: merging world-construction and
player-placement into one use case is what made the rule load-bearing, and it caught the concrete
violation described above — the private phase helper that *presented* its outcome **and**
*returned a continue/stop boolean*. That straddle is exactly the trap a phase-by-phase merge
invites, which is why naming the rule interaction-globally was what surfaced it here.

**Transaction corollary.** `[thread #3]` When the single outcome is a success behind a write, the
rule takes a concrete shape: at most **one** `txOps.doAfterCommit(() -> present*)` is ever
activated in an interaction, registered inside the one `doInTransaction`, and that
`doInTransaction` call is *followed by `return`* — the deferred presentation is the interaction's
terminal act, so nothing may run after it commits. Failures *before* the transaction
present-and-return directly (each its own stripe); a failure *inside* it throws, rolling back and
unwinding to the single outermost `catch → presentError`, so even the error path presents exactly
once. One outcome also invited **one atomic unit**: the world-seed and player-create guards-plus-
writes that were two transactions collapsed into a single `doInTransaction`. The boundary that
keeps the rule from over-reaching: a `doAfterCommit(present)` and a domain-event dispatch are
**not** a second presentation — scheduling deferred work, or dispatching an event that later
triggers a *different* interaction, is allowed (that is the §8 event spine); the invariant governs
an interaction's own forward `present*` calls, not the causal chain it may set in motion.

**A third phase, and the use case turns stochastic without losing its testability.** `[thread #2]` `[thread #3]`
Seeding *items* is a third phase of the same `InitializeGame` interaction — world → player → items — for the
same reason player-placement was the second: world→items is a *domain precondition* (items need scenes to
spawn into), checked in-memory against the world being built, exactly like the exit-target and starting-scene
resolutions. It folds into the one success (`presentGameInitialized`) and the one atomic unit: a third
idempotency guard, **spawn-if-none**, joins seed-if-empty and create-if-absent inside the single
`doInTransaction`, so a restart never re-rolls. What is *new* is that the phase is **non-deterministic**, and
the resolution is the lesson: the entropy is an **output port** (`RandomnessOperationsOutputPort`), so the
use case stays deterministic under test (stub the draws) even though production spawns at random — the random
rolls run *outside* the transaction (a pure in-memory construction with no persistence effect), and only the
saves sit inside it. Two *distinct* randomness roles are kept on two ports, not conflated: opaque **identity**
(`IdGeneratorOperationsOutputPort`) and domain **dice** (`RandomnessOperationsOutputPort`). The single success
also carries the items *spawned this run* (empty on an idempotent re-run) — generated effects, reported
distinctly from the authored scenes, which are the full world on every path.

**Read-only and read-write interactions over one shared scene presentation.** `[thread #2]`
`Look` (read-only) and `move` (read-write, the first interaction to *update* an aggregate) both
operate *from the acting player's current scene*, reaching their outcomes by **branch-and-present**
(missing player, dangling current-scene reference) rather than by throwing. That shared grounding —
not the use case — is what decides presenter sharing, and `move`'s arrival **corrected the first
guess**. The guess was that only the narrow `presentScene` capability would be shared, each use case
keeping its own not-found outcomes. But because `move` resolves the *same* player-and-current-scene
prologue, the not-found outcomes are shared too: the cluster is the three outcomes of "describe where
the acting player stands, or why we can't" — lifted into the shared `OrientPlayerPresenterOutputPort`
(the `orient` subcase's presenter port — see below). `move`'s
port extends it with the two outcomes peculiar to moving (no-such-exit, dangling exit target), and
`look`'s port turns out to *be* the cluster exactly (kept as an empty marker for symmetry). The
lesson: **outcome-sharing tracks the shared prologue, not the use case** — any later interaction
grounded in the current scene (`look <target>`, `take`) joins the same cluster.

**Items sharpen the prologue's edge: a scene's *contents* track the scene each use case presents, not the
prologue.** `[thread #2]` `[thread #4]` The obvious move when items arrived was to fold "the items on the
ground" into the `orient` result alongside player + scene. It is wrong, and the reason draws the boundary of
the shared prologue precisely. The prologue resolves *where the acting player stands* — and `look` presents
*that* scene, but `move` presents the scene it *enters*, a different scene whose items `orient` (which
resolved the origin) cannot supply. So items are not part of the prologue; they belong to **whichever scene
the use case presents**, and each use case fetches them for that scene (`look` the current, `move` the
target) through the shared `ItemRepository` port and the shared `presentScene(scene, items)` renderer
capability. The prologue stays exactly *player + location*; presentation-content (scene + its items) is the
per-use-case part. `take` will confirm the split from the other side: it needs the current scene's items but
**presents none of them**, so it reuses the same fetch without the rendering — the contents are an input to
its logic, not an outcome.

This split sharpens the project's headline finding into **three orthogonal axes of sharing**, each
resolved by its own mechanism. The *port vocabulary* is shared — by interface extension, with **no
default methods** (a presenter port stays behaviour-free; how a scene renders is an adapter concern).
The *adapter rendering* is shared — a single `CurrentSceneRenderer` collaborator over a `Console`
facade (§7), injected into two thin per-use-case presenters: **composition**, not a presenter base
class (which would overclaim "is-a scene presenter") and not a grab-bag port. And the *use-case logic*
is **now shared too**, through the `orient` subcase below. That last axis **reverses an earlier
stance** ("share the vocabulary and the rendering; *never* the logic"): the prologue had been
inline-duplicated because a naive prologue subcase would *present on failure and continue on success* —
the present-or-continue straddle the single-presentation rule forbids. The guarded-prologue subcase
dissolves that straddle, so all three axes are now shared.

**The `orient` guarded-prologue subcase — sharing decisions *and* presentation.** `[thread #4]` The
shared opening (resolve the ambient player → read → resolve their current scene) that `look` and `move`
both perform — and that `examine`, `take`, ... will reuse — is factored into the project's first
**subcase**, `OrientPlayerSubcase.playerGetsBearings()`. It is a *third* subcase shape, distinct from
the methodology's two paths (a pure **helper** that returns/throws and lets the parent present; a
**terminal subcase** that presents and *always* throws, never returning). `orient` **fuses the two,
keyed on the outcome branch**: its **failure branches behave as a terminal subcase** — a missing player
or a dangling current-scene reference is *presented* through the shared presenter, then signalled by
throwing the marker `SubcaseAlreadyPresented`; its **success branch behaves as a helper** — it presents
nothing and *returns* an `OrientPlayerResult` (player + current scene) for the parent to act on (`move`
mutates the player, `look` renders the scene).

Each invocation therefore does exactly one of {present-and-throw, return-without-presenting} — never
both — so **"presentation is terminal" holds at the granularity of the whole interaction** (parent +
subcase): on every path through `look`/`move`, exactly one `present*` fires. The marker rides the
parent's checkpoint ladder — a dedicated `catch (SubcaseAlreadyPresented)` *ahead of* `catch (Exception)`
swallows it as a no-op (the presentation already happened), while genuine faults (a malformed id, a
persistence error) fall through to `presentError`. The swallow lives in the **parent's catch, not the
presenter**: an attempt to filter the marker inside `ErrorHandlingPresenterOutputPort.presentError` was
**rejected** because it gave a *humble* presenter port a *flow decision* and a `default` method (both
against the behaviour-free-port rule above) and rippled a method rename across every presenter — the
explicit parent catch is local, honest about control flow, and is the methodology's own prescription.

The methodology framed Path-1-vs-Path-2 as a choice made *per subcase*; `orient`'s finding is that for a
**guarded prologue** the choice is naturally made *per branch* — equivalently, **a terminal (Path-2)
subcase may return a result on its non-presenting success branch**. This is a promotion candidate for
`subcases.md` (flagged here, not promoted from this project, per the methodology's Prompt-4 discipline).
The composition-root consequence — parent and subcase share one ad-hoc-`new`ed presenter instance — is
in §6.

**Interaction methods are named as the Cockburn step, not as a service verb.** `[thread #4]` An
interaction method's name is its *actor + predicate* — `playerLooksAround`,
`systemInitializesGame` — the subject being the initiating actor (the player, or the system at
startup) and the predicate the goal it advances. A bare verb (`look`, `initialize`) reads as a
framework or service call and sheds the use-case identity the method *is*; because the summary-goal
package and the use-case class already carry the goal noun, the method is free to spell out the
step. The name then states, right at the call site, *who acts and toward what* — the same Cockburn
thread the package and class encode at coarser grain. (Sibling to `clean-ddd-core`'s presenter-method
grammar; a candidate to promote there as the matching rule for input-port methods.)

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

**The symmetric data point: a pure read takes *no* transaction at all.** `Look` reads through
the persistence ports and presents, with no write — so it opens neither `doInTransaction` nor
an after-commit hook. "Reads run outside the transaction" taken to its limit: when an
interaction is *only* reads, the transaction port is not a collaborator it touches at all
(its unit test has no tx port to stub). The after-commit presentation rule exists to avoid
reporting success before durability; with nothing to commit, presentation is simply immediate.

**A consumer-less port built ahead of its use case — justified.** The transaction port was
built before its first consumer, which inverts the usual inside-out order. Acceptable here
because it is *stable cross-project infrastructure*, not an emergent domain artifact; the
emergence discipline (§2) applies to the model, not to plumbing the methodology already
considers settled.

**Insert-vs-update for assigned ids — upsert now, `@Version` deferred.** `[thread #3]` `move` is
the first interaction to *update* an existing aggregate, which exposed a Spring Data JDBC quirk: with
assigned, never-null ids, `save` cannot distinguish a new row from an existing one (it always issues
an update), which is why the player adapter had used an explicit `insert`. The adapter now decides
explicitly — `existsById` ? `update` : `insert` — behind a persistence-ignorant `savePlayer`
("persist this player," not "insert"). The framework's other answer, an `@Version` field, was weighed
and **deferred**: its only unique payoff is optimistic locking, and single-session play has no
contended player aggregate yet. Adopting it now would either keep the version on the DB entity (no
real locking, plus a wasted read to learn the current version) or bleed a non-domain field into the
always-valid `Player` (against §2's minimalism). So `@Version` is held *as material for the
concurrency thread*: when a genuinely contended aggregate arrives, the live question becomes precisely
whether optimistic locking can stay on the persistence entity or must surface in the model — a
sharper lesson than anything a speculative version column would teach today.

## 6. The composition root — the framework held at arm's length

**Wiring is explicit and hidden from the core.** Use cases are declared as
`@Bean @Scope(PROTOTYPE)`, return-typed to the **input-port interface** so the implementation
is invisible to the container, and assembled with explicit `new` — no Spring stereotype ever
lands on a core class. Where Spring can't infer wiring (the two `TransactionTemplate`s,
read-write `@Primary` + `@Qualifier("read-only")`), it is declared by hand in one
`@Configuration` rather than scattered across component-scanned classes.

**Presenters (and subcases) are `new`ed ad-hoc, not injected as beans — the default.** `[thread #2]`
A use-case factory constructs its presenter with `new` inside the `@Bean` method and hands the *same
instance* to the use case and to any subcase it drives, then `new`s the subcase too. This is what makes
the `orient` subcase's shared presenter sound (§4): a single presenter receives every outcome, whether
the subcase presents a not-found or the parent presents the scene. It is now applied uniformly — even
`InitializeGame`, which has no subcase, `new`s its `LoggingInitializeGamePresenter` — so the rule reads
"presenters are constructed by the composition root," with no presenter `@Component` left. The
deliberate trade-off: a presenter is **no longer swappable by bean selection**, which has a *testing*
corollary worth stating because it reshapes the test suite — presenter-outcome assertions live in the
**use-case unit test** (mock the presenter, construct with `new`), while an **integration test asserts
real side-effects** (persisted state), since it can no longer replace the presenter with a `@MockitoBean`.
`InitializeGameIT` was rewritten to this shape. The terminal presenters keep their behaviour purely
through the `Console`/`CurrentSceneRenderer` resources they are `new`ed with (those *stay* beans, gated
by `game.terminal.enabled`), so disabling the terminal still removes the rendering surface even though
the presenter is no longer conditional itself.

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

**Boot-time ordering of driving adapters is a composition-root concern — but only *ordering*,
not business sequencing.** This is the sharpest lifecycle lesson so far, and it took a wrong
turn to sharpen fully. Driving adapters must come up in a defined order — bring the game into
being, *then* hand the main thread to the console — and that order is a real application
invariant.

The tempting move is to push the sequence "up" into the application layer (a startup use case
over ports). **That is doctrinally wrong, and the reason is sharp:** the second step — the
interactive loop — is a driving adapter taking the main thread. Driving adapters call *inward*.
If the core sequenced them, it would be reaching *out* to start its own driving adapters —
reversing the hexagon. The core must never know its driving adapters exist, let alone order
them. So **boot-time ordering of driving adapters is a composition-root / infrastructure
responsibility**, sitting *beside* the composition root, not inside `core`. Expressing it as
two `@Order`-annotated `ApplicationRunner`s was the first shape; we then pulled it into one
named runner whose body — `gameSeeder.seed(); consoleSession.start();` — *was* the order, to
keep the sequence readable in one place rather than dissolved across two files and Spring's
runner-sorting SPI.

**The wrong turn, and the distinction it forced.** That runner then grew to fire *two business
interactions* in turn — seed the world (`ConstructWorld`), then seed the player
(`CreatePlayer`), then start the console — and that conflated two different things. Ordering
driving adapters by **lifecycle readiness** ("the console can't take the thread until the game
exists") is legitimately infrastructure's job. But "the player may be created only *after* the
world was constructed" is not lifecycle readiness — it is a **domain-state precondition**
between two business interactions, and sequencing *that* in the runner is unsound: a `void` use
case reports its outcome to its presenter, so once the runner called the first one it had, by
the unidirectional-flow contract, **renounced the right to know whether it succeeded** — yet it
then made a second, outcome-dependent call on blind assumption. The tell that the dependency
belonged in the core: it was already a domain read inside the player step (`findScene`). The
fix re-sorts the two concerns: the world→player→items business sequence collapses into one
`InitializeGame` use case (§4), where the preconditions gate on a visible outcome; the runner
keeps only lifecycle.

**Then the same unidirectional logic came for the runner body itself.** With business
sequencing gone, the runner was just `gameSeeder.seed(); consoleSession.start();` — defended by
a comment ("we use the return only for lifecycle, never a business decision"). But an imperative
body of two inward fire-and-forgets is *itself* a unidirectional smell: control visibly returns
from the first call, so it is a place where an outcome-dependent branch on the seeding result
*could* be inserted — forbidden by the contract, prevented only by a promise. Two things tipped
the balance to acting on it. First, the §3 seed-source flip makes `seed()` **always** return
normally (a source failure is now *presented*, not thrown), so the return-of-control is
permanent and load-bearing, not incidental. Second — the principle — **removing the *affordance*
beats forbidding the *misuse*.** So the body is gone: `BootSequence` is now a `@Configuration`
declaring two `@Order`-ed `ApplicationRunner` beans (`@Order(1)` seed, `@Order(2)` console),
co-located, each an independent inward fire. No body, no return site, no branch point; the
framework sequences them — which is exactly where driving-adapter *lifecycle* ordering belongs.

This **re-trades** the earlier "one named runner, body-is-order" decision, knowingly. That
decision ranked *imperative visibility* first and rejected `@Order` runners for dissolving the
order into SPI across two files. The re-trade ranks **unidirectional flow above imperative
visibility**, and recovers visibility differently: both runners sit in one `@Configuration`,
with small explicit `@Order(1)`/`@Order(2)` integers (not magic precedence constants) and a
class javadoc stating the order in prose. The old objection is thereby paid down to its
irreducible minimum — a reader need only know that Spring runs `ApplicationRunner`s in `@Order`
order (unchanged on Boot 4; see `spring-boot-4-notes.md`). The seeder stays a thin driving
adapter — pull the prototype input port, fire `systemInitializesGame()`, use no return — the
system-actor peer of `ConsoleSession`. The per-adapter enable flags stay simple: the config and
both runners share the one `game.terminal.enabled` guard with `ConsoleSession`, so a test slice
gets neither runner — never seeding implicitly, blocking on a console, or grabbing a system
terminal.

**Forward fit:** when Phase 3 adds an independent clock and an outbox relay, their ordered start
— and the *reverse-order* shutdown the design requires (`Terminal.close()` must run last) —
belong here too, as further `@Order`-ed runner beans co-located in this config (or their
`SmartLifecycle` equivalent where start and stop must pair), so the boot order stays one
readable declaration.

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
later — while the bean topology stays put. A thin `Console` facade over JLine — long deferred
until a *second* use site needed the same choreography (styling, dumb-terminal handling,
width-aware formatting) — **emerged with `move`**: the styled-writing toolkit the `look` and
`move` presenters share is now one `Console` resource (declared beside `Terminal`/`LineReader` in
`TerminalConfig`), with a domain-aware `CurrentSceneRenderer` rendering the scene cluster on top of
it (§4). Emergence on schedule — a second use site shaped the facade, not speculation. Note the
layering the second site forced into the open: `Console` is **domain-agnostic** (it knows styled
text and the terminal, never `Scene`); turning a `Scene` into styled output is *presenter* logic and
lives in `CurrentSceneRenderer`, one layer up.

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

**Orchestration for fixed sequences, choreography for causation — and boot is the former.**
`[thread #3]` A tempting shortcut to that end state is to chain *boot* through events too —
have `InitializeGame` dispatch a domain event in `doAfterCommit` and let a listener-adapter
fire the next step. We weighed it and **deferred it deliberately**, and the reasons sharpen
*when* events earn their place. Boot is a *fixed, deterministic, precondition-ordered* sequence
with one producer and one consumer — the worst possible fit for a notification mechanism:
synchronous events would only re-obscure the sequence we just made explicit (the §6 lesson,
undone); the idempotent already-seeded path emits nothing, so the chain would silently break
(forcing a *state* event rather than a *fact* event); starting the console is lifecycle, not a
domain reaction; and an in-memory broker is not the crash-durable outbox anyway. So the rule:
**fixed sequences are orchestrated** (explicit, in the core or beside the composition root);
**genuine causation — dynamic, possibly one-to-many, reactive — is choreographed with events.**
Gameplay is where the second mechanism earns its keep (an NPC reacting to the player entering a
scene), so the event spine is introduced at its first *causal* site, not retrofitted onto boot.

## 9. Command parsing as a delivery-mechanism concern

**Parsing is the controller's job, not the core's.** Turning a raw input line into intent is a
*delivery-mechanism* concern (Clean Architecture's controller), so the grammar, the tokenizer
and the parsed `Command` types live **entirely in the driving adapter** and never cross into
`core`. The console controller unpacks a `Command` into a use-case call carrying primitives
inward; the use case has no idea a command syntax exists. That is what lets the parser be
replaced — a richer grammar, a scripting transport, a network protocol — without the core
moving. The `Command` intent object is the adapter's private representation, deliberately *not*
a core type.

**ANTLR was the obvious tool and the wrong one — for now.** A parser generator (grammar DSL,
codegen plugin, generated-sources, a runtime jar, lexer-precedence and error-listener
learning) is real machinery, and for a `verb [word]` language it is exactly the speculative
complexity §2 resists — one layer out from the model. It was deferred in favour of a
**tokenizer + command registry**: a `Map<verb, {arity, factory}>` where adding a command (or a
synonym) is one `register(...)` line. The registry is genuinely "generic and programmable from
the start" without the codegen, and — the load-bearing part — its parsed-intent output is
*exactly the seam* a grammar engine would later produce. So the upgrade to ANTLR, if the
language ever grows real grammar (multi-token noun phrases, quoting, prepositions,
disambiguation), is a change confined to this one adapter and costs the core and the controller
nothing. **Defer the tool, keep the seam tool-ready** — the same emergence discipline applied
to a build decision rather than to the model.

## 10. Orchestration vs computation — the use case owns the rule, the model computes it (Law of Demeter)

This refines §4. An **inter-aggregate consistency rule** (every exit target resolves to an authored scene;
every item's candidate spawn scenes resolve) is the *use case's* responsibility — it is not on any single
entity, and it yields a domain *outcome*, presented. But "owning the rule" is not the same as "computing it,"
and conflating them produces a Law-of-Demeter train wreck. The first cut of the item check read:

> `item.getTemplate().getSpawnRule().getCandidateScenes().stream().filter(id -> !known.contains(id))`

The use case was walking the model's **internal composition** (an item *has-a* template *has-a* rule *has-a*
candidate list) to do work *about* that data. The smell is structural coupling: the use case knows how an
`ItemTemplate` is built, not just what it can answer.

**The fix is Tell-Don't-Ask with a side-effect-free function on the model.** The VO answers about its own
state — `SpawnRule.candidateScenesNotIn(known)`, `Scene.exitsWithTargetNotIn(known)` — delegated outward so a
caller never reaches *through* a holder (`ItemTemplate` and the use-case-private `AuthoredItem` each forward
one level). The use case keeps only what is genuinely its job: assemble the known-id set, collect the
unresolved across items, and **present** the outcome. Orchestration stays in the use case; the per-object
computation moves to the object that owns the data.

**The crux — identity is the decoupling seam, so "this couples `ItemTemplate` to `Scene`" is a false alarm.**
The fear is real *only if the parameter is `Set<Scene>`*. It is `Set<SceneId>` — identities the rule already
holds (its candidates *are* `SceneId`s) — so no new dependency is introduced, and the VO never touches the
`Scene` aggregate. This is the §2/§3 rule paying off twice: aggregates reference one another **by identity**,
and an aggregate reasoning about another *through identities it already holds* collaborates without coupling.
The use case computes the world's identity-set; each VO answers against it; no object ever holds another
aggregate's instance.

**Consequences.** Entropy stays flat — no new type, behaviour merely relocated onto an existing VO — and
*testability rises*: the relocated SEFFs (`candidateScenesNotIn`, `exitsWithTargetNotIn`) get direct unit
tests, where before the logic was only reachable through the use-case test. That is Evans's standing argument
for side-effect-free functions: safe to call, trivial to test. (Promotion candidate for the methodology —
flagged, not promoted, per Prompt-4 discipline.)

**The spawn loop resolved — the whole stochastic policy pushed onto the model, fed suppliers.** `[thread #1]`
`[thread #2]` The spawn roll loop is now pushed down. The use case had been driving the algorithm itself — `item.getTemplate().getSpawnRule()`,
then a `maxTries` loop interleaving `isHitBy`/`pickScene` with the randomness and id-generator *port* calls.
The fix is the **batch form**: `SpawnRule.rollPlacements(DoubleSupplier)` owns the entire placement policy (the
attempt count, the hit and scene decisions, *and* the draw-ordering — one draw per attempt, a second only on a
hit, knowledge that had leaked into the use case); `ItemTemplate.spawnInto(Supplier<ItemId>, DoubleSupplier)`
turns each placement into a valid `Item`, pulling an id *only for an actual placement* (no eager minting for
misses); the use-case-private `AuthoredItem` forwards `spawnInto` one level, exactly as it forwards
`candidateScenesNotIn`. `spawnItems` collapses to a loop that adapts the two output ports to suppliers
(`idGeneratorOps::generateItemId`, `randomnessOps::nextDouble`) and collects — pure orchestration.

This is game-clean's instance of the now-promoted doctrine: the computation lives in the domain, fed values
and JDK suppliers, never ports; the use case keeps the orchestration (§3's boundary currency one layer in —
values/suppliers inward, valid model out). The general rule — functional core / imperative shell, the home
test, value-by-default / supplier-only-when-the-core-owns-the-cardinality, and why the instability objection
*dissolves* rather than relocates — lives canonically in `clean-ddd-core` → *Where inter-aggregate business
rules live*, promoted out of this project and not restated here. `SpawnRule` needed a `DoubleSupplier` for
exactly the cardinality the rule names — one draw per attempt, a second only on a hit, a count the domain owns
— so a *value* would not do here, unlike the day-of-week in the module's `customer.buy` example. The
`maxTries` leak closes; the "rolls run outside the transaction" property is untouched, since the use case
still chooses *where* to call `spawnInto`.

**Why `AuthoredItem` is *not* promoted to the model.** It looks like it embodies a Domain-Entity rule once it
has `spawnInto`, but it does not — it *forwards* to `ItemTemplate`, which already is the domain model, and a
forwarder is a client of a rule, not its owner. `AuthoredItem` is the *application* half: the pairing of a
domain template with its `authoredId` — a seed-file handle used only for the unknown-spawn-scene diagnostic and
discarded the moment spawning succeeds (instances get generated `ItemId`s, never the handle). So it stays in
the use-case layer. The promotion test is **not** "does it carry behaviour" but "**does the domain need this
thing's identity/lifecycle**" — and item templates are still transient (no aggregate references "the kind",
nothing is looked up by it). The named trigger to watch (§2 emergence): a respawn timer ("scene X respawns
`itm1` every N ticks") or stacked inventory ("3× `itm1`") would make the *template* a referenced, persistent
thing — *then* it earns an `ItemKindId`, the authored handle graduates into that id, and the use-case wrapper
dissolves. Until a domain reference exists, promoting would invent an identity on speculation.

**Batch over per-attempt — why the seam is the whole kind, not a single roll.** A per-attempt
`spawn(id, hitRoll, sceneRoll)` was the tempting shape, but it keeps `maxTries` in a use-case loop
(re-fragmenting the one policy `SpawnRule` deliberately unifies) and mints an id for every attempt including
misses. The batch `spawnInto` encapsulates the whole policy and mints only on a hit. The testability
dividend: `SpawnRule.rollPlacements` and
`ItemTemplate.spawnInto` get direct unit tests (a fixed draw-sequence pins the draw-ordering; a throwing
id-supplier proves a miss mints nothing), where before the spawn logic was reachable only through the
use-case test.

**Deferred and parked.**
- The derived `Set<SceneId>` of "scenes that exist in the world being built" is reconstructed for *both* the
  exit and the spawn checks — an **implicit concept** (Evans: *make implicit concepts explicit*). A
  `KnownScenes` / `SceneCatalog` VO with `contains` / `unknownAmong` would unify both resolvers, but minting
  it on two uses spends entropy the parameter already covers; held until a *third* resolver appears or until
  "scene existence" grows behaviour (resolving to actual `Scene`s, world-wide dangling reports).

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
