package com.github.gameclean.infrastructure.persistence.daytime;

import org.springframework.data.repository.CrudRepository;

/**
 * Spring Data JDBC repository over {@link DayPhaseLogDbEntity}. Infrastructure plumbing, not a domain port:
 * the time-of-day announcement and initialization use cases depend on the
 * {@code DayPhaseLogRepositoryOperationsOutputPort} instead, whose adapter delegates here.
 */
public interface DayPhaseLogSpringDataRepository extends CrudRepository<DayPhaseLogDbEntity, String> {
}
