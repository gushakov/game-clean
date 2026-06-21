package com.github.gameclean.infrastructure.persistence.daytime;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC persistence entity for the {@code day_phase_log} table — the storage shape of the world
 * {@code DayPhaseLog} aggregate. A plain mutable data holder (no ORM, no proxies); conversion to and from the
 * domain {@code DayPhaseLog} is MapStruct's job (see {@link DayPhaseLogDbEntityMapper}).
 *
 * <p>The {@link #id} is a fixed <em>singleton</em> key (there is exactly one log), owned here in the
 * persistence ring — the domain {@code DayPhaseLog} has no identity of its own. The adapter
 * ({@link SpringDayPhaseLogRepositoryAdapter}) sets and reads it; it never crosses the boundary.
 *
 * <p>The {@link #version} carries Spring Data JDBC's {@link Version optimistic-locking} token: a {@code 0}
 * version marks a new (insertable) instance, and each write checks-and-increments it, so a stale write is
 * rejected. Unlike most entities the version <em>does</em> cross the boundary — it is mapped onto the domain
 * {@code DayPhaseLog}, which carries it through {@code announceThrough} so the guarded write is checked
 * against the version the use case read (design-notes §5).
 */
@Data
@Table("day_phase_log")
public class DayPhaseLogDbEntity {

    @Id
    private String id;

    @Version
    @Column("version")
    private long version;

    @Column("announced_through_hour")
    private long announcedThroughHour;
}
