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
import java.util.function.Supplier;

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

    private final String shortDescription;
    private final String fullDescription;
    private final SpawnRule spawnRule;

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
                .location(location)
                .shortDescription(shortDescription)
                .fullDescription(fullDescription)
                .build();
    }

    /**
     * Spawns this template's instances into the world: rolls the {@link SpawnRule}'s placements from the given
     * draw source and builds one always-valid {@link Item} per placement, each stamped with a fresh id pulled
     * from the given supplier. Returns the instances in placement order, empty when no attempt hits.
     *
     * <p>Both collaborators are plain JDK function types, never ports: the use case adapts its randomness and
     * id-generator output ports to a {@link DoubleSupplier} and a {@link Supplier}, so the template owns
     * spawning end-to-end — how many instances, where, and how each is built — while the core stays
     * framework-free and the use case keeps only the orchestration (looping authored items, wiring the ports,
     * collecting). An id is pulled only for an actual placement, so a missed attempt mints nothing.
     *
     * @param ids   source of freshly generated item ids — one is pulled per spawned instance
     * @param draws source of uniform random draws in {@code [0, 1)} for the spawn rolls
     * @return the spawned instances, one per successful attempt, in placement order
     */
    public List<Item> spawnInto(Supplier<ItemId> ids, DoubleSupplier draws) {
        Objects.requireNonNull(ids, "item id source must not be null");
        List<SceneId> placements = spawnRule.rollPlacements(draws);
        List<Item> instances = new ArrayList<>(placements.size());
        for (SceneId location : placements) {
            instances.add(instanceAt(ids.get(), location));
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
