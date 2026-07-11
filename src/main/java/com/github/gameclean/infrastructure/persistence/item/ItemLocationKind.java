package com.github.gameclean.infrastructure.persistence.item;

import com.github.gameclean.core.model.item.Location;

/**
 * Persistence-side discriminator for an item's {@link Location}: the storage shape of the sealed location VO,
 * flattened to a {@code (location_kind, location_ref)} column pair. {@code GROUND} pairs with a scene id,
 * {@code HELD} with a holder (player) id.
 *
 * <p>It lives in the persistence ring, not the domain: which string tags the database uses is an encoding
 * artifact, not a domain rule (the domain's {@code Location} is a sealed type, not a string). The mapper
 * ({@link ItemDbEntityMapper}) owns the case↔kind correspondence with an exhaustive {@code switch}, so adding
 * a {@code Location} case is a compile error there until this enum and the mapping grow with it. Spring Data
 * JDBC stores the enum by {@link #name()} in the {@code location_kind} varchar column.
 */
public enum ItemLocationKind {

    /** The item lies on the ground; the ref is a scene id. Maps to {@link Location.OnGround}. */
    GROUND,

    /** The item is carried by a holder; the ref is a player id. Maps to {@link Location.HeldBy}. */
    HELD
}
