package com.github.gameclean.infrastructure.persistence.clock;

import com.github.gameclean.core.model.clock.GameClock;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the domain {@code GameClock} aggregate and its persistence shape
 * ({@link GameClockDbEntity}) — the shock-absorber layer that lets schema and model evolve at different
 * speeds.
 *
 * <p>The domain {@code GameClock} carries no identity (there is exactly one clock), so {@link #toDbEntity}
 * stamps the fixed {@link #SINGLETON_ID} that the {@code game_clock} singleton row lives under, and
 * {@link #toDomain} simply drops it. Reconstitution runs the always-valid constructor (a negative banked
 * total read from the database would surface as a domain error rather than slipping through).
 */
@Mapper(componentModel = "spring")
public interface GameClockDbEntityMapper {

    /** The fixed primary key of the world-singleton clock row. */
    String SINGLETON_ID = "clock";

    @Mapping(target = "id", constant = SINGLETON_ID)
    GameClockDbEntity toDbEntity(GameClock clock);

    GameClock toDomain(GameClockDbEntity entity);
}
