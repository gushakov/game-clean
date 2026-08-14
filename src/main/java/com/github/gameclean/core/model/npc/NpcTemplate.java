package com.github.gameclean.core.model.npc;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.model.spawn.SpawnRule;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An authored kind of NPC together with the rule by which it populates the world — the always-valid form of
 * one {@code npcs:} entry in the seed. The NPC twin of {@code ItemTemplate}: it holds the short and full
 * descriptions every spawned instance carries and the {@link SpawnRule} governing how many instances appear
 * and where, <em>plus</em> the authored {@link Chance moveChance} each spawned NPC wanders with and the
 * authored {@link Chance attackChance} it strikes back with once provoked. Immutable, equality by value.
 *
 * <p>It is <em>transient</em>: the initialization use case constructs it as the validity gate for authored NPC
 * input, uses it to spawn instances, and never persists it (instances copy its descriptions and move chance;
 * there is no persisted template). Validating the descriptions, the spawn rule, and the move chance <em>here</em>,
 * rather than only when an instance is finally built, is the point: an invalid template is rejected up front,
 * independent of how the random spawn rolls fall, closing the gap where an invalid template might otherwise
 * never be exercised.
 *
 * <p>The optional {@code corpseRef} (authored absence when {@code null}) is the authored handle of the item
 * template minted as an instance's corpse when it is slain; it is copied onto every spawned instance like the
 * descriptions. Whether it <em>resolves</em> — to an authored container without a ground-spawn rule — is an
 * inter-template rule the use-case gate checks, exactly like containment targets.
 */
@Value
public class NpcTemplate {

    String shortDescription;
    String fullDescription;
    SpawnRule spawnRule;
    Chance moveChance;
    Chance attackChance;
    int maxHitPoints;

    /** Authored handle of the corpse item template minted when an instance is slain — null when it leaves none. */
    String corpseRef;

    public NpcTemplate(String shortDescription, String fullDescription, SpawnRule spawnRule, Chance moveChance,
                       Chance attackChance, int maxHitPoints, String corpseRef) {
        this.shortDescription = requireNonBlank(shortDescription, "npc short description");
        this.fullDescription = requireNonBlank(fullDescription, "npc full description");
        this.spawnRule = DomainValidation.requireNonNull(spawnRule, "npc spawn rule must not be null");
        this.moveChance = DomainValidation.requireNonNull(moveChance, "npc move chance must not be null");
        this.attackChance = DomainValidation.requireNonNull(attackChance, "npc attack chance must not be null");
        if (maxHitPoints <= 0) {
            throw new InvalidDomainObjectError("npc max hit points must be strictly positive, got " + maxHitPoints);
        }
        this.maxHitPoints = maxHitPoints;
        // Authored absence is a valid state (the NPC leaves no corpse); a present ref must carry content.
        if (corpseRef != null && corpseRef.strip().isEmpty()) {
            throw new InvalidDomainObjectError("npc corpse ref must not be blank when present");
        }
        this.corpseRef = corpseRef;
    }

    /**
     * Builds one NPC instance of this template at the given scene, stamped with the given freshly generated id.
     * The descriptions, move chance, attack chance and corpse ref are copied onto the instance — instances hold
     * their own state and do not reference the template. The instance spawns at full health
     * ({@link HitPoints#full(int)} of {@link #maxHitPoints}), <em>non-hostile</em>, and version {@code 0} (a
     * new, not-yet-persisted row).
     */
    public Npc instanceAt(NpcId id, SceneId currentScene) {
        return Npc.builder()
                .id(id)
                .currentScene(currentScene)
                .shortDescription(shortDescription)
                .fullDescription(fullDescription)
                .moveChance(moveChance)
                .attackChance(attackChance)
                .hitPoints(HitPoints.full(maxHitPoints))
                .hostile(false)
                .corpseRef(corpseRef)
                .version(0)
                .build();
    }

    /**
     * Spawns this template's instances into the world: rolls the {@link SpawnRule}'s placements with the given
     * {@link Dice} and builds one always-valid {@link Npc} per placement, each stamped with a fresh id minted
     * from the <em>same</em> dice ({@link NpcId#mint(Dice)}). Returns the instances in placement order, empty
     * when no attempt hits. The direct twin of {@code ItemTemplate.spawnInto}.
     *
     * @param dice the dice to roll the placements and mint the ids with
     * @return the spawned instances, one per successful attempt, in placement order
     */
    public List<Npc> spawnInto(Dice dice) {
        List<SceneId> placements = spawnRule.rollPlacements(dice);
        List<Npc> instances = new ArrayList<>(placements.size());
        for (SceneId scene : placements) {
            instances.add(instanceAt(NpcId.mint(dice), scene));
        }
        return instances;
    }

    /**
     * This template's candidate spawn scenes that are not among the given known scene ids. Delegates to the
     * {@link SpawnRule}, so a caller asks the template rather than reaching through it into the rule (Law of
     * Demeter); the composition "a template has a spawn rule" stays the template's private business.
     *
     * @param knownSceneIds the identities of the scenes that actually exist in the world being built
     * @return this template's unresolved candidate scene ids
     */
    public List<SceneId> candidateScenesNotIn(Set<SceneId> knownSceneIds) {
        return spawnRule.candidateScenesNotIn(knownSceneIds);
    }

    private static String requireNonBlank(String value, String what) {
        if (DomainValidation.requireNonNull(value, what + " must not be null").strip().isEmpty()) {
            throw new InvalidDomainObjectError(what + " must not be blank");
        }
        return value;
    }
}
