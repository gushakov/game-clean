package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.scene.SceneId;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
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
@Getter
@EqualsAndHashCode
public class ItemTemplate {

    private final String shortDescription;
    private final String fullDescription;
    private final SpawnRule spawnRule;

    public ItemTemplate(String shortDescription, String fullDescription, SpawnRule spawnRule) {
        this.shortDescription = requireNonBlank(shortDescription, "item short description");
        this.fullDescription = requireNonBlank(fullDescription, "item full description");
        this.spawnRule = Objects.requireNonNull(spawnRule, "item spawn rule must not be null");
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
        if (Objects.requireNonNull(value, what + " must not be null").strip().isEmpty()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        return value;
    }
}
