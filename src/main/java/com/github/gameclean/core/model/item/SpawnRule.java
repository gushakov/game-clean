package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.scene.SceneId;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * How — and how often, and where — an item populates the world: a Value Object bundling the spawn
 * {@link Chance}, the number of placement attempts, and the candidate scenes an instance may land in.
 * Always-valid: a non-null chance, a non-negative number of tries, and at least one candidate scene (a
 * rule that could spawn nowhere is meaningless). Equality by value.
 *
 * <p>It owns the <em>whole</em> stochastic placement policy — the loop that runs up to {@code maxTries}
 * attempts, each rolling its {@link Chance} and, on a hit, picking a candidate scene ({@link #rollPlacements}).
 * The chance is rolled and the scene picked by a {@link Dice} — the game's own source of chance (a domain
 * collaborator, not a port) — so the rule stays framework-free and deterministic under test (a
 * {@link com.github.gameclean.core.model.dice.SeededDice} gives a fixed sequence). The per-attempt mechanics
 * (interpret the odds, pick uniformly among candidates) live on {@code Chance}/{@code Dice}, not here.
 *
 * <p>The candidate scene ids are {@link SceneId} value objects (well-formed by construction); whether each
 * resolves to an authored scene is an inter-aggregate world-consistency rule checked by the initialization
 * use case, not here — the same split scenes already use for their exit targets.
 */
@Value
public class SpawnRule {

    Chance chance;
    int maxTries;
    List<SceneId> candidateScenes;

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

    /**
     * Rolls this rule's placements with the given {@link Dice}: makes up to {@code maxTries} attempts, each
     * rolling this rule's {@link Chance} ({@link Dice#roll(Chance)}) and, on a hit, picking a candidate scene
     * ({@link Dice#pick(List)}). The returned list holds one {@link SceneId} per successful attempt, in attempt
     * order (so the same scene may appear more than once), and is empty when no attempt hits.
     *
     * <p>The {@link Dice} is the game's own source of chance — a domain collaborator, not a port — so the rule
     * owns the entire placement policy (count, hit decision, scene choice) end-to-end while staying
     * framework-free and deterministic under test (a {@link com.github.gameclean.core.model.dice.SeededDice}, or
     * a scripted dice, gives a fixed sequence). Driving the rolls here — rather than letting the use case
     * interleave them — keeps "an attempt rolls once, a hit picks a scene" the rule's own knowledge.
     *
     * @param dice the dice to roll and pick with
     * @return the scenes that receive an instance, one per hit, in attempt order
     */
    public List<SceneId> rollPlacements(Dice dice) {
        Objects.requireNonNull(dice, "dice must not be null");
        List<SceneId> placements = new ArrayList<>();
        for (int attempt = 0; attempt < maxTries; attempt++) {
            if (dice.roll(chance)) {
                placements.add(dice.pick(candidateScenes));
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
