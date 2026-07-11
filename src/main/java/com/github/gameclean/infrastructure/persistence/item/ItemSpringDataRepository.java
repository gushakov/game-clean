package com.github.gameclean.infrastructure.persistence.item;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Spring Data JDBC repository over {@link ItemDbEntity}. Infrastructure plumbing, not a domain port: the
 * use cases depend on the {@code ItemRepositoryOperationsOutputPort} instead, whose adapter delegates here.
 *
 * <p>{@link #findByLocationKindAndLocationRef(ItemLocationKind, String)} is a derived query
 * (WHERE {@code location_kind = ?} AND {@code location_ref = ?}) backing both location lookups — "items on the
 * ground in this scene" ({@code GROUND} + scene id) and, later, "items a player holds" ({@code HELD} + holder id).
 */
public interface ItemSpringDataRepository extends CrudRepository<ItemDbEntity, String> {

    List<ItemDbEntity> findByLocationKindAndLocationRef(ItemLocationKind locationKind, String locationRef);
}
