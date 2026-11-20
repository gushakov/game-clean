package com.github.gameclean.core.model.npc;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.Value;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One thing an NPC offers for sale — a Value Object owned by the offering NPC.
 *
 * <p>An offer is a <em>standing authored fact</em>, written like an item's short description: a {@code ref}
 * (the authored handle the player will one day name when buying), a {@code name} (how the offer reads to the
 * player), a {@code price} counted in coins, and an {@code itemRef} — the authored handle of the item template
 * handed over on purchase. It holds <b>no state</b>: no stock, no quantity, no kind, no sold-out flag. Buying
 * is a later phase's interaction; the offer itself only records what the NPC is prepared to part with, and for
 * how many coins.
 *
 * <p>Like {@code Npc.corpseRef} and {@code Item.opens}, {@code ref} and {@code itemRef} are <em>opaque authored
 * handles</em>, not identities: neither is resolvable in-model. {@code itemRef} names an {@code items:} entry of
 * the seed, and whether it resolves — to a portable, non-container item — is an inter-template rule the
 * initialization gate checks, exactly like a corpse ref. So both stay plain {@code String}s rather than id value
 * objects; the gate asks only that each is non-blank.
 *
 * <p>Always-valid: {@code ref}, {@code name} and {@code itemRef} are non-null and non-blank, and {@code price}
 * counts at least one coin (a free offer is an authoring mistake, not a bargain — nothing is handed over for
 * zero coins). Uniqueness of {@code ref} is not a property of a single offer but of the offering NPC's list, so
 * {@link #requireUniqueRefs(List)} states that rule once for {@link Npc} and {@link NpcTemplate} to run. Equality
 * is by value.
 */
@Value
public class Offer {

    /** The offer's authored handle, unique within its NPC. */
    String ref;

    /** How the offer reads to the player — a sentence, like an item's short description. */
    String name;

    /** What the offer costs, counted in coins. At least one. */
    int price;

    /** Authored handle of the item template handed over on purchase — opaque, the {@code corpseRef} precedent. */
    String itemRef;

    public Offer(String ref, String name, int price, String itemRef) {
        this.ref = requireNonBlank(ref, "npc offer ref");
        this.name = requireNonBlank(name, "npc offer name");
        if (price < 1) {
            throw new InvalidDomainObjectError("npc offer price must be at least 1 coin, got " + price);
        }
        this.price = price;
        // A missing item handle is an authoring mistake: an offer that hands over nothing sells nothing.
        this.itemRef = requireNonBlank(itemRef, "npc offer item ref");
    }

    /**
     * The gate over an offering NPC's whole list: no two of its offers share a {@code ref}. The rule belongs to
     * the list, not to an element, so it lives here as a static the two owners ({@link Npc} and
     * {@link NpcTemplate}) run over the offers they are handed — one statement of the rule rather than two
     * copies that could drift. An empty or absent list trivially passes.
     *
     * @param offers the offers about to be owned by one NPC (non-null, elements non-null)
     * @throws InvalidDomainObjectError when two offers of the same NPC carry the same handle
     */
    static void requireUniqueRefs(List<Offer> offers) {
        Set<String> seen = new HashSet<>();
        for (Offer offer : offers) {
            if (!seen.add(offer.ref)) {
                throw new InvalidDomainObjectError(
                        "npc offers must have unique refs, got duplicate '" + offer.ref + "'");
            }
        }
    }

    private static String requireNonBlank(String value, String what) {
        if (DomainValidation.requireNonNull(value, what + " must not be null").strip().isEmpty()) {
            throw new InvalidDomainObjectError(what + " must not be blank");
        }
        return value;
    }
}
