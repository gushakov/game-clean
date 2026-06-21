package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.scene.SceneId;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * How — and how often, and where — an item populates the world: a Value Object bundling the spawn
 * {@link Chance}, the number of placement attempts, and the candidate scenes an instance may land in.
 * Always-valid: a non-null chance, a non-negative number of tries, and at least one candidate scene (a
 * rule that could spawn nowhere is meaningless). Equality by value.
 *
 * <p>It owns the <em>whole</em> stochastic placement policy: the two pure per-attempt decisions — whether a
 * draw hits ({@link #isHitBy}) and which candidate scene a draw selects ({@link #pickScene}) — and the loop
 * that runs up to {@code maxTries} attempts ({@link #rollPlacements}). The randomness is supplied as a plain
 * {@link DoubleSupplier}, never a port, so the use case adapts its randomness output port to that supplier
 * and the rule stays framework-free and deterministic under test.
 *
 * <p>The candidate scene ids are {@link SceneId} value objects (well-formed by construction); whether each
 * resolves to an authored scene is an inter-aggregate world-consistency rule checked by the initialization
 * use case, not here — the same split scenes already use for their exit targets.
 */
@Value
public class SpawnRule {

    private final Chance chance;
    private final int maxTries;
    private final List<SceneId> candidateScenes;

    public SpawnRule(Chance chance, int maxTries, List<SceneId> candidateScenes) {
        this.chance = DomainValidation.requireNonNull(chance, "spawn chance must not be null");
        if (maxTries < 0) {
            throw new InvalidDomainObjectError("spawn max tries must not be negative, got " + maxTries);
        }
        this.maxTries = maxTries;
        this.candidateScenes = List.copyOf(
                DomainValidation.requireNonNull(candidateScenes, "spawn candidate scenes must not be null"));
        if (this.candidateScenes.isEmpty()) {
            throw new InvalidDomainObjectError("spawn rule must have at least one candidate scene");
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
     * Rolls this rule's placements from a source of random draws: makes up to {@code maxTries} attempts, each
     * consuming one draw to decide whether it hits ({@link #isHitBy}) and, on a hit, a second draw to choose
     * the scene ({@link #pickScene}). The returned list holds one {@link SceneId} per successful attempt, in
     * attempt order (so the same scene may appear more than once), and is empty when no attempt hits.
     *
     * <p>The draw <em>source</em> is a {@link DoubleSupplier}, not a randomness port: the use case adapts its
     * randomness output port to this supplier, so the rule owns the entire placement policy (count, hit
     * decision, scene choice, draw ordering) end-to-end while staying framework-free and deterministic under
     * test (supply a fixed sequence of draws). Pulling the draws here — rather than letting the use case
     * interleave them — keeps "an attempt consumes one draw, a hit a second" the rule's own knowledge.
     *
     * @param draws a source of uniform random draws in {@code [0, 1)}
     * @return the scenes that receive an instance, one per hit, in attempt order
     */
    public List<SceneId> rollPlacements(DoubleSupplier draws) {
        Objects.requireNonNull(draws, "draw source must not be null");
        List<SceneId> placements = new ArrayList<>();
        for (int attempt = 0; attempt < maxTries; attempt++) {
            if (isHitBy(draws.getAsDouble())) {
                placements.add(pickScene(draws.getAsDouble()));
            }
        }
        return placements;
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
