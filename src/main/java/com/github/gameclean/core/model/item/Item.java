package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.scene.SceneId;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.Locale;
import java.util.Objects;

/**
 * A thing that can be found in the world — the aggregate root of the item aggregate.
 *
 * <p>Immutable and always-valid: an {@code Item} cannot be constructed without an id or a location, or
 * with a blank short or full description. It carries a {@code shortDescription} (how it reads when listed
 * among a scene's contents) and a {@code fullDescription} (reserved for {@code examine}; not yet rendered,
 * just as {@code Scene.shortDescription} exists ahead of its consumer).
 *
 * <p>An item is <em>not</em> held by the scene aggregate; it references <em>where it is</em> by identity
 * ({@link SceneId location}), exactly as a {@link com.github.gameclean.core.model.player.Player} references
 * its current scene and an {@code Exit} references its target. Aggregates reference one another by id, so
 * "the items in a scene" is a query against this reference, not a collection owned by the scene. The
 * location is a {@code SceneId} because items are only ever on the ground for now; it generalizes (to a
 * player's or NPC's possession) when {@code take}/inventory forces it — the same emergence discipline that
 * kept {@code Player} to a single field.
 *
 * <p>Equality is by identity (id) only — two items are the same item when their ids match, even if two
 * instances spawned from the same authored template share every description.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class Item {

    @EqualsAndHashCode.Include
    ItemId id;
    SceneId location;
    String shortDescription;
    String fullDescription;

    @Builder
    public Item(ItemId id, SceneId location, String shortDescription, String fullDescription) {
        this.id = DomainValidation.requireNonNull(id, "item id must not be null");
        this.location = DomainValidation.requireNonNull(location, "item location must not be null");
        this.shortDescription = requireNonBlank(shortDescription, "item short description");
        this.fullDescription = requireNonBlank(fullDescription, "item full description");
    }

    /**
     * Tells whether this item is <em>designated</em> by the given free-text fragment — a side-effect-free
     * query the {@code examine} use case uses to resolve a player's typed target against the things on the
     * ground. The test is a case-insensitive substring over the {@link #shortDescription} (what the player
     * sees listed), so {@code "rusty"} designates both "A rusty dagger." and "A rusty key." — which is exactly
     * the ambiguity {@code examine} must disambiguate.
     *
     * <p>Matching the short description only (not the full one, reserved for the reveal) is the minimal rule
     * the interaction needs; it widens when an interaction asks it to (emergence). Behaviour lives on the item
     * itself — Tell-Don't-Ask — so the use case asks each item "are you the one?" rather than reaching into its
     * fields.
     *
     * <p>A null fragment is a <em>caller programming error</em>, not invalid domain input, so it stays a plain
     * {@link NullPointerException} — the boundary between behaviour-method guards and the construction gate
     * (which throws {@link InvalidDomainObjectError}). The use case only ever calls this with a non-blank,
     * already-trimmed target.
     */
    public boolean matches(String fragment) {
        String needle = Objects.requireNonNull(fragment, "fragment must not be null").strip().toLowerCase(Locale.ROOT);
        return shortDescription.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String requireNonBlank(String value, String what) {
        if (DomainValidation.requireNonNull(value, what + " must not be null").strip().isEmpty()) {
            throw new InvalidDomainObjectError(what + " must not be blank");
        }
        return value;
    }
}
