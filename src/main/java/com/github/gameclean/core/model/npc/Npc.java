package com.github.gameclean.core.model.npc;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.designation.Designatable;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.scene.SceneId;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A non-player character — the aggregate root of the npc aggregate.
 *
 * <p>Immutable and always-valid: an {@code Npc} cannot be constructed without an id, a current scene, or a
 * move chance, or with a blank short or full description. It carries a {@code shortDescription} (how it reads
 * when listed among a scene's occupants) and a {@code fullDescription} (reserved for a later {@code examine
 * <npc>}; not yet rendered, just as {@code Item.fullDescription} existed ahead of its consumer). The
 * {@code moveChance} is the authored odds that, on any given tick of the autonomous-movement metronome, this
 * NPC wanders to an adjacent scene.
 *
 * <p>The current scene is referenced <em>by identity</em> ({@link SceneId}), exactly as a
 * {@link com.github.gameclean.core.model.player.Player} references its current scene: an NPC is its own
 * aggregate, not held by the scene. "The NPCs in a scene" is a query against this reference, not a collection
 * owned by the scene. The position is mobile: {@link #moveTo(SceneId)} produces a copy standing in the target
 * scene (immutable copy-on-write, the {@code Player.moveTo} twin).
 *
 * <p>It carries {@link HitPoints} (spawned at full health) and takes damage via {@link #takeDamage(int)}; an
 * NPC whose pool is spent {@linkplain #isDead() is dead}. It also carries an opaque optimistic-locking
 * {@link #version} — set by persistence on read, checked on write — exactly like {@code Item}: the {@code hit}
 * vertical makes the player a <em>second</em> writer alongside the autonomous-movement ticker, so two actors
 * racing to write the same NPC cannot both succeed (the loser's stale write is rejected). This cashes the
 * previously-deferred version trigger: it arrives now that the player can affect an NPC, mirroring the
 * {@code drop} (single-writer, plain transaction) vs {@code take} (contested, versioned) contrast among items.
 * A freshly spawned NPC is version {@code 0} (persistence treats that as a new row).
 *
 * <p><b>Combat is a persisted stance, not a one-shot reaction (#66 step 2).</b> An NPC struck by the player
 * becomes {@code hostile} ({@link #provoked()}), and stays so indefinitely — hostility is a bare {@code boolean},
 * not a {@code hostileToward(PlayerId)} in a single-player world. The animate policy <em>re-derives</em> the
 * counterattack from that persisted stance every tick (a hostile, co-located NPC strikes back), so combat rounds
 * fall out of the tick cadence without any per-episode event. The {@link #attackChance} is the authored per-tick
 * odds a hostile, co-located NPC actually swings on a given round — the combat twin of {@link #moveChance}; a
 * hostile NPC never wanders (the stance pins it in the fight), so the two chances gate mutually-exclusive
 * behaviours.
 *
 * <p>An NPC is a designation target ({@link Designatable}): {@code hit <npc>} resolves a typed fragment
 * against the NPCs in the scene exactly as {@code examine}/{@code take} resolve items, so an {@code Npc}
 * answers {@link #matches(String)} and {@link #hasIdToken(String)} — the first non-item consumer of the
 * generic select subcase.
 *
 * <p><b>The corpse ref is an authored handle, deliberately opaque (#93).</b> {@link #corpseRef} names the
 * authored item template minted as this NPC's corpse when it is slain — {@code null} when the NPC leaves no
 * corpse (authored absence). It is the first authored handle to enter the model: not a domain identity (no
 * corpse <em>instance</em> exists until the death mints one), not resolvable in-model (resolution happens
 * through the corpse-blueprint port at death time), so it stays a plain, opaque {@code String} rather than
 * an id value object — the gate asks only that a present ref is non-blank.
 *
 * <p><b>Sayings give the NPC a voice (#104).</b> {@link #sayings} are the authored lines it may answer with when
 * the player talks to it — plain prose in authored order, empty when it has none (authored absence, like an
 * absent corpse). The NPC owns the selection ({@link #pickSaying(Dice)}, a uniform dice pick) and the fact the
 * {@code talk} use case decides on ({@link #hasSayings()}); it does not own <em>whether</em> it will talk at all —
 * that a hostile NPC refuses is the use case's reading of the {@link #hostile} stance, an interaction-shape
 * decision, not a property of the saying list.
 *
 * <p>Equality is by identity (id) only — two NPCs are the same NPC when their ids match; the version and hit
 * points are naturally outside value equality.
 */
@Getter
@With
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class Npc implements Designatable {

    @EqualsAndHashCode.Include
    NpcId id;
    SceneId currentScene;
    String shortDescription;
    String fullDescription;
    Chance moveChance;
    Chance attackChance;
    HitPoints hitPoints;

    /** Whether this NPC is in a hostile stance toward the player (struck and fighting back), re-derived each tick. */
    boolean hostile;

    /** Authored handle of the item template minted as this NPC's corpse when slain — null when it leaves none. */
    String corpseRef;

    /** The authored lines this NPC may answer with when talked to, in authored order — empty when it has none. */
    List<String> sayings;

    /** Optimistic-locking token — opaque to the domain, managed by persistence, not part of value equality. */
    long version;

    @Builder
    public Npc(NpcId id, SceneId currentScene, String shortDescription, String fullDescription, Chance moveChance,
               Chance attackChance, HitPoints hitPoints, boolean hostile, String corpseRef, List<String> sayings,
               long version) {
        this.id = DomainValidation.requireNonNull(id, "npc id must not be null");
        this.currentScene = DomainValidation.requireNonNull(currentScene, "npc current scene must not be null");
        this.shortDescription = requireNonBlank(shortDescription, "npc short description");
        this.fullDescription = requireNonBlank(fullDescription, "npc full description");
        this.moveChance = DomainValidation.requireNonNull(moveChance, "npc move chance must not be null");
        this.attackChance = DomainValidation.requireNonNull(attackChance, "npc attack chance must not be null");
        this.hitPoints = DomainValidation.requireNonNull(hitPoints, "npc hit points must not be null");
        this.hostile = hostile;
        // Authored absence is a valid state (the NPC leaves no corpse); a present ref must carry content.
        if (corpseRef != null && corpseRef.strip().isEmpty()) {
            throw new InvalidDomainObjectError("npc corpse ref must not be blank when present");
        }
        this.corpseRef = corpseRef;
        this.sayings = requireNonBlankSayings(sayings);
        if (version < 0) {
            throw new InvalidDomainObjectError("npc version must not be negative, got " + version);
        }
        this.version = version;
    }

    /**
     * Whether this NPC has anything to say when talked to — at least one authored saying. The domain fact the
     * {@code talk} use case decides its nothing-to-say stripe on, so the presenter never inspects the list.
     *
     * @return {@code true} when at least one saying is authored
     */
    public boolean hasSayings() {
        return !sayings.isEmpty();
    }

    /**
     * Picks, uniformly, the line this NPC answers with by asking the given {@link Dice} to choose among its
     * {@link #sayings} — the {@code DayPhase.pickMessage(Dice)} twin: the uniform-pick mechanic lives on the
     * dice, the NPC only names <em>what</em> is chosen among. Side-effect free; the NPC is unchanged by
     * speaking (talk is read-only — a saying is prose, not a state change).
     *
     * <p>Asking an NPC with no sayings to speak is a <em>caller programming error</em> (the use case checks
     * {@link #hasSayings()} first and presents that outcome itself), so it stays a plain
     * {@link IllegalStateException} per the behaviour-method guard convention, distinct from the construction
     * gate — exactly as a null dice is a plain {@link NullPointerException}.
     *
     * @param dice the dice to pick with (must not be null)
     * @return the chosen saying
     * @throws IllegalStateException when this NPC has no sayings
     */
    public String pickSaying(Dice dice) {
        Objects.requireNonNull(dice, "dice must not be null");
        if (sayings.isEmpty()) {
            throw new IllegalStateException("npc %s has no sayings to pick from".formatted(id.asString()));
        }
        return dice.pick(sayings);
    }

    /**
     * The construction gate for the sayings: authored absence ({@code null}) is the empty list, and every
     * present line must carry content. Copied defensively so the aggregate owns an immutable list.
     */
    private static List<String> requireNonBlankSayings(List<String> sayings) {
        if (sayings == null) {
            return List.of();
        }
        for (String saying : sayings) {
            if (DomainValidation.requireNonNull(saying, "npc saying must not be null").strip().isEmpty()) {
                throw new InvalidDomainObjectError("npc saying must not be blank");
            }
        }
        return List.copyOf(sayings);
    }

    /**
     * Returns a copy of this NPC standing in {@code target} — the state change autonomous movement records.
     * The aggregate is immutable, so moving yields a <em>new</em> instance with the same identity and the new
     * position; the original is untouched. The direct twin of {@code Player.moveTo}.
     *
     * <p>A null target is a <em>caller programming error</em>, not invalid domain input — the animate use case
     * always resolves a real target scene before calling this — so it stays a plain {@link NullPointerException}
     * (the behaviour-method guard convention), distinct from the construction gate.
     *
     * @param target the scene the NPC moves into (must not be null)
     * @return a new {@code Npc} with this NPC's id and {@code target} as its current scene
     */
    public Npc moveTo(SceneId target) {
        Objects.requireNonNull(target, "target must not be null");
        return withCurrentScene(target);
    }

    /**
     * Tells whether this NPC is <em>designated</em> by the given free-text fragment — a case-insensitive
     * substring over the {@link #shortDescription} (what the player sees listed among a scene's occupants), the
     * direct {@code Item.matches} twin. So {@code "hooded"} designates "A hooded wanderer." This is the minimal
     * rule {@code hit}'s disambiguation needs; it widens when an interaction asks it to (emergence).
     *
     * <p>A null fragment is a <em>caller programming error</em> (the select subcase only calls this with a
     * non-blank, trimmed target), so it stays a plain {@link NullPointerException} per the behaviour-method
     * guard convention, distinct from the construction gate.
     */
    @Override
    public boolean matches(String fragment) {
        String needle = Objects.requireNonNull(fragment, "fragment must not be null").strip().toLowerCase(Locale.ROOT);
        return shortDescription.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * Tells whether the given raw id token identifies this NPC — a pure comparison against this NPC's own id
     * flatten ({@code id.asString()}), the {@link Designatable} re-confirmation fact. No reconstitution happens
     * here: the token's shape gate is the select concrete's, fired before any candidate is asked.
     *
     * <p>A null token is a <em>caller programming error</em> (the select subcase gates the token before
     * comparing), so it stays a plain {@link NullPointerException} per the behaviour-method guard convention.
     */
    @Override
    public boolean hasIdToken(String idToken) {
        return id.asString().equals(Objects.requireNonNull(idToken, "id token must not be null"));
    }

    /**
     * Takes {@code amount} points of damage: returns a new NPC whose {@link #hitPoints} are lowered by that
     * much (floored at zero — the clamp is {@link HitPoints}'s rule), carrying the current {@link #version}
     * forward so the persisting write is checked against the version the use case read. The original is
     * untouched (immutable copy-on-write via Lombok {@code @With}, which routes through the validating
     * constructor). The {@code Item.takenBy} twin on the write side.
     *
     * @param amount the damage to apply (must not be negative — a caller guard in {@link HitPoints#damage(int)})
     * @return a new NPC with reduced hit points, carrying this NPC's version
     */
    public Npc takeDamage(int amount) {
        return withHitPoints(hitPoints.damage(amount));
    }

    /**
     * Returns a copy of this NPC turned <em>hostile</em> — struck by the player, it enters the combat stance
     * and stays there (the animate policy re-derives the counterattack from this persisted flag every tick).
     * Immutable copy-on-write via Lombok {@code @With}, carrying the current {@link #version} forward. Already
     * hostile ↦ an equal copy; there is no de-escalation yet (pursuit / de-aggro is deferred).
     *
     * @return a new NPC in the hostile stance, carrying this NPC's version
     */
    public Npc provoked() {
        return withHostile(true);
    }

    /**
     * Whether this NPC is dead — its {@link #hitPoints} are {@linkplain HitPoints#isDepleted() spent}. A dead
     * NPC stops being returned by the scene/world queries (it is gone from listings and targeting), the kill
     * outcome of a strike.
     *
     * @return {@code true} when no hit points remain
     */
    public boolean isDead() {
        return hitPoints.isDepleted();
    }

    private static String requireNonBlank(String value, String what) {
        if (DomainValidation.requireNonNull(value, what + " must not be null").strip().isEmpty()) {
            throw new InvalidDomainObjectError(what + " must not be blank");
        }
        return value;
    }
}
