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

**The dependency rule is a *proxy* — relocation can satisfy it while defeating it.** `[thread #4]`
`core ↛ infrastructure` is a *mechanical* check standing in for a *semantic* invariant: **the core must
not be shaped by a delivery mechanism.** The two come apart the moment someone moves an infra type (a
parser `Command`, the terminal's `AffordanceContext`) *into* `core` to make a forbidden edge compile —
the build goes green while the invariant is *defeated*. So an edge that relocation makes legal is the tell
that the **type is misfiled**, not that the coupling was harmless. The honest test is not "does it import
infra" but **"would this type still make sense if a second adapter existed?"**: `ItemId`/`SceneId`/`Player`
survive any driver (core); `Command`/`SelectCommand(ordinal)`/`AffordanceContext` are meaningful only to a
line-oriented terminal (infra). The async ticker is the standing proof — it drives a use case with *no*
`Command` at all, so the core's input ports are already device-neutral, and importing a command vocabulary
would break that. (Surfaced rejecting a *core* `Conversation` interface for the conversation dispatcher,
§9.) (Promotion candidate, flagged not promoted: *the dependency rule proxies "the core is not shaped by
any delivery mechanism"; an edge that relocation makes legal means the type is misfiled — test by "would
it survive a second adapter?"*)

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

**Reconstitution failure is an integrity fault — wrapped, not shared with the input gate.** `[thread #2]`
The persistence mappers rebuild VOs and aggregates through the same validating constructors, so a corrupt
stored row fails *there*, throwing `InvalidDomainObjectError`. The question is what that failure *means*, and
the driven-adapter exception-wrapping work (the driven-side twin of this section's named-error move) settled
it: a corrupt row is an **integrity fault of the persistence port** — the store, *valid by provenance* (§3),
handed back something unbuildable — and is emphatically *not* the same thing as a human authoring a blank
name. So the read-path adapter catch is `catch (DataAccessException | InvalidDomainObjectError) →
PersistenceOperationsError`: an *unreadable* row and a *corrupt* row are one category — "the repository could
not deliver a valid aggregate" — wrapped into the port's own currency, while a stray bug (a
non-`DataAccessException` runtime) rides raw to the use case's catch-all, un-mislabelled.

This **un-defers a parked option**, and the un-defer is the lesson. We had earlier *shared* the one gate —
let a corrupt row ride to `presentError` as a raw `InvalidDomainObjectError` — and weighed "rewrap reads into a
distinct `PersistenceOperationsError`" only to defer it on two grounds, both now answered. *"The wrap is not a
one-liner"* — it **is** (`| InvalidDomainObjectError`, because the failure surfaces out of the
MapStruct-generated `toDomain` *whole*, not constructor-by-constructor). *"No real need to tell the two apart"*
— the labeling analysis **is** the need: lumping an integrity fault together with a *presented* input outcome
under one `InvalidDomainObjectError` is the more dangerous conflation, and would let a future use case that
wraps a read in an `InvalidDomainObjectError` gate mislabel a corrupt row as invalid authored input. Both types
stay unchecked, like `PersistenceOperationsError` (§5), so either composes through the transaction template and
triggers rollback on the rare construction that runs inside one. (Promotion candidate, flagged not promoted:
*a reconstitution failure is an integrity fault, wrapped into the persistence port's currency — never the
input gate's type*.)

**The "single source of truth" conundrum — its first resolution, then its dissolution.** An always-valid id
VO appears to need to validate the very character pattern an id *generator* emits, and two drifting copies of
the pattern are unacceptable. The first resolution **untangled the two concerns the pattern conflates.** The
*prefix* (e.g. `scn`) is a domain concern — it says which kind of identity this is — so the model owns it. The
*body alphabet and length* are an encoding artifact of the generation scheme, **not** a domain invariant; the
model validates **prefix + structure only** (non-empty, single token, no whitespace) and never the charset,
because "always-valid" constrains *domain* state and the charset is not a domain rule. Reinforced by authored
seed ids being *logical short keys* (`scn1`), deliberately distinct in body from generated ids — the model
could not own a single body-format even if it wanted to.

The original framing then said the *generator adapter* owns the alphabet privately, because "the dependency
rule forbids the model from knowing the generator." **That premise is now gone (#53): the model mints its own
ids by rolling its own `Dice` — there is no external generator for it to be forbidden from knowing.** Once
randomness became a domain `Dice` (§4), an id minted from dice reaches nowhere outside the hexagon, so the id
*output port* was the remaining inconsistency and was deleted. The split *survives the dissolution* but both
halves now live inside the model: the **prefix** on each id VO (`ItemId`), the **body encoding** (alphabet +
length) in a single `Ids` helper (`core/model/id/`) — the in-hexagon successor of the generator adapter, and
the one knower of the alphabet. So "exactly one knower, no drift" holds *verbatim*; the knower simply moved
from the adapter to `Ids`. It is arguably **cleaner**: there is no longer a hexagon boundary to split the
prefix from the alphabet across — the prefix/alphabet split the §2 conundrum invented to cross the boundary
safely is now an *intra-model* cohesion choice, not a boundary device. The validation rule is unchanged
(prefix + structure only, so authored `scn1`/`itm1` still pass); the alphabet is a *generation* concern owned
by `Ids`, never a *validation* rule — generation ≠ validation, still. The parked alternatives all fall away:
(a) a shared `IdFormat` consumed inward by an external generator is moot — `Ids` *is* the in-core minter, not a
contract handed outward; (b) validating authored-id format in the YAML adapter is still held in reserve. (The
NanoID dependency is gone with the adapter: NanoID *was* "roll a uniform RNG N times over an alphabet," which
is exactly what `Ids.randomBody(Dice)` now does in the model — identical algorithm, identical probabilistic
uniqueness, one fewer dependency.) (Promotion candidate, flagged not promoted: *when a domain `Dice` exists,
identity minting is a domain concern too — an `Ids` helper fed the dice replaces the id output port; the
single-knower-of-the-encoding invariant is preserved by relocating the knower into the model, not by hiding it
behind a port.*)

**The remaining hazard, parked by emergence.** A pure dice-rolled id carries only *probabilistic* uniqueness —
exactly as NanoID always did, so nothing regressed. But the deleted port's contract had reserved a future
("sufficient entropy, *or a sequence* — its private choice") that was the natural seam to evolve uniqueness
(collision-retry, a monotonic sequence) *without the model knowing*. Folding minting into the model gives that
up: a future collision-*checked* scheme would need the model to consult the store (a port) or would
re-introduce a generator port. Today this is moot — init-time spawn-if-none against an empty world, no
collision surface — so by emergence we don't keep the seam on speculation. The concrete trigger to revisit:
**runtime minting against a populated store** (`take`, drops, NPC spawns) *if* probabilistic uniqueness ever
stops being enough. A welcome mind-change then, not a regret now.

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
item might otherwise never be exercised. **Runtime-generated identity** also arrived here: item *instances*
are the first identity the system itself mints (authored/configured ids like `scn1`, `plr1` are never
generated; one template spawns several instances, each needing a distinct id). It first landed as an *id
output port* (NanoID adapter, the body alphabet private to it) — then, once `Dice` existed, was pulled inside
the hexagon (#53): `ItemId.mint(Dice)` composes the prefix with a body rolled by `Ids` (the model's one
knower of the alphabet). Either way the prefix lives in the VO and the alphabet has exactly one knower, so
nothing drifts — the move only changed *which side of the boundary* that knower sits on (see the dissolution
above). That also retires parked alternative (a): authored ids are not generated-shape, so a shared
`IdFormat` still earns nothing.

**The item aggregate boundary — by identity, not containment.** `[thread #1]` `[thread #3]` An item is its
*own* aggregate, not a child of `Scene`. The test is invariants: there is no scene↔item consistency rule that
must hold inside one transaction (a scene is not made invalid by what lies on its floor), and an item's
location is mobile — on the ground now, in a player's or NPC's possession once `take` arrives. So `Item`
references *where it is* by `SceneId`, exactly as `Player` references `currentScene` and `Exit` its target;
"the items in a scene" is a *query* against that reference, never a collection the scene holds. Containing
items inside `Scene` would both invent a false invariant and force every pickup to rewrite the whole scene
aggregate — the contention seed of `[thread #3]`. Minted minimal, like `Player`: id, location, two
descriptions, nothing speculative.

**`take` cashes the "mobile location" prediction — a sealed `Location` VO, not a nullable holder.** `[thread #1]`
`[thread #3]` The boundary above *predicted* this ("on the ground now, in a player's possession once `take`
arrives"), and `take` collected: `Item.location` generalized from a bare `SceneId` to a sealed `Location` —
`OnGround(SceneId)` | `HeldBy(PlayerId)`. The *shape* is the always-valid discipline answering a modelling fork.
A nullable `holder` beside the `SceneId` would split **one** concept — where the item is — across two fields
bound by an exactly-one-set rule the constructor must police; the sealed VO makes that XOR **structurally
impossible** (one case or the other, never both, never neither), and a pattern-matching `switch` over the two is
exhaustively compiler-checked, so the future third case (inside a container, a corpse) cannot be silently
forgotten at a mutate or persist site. The holder stays a `PlayerId` until NPCs force the generalization — the
same emergence beat that kept `Player` to one field. The rejected third option — an inventory *list* on
`Player` — is the **containment** error this very section argued against for `Scene`↔`Item`, seen from the
other side: it would invent a false `Player`↔`Item` invariant and rewrite the whole player aggregate per
pickup. (Promotion candidate, flagged not promoted: *model a value with a closed set of mutually-exclusive
shapes as a sealed type, not co-existing nullable fields plus an XOR guard — the type makes the invariant
structural and the exhaustive `switch` makes the next case unforgettable.*)

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

**Provenance is the real discriminator — and the failure currency falls out of it.** `[thread #2]` Stepping
back from the three shapes above, the line that actually decides *valid model out vs invalid-capable carrier*
is **not** driven-vs-driving (the seed is driven yet returns a carrier) but the **provenance / trust of the
data**. The driven-adapter exception-wrapping work made this precise by asking one question of each port —
*what does a failure to produce a valid domain object mean here?* — and the answers split cleanly:
- **Valid-by-provenance (persistence).** The domain wrote this row through the gate; reconstitution is a
  defensive corruption check, and a row that fails it is an **integrity fault**, never a presented outcome
  (nobody shows a player "your saved scene has a blank name"). So the **model** is the natural return type, and
  a reconstitution failure is wrapped into the port's `PersistenceOperationsError` (§2) — distinct from any
  domain-input invalidity.
- **Untrusted-external (the YAML seed).** Invalidity here *is* a first-class, **presented** domain outcome the
  use case must test and branch on. So the carrier must be able to *hold* invalid state, construction is
  deferred to the use-case gate, and invalidity flows as a *domain outcome* — never wrapped as an infra fault.

So the dichotomy is not a stylistic wart you tolerate but a **consequence**: the carrier type and the failure
currency are the *same decision seen twice*, both read off provenance. The one residual asymmetry — a
persistence adapter must *have an opinion* about a reconstitution failure, while the YAML adapter has none
because it never constructs — is not a leak: owning "I couldn't reconstitute a valid aggregate" is the same
job as "reconstitute the aggregate," i.e. the repository pattern earning its keep. We did consider relegating
persistence mapping to the use case (returning flat DTOs, hand-built into models like the seed's `*Entry`) to
*remove* the adapter's construction step and so dissolve the exception-labeling tension — and **rejected it**:
it guts the repository pattern, forces hand-rolled graph reassembly per aggregate with no MapStruct, and the
§3 testability dividend that *justifies* the carrier on the input side is **absent** here (corrupt persisted
data is never a presented outcome to test against). The labeling fidelity that refactor would buy costs **one
line** of union catch instead (§2). (Promotion candidate, flagged: *provenance, not hexagon side, decides the
boundary currency; the carrier type and the failure currency are one decision*.)

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
the resolution is the lesson: the random rolls run *outside* the transaction (a pure in-memory construction
with no persistence effect), and only the saves sit inside it, while the use case stays deterministic under
test even though production spawns at random. The single success also carries the items *spawned this run*
(empty on an idempotent re-run) — generated effects, reported distinctly from the authored scenes, which are
the full world on every path.

**The source of that non-determinism is a *domain* capability, not an output port — this reverses the earlier
"entropy is an output port" stance.** `[thread #2]` This section first concluded that the entropy was an
**output port** (`RandomnessOperationsOutputPort`), kept *distinct* from the opaque-identity port
(`IdGeneratorOperationsOutputPort`) as "two randomness roles on two ports." That has been reversed: game
randomness is now a domain type, **`Dice`** (`core/model/dice/`), with `roll(Chance) -> boolean` and
`pick(List) -> T` — and `RandomnessOperationsOutputPort` and its JDK adapter are deleted. **For an RPG, dice
are domain, not infrastructure.** The model already owned the probability *semantics* — `Chance`,
`SpawnRule.rollPlacements`, `DayPhase.pickMessage` — and only the raw entropy bit (`nextDouble()`) ever reached
out through the port; completing the ownership (the model rolls its own dice) is the DDD-faithful framing. It
is also the **cardinality rule taken to its conclusion** (clean-ddd-core → *Where inter-aggregate business
rules live*): *value when read once, supplier/source only when the model owns the cardinality.* The spawn loop
draws N times — the model owns the cardinality — so handing it a *port-closure* (`randomnessOps::nextDouble`)
was the half-measure: own the source as a domain collaborator, don't smuggle an effect-function over an adapter
into the model's loop. `Dice.pick(List)` also *absorbs* the scale-and-clamp uniform-selection mechanic that
`SpawnRule.pickScene` and `DayPhase.pickMessage` had each duplicated, and `roll(Chance)` keeps `Chance` (now in
`core/model/dice/` beside `Dice`, which also breaks the `item`↔`dice` package cycle `roll(Chance)` would
otherwise create) as the pure odds authority it asks.

**Determinism — the port's whole justification — survives the reversal, achieved with a domain abstraction
instead of a mocked port.** A **seeded** `Dice` (`SeededDice`) gives deterministic tests (and opens a
reproducible-world-from-a-seed feature later — noted, deferred; no config-seed is wired now), while production
uses a thread-safe `SystemDice` (`ThreadLocalRandom.current()` per draw — safe to share across the boot/seeder
and ticker threads that roll dice). Tests script a tiny `ScriptedDice` (fixed roll booleans + pick indices),
which reads cleaner than computing what a seed produces and keeps the exact-placement / draw-ordering
assertions. The move also **avoids a global-state trap**: a "domain service over `ThreadLocalRandom.current()`"
would put global mutable state in `core/model/` (a Clean-Arch smell); an injected/seedable `Dice` keeps the
dependency explicit and confines the entropy to one clearly-labelled `SystemDice`.

**The principled line — and the asymmetry with the time port is the lesson, not an inconsistency.** Wall-clock
*time* is the world *outside* the game, delivered as a **value read once** in the shell, so it stays an
infrastructure port (`GameTimeSourceOutputPort`); *dice* are *part* of the game, so they are a domain
capability the model owns. **Id minting followed the dice across the same line — and that reverses a stance
this section held for exactly one session.** The first cut of this passage argued the **id generator stays a
port** (id-encoding is infrastructural, the NanoID alphabet the generator's private concern), so
`ItemTemplate.spawnInto(Supplier<ItemId>, Dice)` *deliberately mixed* an infra-closure (ids, behind a port)
with a domain collaborator (dice). The very next session (#53) judged that the inconsistency, not the design:
that `spawnInto` signature was the **only** place a camouflaged output-port closure still crossed into the
model. Once `Dice` is a domain capability, an id *rolled from dice* reaches nowhere outside the hexagon — and
NanoID was only ever "roll a uniform RNG N times over an alphabet," the same algorithm — so the id port earned
nothing the model couldn't do itself. It was deleted: `ItemTemplate.spawnInto(Dice)` now takes *only* the
dice, and `ItemId.mint(Dice)` rolls its own body via the in-model `Ids` helper (§2's dissolution). The
remaining asymmetry is clean and *purely* time-vs-game: wall-clock time stays a port (world outside, a value
read once); dice — *and the identities minted by rolling them* — are domain.

**Sharpened boundary rule, now fully realized** (superseding the old "entropy is an output port" *and* the
one-session "id generator stays a port"): *no infrastructure crosses into the model as a callable; the model's
own domain collaborators (`Dice`) are fine; ports deliver values read in the shell.* With the id supplier
gone, this isn't just stated — there is no longer **any** port-closure threaded into a model method. The lesson
that the old "two randomness roles on two ports" framing got wrong also generalizes: identity and dice were
never *one role split in two ports*; they were two concerns mis-filed as ports, and both turned out to be the
*same* domain capability (rolling) once examined. (Promotion candidate, flagged not promoted: *when an RPG has
a domain `Dice`, both stochastic outcomes and generated identities are the model's own — neither is an output
port; determinism is preserved by a seeded `Dice`; the discriminator is "is this part of the game or the world
outside it," and the acid test is that no infrastructure crosses into the model as a callable.*)

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

**The first *multi-interaction* use case — `examine`, and where conversational state lives.** `[thread #4]`
`[thread #3]` `look <target>` / `examine <target>` is the project's first goal whose presentation in *one*
interaction sets up a *follow-up* interaction that completes it. The player names a target; if the fragment is
ambiguous the system offers a numbered menu and the player picks "2". The pivotal question — *where do we
remember the offered list so a later "2" resolves?* — answers itself once the list is named correctly: it is
**conversational/parser state, a delivery-mechanism concern (§9), so it lives in the driving adapter
(`AffordanceContext`, a terminal *resource* per §7), never in the core.** The candidate homes for that buffer
all fail except that one — the prototype use case (fresh per interaction), the ad-hoc-`new`ed presenter
(per interaction), a domain aggregate (no invariant, no identity — UI scratch), and the database (wrong
lifetime: the prompt must survive *to the next line*, not across `bye`/restart) — leaving the
session-lifetime terminal resource as the only fit. The decisive payoff is concurrency-honesty (`[thread #3]`):
the buffer remembers **identities, not positions**, and the follow-up interaction
(`playerExaminesChosenCandidate`) is id-precise and **re-validates against live state**, so a candidate an NPC
removed between offer and pick surfaces as an honest "no longer here" outcome rather than a number silently
pointing at a different item.

**But the core is oblivious to the *form*, not to the *conversation* — and a controller that presents is the bug
that hides the difference.** `[thread #4]` A first cut had the console *resolve* the pick against the buffer and
*present* the "nothing pending" / "no such option" failures itself (raw `printLine`s), re-calling a
`playerExaminesItem(id)` interaction only on a hit. That is the classic Clean Architecture violation twice over:
the controller *decides* (hit vs. miss) and *renders* an outcome, bypassing the presenter every other outcome
goes through. The fix forces a sharper line than "conversational state is infra, core is oblivious": the core is
oblivious to the **form** of the offer (the numbered menu, the styling, the buffer) but it **owns the
conversation** — *presenting* every selection outcome (described, nothing-offered, no-such-option, no-longer-here)
is the use case's prerogative. So selection becomes a real examine interaction,
`playerExaminesChosenCandidate(ordinal, offeredTokens)`, and the console *only detects the selection intent and
delegates*. The earlier obliviousness was only purchased by letting the controller present — the very thing being
fixed; honoring "no presentation in controllers" necessarily pulls the *conversation* (not the *form*) into the
core. This makes examine a textbook multi-interaction use case: a stateless use case whose between-interaction
state lives outside it and is **supplied per interaction**, each interaction loading what it needs.

**Dependency rejection picks the carrier: the controller hands the offer in as a *value*, not a *port*.**
`[thread #4]` Once the use case must resolve the pick, it needs the offered set — so either it reads a new
output port, or the controller (the imperative shell) hands it the remembered tokens as a parameter. We chose the
value: `playerExaminesChosenCandidate(int ordinal, List<String> offeredTokens)`. This is the methodology's own
"functional core, imperative shell" / dependency-rejection rule generalized from domain rules to conversational
input — *pass a value when the input is read once, unconditionally; reach for a port only when the core owns the
cardinality.* The offer is read exactly once per selection, so a value beats a port: no new port, the
`examineUseCase` bean's dependencies are unchanged, and the core gains no "selection buffer" abstraction — it is
simply told "these were offered, here is the pick." The shell resolves its own I/O (its `currentOffer()`) and
hands the core the result; the core decides and presents. Robustness is unaffected because the chosen token is
re-validated against live state regardless of what the offer claimed, so a stale or wrong list cannot mislead —
worst case it resolves to "no longer here." (Promotion candidate, flagged not promoted: *conversational state for
a multi-interaction use case is supplied to each interaction as a value by the driving shell — dependency
rejection — not fetched by the core through a port, when it is read once per interaction; and presenting the
outcomes of that conversation is the use case's prerogative, never the controller's.*)

**The buffer holds a raw id *token* (`String`), not the `ItemId` model VO — and the reason sharpens "primitives
inward".** `[thread #2]` The first cut had `AffordanceContext` trade in `ItemId`. The objection that corrected
it: **trust flows with control direction.** The core hands its models *outward* to the driven adapters it drives
(presenters, the persistence gateway reconstituting aggregates), so those are legitimately model-aware; but a
*primary* adapter — the console loop that **reads** this buffer — is the "primitives inward" edge (§6), where
the core owns value-object construction at the single gate. Every other inbound console path already obeys this
(`move(String exitName)`, and `PlayerOperationsOutputPort.currentPlayerId()` returns a `String` on purpose), so
a driving adapter holding a model would be a lone exception with no precedent — all the model-in-infra cases are
on the *driven* side. The decisive move is not merely tolerable but *more* faithful: do the `ItemId → String`
flatten on the **driven** side, in the presenter, where the model legitimately lives (rendering an identity to a
stable token is a presentation act, like rendering a description to styled text); the buffer then carries a pure
correlation token and the primary adapter stays model-free. A model type would have bought nothing here anyway —
the token is **relay-only** (written once by the presenter, read once by the controller, forwarded straight into
the input port), and a type earns its keep by guarding *operations*, of which there are none; its only effect
would be to drag the core onto the primary adapter. This is the domain-agnostic `Console` precedent (§7) applied
on the axis that matters — *a shared terminal resource touched by the driving loop is kept model-free* — not the
"does it segregate behaviour" axis. The forward fit confirms the cut: when `look <exit>` adds a second
examinable kind, "how do I tag a selection token by kind?" is answered **inside infrastructure** (a tagged token,
or separate buffers), explicitly *not* by reaching for a core `Examinable` type — further evidence the buffer
should not touch the core at all. (Promotion candidate, flagged not promoted: *the carrier type at a
driving-adapter edge follows the trust direction — primitives inward, even for a value that began life as a
domain VO on the driven side; flatten on the trusted side, relay a token on the untrusted one.*)

**A multi-interaction conversation has two layers — and the *continuation* logic belongs to the infra one, not
the use case and not the controller's body.** `[thread #4]` `examine`'s disambiguation raises a lifecycle
question the single-interaction use cases never did: *what ends the pending offer?* The menu persists across
turns (it is conversational state, above), so something must decide when a later line still answers "which
one?" versus moves on. The reflex is to read that as *conversation logic* and recoil from finding it in the
driving adapter (`!(command instanceof SelectCommand) → clear`), because "the conversation is the use case."
The recoil is half-right, and splitting the two halves is the finding. A conversation has a **semantic** layer
— the dialogue's steps, decisions, and outcomes (is the target ambiguous? menu-or-describe? re-validate the
pick? which outcome?) — which *is* the use case, and which already lives wholly inside `ExamineUseCase`: the
controller decides nothing and renders nothing, it hands `currentOffer()` in as a value and the use case
presents every outcome (above). And it has a **modal input-routing** layer — given a *line-oriented* channel,
which interaction does the next raw line invoke while an offer is open — which is the *shape of the input
device*, not the dialogue: a GUI barely has it (the menu is buttons; clicking "2" invokes selection directly),
a voice UI has another form again. The `clear` predicate is purely this second layer.

**That layer is irreducibly infrastructural — it cannot move to the core even in principle, and it is the same
category of logic the adapter already owns.** `[thread #4]` The continuation predicate quantifies over
`Command` types (`SelectCommand` continues the examine offer; a future card-play continues a tavern game), and
the `Command` set is the parser's output that §9 deliberately keeps out of the core (Guidance presents
*abstract* outcomes precisely so no command vocabulary crosses inward). "A bare number continues the examine
conversation" is therefore *unspeakable* in the core without importing the very vocabulary we engineer to
exclude. And `CommandParser` is already logic in the primary adapter (tokenizer, verb registry, intent
production) that no one calls a violation, because parsing *is* the adapter's job — a conversation mode is just
a **context-sensitive parser**, reading the next line differently because an offer is open. Clean DDD evicts
*business decisions* from adapters; it does not evict *delivery-mechanism logic* — parsing, rendering,
input-mode management *are* the adapter. So `clear` is not use-case logic leaking outward; it is
input-interpretation, sitting where the parser already sits. Proliferation (combat, spells, a tavern card
game, trading, each with its own continuation rule) is then an *intra-infrastructure* structuring problem, not
a boundary breach, with an infra-local answer that keeps the dispatcher flat: give each conversation a small
infra **mode object** owning its pending affordance *and* its own "does this line continue me?", and reduce
the dispatcher to generic plumbing — an active mode gets first crack at the line, on refusal it is abandoned
and the line falls through to normal parsing. New conversations are new mode objects; `ConsoleSession` never
grows an `instanceof` tangle. This is the classic modal-input-stack / REPL structure. The core is *not* absent
from the lifecycle — it *opens* the pending state by presenting the ambiguous outcome (`presentAmbiguousTarget`),
the presenter arms the mode as it renders the menu (the core-signal → infra-realization handoff, above), and
infra alone decides which keystroke continues it. By emergence (§2) the framework waits: one conversation
today, so the one-line predicate stays; the mode object earns itself at conversation #2. (Promotion candidate,
flagged not promoted: *a multi-interaction conversation splits into a semantic layer owned by the use case and
a modal input-routing layer owned by the delivery mechanism; the continuation predicate is the latter,
irreducibly infra because it quantifies over the command vocabulary the core excludes, and scales by
per-conversation mode objects under a generic dispatcher — a context-sensitive parser, not use-case logic in
the adapter.*)

**Two other homes for the continuation logic were weighed and rejected.** `[thread #4]` *Moving `clear` into
the presenters* — only `presentAmbiguousTarget` arms, every other present-method clears — restores a tidy
"presenter writes / controller only reads" symmetry but fails on the discriminator: clearing keys on the
*input intent* (select vs. anything-else), which the presenter cannot see, and the shared success outcome
`presentItemDescription` is reached by *two* paths (designate-by-description and designate-by-selection) whose
clearing needs are opposite — clear it and repeatable selection plus retry-after-a-bad-pick break; don't and a
fresh `examine` leaves a stale offer armed. A presenter method cannot tell the paths apart, and splitting the
converged outcome to carry clearing semantics would re-introduce, for an infra-lifecycle reason, the very
distinction "variation vs. extension" (below) was glad to collapse. *Reifying a Spring custom conversation
scope* (`AffordanceContext` as a `@Scope("conversation")` bean, arming as scope-begin, a `@PreDestroy` home
for end-logic) was rejected twice over: it commits the design to a framework SPI at exactly the layer Clean
Architecture keeps framework-free, and it founders on a **push/pull impedance mismatch** — Spring scopes are
*pull-based*, the container instantiating on first access within an *ambient* lifecycle (request thread, HTTP/
STOMP session) and destroying at its end, whereas a console conversation is *push-based* (the presenter pushes
content, the dispatcher signals end) with *no* container-recognized ambient unit to key on. You would
hand-build the id, holder, and store and then wear Spring's `Scope` interface as an awkward veneer, for a
`@PreDestroy` that would hold almost nothing (discarding the buffer is what scope-end already does). The
lightweight realization of the same concept — a plain object with named `begin`/`end` — needs none of that,
and is exactly what the mode object above already is. The through-line of both rejections: the continuation
decision keys on a fact (the parsed input intent) that only the dispatcher holds, so neither a downstream
presenter nor an upstream container scope is the right owner — the dispatcher is, exercising it *generically*
over per-conversation mode objects rather than in a growing switch.

**Variation vs. extension — two interaction methods, one goal.** `[thread #4]` The Cockburn structure is the
finding worth recording. The "more than one match" branch is an **extension** — entered on a *condition* (the
fragment is ambiguous), opening a sub-dialogue. The player's act of picking by number is a **variation** of the
main scenario's "designate the target" step: same target kind, same goal, a different *modality* (an ordinal
into an offered set vs. a free description). The proof it is a variation and not a separate goal is that both
designations end at the *identical* presentation — `presentItemDescription`. So `ExamineUseCase` carries two
interaction methods (`playerExaminesTarget` by description, `playerExaminesChosenCandidate` by picking from the
offer) that converge on one outcome; the extension is precisely what *makes the variation reachable* (you can
only pick "#2" from a list you were shown), and the `AffordanceContext` is the bridge between them — written by
the presenter, handed by the controller into the second interaction. Resolving a chosen pick to a concrete
identity is *internal* to that interaction (kept inline, not a public designation of its own — and inline
rather than a private helper, so every checkpoint's present-and-return stays visible in the one method): the
only driver that would designate by raw id is a future GUI clicking a row, which would earn its own interaction
then (emergence), and the public variation today is the by-selection one the terminal actually performs.

**ISP forces the orient presenter port to re-split — and port granularity tracks *outcomes a consumer can
present*, not the shared prologue.** `[thread #2]` `examine` opens with the same `orient` prologue as
`look`/`move`, so it genuinely needs that subcase's two not-found outcomes — but it renders an *item*, never a
scene, so it would never call `presentScene`. Leaving `presentScene` on `OrientPlayerPresenterOutputPort` would
force the examine presenter to implement a method it can never receive: an **Interface Segregation violation**.
So `presentScene` was re-split *down* into a new `CurrentScenePresenterOutputPort` (the capability `look`/`move`
share and `examine` does not), and the orient port shrank to exactly what the subcase itself presents. This
**re-separates what the `move`-era coincidence had collapsed**: the cluster was first lifted as three outcomes
because its only two consumers then both ended by rendering a scene; `examine` breaks the coincidence and
proves the sharper rule — **presenter-port granularity tracks the set of outcomes a consumer can actually
present, not the prologue they happen to share.** Sharing the *opening* (the subcase) and sharing the *ending*
(scene rendering) are two different axes; conflating them over-couples the third consumer the moment it shares
the opening without the ending. The renderer side mirrors the port split (the not-found rendering extracted
into a shared `OrientRenderer`, `presentScene` rendering left on `CurrentSceneRenderer`, a new `ItemRenderer`
for examine), so composition tracks the interface segregation. (Promotion candidate, flagged not promoted:
*port granularity follows distinguishable outcomes per consumer; a shared prologue is not a reason to share an
ending.*)

**Why the candidate *ordering* lives in the presenter, not the use case.** `[thread #2]` The disambiguation
outcome has two faces of one affordance — the visible numbered menu and the latent number→identity mapping —
and both must agree on the order, so the order is decided in exactly one place: the **presenter**. Two reasons.
First, **ordering is a presentation concern**: which candidate is "1." and which is "2." is a property of the
rendered menu, not of the domain; the use case's outcome is "these things are ambiguous" (a *set*), and
imposing a display sequence on a set is rendering — sorting it in the use case would push a delivery-mechanism
detail (that *this* UI numbers a list) into the core, the very leak we keep the whole affordance out of the
core to avoid. Second, **a single source of order cannot drift**: the presenter sorts once, hands the ordered
list to the renderer to number 1..N, and offers the *same* ordered identities to the `AffordanceContext`; if
the use case sorted and the presenter re-derived the numbering, two places would have to agree forever. So the
use case passes the matches *unordered* (repository order) and the unit test asserts membership, not order —
pinning the division of labour. The presenter stays humble throughout: the use case already decided *that* the
match was ambiguous (by calling `presentAmbiguousTarget` rather than `presentItemDescription`); the presenter
only renders that outcome and records the numbering it produced.

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

**A use case never mints-and-throws to its own catch-all.** `[thread #4]` An anticipated absence — an
`Optional` the flow checks (a missing world-singleton, a dangling reference) — is a *presented outcome*:
branch, call a dedicated `present*`, and `return`. It is **never** `orElseThrow(() -> new <JdkException>())`
routed to the outermost `catch (Exception) → presentError`. That shortcut conflates three doctrinally-separate
things: it expresses an *anticipated* outcome by *throwing* (against this section's "express outcomes by
presenting"); it injects a *raw JDK technical exception* into the core — the very thing the named
`InvalidDomainObjectError` moved away from (§2) and the wrapping discipline forbids (only adapters mint
port-error types, by wrapping their framework's faults — §3/§5); and it launders a known condition through the
*humble catch-all* (`ErrorHandlingPresenterOutputPort.presentError`) reserved for the unforeseen and for
already-wrapped port faults propagating from below. It also quietly erodes **unidirectional flow**: a
self-thrown exception is one narrowed `catch` (or one moved line) away from escaping to the controller,
whereas branch-and-present *structurally cannot* leak. The discriminator for every `Optional`/absence in a use
case: classify it as **outcome** (present + return) or **fault-from-below** (a *named, wrapped* port error an
adapter already threw, which simply propagates to `presentError`) — there is no third "mint a raw exception
and catch it myself" category. The surfacing case: `AskForTime`/`SuspendGame` first reached for
`findClock().orElseThrow(IllegalStateException::new)`, defended by a *post-hoc* comment ("an integrity fault,
not a presented outcome") — the rationalization-after-the-shortcut was the tell — and was corrected to
`presenter.presentGameNotInitialized(); return;`. **Code smell to grep:** a bare `throw new
IllegalStateException/IllegalArgumentException(...)` or `orElseThrow(() -> new <jdk-exception>)` inside a
`core/usecase` class — a mechanical lint that catches this without depending on reviewer vigilance. (Promotion
candidate for `clean-ddd-core`, flagged not promoted.)

**"Is the game initialized?" is not one outcome — readiness decomposes into per-aggregate clusters.**
`[thread #2]` When the clock-readiness check arrived, the tempting generalization was a single
`presentGameNotInitialized()` that every play use case calls through one shared gate. That is a **false
superset**: a use case only ever checks the precondition on *the state it actually reads* (the `orient`
prologue checks player + current scene because `look`/`move` read those; `now`/`bye` check the clock because
they read that), and *no interaction reads the whole world*, so none can honestly evaluate "is the game
initialized." A single gate would also collapse distinctions `orient` deliberately keeps (player-not-found ≠
dangling-current-scene). So readiness lives as **sibling presenter clusters keyed to shared sub-state** —
`OrientPlayerPresenterOutputPort` for player+scene, a small `ClockReadinessPresenterOutputPort` (one outcome,
`presentGameNotInitialized`) shared by the two clock use cases — each extending `ErrorHandlingPresenterOutputPort`
*directly*, by interface extension, exactly as the orient cluster is. No common `GameReadinessPresenterOutputPort`
super-interface is hoisted above them: the clusters share no *method* (player-not-found is not clock-not-ready),
so a super would be an empty marker or force the collapse just rejected — it waits for a third cluster that
genuinely shares an outcome (emergence). And the *check logic* is not subcased: at one line per use case it
stays inline (the `orient` subcase earned itself with a multi-step prologue and 2+ consumers; a one-line clock
load does not). This sharpens thread #2: **output-port granularity tracks *(audience × distinguishable
outcomes)*** — shared outcomes factor by interface extension keyed to shared sub-state, never a false superset
and never a grab-bag god-presenter. The same cut explains why the **producer** and **consumer** sides of the
same invariants stay on separate ports: `InitializeGame` reports authoring/consistency failures (a dangling
exit target, an unknown starting scene) to an **operator/log** audience at *build* time, while the play use
cases report readiness gaps to a **player** audience at *play* time — same invariant guarded twice, two
audiences, two homes; merging them would be the same overload the system-seeder's logging presenter was kept
apart to avoid (above). (Promotion candidate, flagged not promoted.)

**The `select` subcase — sharing the *disambiguation* dialogue, orthogonally to `orient`.** `[thread #4]`
`take`/`drop` will disambiguate a target exactly as `examine` does, so the dialogue is factored into the
project's *second* subcase — and the factoring sharpened what a subcase *is*. A first cut handed the subcase
its candidates as a `List<Item>` value and let the parent fetch them; that was **rejected**. A subcase is not
a pure function over inputs the parent prepared — it is the **owner of a reusable scenario slice and its
outcomes**, exactly as `orient` owns the whole "where does the player stand" slice (holding and calling its
own ports, not handing back a half-resolved result). "Resolve which thing the player means among the available
candidates" *includes deciding what those candidates are*, so the select subcase **holds the item port and
provisions its own candidates**; the parent passes only the coordinate and drops its own item dependency
(`examine` no longer touches persistence at all). Pushing the fetch up to the parent would fragment the shared
slice and re-duplicate it across `examine`/`take` — the very thing extraction removes. (It also tightened the
gate: provisioning *inside* the subcase lets the ordinal check run *before* any read, so a bad pick costs no
fetch — a small improvement over the parent-fetches sketch.)

**`select` ⟂ `orient` — composition, not the inheritance chain.** `[thread #2]` `[thread #4]` Disambiguating
*which* (intent) is orthogonal to locating *where* (geospatial): a card to play, a spell to cast, a combat
maneuver to choose involve no orientation. So `select` does **not** telescope `orient`, and
`SelectTargetPresenterOutputPort` does **not** extend `OrientPlayerPresenterOutputPort` — the parent composes
the two subcases *sequentially*, and one concrete presenter implements three **flat, narrow** ports (orient /
select / parent-outcome), bound per role by the composition root, with the parent use case's presenter field
typed to *only its own* outcome. This is the §6 composition-over-inheritance stance carried to the port layer:
a `Select extends Orient` chain would assert a false is-a (select is-not-a orient); flat composition asserts
none. The disambiguation outcomes (`presentNoSuchTarget`, the ambiguity menu, `presentItemNoLongerHere`, the
two selection-gate misses) moved *off* `examine`'s port *onto* the select port; `examine` keeps only its
terminal `presentItemDescription`. (Promotion candidate, flagged not promoted: *a shared sub-dialogue
orthogonal to the shared prologue is composed beside it, not nested under it; presenter ports compose as flat
narrow interfaces on one concrete presenter, never an inheritance chain that asserts a false is-a.*)

**Values between procedures; suppliers only into the model — the direction the "can of worms" hides.**
`[thread #4]` The provisioning question first reached for a `Function<…, List<Item>>` handed into the subcase —
a category error worth recording. §10's dependency rejection passes `Supplier`s to the **model** (the
functional core, e.g. `SpawnRule.rollPlacements`), never between use cases. A subcase is a **procedure** read
as a Jacobson requirements spec; passing it a function inverts control and hides a chunk of the procedure
behind a lambda, defeating the down-the-stripes readability the methodology exists to protect. The codebase
already obeyed the rule without naming it (`examine` hands the offered tokens in as a **value**; only the model
gets suppliers), so the discipline is: **values flow between procedures; functions terminate *into* the
model.** With provisioning owned by the subcase the question is moot anyway — the subcase fetches, the parent
passes a value coordinate. (Promotion candidate, flagged not promoted: *within the procedural shell, compose
subcases with values and sequential calls; reserve function-passing for the shell→model boundary, and even
there prefer a value unless the model owns the cardinality.*)

**A plain `SceneId`, not a one-field request DTO — and why the asymmetry with `OrientPlayerResult` is
principled.** `[thread #2]` The select subcase needs one coordinate (the scene to read), so it crosses as a
**plain `SceneId`**, not a `SelectItemRequest` envelope. This is the methodology's own input-DTO rule applied
honestly: *a DTO earns its place by carrying structure — a composite or a collection — not by wrapping a
scalar that could be a plain parameter* (the same call that gave `look` a bare `playerId` and no `LookRequest`).
The non-symmetry with `orient`'s returned `OrientPlayerResult` is the *same rule* giving opposite verdicts:
`OrientPlayerResult` carries two things (player + scene) — a composite, earned; a one-`SceneId` request would
not be. Matching the `…Request`/`…Result` names for symmetry's sake would violate the rule.

**One concrete subcase now; the Template-Method base emerges at provisioner #2.** `[thread #1]` `[thread #4]`
There is one provisioner today — items on the scene ground (`examine`, and later `take`, share it) — so there
is one concrete `SelectSceneItemSubcase` with a private `provisionCandidates`, *not* an abstract base with a
single subclass. When `drop` brings the **second** provisioner (an inventory, keyed on a `PlayerId`),
`provisionCandidates` is extracted onto an `AbstractSelectTargetSubcase` as a `protected abstract` hook — a
one-level **Template Method** whose concretes are genuine is-a target-selectors, the *legitimate* face of
inheritance that the composition-over-inheritance heuristic guards the flip side of (it forbids stealing
implementation through a base you are not a kind of; it does not forbid a true taxonomic specialization
varying one hook bound at wiring time). Crucially, the **abstract base and its generic context type are the
same deferred decision**: a request abstraction "to work generically" cannot be designed over one provisioner
without guessing (fat DTO? sealed hierarchy? generic parameter?); two real context shapes (`SceneId`,
`PlayerId`) make the right one obvious. So the plain-`SceneId` parameter and the concrete subcase are the
*matched* choice — concrete subcase, concrete input — and both generalize together when the second instance
lands; the localized cost (the select signature changes at that point) is the right time to introduce the
abstraction, not before. Residual tension named for later: today's `SceneId` context presumes a *grounded*
selection — a future non-grounded one (the card game) forces the generic context then, the same emergence
beat. (Promotion candidate, flagged not promoted: *composition-over-inheritance is not absolute — a one-level
Template Method varying a single hook, over genuine is-a subtypes bound at wiring time, is legitimate; defer
both the base and its generic input until the second concrete makes the generalization visible.*)

**`take` is the select subcase's first *writing* consumer — orchestration + a write tail, and no construction
checkpoint.** `[thread #4]` `take` is `examine`'s twin with a write: the *same* two interactions
(designate-by-description / designate-by-choice) over the *same* `orient`+`select` opening, converging on one
success (`presentItemTaken`), then the `move`-style write tail — mutate (`item.takenBy(player)`), one
`doInTransaction`, present after commit. Two things the implementation pinned. First, it **confirms the
select-subcase prediction above**: `take` shares the *scene-ground* provisioner `examine` already uses and adds
none, so it reuses `SelectSceneItemSubcase` unchanged and the `AbstractSelectTargetSubcase` base stays deferred
to `drop` (the second *provisioner*). Second — and quietly instructive — `take` has **no value-object-construction
checkpoint at all**: `orient` hands it a valid `Player`, `select` a valid `Item`, and `takenBy` takes the
already-valid `PlayerId`; the lone literal in the interaction (the target fragment) is consumed *inside*
`select`. So the §2 construction gate, ubiquitous in `InitializeGame`, is simply **absent** here — a use case
whose every input is already a domain object needs no gate, and inventing one (re-wrapping an id "to be safe")
would be ceremony. The checkpoint count tracks where *literals* cross the boundary, not a fixed per-use-case
ritual.

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

**The transaction port has its own failure currency — wrapped *narrowly*, the inverse of persistence.**
`[thread #3]` Like the persistence and seed ports, the transaction port translates its framework's exceptions
into a port type: a fault in *demarcation* — begin with no connection, a failed commit, an unexpected rollback
— surfaces from Spring as a `TransactionException` and is wrapped into the port's own
`TransactionOperationsError`, so no use case ever catches a raw `org.springframework.transaction.*` type. (A
distinct type, not a reused `PersistenceOperationsError`: failing to *demarcate* a transaction is the
transaction port's concern, not the persistence port's, and each driven port owning its boundary type is the
§3 symmetry.) The subtlety that sets it apart from persistence: the adapter catches `TransactionException`
**specifically, never `Exception`** — because the action running *inside* the transaction already throws a
port type (`PersistenceOperationsError`), so a broad catch would both *double-wrap* it and swallow the very
runtime exception that triggers Spring's rollback. This is the **exact inverse** of the persistence read
adapter, which catches *broadly enough* to also wrap a reconstitution `InvalidDomainObjectError` (§2): a
persistence adapter can widen its catch because nothing it calls is pre-translated, while the transaction
adapter must narrow its catch *because its callee is*. Two driven ports, opposite catch widths, one
principle — **wrap your own framework's faults, never your callee's already-wrapped ones**. (Promotion
candidate, flagged: *catch width is set by whether the callee is already translated*.)

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

**Atomicity is not isolation — the transaction boundary is not, by itself, the concurrency mechanism.** `[thread #3]`
The dawn/dusk watermark forced a sharpening of this section's earlier "keeping the read-then-write decision
atomic stops two concurrent X" framing, which quietly overclaims. A transaction gives **atomic rollback**; it
does **not**, at the default READ COMMITTED, serialize a read-then-write against a concurrent one. Two
observers can both `SELECT` the watermark, both see it pending, both `UPDATE` — the row lock serializes the
*writes* but the loser never *re-decides*, so it overwrites and both announce. The boundary alone has no teeth
here. The honest model is a **detector lens**: *a transaction provides atomic rollback when a **detector**
fires — a unique constraint, an optimistic version, or a `SELECT … FOR UPDATE` lock — and provides no
isolation without one.* Audit game-clean's three init guards through it: **seed-if-empty** is safe because the
**primary key** is the detector (the second insert of the singleton clock/log row duplicate-keys and rolls
back); **spawn-if-none** has *no* detector (distinct generated item ids), so concurrent inits would
double-spawn — latent only because init has one actor; **the watermark update** had no detector, so the
read-then-write "guard" narrowed the race window but never closed it. The boundary is still the use case's to
*demarcate* (that part is fully Clean-and-DDD-consistent — demarcation is an application concern in both); what
the boundary cannot supply is *conflict detection*, which is the **aggregate's** concern (the aggregate is the
DDD consistency-and-concurrency boundary). The two compose as layers; they were never substitutes.

**`DayPhaseLog` is the first aggregate *updated* under a guard — so it earns the canonical `@Version`, and
adopting it *removes* code.** `[thread #3]` This un-defers the `@Version` parked above, for the aggregate where
it finally fits. `DayPhaseLog` is updated (not inserted) under a dedup guard, so it is exactly where the
no-detector gap bites — and optimistic locking is the canonical detector. The striking part is that adopting it
**simplified two layers** rather than adding ceremony: with a `@Version` on the entity, `repository.save`
decides insert-vs-update from the version itself, so the adapter dropped the hand-rolled `existsById ? update :
insert` *and* its `JdbcAggregateTemplate`; and because the version is read with the aggregate and checked on
write, the use case dropped its inside-transaction re-read entirely — *read once outside, carry the version,
save version-checked*. The loser's stale write surfaces as `OptimisticLockingFailureException`, wrapped by the
adapter into a port-level `OptimisticLockingError`, which the use case catches and maps onto the outcome it
*already had* — `presentNothingToAnnounce` ("someone announced first; my goal is already met"). So the real
detector slotted into the exact shape the proxy had sketched, and the use case needed no new outcome.

**The version is carried *all the way to the model* — a deliberate §2 departure, and the layering it demonstrates.**
The §2-minimal instinct is to keep a concurrency column on the entity only. We chose the opposite here, knowingly:
the `version` rides on the always-valid `DayPhaseLog`, set by persistence on read and checked on write, *opaque*
to the domain (it carries but never interprets it) and **excluded from value equality** (two logs with the same
watermark are the same domain value). This is what makes the Clean-vs-DDD composition concrete and visible: the
**aggregate owns the version** (DDD's concurrency token, on the model), the **adapter owns wrapping the
framework's failure** into the port's own currency (a *sibling* of `PersistenceOperationsError`, not a subtype —
a concurrent loss is an expected outcome, not a fault), and the **use case owns demarcation and the reaction**
(catch the port error, present the domain outcome). For a singleton whose entire purpose is this one guarded
watermark, surfacing the token end-to-end teaches the mechanism better than hiding it; the §2 cost (a non-domain
field on the model) is paid with eyes open and bounded by the equality exclusion.

**The honest caveat: this is adopted slightly ahead of real contention — a chosen showcase deviation from
emergence.** There is still exactly *one* ticker thread (§8), so no two `AnnounceTimeOfDay` runs are actually
concurrent; the race the version guards against cannot occur today. Strict emergence (§2) would defer the
version until a second writer exists. We took it now because (a) it is the natural *teaching* site for the
detector lesson, and (b) — decisively — it **reduced** code rather than speculatively adding it, so it is not
the usual "speculative complexity" emergence warns against. This is the inverse of why `@Version` stays
deferred for `Player`: there it would add a wasted version-read or bleed a field into the model for *no*
simplification and no current contention. And it leaves a clean contrast in the codebase: the version-less
`GameClock` keeps its explicit `JdbcAggregateTemplate` insert/update; the versioned `DayPhaseLog` uses
`repository.save`. (Promotion candidate, flagged not promoted: *atomicity ≠ isolation; a transaction needs a
detector — unique constraint, version, or row lock — and optimistic `@Version` on the aggregate is the
canonical one, demarcated by the use case and reacted to as an outcome*.)

**The lock-catch generalized into `doInTransaction(action, onLockDetected)` — and the neutral `concurrency`
package it forced.** `[thread #3]` The reaction to a lost optimistic-lock race began as an explicit
`catch (OptimisticLockingError)` in the use case. The question was whether to lift it into
`TransactionOperationsOutputPort` as an idiom beside `doAfterCommit`. The first answer was *no*, on an
asymmetry worth recording because it is the right test for *which* reactions deserve machinery: `doAfterCommit`
earns its callback for a **correctness property the use case cannot satisfy itself** (never report success
before durability — commit is an unobservable lifecycle event, so the machinery *must* own it and forgetting it
is a silent bug), whereas a lock loss is **plain handling of an exception that already propagates**, so the
`catch` is the natural idiom and forgetting it falls *safely* through to `presentError`.

What overturned the *no* were three points, the decisive one structural. **(a) In addition, not a
replacement.** The idiom is opt-in: the signature stays two `Runnable`s (no error type in the contract), and
with a `null` handler — or via every other overload — `OptimisticLockingError` still propagates to the
outermost checkpoint. "Failure is expressed by throwing" is *preserved as the default*; the handler is sugar
over the rollback exactly as `doAfterCommit` is sugar over the commit, not a `rollback()`-style bypass. **(b)
The distinguish argument inverts the "hides control flow" objection.** A single outermost `catch` cannot tell
*which* of several `doInTransaction` blocks in one interaction lost its race; a per-block handler co-locates the
reaction with the very write that can lose — *more* visible at the point of failure, not less. (Today's
single-write announce does not yet need this, so it is adopted slightly ahead of contention — like the version
itself — against the combat future that will put several contended writes in one interaction.) **(c) The
interpretation stays with the use case.** The reaction `Runnable` is supplied *by* the use case
(`presenter::presentNothingToAnnounce`); only the catch *mechanics* move into the adapter.

The remaining objection — **wrong-port coupling** — refused to be waved away, and produced the real finding.
Having the *transaction* adapter catch a *persistence*-port error type is a genuine smell (the methodology
keeps `TransactionOperationsError` and `PersistenceOperationsError` deliberately distinct). Asking what a
concurrent-modification conflict *is* gives a three-owner split: the **aggregate** owns the *concept* (it
carries the `version`), **persistence** *detects* it (the store enforces the check, so the persistence adapter
— owning that framework call — raises it), and the **transaction** *rolls back* on it. It is **both** a
persistence and a transaction concern *by implementation* but **neither by concept** — a concurrency-control
signal. A type two boundaries both legitimately need but neither conceptually owns belongs on neutral ground,
so `OptimisticLockingError` moved to its own `core.port.concurrency` package that both adapters depend on
symmetrically — **raiser is not owner**. The smell was never the dependency *direction* (infra → core is fine);
it was a *misplaced type*, and it surfaced only when the reaction moved *into an adapter* — a use case may
freely know every port's vocabulary (orchestrating them is its job), but one adapter knowing a *sibling* port's
currency is the tighter coupling worth dissolving. (Promotion candidate, flagged not promoted: *when two
adapters both react to a boundary type that neither conceptually owns, house it in a neutral package both
depend on — raiser ≠ owner*.)

Two findings the adoption pinned down. **(B) beat (A), and (A) is mechanically broken.** Of the two sketched
shapes — (A) register the handler *inside* the transaction body like `doAfterCommit`, (B) pass it as a second
argument — (A) cannot work: the throw from the failing `save` short-circuits the rest of the lambda, so an
in-body registration *after* the write never takes effect; the handler must be established *before* the body
runs, i.e. it must be a parameter. That (A) cannot mirror `doAfterCommit`'s in-body shape is the structural
proof the two hooks are not the same kind of thing — the first *no*'s asymmetry, now expressed as an API
constraint. **A presenting handler must be the interaction's terminal act.** Unlike a propagating error (which
unwinds the whole interaction, *guaranteeing* exactly-once presentation), the handler *swallows* the loss and
returns control to the caller, which continues past the call — so a handler that *presents* must sit on the
interaction's last `doInTransaction`, the same discipline `doAfterCommit` carries (§4, presentation is
terminal). That is the cost the distinguish-benefit is paid against: the multi-block future motivating the
idiom is exactly where a swallow-and-continue handler could present twice if the discipline is ignored. The
predicted **`ConcurrencyConflictError` parent** (a unique-constraint race, a serialization failure are sibling
detectors per the detector lens) is left unbuilt — deferred by emergence until a second detector needs a home;
the `concurrency` package is its eventual address. The one behaviour still *not* generalized is a **retry**
policy (re-read, recompute, re-save) — intricate and footgun-prone (unbounded retry, re-presentation) — which
`AnnounceTimeOfDay` deliberately forgoes (the loser's goal is already met), so it too waits for a contended
aggregate that wants it.

**`Item` is the first aggregate contended *by design* — so `@Version` finally meets the contention it was
parked for, and the lost race is a *new* outcome with a read-side twin.** `[thread #3]` The `@Version` deferred
for `Player` and adopted slightly-ahead-of-contention for `DayPhaseLog` lands, on `take`, at an aggregate that
is genuinely contended: a ground item is grabbable by *any* actor in the scene — unlike a player's own position
(single-writer) or, later, its own inventory (single-writer for `drop`). So `take` is the project's first
select-then-mutate where two actors can really race, and the detector lens says the read-then-confirm in
`select` is only **advisory** (it narrows the window); the `@Version` is what **closes** it. The instructive
part is that the same player-facing fact — "someone got there first" — now surfaces at **two layers**, kept as
**distinct outcomes** rather than collapsed: the **read-side** advisory (`select` re-provisions for a menu pick
and finds the item gone → `presentItemNoLongerHere`) and the **write-side** authoritative (the versioned
`saveItem` loses the commit race → `onLockDetected` → a *new* `presentItemGotAway`). They are reachable on
*different paths* — the single-match `take rusty` does no read-side re-check, so the write-side guard is its
only net — which is exactly why they are two methods a renderer happens to collapse to one line, not one method
(the "distinct outcomes get distinct present-methods" rule applied to a concurrency pair). And `take` *adds* an
outcome rather than reusing one (`DayPhaseLog` mapped its lock-loss onto the existing `presentNothingToAnnounce`;
`take`'s is genuinely new), confirming the lock-loss reaction is shaped by **what the interaction's goal makes
of losing**, not by the mechanism. One persistence detail the round-trip pinned: Spring Data JDBC **increments
the version on insert** (a fresh `Item` at version 0 is stored at 1 — proven by `DayPhaseLogRoundTripIT`'s
insert-then-update succeeding), so the `V6` backfill of pre-existing rows uses version **1**, the
"already-persisted" state, so a `take` of a legacy item is an *update*, not a duplicate-key insert. (Promotion
candidate, flagged not promoted: *when one player-facing outcome has both an advisory read-side detection and an
authoritative write-side one, keep them distinct present-methods reachable on distinct paths — collapsing them
hides that the unique-match path has only the write-side net.*)

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

**Forward fit realized — and a walk-back that *is* the lesson: `@Scheduled`, not a hand-rolled `SmartLifecycle`
thread.** `[thread #3]` The dawn/dusk ticker first reached for the "`SmartLifecycle` equivalent where start and
stop must pair" branch — a daemon thread with `start`/`stop`/`isRunning`, a `volatile` flag, interrupt/join.
Reviewing it against §2, we recognized it as **speculative complexity applied to our own plumbing**: the only
thing a hand-rolled `SmartLifecycle` buys over Spring's scheduler is *per-bean phase ordering relative to
another lifecycle participant*, and there is no second async writer to order against yet. So it was simplified
to framework scheduling (Spring owns the thread), and the emergence rule was applied *reflexively* — the same
discipline we use on the model, turned on the infrastructure. The justification I had used for the thread —
"the ticker must stop before the JLine `Terminal` closes, or `printAbove` throws" — turned out **not** to need
a hand-rolled lifecycle at all: Spring's scheduled tasks are lifecycle-managed (≥6.1), so the container
*cancels* them at context close, waiting for an in-flight run, **before** it destroys plain singletons like the
`Terminal` (verified against the Spring reference). The reverse-order shutdown the §7 hazard demanded falls out
of the framework either way — the mistake was thinking I had to *implement* the lifecycle to *get* the ordering.
The thin-driving-adapter shape is unchanged (pull the prototype, fire, use no return — the polling-system-actor
peer of `ConsoleSession`/`GameSeeder`), and the same knowing looseness remains: scheduling starts during context
refresh, before the `@Order(1)` seeder, so an early tick observes an uninitialized game — a safe no-op routed to
the readiness gate. **`SmartLifecycle` phases are deferred to their real trigger**: the first time two async
writers (the ticker and a future outbox relay) must be ordered against *each other*. The enablement
(`@EnableScheduling`) lives on the equally-gated `BootSequence`, beside the boot runners, so a test slice spins
up no scheduler.

A **second misstep, caught only when the app refused to boot**, sharpened the same point. The first cut reached
for the `@Scheduled(fixedDelayString = "${game.time.ticker.interval}")` *annotation* — which failed twice over:
the `${}` placeholder does not resolve (a `@DefaultValue` is a *binding-time* default on the catalog, **not** an
`Environment` entry the placeholder can see), and `@Scheduled`'s string parser would reject the readable `5s`
form anyway (it wants ISO-8601 `PT5S` or a millis number — the simplified style is for `@ConfigurationProperties`
binding, not `@Scheduled`). The fix is a `SchedulingConfigurer` that injects the **bound, typed `Duration`** from
the catalog: scheduling is configured *after* binding (unlike a *pre*-binding `@ConditionalOnProperty`, which is
why that one must read a raw key), so it can — and should — consult the bound object rather than re-read a raw,
stringly-typed placeholder. A bonus: the previously-dead bound `ticker.interval` becomes the single source of
truth, and the scheduling path has no string parsing left to get wrong. (The lesson worth keeping, now twice
earned: *emergence governs plumbing too — reach for the framework's managed, typed abstraction before
hand-rolling threads/lifecycle or re-stringifying config the framework has already bound; hand-roll only when a
concrete need the abstraction can't express has actually arrived.*)

**A third walk-back, surfaced only by a live run: the ordered shutdown is real, but nothing *triggered* it.**
`[thread #3]` The shutdown-ordering claim above — Spring cancels scheduled tasks at context close, *before*
destroying plain singletons like the JLine `Terminal`, so `printAbove` never hits a closed terminal — rests on a
single load-bearing premise: that the context actually **closes**. It did not. `main` fired
`SpringApplication.run(...)` and *discarded the returned context*. `run()` returns only once every
`ApplicationRunner` has completed — including the console loop that blocks until `bye` — so on `bye` the main
thread simply ended. But `@EnableScheduling` runs Spring's scheduler on **non-daemon** threads, and a single
live non-daemon thread keeps the JVM up: after `bye` the process did not exit, the ticker kept firing into a
still-open context (announcing *after* "Farewell"), and only Ctrl-C killed it. It is a deadlock of assumptions —
the scheduler thread won't stop until the context closes; Boot's shutdown hook won't close it until the JVM
begins shutting down; the JVM won't begin shutting down while the non-daemon scheduler thread lives. The fix
supplies the missing trigger at the only altitude that owns it, the **process boundary**: `main` now
`System.exit(SpringApplication.exit(run(...)))`, closing the context explicitly once the runners return. Every
guarantee the paragraph above asserts then comes true *because the context closes*: cancel the scheduled tasks
(waiting for an in-flight run) → destroy the plain singletons last, `Terminal` included → tty restored → exit.
The boot story's division of labour extends one notch: `BootSequence` owns startup *ordering*; `main` owns the
process *run-and-exit*. The lesson the ticker saga keeps re-teaching, now a third time: a framework's managed
lifecycle hands you the *ordering* for free, but you must still **trigger** the lifecycle — here, close the
context — or none of its guarantees ever run. (Not a Boot-4 delta — non-daemon scheduler threads and
`SpringApplication.exit` are framework-general Spring behaviour, so nothing for `spring-boot-4-notes.md`.)

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

**A third composition layer, and grammar as a static helper — not an abstract method.** `[thread #2]` The
`now`/`time` interaction extended this two-layer split (domain-agnostic `Console`; domain-aware
`CurrentSceneRenderer`) with a **third, even-more-generic layer beneath**: a pure `English` grammar helper
(ordinals, singular/plural) that knows neither the terminal nor the domain. Rendering a date composes all
three — `CalendarRenderer` (domain-aware: resolves month/weekday names against the calendar, does 1-based
counting) over `Console` (terminal styling) over `English` (language). The boundary is sharp: the **model
supplies names and numbers** (language-neutral — a `GameDate` holds 0-based integers; `Month`/`Weekday` carry
authored names), the **presenter supplies grammar, labels and 1-based counting** ("the 6th day", "15 hours").
Ordinals/plurals are a *third placement category* beyond §10's model-rule-vs-use-case-orchestration: not a
domain rule and not orchestration but **locale/grammar**, unambiguously presentation. And because each is a
pure function of an `int`, its home is a **stateless static helper composed in** — never a method on a domain
object, never an abstract hook on a presenter base class, the same composition-over-inheritance stance §6
takes against presenter base classes. The reuse is structural: the anniversary message ("your 4th
anniversary") and weather text will compose the same `English` helper with no inheritance. (A faithfulness
note the radices forced: with no minutes radix, the clock label is "N hours and M seconds into the day", not
an `HH:MM` that would assume 60.)

**The first *asynchronous* presentation — `printAbove` realized, and the §7 seam closed.** `[thread #3]` The
dawn/dusk ticker is the first interaction whose presenter writes *while a `readLine` is in flight* — a system
actor producing output the player sees mid-prompt. This is exactly the "one line editor, N async `printAbove`
writers onto one terminal buffer" scenario this section said JLine was *chosen* for, and it cashed in the
deferred `Console` seam: `Console` now has two write paths — `write` (between reads, via `Terminal.writer()`,
for player-command responses) and `printAbove` (during a read, via `LineReader.printAbove`, for the ticker). The
split is by **timing**, not by content, and both stay behind the domain-agnostic facade (the presenter builds
the styled line, `Console` writes it) — so the §7 layering holds even though one writer is now concurrent. The
presenter also shows the §4 "mandated presenter even with no human audience" rule applied to a *background*
actor sharpened by repetition: only an actual announcement reaches the console; the quiet poll and the
not-yet-initialized poll are trace logs, the error a warn log — never per-interval console spam.

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

**The first parallel actor is a *polling metronome*, not the event spine — and that is the right first step.**
`[thread #3]` The dawn/dusk ticker (`GameClockTicker`) is the project's first concurrency: a scheduler-driven
background task (a `SchedulingConfigurer` reading the bound interval) that drives `AnnounceTimeOfDay` on a fixed
delay. It is deliberately **not** built on the outbox/event mechanism above, because announcing the time of day
is not *causation* — nothing in the domain
"happened" that a phase boundary reacts to; game time simply advanced, and a phase boundary is a *derived* fact
of the current instant. Polling-and-deriving fits a derived, idempotent observation; events fit discrete causal
facts. So this slice exercises *threading and async presentation* without prejudging the event spine, which
still waits for its first causal site (an NPC reacting). The two will coexist: a polling clock for derived
time-of-day, an event outbox for causal reactions.

**"Dumb metronome, smart use case" — the driving adapter carries no domain knowledge.** The decisive design
choice (Package B) was to make the ticker a *blind* metronome: every scheduled interval it pulls a fresh
prototype use case and fires it — knowing nothing of the calendar, the seconds-per-hour, or what a day phase is.
All time semantics live in the use case. This dissolved the question that *started* the design — "how does the
ticker learn the seconds-per-hour, which only initialization has?" — into a non-problem: the metronome needs
neither the seed nor the radices (it needs only its own `interval` config), and the use case reads
seconds-per-hour from the calendar port it already injects (the same port `AskForTime` uses), not from a
hand-off. The general trap avoided: a *driving* adapter reaching into a *driven* port (the calendar source) to
size its own behaviour — the metronome touches no domain port at all.

**The dedup watermark, guarded by optimistic locking — thread #3's second data point.** `[thread #3]` A dumb
metronome over-fires, so the use case must make announcing *idempotent per occurrence*. The mechanism is a
persisted monotonic watermark (`DayPhaseLog.announcedThroughHour`, the absolute game hour announced through),
and the concurrency guard is **optimistic locking carried on the aggregate** (`DayPhaseLog.version`): the use
case reads the log once (capturing the version), advances the watermark carrying that version, and the single
save inside `doInTransaction` is version-checked — a concurrent observer that won makes the loser's write stale,
surfaced as `OptimisticLockingError` and presented as "nothing to announce." This is the §5 *detector* lesson:
the transaction boundary gives atomic rollback, the version gives the actual conflict detection (an earlier
inside-transaction re-read was the *proxy* that §5 shows is toothless, and it was removed once the real detector
landed). The random message pick is made by an injected domain `Dice` (§4) and runs *outside* the transaction
(a pure choice with no persistence effect, exactly like the item-spawn rolls). Persisting the watermark (rather than holding "last hour" in the ticker
thread) is what makes it restart-safe: a `bye`/restart mid-dawn-hour does not re-announce. And the quiet poll —
no phase, already past the watermark, or the concurrent-loss — is always a *presented* outcome
(`presentNothingToAnnounce`), never a silent return, so "exactly one `present*` per run" (§4) holds even for the
overwhelmingly common no-op tick. The watermark earned its own **singleton aggregate** rather than a field on
`GameClock`: "how far have we narrated" is a different concern from "how much time is banked," and keeping them
apart gives the concurrency thread a clean second example (and the natural `@Version` home — §5) instead of
muddying the accumulator (§2 minimalism applied to aggregate boundaries).

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

**The driving loop is the console's internalized request-dispatcher — and naming it that dissolves an
apparent exception to fire-and-forget.** `[thread #4]` The unidirectional rule (§4) says a controller fires a
`void` use case and renounces its outcome — control flows forward, never back to branch on a result. A
*long-running* primary adapter appears to break this: its `while` loop plainly *continues* after each
use-case call, to read the next command. The resolution is to see the loop for what it is. A web controller
or a telnet server gets its request-dispatch loop *from the container* (servlet dispatch; the server's
accept-loop); a console adapter has no container, so it **embeds the loop itself**. The loop is therefore not
"code that runs after a use-case call" — it is the mechanism that *re-arms for the next request*. The
invariant each turn upholds, stated precisely:

> Within one iteration, the controller obtains exactly one unit of work, dispatches it to at most one use
> case, and then yields to the loop top. The only statements allowed after the dispatch are loop control
> (`continue` / `break`) — never result-inspection, outcome-branching, or a second use-case call.

Under that statement, fire-and-forget holds *per turn with no exception*: `break`/`continue` are *dispatcher*
control (stop accepting / accept the next request), not business continuation. There is nothing to carve out —
the loop is the in-process stand-in for the request/response cycle the web environment hands you for free.
The earlier instinct to call the loop "the one allowed exception to fire-and-forget" conceded too much; the
honest framing is that there is no exception, only a dispatcher the console must supply itself. (Promotion
candidate, flagged not promoted: *a long-running primary adapter's command loop is the internalized
request-dispatcher; per turn fire-and-forget holds without exception — the loop re-arms, it does not continue
a call.*)

**The welcome is the system's turn-1 request — dispatched directly, not minted as a `Command`.** `[thread #4]`
The session-opening greeting is player-facing output, so by "controllers never present" it must be a use-case
outcome, not a controller `printLine` (its removal finally deletes the last direct write from the console —
the milestone the §9 parser work set up). Removing it also completes the **read/write split on the shared
`Terminal`/`LineReader` resource** (§7): the driving adapter now touches JLine *only to acquire the request*
(`readLine` and its prompt), while *all* output is the driven presenters' — so the read/write asymmetry is the
driving/driven boundary made concrete on one shared resource, with no output leaking from the controller. It
is fired as the loop's *first turn* (`greetPlayer(); continue;`),
before any line is read — the one turn whose unit of work originates from the *system*, not from a read. The
tempting unification — synthesize a `WelcomeCommand` so *every* turn is "obtain a Command, dispatch it" — was
**weighed and deferred**, and the discriminator is **does the parser produce it?** The `Command` set *is* the
parser's output and the swappable grammar seam (above), and the parser can never produce a welcome (there is
no line to parse). `UnknownCommand` does *not* license `WelcomeCommand`: the parser genuinely produces
`UnknownCommand` (the parsed intent "unrecognized") but never a welcome. Keeping `Command` = parser-output
protects the seam's meaning, so the welcome stays a direct turn-1 dispatch. The alternative — redefining
`Command` as "a unit of work the dispatcher handles" with *two* producers (the parser for player intents, the
session for lifecycle signals) — becomes the principled shape only once a *family* of system-issued signals
appears (an autosave notice, an idle "still there?" prompt, an NPC interruption surfaced to the console); then
`WelcomeCommand` is that family's first member, dispatched uniformly (a labeled `break` lets the `bye` arm
exit the loop without a flag). One welcome does not earn that redefinition (emergence, as with
`KnownScenes`/`AuthoredItem` in §10). The greeting *use case* is a second interaction on `Guidance` —
system-actor `systemGreetsPlayer()` beside player-actor `playerIssuesUnrecognizedCommand` — sharing one player
audience and the "orient the player toward what they can do" goal (a use case may host multiple actors;
clean-ddd-core §0), with the shared command-list text factored into one presenter constant so the welcome and
the unrecognized-command nudge cannot drift. (This is the resolution of the §4 "the welcome is a separate
interaction with its own port" note: *separate interaction, yes — but a sibling on `Guidance`, not on
`InitializeGame`*, whose `presentGameInitialized` speaks to an operator/log audience and whose lifecycle moment
is world-construction, not session-start. Folding the player greeting there would reopen exactly the
two-audience overload that note warned against, and would force a second presentation into a single-success
interaction.)

**`bye` is intercepted before the dispatch switch.** Quitting both fires `SuspendGame` (fire-and-forget) *and*
must stop the loop — and a `switch` arm cannot `break` the enclosing `while` without a flag or a labeled
break. Rather than a `quitRequested` flag read *after* the switch (harmless, but it reads as "continue past
the dispatch"), `bye` is handled by an early `if (command instanceof QuitCommand) { leaveGame(); break; }`; the
`break` is unconditional — the loop exit is never contingent on what `SuspendGame` did. The switch then keeps a
no-op `QuitCommand` arm purely for sealed-set exhaustiveness, and that dead arm is the honest signal that `bye`
is the one command whose handling carries a loop-control effect the switch cannot express.

**Resuming a multi-step conversation — substance (use case) vs. modality (infra), the container as the
resumer map.** `[thread #4]` When a follow-up line must resume a pending dialogue (`examine`'s pick, and the
same for `take`/`drop`), the §4 split decides the wiring: the use case is the conversation's **substance** —
its semantic steps are the converging interaction methods (`playerExaminesChosenCandidate`) — while the
**modality** (the affordance buffer, the continuation predicate, the resume routing) is a delivery-mechanism
concern kept in infra. So we **"dress up" each use case as a conversation** with a thin *infra* handler
(`Conversation { SelectionKind kind(); void resume(Command, List<String>); }`) declared in the composition
root (the §6 ad-hoc-`new` convention, *named* — not anonymous-in-`@Bean` — so it is testable, grows a state
machine for >2 steps, and can share a base), and we let the **DI container be the resumer map**:
`ConsoleSession` injects `List<Conversation>` and matches the armed `kind()`, instead of a hand-maintained
`kind→useCase` table that would duplicate wiring the container already holds. Each handler **looks its use
case up from `ApplicationContext` per `resume`** — the established prototype-pull idiom (§6, `getBean` over
`ObjectProvider`), because a singleton handler that *captured* its prototype use case would silently defeat
the scope (scope is freshness *per lookup*; a `List<Conversation>` is injected once). The cast-and-call
factors into an `AbstractSelectionConversation` (concretes vary only the use-case method), mirroring
`AbstractSelectTargetSubcase` (§4) and emerging at the second conversation — which, implementation revealed, is
**`take`, not `drop`** (the two-second-instances note below). The `continuedBy` predicate stays inline in the
dispatcher until conversation #3 (one continuing on something other than a bare number) — staged emergence, so
a handler carries only `kind()`+`resume()` until then.

**Why not a *core* `Conversation` the input ports implement.** `[thread #4]` The tempting unification —
`*InputPort extends Conversation`, `resume` on the use case — was **rejected**: a *generic* `Conversation`
must speak the routing vocabulary (`Command`), dragging the parser's delivery types into `core`; relocating
`Command`/`AffordanceContext`/a `@ConversationKind` annotation inward to satisfy ArchUnit only *passes the
proxy while defeating it* (§1). The device-neutral conversation vocabulary the core needs already exists —
the converging interaction methods and their **primitive** parameters (`int ordinal, List<String>
offeredTokens`); a core `Conversation`/`Command` type would be the reflexive envelope DTO + parallel
hierarchy the methodology rejects (clean-ddd-core §8, applied on the input side), and routing would *still*
need the active-conversation identity, which is infra session state. DCI agrees: the Context coordinates the
interaction; it never makes domain participants speak the UI's input format. (Promotion candidate, flagged
not promoted: *model a use case as a conversation's substance and its modality — affordance, continuation,
resume routing — as a delivery-mechanism concern; dress the use case as an infra conversation handler at the
composition root and let the DI container be the resumer registry; never give the core a `Conversation`
interface, which would import the delivery vocabulary the core excludes.*)

**Two different "second instances", in two different PRs — the finding `take` forced.** `[thread #1]` `[thread #4]`
The plan above (and issue #55's first draft) put *both* shared abstractions at `drop`. Implementing `take`
corrected it: there are **two distinct "second instances"**, and they fall in different PRs because they
generalize different axes. The **conversation dispatcher** generalizes *"which dialogue does a bare number
resume?"* — `examine` is the first number-continued dialogue, **`take` is the second**, so kind-routing and
`AbstractSelectionConversation` are forced *at `take`* (without them, a number after `take rusty` would wrongly
resume `examine` and *describe* the item instead of taking it). The **`select` Template-Method base** (§4)
generalizes *"where do candidates come from?"* — `examine` is the first provisioner (scene ground), `take`
*reuses* it, so the base waits for **`drop`** (the second *provisioner*, an inventory). Same "mint the
abstraction at the second instance" rule, two different counts, two PRs — the lesson being that **"second
instance" is meaningful only relative to the specific axis being generalized**; lumping two axes under one
feature ("drop forces the shared abstractions") miscounts both. Two ripples the dispatcher's arrival forced,
both at `take`: `SelectTargetPresenterOutputPort` lost `presentNoPendingSelection` — with the
container-as-resumer-map the console resumes a selection *only when one is armed*, so an empty offer can no
longer reach the subcase as a player action; it becomes a **precondition throw** (a wiring fault routed to the
catch-all), *not* a deleted case (deleting it would let an empty offer mislabel as `presentNoSuchOption`). And
the wiring grew a **startup completeness assertion** — every `SelectionKind` must have a `Conversation` bean —
so a kind with no handler fails fast at boot rather than silently dropping a pick at runtime. (Promotion
candidate, flagged not promoted: *"emerge at the second instance" is per-axis — one feature can be the second
instance of one abstraction and merely the first reuse of another; count per abstraction, not per feature.*)

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

**The spawn loop resolved — the whole stochastic policy pushed onto the model, fed only a `Dice`.**
`[thread #1]` `[thread #2]` The spawn roll loop is now pushed down. The use case had been driving the algorithm
itself — `item.getTemplate().getSpawnRule()`, then a `maxTries` loop interleaving `isHitBy`/`pickScene` with the
randomness and id-generator *port* calls. The fix is the **batch form**: `SpawnRule.rollPlacements(Dice)` owns
the entire placement policy (the attempt count, the hit and scene decisions, *and* the ordering — one roll per
attempt, a pick only on a hit, knowledge that had leaked into the use case); `ItemTemplate.spawnInto(Dice)`
turns each placement into a valid `Item`, **minting its id from the same dice** (`ItemId.mint(Dice)`) only for
an actual placement (no eager minting for misses); the use-case-private `AuthoredItem` forwards `spawnInto` one
level, exactly as it forwards `candidateScenesNotIn`. `spawnItems` collapses to a loop that holds *only* the
`Dice` (a domain collaborator) and collects — pure orchestration. **The id supplier is gone (#53):** an earlier
form threaded `idGeneratorOps::generateItemId` in as a second argument — the last camouflaged output-port
closure crossing into a model method — and removing it is what made `spawnInto` take the dice alone (§2's
dissolution, §4's "id minting followed the dice").

This is game-clean's instance of the now-promoted doctrine: the computation lives in the domain, fed values,
JDK suppliers, and the model's own collaborators, never infrastructure ports; the use case keeps the
orchestration (§3's boundary currency one layer in — values inward, valid model out). The general rule —
functional core / imperative shell, the home test, value-by-default /
supplier-or-source-only-when-the-core-owns-the-cardinality, and why the instability objection *dissolves*
rather than relocates — lives canonically in `clean-ddd-core` → *Where inter-aggregate business rules live*,
promoted out of this project and not restated here. **The draw source evolved from a `DoubleSupplier` to a
domain `Dice`** (§4's reversal): a supplier was first reached for because `SpawnRule` owns *exactly the
cardinality* the rule names (one roll per attempt, a pick only on a hit, a count the domain owns), so a *value*
would not do — but the cardinality argument's conclusion is to own the **source** outright as a domain `Dice`,
not to thread a port-closure (`randomnessOps::nextDouble`) through the loop. **The same conclusion then retired
the id port-closure (#53):** `idGeneratorOps::generateItemId` was the one remaining adapter-function threaded
into a model method, and once the model rolls its own dice it mints its own ids too (`ItemId.mint(Dice)` via
`Ids`), so `spawnInto` is fed *only* the domain `Dice`. The `maxTries` leak closes; the "rolls run outside the
transaction" property is untouched, since the use case still chooses *where* to call `spawnInto`.

**`Dice` sharpens the vague application-service / domain-service line — and testability is the operational
litmus.** `[thread #2]` The use case is the **application service** (the imperative shell — orchestrate, hold
ports, demarcate the tx, present); `Dice` is a **domain service** — a stateless domain operation
(`roll(Chance)`, `pick(List)`) consumed by *several* aggregates' methods (`SpawnRule.rollPlacements`,
`DayPhase.pickMessage`, `ItemId.mint`). That boundary is classically fuzzy — both are "stateless services" —
and `Dice` makes it concrete on three axes:

- **Inject-vs-`new`: testability is the litmus.** The model never does `new SystemDice()`; the composition
  root does (`UseCaseConfig`), injects it into the use case (a `Dice dice` field), which *hands it to the
  model* (`rollPlacements(dice)`). The forcing criterion is **testability** — substitute a
  `SeededDice`/`ScriptedDice` and the domain logic is deterministic, both in the use-case test and in a direct
  VO test. But testability is the *detector*, not the law: the law is **functional-core purity** (entropy is
  an *input*, never something a domain method conjures) plus **no ambient global state in core**
  (`ThreadLocalRandom` is sealed inside one labelled `SystemDice`, never reached for statically by a rule). A
  domain rule you can't unit-test deterministically without infrastructure has an un-injected environment
  dependency — the failing test is the signal, injection the fix.
- **"Domain service" classifies the *logic*, not the *wiring*.** `Dice`'s type lives in `core/model/dice/`,
  yet it is *wired by the composition root* and *flows through the application service*. Wiring location ≠
  conceptual layer — conflating them is half the original vagueness.
- **Consumption locus splits domain-service from port** — testability alone does not, since *both* are
  injected and substitutable. A **domain service** is consumed *inside the model* (handed in as a collaborator
  the model calls — `SpawnRule` rolls the dice); an **output port** is consumed *by the shell* (the use case
  calls it and hands the *value* inward — `GameTimeSource.elapsedSessionSeconds()`). This operationalizes the
  §4 *part-of-the-game vs world-outside* split: dice are part of the game → a domain service handed to the
  model; wall-clock time is the world outside → a port called by the shell. The app service's whole
  relationship to `Dice` is **provision, not computation** — hold and forward; a use case that *itself* rolled
  the dice to decide a domain outcome would be domain logic leaking into the shell.

(Promotion candidate, flagged not promoted: *resolve the application-service/domain-service boundary on two
axes — testability is the litmus for inject-vs-`new` (a domain rule must be deterministically unit-testable,
so its environment-coupled collaborators are injected, never `new`ed in the model), and consumption locus
splits the injected collaborators (a domain service is consumed inside the model and handed in; an output port
is consumed by the shell, its result handed in as a value). "Domain service" names the logic, not the wiring.*)

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

## 11. Time as a mixed-radix value — the calendar owns the arithmetic

**Principle.** Game time is authored as a calendar of *uniform fixed radices* — seconds per hour, hours per
day, days per month, and the ordered named cycles of weekdays and months (the year's length *is* the month
count). Because one real second is one game second, a date is a **pure function of elapsed seconds**: a
mixed-radix positional number, decomposed on demand by repeated `divmod` down the ladder, never ticked and
never stored. The decomposition lives on `GameCalendar.placeInstant`, **not** in a use case, because the
calendar owns the radices — this is §10 in a new guise: the eventual "what is the date?" use case will
*orchestrate* (fetch elapsed seconds, ask the calendar to place them, hand the result to a presenter) while
the arithmetic stays computation on the model.

**The companion that proved the principle wasn't yet complete — `absoluteHourOf`.** `[thread #3]` The dawn/dusk
announcement needed not the *cyclic* hour-of-day but the *monotonic* absolute hour since the epoch (its dedup
watermark must climb, never wrap — §5/§8). The first cut computed it in the use case as
`elapsed / calendar.getSecondsPerHour()` — exactly the §10 leak this principle warns against: the use case
reaching for a radix *getter* and doing the arithmetic itself. ("Use `placeInstant`'s `hourIndex`" is *not* the
fix — that is the wrong coordinate, cyclic where the watermark needs monotonic; and re-deriving the absolute
hour from a `GameDate` would multiply back through *more* radices in the use case, worse still.) The fix is a
second calendar SEFF, `absoluteHourOf(elapsed)` — the un-wrapped companion to `placeInstant`, consistent by
construction (`placeInstant(e).getHourIndex() == absoluteHourOf(e) % hoursPerDay`). Now the use case
orchestrates both (ask for the date *and* the absolute hour) and never touches `secondsPerHour`. The lesson:
*"the calendar owns the arithmetic" is not satisfied by `placeInstant` alone — only when **every** derivation
that needs a radix lives on the calendar, the coarse un-decomposed ones (an absolute hour) as much as the full
decomposition.* A radix *getter* called from a use case that then computes on the result is the tell that an
arithmetic has leaked outward.

**A domain choice decided where a computation lives — the sharpest finding.** `[thread #1]` `GameDate` is a
pure positional tuple `{year, monthIndex, dayIndex, hourIndex, secondOfHour}` and is minted minimal: it stores
no month or weekday *names* (resolved against the calendar — `monthOf`, `weekdayOf`), and — the cutting case —
no weekday *index* either. The reason is not tidiness, it is the week's semantics. Under a **per-month-reset**
week, weekday is `dayIndex % weekLength` — local to the date, cheap to store on it. We chose a **continuous**
week instead (weekday = absolute days since the epoch, modulo week length, unbroken across month and year
boundaries), and that choice *moved the weekday off the value*: a continuous weekday is a total derivation
from *all* the positional fields plus the radices, i.e. a multi-field computation that belongs on the
radix-owner as a side-effect-free function, never duplicated onto the value where it could drift from the
year/month/day it is read off. So a semantic decision about the *domain* (continuous vs reset) settled an
*architectural* one (stored field vs calendar SEFF), via §2 (do not store derivable state) and §10
(computation lives with the data it is about). The same logic that keeps the month *name* off the date keeps
the weekday off it.

**Factory shape A — the range invariant has one knower.** `GameCalendar` is the *sole* factory of `GameDate`
(`placeInstant`), and `GameDate`'s own construction gate checks only **non-negativity**. Whether a
`monthIndex` is *within range* needs the calendar's month count — knowledge the value neither holds nor should
— so the bound is guaranteed by the factory, not re-checked on the value. This is the §2 single-source-of-
truth resolution wearing different clothes (cf. the `SceneId` prefix-vs-alphabet split): the value owns what
it can judge alone, the calendar owns the invariant that needs the radices, and there is no drift because
there is exactly one knower. The deviation from strict always-valid self-sufficiency is deliberate and
bounded — a `GameDate` is unreachable except by placing an instant.

**Deferred, and the forward fit it sets up.** `[thread #3]` The first slice is VOs + arithmetic only;
`placeInstant` takes a plain `long`, so the whole calendar is unit-testable with no port, clock, or database.
Two pieces are parked with their shapes already chosen. (1) When time becomes a *live* query, `now` enters as
an **output port** — the wall-clock is non-deterministic infrastructure, so stubbing it keeps the use case
deterministic; the persisted real epoch is the anchor that port's readings are measured against. (This time
port and game *dice* sit on opposite sides of a deliberate line — see §4: wall-clock time is the world outside
the game, a value read once, so it stays a port; dice are part of the game, a domain capability the model owns.
An earlier version of this note compared the time port to a now-deleted randomness *port*; that comparison no
longer holds, which is itself the §4 lesson.) (2) The authored `calendar:` block is *world
content*, so it flows through the existing seed carrier and is constructed into a `GameCalendar` at the
`InitializeGame` gate (§3/§4 — invalid-capable carrier in, valid model out), distinct from operational
`game.*` configuration. A consequence worth stating because it confirms the wall-clock-derived design: closing
the game does **not** pause time — elapsed real seconds keep accruing — which is right for a persistent world
and is the reason the clock is derived rather than stored.

**Model B realized — the clock is *stored and paused*, reversing the wall-clock lean.** `[thread #3]` The
deferred slice above leaned wall-clock-derived ("closing the game does not pause time… the clock is derived
rather than stored"). Building the first time-facing interaction (`now`/`time`) **reversed that to Model B —
accumulated play-time**: game time advances only *while playing*, so the clock is a persisted `GameClock`
aggregate holding the banked total, and current time is that total *plus* the live session's elapsed seconds.
The reversal is a **UX call, not an architectural one** — for a sporadically-played single-player game, a
spell or a store schedule measured against real wall-clock time (with the game closed for days) is surprising;
pausing on quit is what a player expects. The point that matters for the showcase: **the choice lived entirely
behind the time-source port and what persistence stored.** `placeInstant(long)` is identical either way, so
Model A↔B is a localized swap (read an anchor vs bank an accumulator), vindicating the deferral — the model
needed no change. Banking happens on an explicit `bye` only (no ticker yet); a hard kill loses the current
session's unbanked seconds, accepted until an autosave/ticker arrives (its teardown is the §6 shutdown-ordering
forward fit). The session-elapsed figure is wall-clock derived behind `GameTimeSourceOutputPort` (one real
second = one game second), kept off the model so the time-reading use cases stay deterministic under test —
the role randomness plays for spawning. And the banking arithmetic (`elapsedWith`/`accumulate`) is the
**model's**, not the use case's (§10): the use case orchestrates (load clock, ask the session length, save);
`GameClock` computes.

**The calendar is load-each-boot, and that moved its validity from a presented outcome to a boot fault.**
`[thread #2]` The authored calendar is *world content* like the scenes, but — decision taken here — it is **not
persisted**: a `CalendarSourceOperationsOutputPort` loads and constructs it once at boot, held in memory. That
forced a *documented departure from §3's boundary-currency rule*. §3 says untrusted authored data crosses as an
invalid-capable carrier so its invalidity is a **presented** outcome; the calendar instead **returns a valid
`GameCalendar` from the port**, because a non-persisted, cached, multi-reader authored singleton behaves like
*configuration*, not per-interaction input — re-validating it on every `now` is pointless, and a malformed
calendar is a boot-time config fault, not something to show a player. So the adapter constructs it eagerly and
**fails fast at startup**. This adds a *third* discriminator to §3's "provenance decides the currency" finding:
*persistence* (valid-by-provenance → model out) and *untrusted-pulled-and-presented* (the seed → carrier out)
are now joined by *untrusted-pulled-but-cached-as-config* (the calendar → model out, fail-fast). The cost was
chosen with eyes open: load-each-boot over persisting traded a *presented* authoring-validity outcome for a
*boot* one, and **parked the drift hazard** — radices that change between runs silently reinterpret a stored
clock; persisting or hashing the calendar is the future guard, taken up if it bites.

**One prefix, content split from config — `game.time.*`.** The calendar's *content* (radices, named cycles) is
authored YAML flowing through the domain gate; only its *location* (`game.time.calendar-location`) is
operational `game.*` configuration. Every game property shares the one memorable `game.<area>.*` prefix
(`world`/`terminal`/`player`/`time`), grouped by subsystem — so the directory a file sits in (by content kind)
and the config prefix (by subsystem) are deliberately decoupled, exactly as `game.world.seed-location` already
points at `world/scenes.yaml`.

**Day phases are co-authored with the calendar but kept *out* of it — narrative is not structure.** `[thread #1]`
The dawn/dusk feature could have bolted `dawnHour`/`duskHour` + messages onto `GameCalendar`. It did not:
`GameCalendar` stays pure mixed-radix arithmetic + named cycles, and day phases became their own model
(`DayPhase`/`DayPhaseSchedule` in `core/model/daytime/`). The cut is *what kind of thing* each is — a day phase
carries player-facing *prose* and triggers an announcement, which is narrative, not the fabric of time (§2:
keep the VO minimal; don't let a new concern bloat an existing one). They are still co-authored in one
`calendar.yaml` (a dawn hour is meaningless without `hoursPerDay`), so one adapter (`YamlCalendarSource`) reads
the file and serves **two** ports returning **two** carriers — the calendar and the schedule — with the
inter-model check (every phase hour `< hoursPerDay`) done once, fail-fast at boot, in the adapter that holds
both (the same load-each-boot, return-a-valid-model §3 deviation the calendar itself takes; each port still
owns its own boundary error). Generalizing to a *set* of phases rather than two hard-coded fields is not
speculation — dawn and dusk are literally two instances of one shape (`{name, hourOfDay, messages}`), and the
list is less code than two special-cased fields.

---

## Open hazards / stances (genuinely unresolved)

- **Testability stance.** Game logic lives in unit-testable **use cases**, not in the
  terminal adapter. Headless app-driving (recipe in `project-context-extended.md`) is
  reserved for adapter smoke checks and the wall-clock / concurrency exploration of
  `[thread #3]`, where unit tests are weak — and it verifies output *content and ordering*,
  not visual rendering.
- **Shutdown ordering — first case resolved (§6).** The dawn/dusk ticker, the first async writer, is a
  `@Scheduled` task, so it stops before the `Terminal` bean is destroyed *for free*: Spring's scheduling is
  lifecycle-managed (≥6.1) and cancels tasks (waiting for an in-flight run) at context close, before
  plain-singleton destruction — no hand-numbered phases (verified against the Spring reference). The hazard
  reopens only if a *second* async writer must stop in a defined order *relative to the ticker* (e.g. the outbox
  relay), at which point explicit `SmartLifecycle` phases return. Confirmed by docs; the runtime path
  (`@Scheduled` firing + `printAbove` over the live prompt) runs only in the real app (tests disable the
  terminal), so it is asserted by inspection/app-run, not yet by an automated test.

## Non-doctrinal project decision

- **`.claude/` is versioned.** The memory files capture the Clean DDD reasoning, which is
  itself part of what the project shares with the community, and the author wants them backed
  up for reinstalls. Only `settings.local.json` stays git-ignored (machine-local paths,
  username, JDK location). Committed `.claude/` content is kept institution-neutral; the
  methodology `@`-refs in the project `CLAUDE.md` are author-local provenance that will not
  resolve in a fresh clone. (Not Clean DDD doctrine — recorded here only because it explains
  why these notes are public.)
