package com.github.gameclean.infrastructure.persistence.daytime;

import com.github.gameclean.core.model.daytime.DayPhaseLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the domain {@code DayPhaseLog} aggregate and its persistence shape
 * ({@link DayPhaseLogDbEntity}) — the shock-absorber layer that lets schema and model evolve at different
 * speeds.
 *
 * <p>The domain {@code DayPhaseLog} carries no identity (there is exactly one log), so {@link #toDbEntity}
 * stamps the fixed {@link #SINGLETON_ID} that the {@code day_phase_log} singleton row lives under, and
 * {@link #toDomain} simply drops it. Reconstitution runs the always-valid constructor (a watermark below the
 * sentinel read from the database would surface as a domain error rather than slipping through).
 */
@Mapper(componentModel = "spring")
public interface DayPhaseLogDbEntityMapper {

    /** The fixed primary key of the world-singleton day-phase-log row. */
    String SINGLETON_ID = "day-phase-log";

    @Mapping(target = "id", constant = SINGLETON_ID)
    DayPhaseLogDbEntity toDbEntity(DayPhaseLog log);

    DayPhaseLog toDomain(DayPhaseLogDbEntity entity);
}
