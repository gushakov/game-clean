package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import lombok.Value;

/**
 * Where an {@link Item} currently is — a sealed value object with exactly three cases: {@link OnGround}
 * (lying in a scene, the only state items had before {@code take}), {@link HeldBy} (carried by a holder),
 * and {@link Inside} (contained in a container item). It generalizes {@code Item}'s former
 * {@code SceneId location} field: an item still references <em>where it is</em> by identity, but that
 * "where" is now mobile (ground ↔ held ↔ contained), so "the items in scene S", "the items held by P" and
 * "the items inside container C" are all queries against this reference (design-notes §2).
 *
 * <p><b>Why a sealed VO, not a nullable {@code holder} beside the {@code SceneId}.</b> Location is <em>one</em>
 * concept with a closed set of cases; nullable fields side by side would split it across fields bound by an
 * exactly-one-set rule the constructor must police. The sealed interface makes that invariant
 * <em>structurally impossible</em> — an item is on the ground, held, or contained, never two at once, never
 * none — and a pattern-matching {@code switch} over the cases is exhaustively checked by the compiler. The
 * {@link Inside} case is the proof: predicted by this very javadoc when the type was two cases, it arrived
 * (containers vertical) as a compile error at every mutate and persist {@code switch} until handled.
 *
 * <p>The holder is a {@link PlayerId} for now; it generalizes to an NPC/creature holder when that interaction
 * arrives (emergence — the same discipline that kept {@code Player} to a single field). Each case is
 * immutable and always-valid: its referenced id must not be null. Equality is by value.
 */
public sealed interface Location permits Location.OnGround, Location.HeldBy, Location.Inside {

    /**
     * The item lies on the ground in a scene, referenced by identity. Not a containment relationship — the
     * scene does not own the item; this is the item pointing at where it lies, exactly as {@code Player}
     * points at its current scene.
     */
    @Value
    class OnGround implements Location {

        SceneId scene;

        public OnGround(SceneId scene) {
            this.scene = DomainValidation.requireNonNull(scene, "ground location scene must not be null");
        }
    }

    /**
     * The item is carried by a holder (a player for now). Like {@link OnGround}, a by-identity reference — the
     * holder is not modelled as owning a collection of items; the item points at who holds it.
     */
    @Value
    class HeldBy implements Location {

        PlayerId holder;

        public HeldBy(PlayerId holder) {
            this.holder = DomainValidation.requireNonNull(holder, "held location holder must not be null");
        }
    }

    /**
     * The item is inside a container item. Like the other cases, a by-identity reference — the container does
     * not own a collection of contents; the contained item points at what holds it, so "the items inside
     * container C" is a query against this reference. Whether the referenced item can actually contain (its
     * {@code container} capability) is an <em>inter-aggregate</em> rule: this case holds only an id and cannot
     * reach out to check, so the rule is enforced where such locations are created — the seed's containment
     * validation today, a future {@code put into} interaction's checkpoint tomorrow — exactly as
     * {@code exit.target_scene_id} resolution is a use-case rule, not a constructor's.
     */
    @Value
    class Inside implements Location {

        ItemId container;

        public Inside(ItemId container) {
            this.container = DomainValidation.requireNonNull(container, "inside location container must not be null");
        }
    }
}
