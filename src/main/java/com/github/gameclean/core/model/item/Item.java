package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.player.PlayerId;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
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
 * <p>An item is <em>not</em> held by the scene aggregate; it references <em>where it is</em> by identity via a
 * {@link Location} value object — {@link Location.OnGround} (lying in a scene) or {@link Location.HeldBy}
 * (carried by a player), exactly as a {@link com.github.gameclean.core.model.player.Player} references its
 * current scene by id. Aggregates reference one another by id, so "the items in a scene" and "the items a
 * player holds" are both queries against this reference, not collections owned by the scene or the player.
 * The location is mobile: {@link #takenBy(PlayerId)} moves an item from the ground into a player's keeping,
 * the first behaviour to change an item's state (immutable copy-on-write, like {@code Player.moveTo}).
 *
 * <p>It also carries an opaque optimistic-locking {@link #version} — set by persistence on read, checked by
 * persistence on write — so two actors racing to take the same ground item cannot both succeed (the loser's
 * stale write is rejected). Like {@code DayPhaseLog}, the version is carried on the model but never interpreted
 * by it; a freshly spawned item is version {@code 0} (persistence treats that as a new row). It is naturally
 * outside value equality, which is by identity (id) only — two items are the same item when their ids match.
 */
@Getter
@With
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class Item {

    @EqualsAndHashCode.Include
    ItemId id;
    Location location;
    String shortDescription;
    String fullDescription;

    /** Optimistic-locking token — opaque to the domain, managed by persistence, not part of value equality. */
    long version;

    @Builder
    public Item(ItemId id, Location location, String shortDescription, String fullDescription, long version) {
        this.id = DomainValidation.requireNonNull(id, "item id must not be null");
        this.location = DomainValidation.requireNonNull(location, "item location must not be null");
        this.shortDescription = requireNonBlank(shortDescription, "item short description");
        this.fullDescription = requireNonBlank(fullDescription, "item full description");
        if (version < 0) {
            throw new InvalidDomainObjectError("item version must not be negative, got " + version);
        }
        this.version = version;
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

    /**
     * Picks this item up into the given holder's keeping: returns a new item whose {@link #location} is
     * {@link Location.HeldBy} that holder, carrying the current {@link #version} forward so the persisting
     * write is checked against the version the use case read. The original is untouched (immutable
     * copy-on-write via Lombok {@code @With}, which routes through the validating constructor).
     *
     * <p>A null holder is a <em>caller programming error</em>, not invalid domain input — the {@code take} use
     * case always resolves a real player before calling this — so it stays a plain {@link NullPointerException}
     * (the behaviour-method guard convention), distinct from the construction gate.
     *
     * @param holder the player taking this item (must not be null)
     * @return a new item held by {@code holder}, carrying this item's version
     */
    public Item takenBy(PlayerId holder) {
        Objects.requireNonNull(holder, "holder must not be null");
        return withLocation(new Location.HeldBy(holder));
    }

    private static String requireNonBlank(String value, String what) {
        if (DomainValidation.requireNonNull(value, what + " must not be null").strip().isEmpty()) {
            throw new InvalidDomainObjectError(what + " must not be blank");
        }
        return value;
    }
}
