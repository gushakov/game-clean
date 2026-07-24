package com.github.gameclean.core.model.player;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.scene.SceneId;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import lombok.experimental.FieldDefaults;

/**
 * The player — the aggregate root of the player aggregate.
 *
 * <p>Immutable and always-valid: a {@code Player} cannot be constructed without an id, a current scene,
 * or a hit-point pool. Its state is deliberately <em>minimal</em> — where the player currently is, and,
 * since retaliation arrived, how much health remains. The aggregate grows only when an interaction forces
 * it to; {@code look} forced only the position, and {@code hit}'s NPC counterstrike (#66 step 2) forced
 * the health pool, because an NPC striking the player needs somewhere to record the damage.
 *
 * <p>The current scene is referenced <em>by identity</em> ({@link SceneId}), not by holding a
 * {@code Scene}: aggregates reference one another by id. Whether that id resolves to a real scene is
 * an inter-aggregate world-consistency rule checked by the use case that reads it, not an invariant
 * of this entity.
 *
 * <p>It carries {@link HitPoints} (spawned at full health) and takes damage via {@link #takeDamage(int)};
 * a player whose pool is spent {@linkplain #isDead() is dead}. It also carries an opaque optimistic-locking
 * {@link #version} — set by persistence on read, checked on write — exactly like {@code Npc}: an NPC's
 * counterstrike and the player's own {@code move} now both write the player, so two actors racing to write
 * it cannot both succeed. A freshly spawned player is version {@code 0} (persistence treats that as a new row).
 *
 * <p>Equality is by identity (id) only — the version and hit points are naturally outside value equality.
 */
@Getter
@With
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class Player {

    @EqualsAndHashCode.Include
    PlayerId id;
    SceneId currentScene;
    HitPoints hitPoints;

    /** Optimistic-locking token — opaque to the domain, managed by persistence, not part of value equality. */
    long version;

    @Builder
    public Player(PlayerId id, SceneId currentScene, HitPoints hitPoints, long version) {
        this.id = DomainValidation.requireNonNull(id, "player id must not be null");
        this.currentScene = DomainValidation.requireNonNull(currentScene, "player current scene must not be null");
        this.hitPoints = DomainValidation.requireNonNull(hitPoints, "player hit points must not be null");
        if (version < 0) {
            throw new InvalidDomainObjectError("player version must not be negative, got " + version);
        }
        this.version = version;
    }

    /**
     * Returns a copy of this player standing in {@code target} — the state change {@code move} records.
     * The aggregate is immutable, so moving yields a <em>new</em> instance with the same identity and the
     * new position (its hit points and version carried forward); the original is untouched. This is the
     * player's first behaviour beyond holding its position, surfaced because {@code move} forced it — the
     * same emergence discipline that kept the aggregate to a single field until now. A null target is
     * rejected by the constructor's validity gate.
     *
     * @param target the scene the player moves into
     * @return a new {@code Player} with this player's id and {@code target} as its current scene
     */
    public Player moveTo(SceneId target) {
        return withCurrentScene(target);
    }

    /**
     * Takes {@code amount} points of damage: returns a new player whose {@link #hitPoints} are lowered by
     * that much (floored at zero — the clamp is {@link HitPoints}'s rule), carrying the current
     * {@link #version} forward so the persisting write is checked against the version the use case read. The
     * original is untouched (immutable copy-on-write via Lombok {@code @With}, which routes through the
     * validating constructor). The {@code Npc.takeDamage} twin on the player's side.
     *
     * @param amount the damage to apply (must not be negative — a caller guard in {@link HitPoints#damage(int)})
     * @return a new player with reduced hit points, carrying this player's version
     */
    public Player takeDamage(int amount) {
        return withHitPoints(hitPoints.damage(amount));
    }

    /**
     * Whether this player is dead — its {@link #hitPoints} are {@linkplain HitPoints#isDepleted() spent}.
     * The kill outcome of an NPC's counterstrike.
     *
     * @return {@code true} when no hit points remain
     */
    public boolean isDead() {
        return hitPoints.isDepleted();
    }
}
