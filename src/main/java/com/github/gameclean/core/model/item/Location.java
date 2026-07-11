package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import lombok.Value;

/**
 * Where an {@link Item} currently is — a sealed value object with exactly two cases: {@link OnGround} (lying
 * in a scene, the only state items had before {@code take}) and {@link HeldBy} (carried by a holder). It
 * generalizes {@code Item}'s former {@code SceneId location} field: an item still references <em>where it
 * is</em> by identity, but that "where" is now mobile (ground ↔ held), so "the items in scene S" and "the
 * items held by P" are both queries against this reference (design-notes §2).
 *
 * <p><b>Why a sealed VO, not a nullable {@code holder} beside the {@code SceneId}.</b> Location is <em>one</em>
 * concept with a closed set of cases; a nullable holder alongside a scene id would split it across two fields
 * bound by an exactly-one-set rule the constructor must police. The sealed interface makes that invariant
 * <em>structurally impossible</em> — an item is on the ground or held, never both, never neither — and a
 * pattern-matching {@code switch} over the two cases is exhaustively checked by the compiler, so a future
 * third case (e.g. inside a container) cannot be silently forgotten at a mutate or persist site.
 *
 * <p>The holder is a {@link PlayerId} for now; it generalizes to an NPC/creature holder when that interaction
 * arrives (emergence — the same discipline that kept {@code Player} to a single field). Each case is
 * immutable and always-valid: its referenced id must not be null. Equality is by value.
 */
public sealed interface Location permits Location.OnGround, Location.HeldBy {

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
}
