package com.github.gameclean.core.model.npc;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.designation.Designatable;
import lombok.Value;

import java.util.Locale;
import java.util.Objects;

/**
 * What one NPC stands ready to hand over — an {@link Offer} paired with the {@link Npc} that makes it. The
 * unit a {@code buy} transaction is about: an offer alone does not say who is selling, and the vendor matters
 * twice over — the buyer pays <em>this</em> NPC and the ware is minted into the buyer's hands from
 * <em>this</em> NPC's offer, so the pairing cannot be left implicit in a candidate list.
 *
 * <p>It is a {@link Designatable} projection over authored data, assembled per designation from a living
 * vendor's offer list and never persisted: {@link #matches(String)} is a case-insensitive substring over the
 * offer's {@code name} only (the {@code Item}/{@code Npc} normalization twin), never over the vendor's
 * description — naming a ware selects among wares, not among vendors. {@link #idToken()} is the composite
 * handle {@code vendorId:offerRef}, e.g. {@code npc2:room}.
 *
 * <p>Both parts are required: a standing offer without a vendor or without an offer cannot be constructed
 * (always-valid). Equality is by value, as for any value object: the same vendor (an {@code Npc} is equal by
 * its id) making an equal offer.
 */
@Value
public class StandingOffer implements Designatable {

    /**
     * The separator joining the vendor id and the offer handle into a composite id token. It may not appear in
     * an offer handle: an authored handle containing it would make a rendered token ambiguous.
     */
    public static final String TOKEN_SEPARATOR = ":";

    /** The NPC this offer stands with — the party the buyer pays. Never null. */
    Npc vendor;

    /** What the vendor stands ready to hand over. Never null. */
    Offer offer;

    public StandingOffer(Npc vendor, Offer offer) {
        this.vendor = DomainValidation.requireNonNull(vendor, "standing offer vendor must not be null");
        this.offer = DomainValidation.requireNonNull(offer, "standing offer must not be null");
    }

    /**
     * This ware's composite candidate handle: the vendor's id flattened, the {@link #TOKEN_SEPARATOR}, and the
     * offer's handle — {@code npc2:room}. It is what the disambiguation menu numbers and what
     * {@link #hasIdToken(String)} is confirmed against.
     */
    public String idToken() {
        return vendor.getId().asString() + TOKEN_SEPARATOR + offer.getRef();
    }

    /**
     * Tells whether this ware is <em>designated</em> by the given free-text fragment — a case-insensitive
     * substring over the {@link Offer#getName()} only (the ware is what the player names), normalized exactly
     * as {@code Item.matches} normalizes: fragment trimmed and lower-cased, name lower-cased. So {@code "room"}
     * designates "A room for the night.".
     *
     * <p>A null fragment is a <em>caller programming error</em> (the select subcase only calls this with a
     * non-blank, trimmed target), so it stays a plain {@link NullPointerException} per the behaviour-method
     * guard convention, distinct from construction.
     */
    @Override
    public boolean matches(String fragment) {
        String needle = Objects.requireNonNull(fragment, "fragment must not be null").strip().toLowerCase(Locale.ROOT);
        return offer.getName().toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * Tells whether the given raw id token identifies this ware — a pure comparison against this ware's own
     * composite {@link #idToken()}, the {@link Designatable} re-confirmation fact. No reconstitution happens
     * here: the token's shape gate is the select concrete's, fired before any candidate is asked.
     *
     * <p>A null token is a <em>caller programming error</em> (the select subcase gates the token before
     * comparing), so it stays a plain {@link NullPointerException} per the behaviour-method guard convention.
     */
    @Override
    public boolean hasIdToken(String idToken) {
        return idToken().equals(Objects.requireNonNull(idToken, "id token must not be null"));
    }

    /**
     * The shape gate for a remembered id token: it must carry the {@link #TOKEN_SEPARATOR} with content on
     * both sides — a left side a well-formed {@link NpcId}, and a non-blank handle right of the <em>first</em>
     * separator (a handle may itself contain separators, so only the first one splits). Fired eagerly by the
     * select concrete before any read: a malformed token came from the system's own remembered menu, not from
     * the player, so it is an internal fault — an {@link InvalidDomainObjectError} propagating to the use
     * case's catch-all.
     *
     * @param idToken the remembered composite token to check (must not be null — a caller guard)
     * @throws InvalidDomainObjectError when the token lacks the separator or has a blank side
     */
    public static void requireWellFormedToken(String idToken) {
        Objects.requireNonNull(idToken, "id token must not be null");
        int separator = idToken.indexOf(TOKEN_SEPARATOR);
        if (separator < 0) {
            throw new InvalidDomainObjectError(
                    "standing-offer id token must be of the form '<vendor id>" + TOKEN_SEPARATOR
                            + "<offer handle>', got " + idToken);
        }
        NpcId.of(idToken.substring(0, separator));
        if (idToken.substring(separator + TOKEN_SEPARATOR.length()).strip().isEmpty()) {
            throw new InvalidDomainObjectError(
                    "standing-offer id token must name an offer handle after the separator, got " + idToken);
        }
    }
}
