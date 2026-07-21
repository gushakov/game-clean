package com.github.gameclean.infrastructure.persistence.common;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;

/**
 * Embeddable persistence shape of the domain {@code HitPoints} pool — declared {@link Embedded @Embedded} on an
 * owning {@code *DbEntity}, so its two fields land as columns of the <em>owner's own table</em> (no join, no
 * child table). A plain mutable holder like every other DB entity; conversion to and from the domain VO is the
 * shared {@link CompositeDbConverter}'s job.
 *
 * <p>Lives in this shared {@code common} package (the persistence twin of the model's neutral {@code combat/}
 * home) because hit points are not owned by any one aggregate's table: npc carries them today, the player's own
 * pool joins once retaliation arrives. The explicit column names pin the existing {@code npc} schema (V8's
 * {@code hit_points}/{@code max_hit_points}), so embedding changed no SQL; a future owner whose table needs
 * different names would reuse this shape through {@code @Embedded}'s per-use column-name {@code prefix}.
 */
@Data
public class HitPointsDbEntity {

    @Column("hit_points")
    private int current;

    @Column("max_hit_points")
    private int max;
}
