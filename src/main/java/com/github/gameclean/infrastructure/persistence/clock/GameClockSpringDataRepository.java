package com.github.gameclean.infrastructure.persistence.clock;

import org.springframework.data.repository.CrudRepository;

/**
 * Spring Data JDBC repository over {@link GameClockDbEntity}. Infrastructure plumbing, not a domain port:
 * the time-reading, suspend, and initialization use cases depend on the
 * {@code GameClockRepositoryOperationsOutputPort} instead, whose adapter delegates here.
 */
public interface GameClockSpringDataRepository extends CrudRepository<GameClockDbEntity, String> {
}
