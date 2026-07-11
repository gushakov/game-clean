package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.scene.SceneId;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An authored kind of item together with the rule by which it populates the world — the always-valid form
 * of one {@code items:} entry in the seed. Holds the short and full descriptions every spawned instance
 * carries, and the {@link SpawnRule} governing how many instances appear and where. Immutable, equality by
 * value.
 *
 * <p>It is <em>transient</em>: the initialization use case constructs it as the validity gate for authored
 * item input, uses it to spawn instances, and never persists it (instances copy its descriptions; there is
 * no persisted template — see design notes). Validating the descriptions <em>here</em>, rather than only
 * when an instance is finally built, is the point: a blank description is rejected up front, independent of
 * how the random spawn rolls fall, closing the gap where an invalid template might otherwise never be
 * exercised.
 */
@Value
public class ItemTemplate {

    String shortDescription;
    String fullDescription;
    SpawnRule spawnRule;

    public ItemTemplate(String shortDescription, String fullDescription, SpawnRule spawnRule) {
        this.shortDescription = requireNonBlank(shortDescription, "item short description");
        this.fullDescription = requireNonBlank(fullDescription, "item full description");
        this.spawnRule = DomainValidation.requireNonNull(spawnRule, "item spawn rule must not be null");
    }

    /**
     * Builds one item instance of this template at the given location, stamped with the given freshly
     * generated id. The descriptions are copied onto the instance — instances hold their own descriptions
     * and do not reference the template.
     */
    public Item instanceAt(ItemId id, SceneId location) {
        return Item.builder()
                .id(id)
                .location(new Location.OnGround(location))
                .shortDescription(shortDescription)
                .fullDescription(fullDescription)
                .build();
    }

    /**
     * Spawns this template's instances into the world: rolls the {@link SpawnRule}'s placements with the given
     * {@link Dice} and builds one always-valid {@link Item} per placement, each stamped with a fresh id minted
     * from the <em>same</em> dice ({@link ItemId#mint(Dice)}). Returns the instances in placement order, empty
     * when no attempt hits.
     *
     * <p>The {@link Dice} is the only collaborator — a domain capability the model owns. The template owns
     * spawning end-to-end (how many instances, where, and how each — id and all — is built); there is no longer
     * an id <em>output port</em> threaded in, because the model mints its own identities from its own dice. The
     * use case keeps only the orchestration (looping authored items, holding the dice, collecting). An id is
     * minted only for an actual placement, so a missed attempt mints nothing.
     *
     * @param dice the dice to roll the placements and mint the ids with
     * @return the spawned instances, one per successful attempt, in placement order
     */
    public List<Item> spawnInto(Dice dice) {
        List<SceneId> placements = spawnRule.rollPlacements(dice);
        List<Item> instances = new ArrayList<>(placements.size());
        for (SceneId location : placements) {
            instances.add(instanceAt(ItemId.mint(dice), location));
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
