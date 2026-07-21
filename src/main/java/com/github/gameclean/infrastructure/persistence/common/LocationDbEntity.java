package com.github.gameclean.infrastructure.persistence.common;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;

/**
 * Embeddable persistence shape of the domain {@code Location} — declared {@link Embedded @Embedded} on an
 * owning {@code *DbEntity}, so its two fields land as columns of the <em>owner's own table</em> (no join, no
 * child table). A plain mutable holder like every other DB entity; conversion to and from the sealed domain VO
 * is the shared {@link CompositeDbConverter}'s job.
 *
 * <p>The sealed {@code Location} itself is sum-shaped and cannot be embedded structurally; what embeds is its
 * flattened <em>encoding</em> — the {@code (location_kind, location_ref)} product: the {@link ItemLocationKind
 * kind} tag and the raw id of what it references (a scene id on the ground, a holder id when held). The ref is
 * deliberately <em>not</em> a foreign key — an item references where it is by identity, and whether that id
 * resolves is an inter-aggregate concern, mirroring {@code player.current_scene_id} and
 * {@code exit.target_scene_id}. The explicit column names pin the existing {@code item} schema (V6), so
 * embedding changed no SQL.
 */
@Data
public class LocationDbEntity {

    @Column("location_kind")
    private ItemLocationKind kind;

    @Column("location_ref")
    private String ref;
}
