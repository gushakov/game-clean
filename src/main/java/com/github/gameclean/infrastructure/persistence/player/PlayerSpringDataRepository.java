package com.github.gameclean.infrastructure.persistence.player;

import org.springframework.data.repository.CrudRepository;

/**
 * Spring Data JDBC repository over {@link PlayerDbEntity}. Infrastructure plumbing, not a domain
 * port: the {@code Look} use case (and the boot seeder) depend on the
 * {@code PlayerRepositoryOperationsOutputPort} instead, whose adapter delegates here.
 */
public interface PlayerSpringDataRepository extends CrudRepository<PlayerDbEntity, String> {
}
