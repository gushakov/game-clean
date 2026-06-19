package com.github.gameclean.infrastructure.persistence.clock;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC persistence entity for the {@code game_clock} table — the storage shape of the world
 * {@code GameClock} aggregate. A plain mutable data holder (no ORM, no proxies); conversion to and from the
 * domain {@code GameClock} is MapStruct's job (see {@link GameClockDbEntityMapper}).
 *
 * <p>The {@link #id} is a fixed <em>singleton</em> key (there is exactly one clock), owned here in the
 * persistence ring — the domain {@code GameClock} has no identity of its own. The adapter
 * ({@link SpringGameClockRepositoryAdapter}) sets and reads it; it never crosses the boundary.
 */
@Data
@Table("game_clock")
public class GameClockDbEntity {

    @Id
    private String id;

    @Column("accumulated_game_seconds")
    private long accumulatedGameSeconds;
}
