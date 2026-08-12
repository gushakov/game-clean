package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.model.spawn.SpawnRule;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An authored kind of item together with the rule by which it populates the world — the always-valid form
 * of one {@code items:} entry in the seed. Holds the short and full descriptions every spawned instance
 * carries, the {@code container} capability the instances inherit, and the {@link SpawnRule} governing how
 * many instances appear on the ground and where. Immutable, equality by value.
 *
 * <p>The spawn rule is <em>optional</em> (authored absence, not invalid input): a contained-only item — one
 * that appears in the world exclusively inside containers — never spawns onto the ground, so its template
 * carries no rule. {@link #spawnInto(Dice)} then places nothing; the template still exists as the validity
 * gate for the descriptions and as the source {@link #spawnInside} mints contained instances from.
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

    /** Whether instances of this template can contain other items (copied onto every instance). */
    boolean container;

    /** Whether instances are fixed where they stand — refused by {@code take} (copied onto every instance). */
    boolean anchored;

    /** The ground-spawn rule, or {@code null} for a contained-only item that never spawns onto the ground. */
    SpawnRule spawnRule;

    public ItemTemplate(String shortDescription, String fullDescription, boolean container, boolean anchored,
                        SpawnRule spawnRule) {
        this.shortDescription = requireNonBlank(shortDescription, "item short description");
        this.fullDescription = requireNonBlank(fullDescription, "item full description");
        this.container = container;
        this.anchored = anchored;
        this.spawnRule = spawnRule;
    }

    /**
     * Builds one item instance of this template at the given location, stamped with the given freshly
     * generated id. The descriptions and the container capability are copied onto the instance — instances
     * hold their own state and do not reference the template.
     */
    public Item instanceAt(ItemId id, SceneId location) {
        return instanceWith(id, new Location.OnGround(location));
    }

    /**
     * Builds one item instance of this template inside the given container item, stamped with the given
     * freshly generated id — the containment counterpart of {@link #instanceAt(ItemId, SceneId)}.
     */
    public Item instanceInside(ItemId id, ItemId container) {
        return instanceWith(id, new Location.Inside(container));
    }

    private Item instanceWith(ItemId id, Location location) {
        return Item.builder()
                .id(id)
                .location(location)
                .shortDescription(shortDescription)
                .fullDescription(fullDescription)
                .container(container)
                .anchored(anchored)
                .build();
    }

    /**
     * Spawns this template's instances onto the ground: rolls the {@link SpawnRule}'s placements with the given
     * {@link Dice} and builds one always-valid {@link Item} per placement, each stamped with a fresh id minted
     * from the <em>same</em> dice ({@link ItemId#mint(Dice)}). Returns the instances in placement order, empty
     * when no attempt hits — or when this template carries no spawn rule at all (a contained-only item).
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
        if (spawnRule == null) {
            return List.of();
        }
        List<SceneId> placements = spawnRule.rollPlacements(dice);
        List<Item> instances = new ArrayList<>(placements.size());
        for (SceneId location : placements) {
            instances.add(instanceAt(ItemId.mint(dice), location));
        }
        return instances;
    }

    /**
     * Spawns at most one instance of this template inside the given container item: rolls the given
     * {@link Chance} once with the given {@link Dice} and, on a hit, builds an always-valid {@link Item}
     * located {@link Location.Inside} the container, stamped with a fresh id minted from the same dice —
     * the containment counterpart of {@link #spawnInto(Dice)}, with the same discipline (the template owns
     * how an instance is built; an id is minted only for an actual appearance).
     *
     * <p>The chance is handed in as a <em>value</em> rather than held by this template because it is not this
     * template's fact: it belongs to the <em>container's</em> authored containment entry ("the chest may hold
     * a dagger, odds 1/45") — the use case resolves that authoring and hands the odds to the contained
     * template (dependency rejection: values in, computation here).
     *
     * <p>Null arguments are <em>caller programming errors</em>, not invalid domain input, so they stay plain
     * {@link NullPointerException}s per the behaviour-method guard convention.
     *
     * @param dice      the dice to roll the appearance and mint the id with
     * @param chance    the odds that the instance appears inside the container
     * @param container the id of the container instance to place the item into
     * @return the spawned instance, or empty when the roll misses
     */
    public Optional<Item> spawnInside(Dice dice, Chance chance, ItemId container) {
        Objects.requireNonNull(dice, "dice must not be null");
        Objects.requireNonNull(chance, "chance must not be null");
        Objects.requireNonNull(container, "container must not be null");
        if (!dice.roll(chance)) {
            return Optional.empty();
        }
        return Optional.of(instanceInside(ItemId.mint(dice), container));
    }

    /**
     * This template's candidate spawn scenes that are not among the given known scene ids — empty when the
     * template carries no spawn rule (a contained-only item references no scenes). Delegates to the
     * {@link SpawnRule}, so a caller asks the template rather than reaching through it into the rule (Law of
     * Demeter); the composition "a template has a spawn rule" stays the template's private business.
     *
     * @param knownSceneIds the identities of the scenes that actually exist in the world being built
     * @return this template's unresolved candidate scene ids
     */
    public List<SceneId> candidateScenesNotIn(Set<SceneId> knownSceneIds) {
        return spawnRule == null ? List.of() : spawnRule.candidateScenesNotIn(knownSceneIds);
    }

    private static String requireNonBlank(String value, String what) {
        if (DomainValidation.requireNonNull(value, what + " must not be null").strip().isEmpty()) {
            throw new InvalidDomainObjectError(what + " must not be blank");
        }
        return value;
    }
}
