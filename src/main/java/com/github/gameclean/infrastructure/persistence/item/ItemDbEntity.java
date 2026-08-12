package com.github.gameclean.infrastructure.persistence.item;

import com.github.gameclean.infrastructure.persistence.common.LocationDbEntity;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC persistence entity for the {@code item} table — the storage shape of the {@code Item}
 * aggregate. A plain mutable data holder (no ORM, no proxies); conversion to and from the domain
 * {@code Item} is MapStruct's job (see {@link ItemDbEntityMapper}), so the schema can evolve independently
 * of the model.
 *
 * <p>The item's mobile {@code Location} embeds as the shared {@link LocationDbEntity} over the same
 * {@code (location_kind, location_ref)} column pair of this very table — the kind tag ({@code GROUND}/
 * {@code HELD}) and the raw id of what it references (a scene id when on the ground, a holder id when held),
 * reachable in derived queries through the embedded property path ({@code location.kind}/{@code location.ref}).
 * The ref is deliberately <em>not</em> a foreign key — an item references where it is by identity, and whether
 * that id resolves is an inter-aggregate concern, mirroring {@code player.current_scene_id} and
 * {@code exit.target_scene_id}.
 *
 * <p>The {@link #version} carries Spring Data JDBC's {@link Version optimistic-locking} token: a {@code 0}
 * version marks a new (insertable) row, and each write checks-and-increments it, so two actors racing to take
 * the same ground item cannot both win. Unlike most entities it crosses the boundary — mapped onto the domain
 * {@code Item}, which carries it through {@code takenBy} so the guarded write is checked against the version
 * the use case read (mirrors {@code DayPhaseLog}).
 */
@Data
@Table("item")
public class ItemDbEntity {

    @Id
    private String id;

    @Version
    @Column("version")
    private long version;

    @Column("short_description")
    private String shortDescription;

    @Column("full_description")
    private String fullDescription;

    @Column("container")
    private boolean container;

    @Column("anchored")
    private boolean anchored;

    @Embedded.Nullable
    private LocationDbEntity location;
}
