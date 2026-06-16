package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.scene.SceneId;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * How — and how often, and where — an item populates the world: a Value Object bundling the spawn
 * {@link Chance}, the number of placement attempts, and the candidate scenes an instance may land in.
 * Always-valid: a non-null chance, a non-negative number of tries, and at least one candidate scene (a
 * rule that could spawn nowhere is meaningless). Equality by value.
 *
 * <p>It owns the two <em>pure</em> decisions the spawn algorithm delegates to the domain — whether a draw
 * hits ({@link #isHitBy}) and which candidate scene a draw selects ({@link #pickScene}) — while the
 * randomness itself comes from a port, so the use case orchestrating it stays deterministic under test.
 *
 * <p>The candidate scene ids are {@link SceneId} value objects (well-formed by construction); whether each
 * resolves to an authored scene is an inter-aggregate world-consistency rule checked by the initialization
 * use case, not here — the same split scenes already use for their exit targets.
 */
@Getter
@EqualsAndHashCode
public class SpawnRule {

    private final Chance chance;
    private final int maxTries;
    private final List<SceneId> candidateScenes;

    public SpawnRule(Chance chance, int maxTries, List<SceneId> candidateScenes) {
        this.chance = Objects.requireNonNull(chance, "spawn chance must not be null");
        if (maxTries < 0) {
            throw new IllegalArgumentException("spawn max tries must not be negative, got " + maxTries);
        }
        this.maxTries = maxTries;
        this.candidateScenes = List.copyOf(
                Objects.requireNonNull(candidateScenes, "spawn candidate scenes must not be null"));
        if (this.candidateScenes.isEmpty()) {
            throw new IllegalArgumentException("spawn rule must have at least one candidate scene");
        }
    }

    /** Whether a spawn attempt with this draw succeeds — delegates to the {@link Chance}. */
    public boolean isHitBy(double draw) {
        return chance.isHitBy(draw);
    }

    /**
     * Selects the candidate scene a successful attempt places its instance in, scaling the draw across the
     * candidates. The draw is expected in {@code [0, 1)}; the top of the range is clamped so a draw
     * arbitrarily close to 1 still selects the last candidate rather than running off the end.
     *
     * @param draw a uniform random draw in {@code [0, 1)}
     * @return the chosen candidate scene
     */
    public SceneId pickScene(double draw) {
        int index = (int) (draw * candidateScenes.size());
        if (index >= candidateScenes.size()) {
            index = candidateScenes.size() - 1;
        }
        return candidateScenes.get(index);
    }

    /**
     * The candidate scenes of this rule that are <em>not</em> among the given known scene ids — this rule's
     * contribution to the inter-aggregate world-consistency check the initialization use case performs.
     * Expressed purely in identities, so a spawn rule never references the
     * {@link com.github.gameclean.core.model.scene.Scene} aggregate (only the shared {@link SceneId} it
     * already holds): the use case asks the rule about its own candidates rather than reaching in to read
     * them. A side-effect-free function — empty when every candidate resolves.
     *
     * @param knownSceneIds the identities of the scenes that actually exist in the world being built
     * @return this rule's unresolved candidate scene ids, in declaration order
     */
    public List<SceneId> candidateScenesNotIn(Set<SceneId> knownSceneIds) {
        Objects.requireNonNull(knownSceneIds, "known scene ids must not be null");
        return candidateScenes.stream()
                .filter(id -> !knownSceneIds.contains(id))
                .toList();
    }
}
