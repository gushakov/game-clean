package com.github.gameclean.core.model.npc;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.scene.SceneId;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import lombok.experimental.FieldDefaults;

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
 * <p><b>No optimistic-locking version (deferred by emergence).</b> Unlike {@code Item}, an {@code Npc} carries
 * no {@code version}: today the autonomous-movement ticker is its <em>only</em> writer (a single-writer
 * aggregate), so there is no take-vs-take race to arbitrate. A version arrives only when the player can affect
 * an NPC — the trigger for a second writer — mirroring the {@code drop} (single-writer, plain transaction) vs
 * {@code take} (contested, versioned) contrast among items.
 *
 * <p>Equality is by identity (id) only — two NPCs are the same NPC when their ids match.
 */
@Getter
@With
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class Npc {

    @EqualsAndHashCode.Include
    NpcId id;
    SceneId currentScene;
    String shortDescription;
    String fullDescription;
    Chance moveChance;

    @Builder
    public Npc(NpcId id, SceneId currentScene, String shortDescription, String fullDescription, Chance moveChance) {
        this.id = DomainValidation.requireNonNull(id, "npc id must not be null");
        this.currentScene = DomainValidation.requireNonNull(currentScene, "npc current scene must not be null");
        this.shortDescription = requireNonBlank(shortDescription, "npc short description");
        this.fullDescription = requireNonBlank(fullDescription, "npc full description");
        this.moveChance = DomainValidation.requireNonNull(moveChance, "npc move chance must not be null");
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

    private static String requireNonBlank(String value, String what) {
        if (DomainValidation.requireNonNull(value, what + " must not be null").strip().isEmpty()) {
            throw new InvalidDomainObjectError(what + " must not be blank");
        }
        return value;
    }
}
