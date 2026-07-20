package com.github.gameclean.core.model.npc;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.designation.Designatable;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.scene.SceneId;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import lombok.experimental.FieldDefaults;

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
 * <p>An NPC is a designation target ({@link Designatable}): {@code hit <npc>} resolves a typed fragment
 * against the NPCs in the scene exactly as {@code examine}/{@code take} resolve items, so an {@code Npc}
 * answers {@link #matches(String)} and {@link #hasIdToken(String)} — the first non-item consumer of the
 * generic select subcase.
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
    HitPoints hitPoints;

    /** Optimistic-locking token — opaque to the domain, managed by persistence, not part of value equality. */
    long version;

    @Builder
    public Npc(NpcId id, SceneId currentScene, String shortDescription, String fullDescription, Chance moveChance,
               HitPoints hitPoints, long version) {
        this.id = DomainValidation.requireNonNull(id, "npc id must not be null");
        this.currentScene = DomainValidation.requireNonNull(currentScene, "npc current scene must not be null");
        this.shortDescription = requireNonBlank(shortDescription, "npc short description");
        this.fullDescription = requireNonBlank(fullDescription, "npc full description");
        this.moveChance = DomainValidation.requireNonNull(moveChance, "npc move chance must not be null");
        this.hitPoints = DomainValidation.requireNonNull(hitPoints, "npc hit points must not be null");
        if (version < 0) {
            throw new InvalidDomainObjectError("npc version must not be negative, got " + version);
        }
        this.version = version;
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
