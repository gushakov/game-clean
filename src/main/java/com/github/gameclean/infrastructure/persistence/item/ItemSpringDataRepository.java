package com.github.gameclean.infrastructure.persistence.item;

import com.github.gameclean.infrastructure.persistence.common.ItemLocationKind;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Spring Data JDBC repository over {@link ItemDbEntity}. Infrastructure plumbing, not a domain port: the
 * use cases depend on the {@code ItemRepositoryOperationsOutputPort} instead, whose adapter delegates here.
 *
 * <p>{@link #findByLocationKindAndLocationRef(ItemLocationKind, String)} is a derived query
 * (WHERE {@code location_kind = ?} AND {@code location_ref = ?}) backing both location lookups — "items on the
 * ground in this scene" ({@code GROUND} + scene id) and "items a player holds" ({@code HELD} + holder id). The
 * name survived the location's move into an embedded {@code LocationDbEntity} by coincidence of spelling: it
 * now resolves through the embedded property path ({@code location.kind}/{@code location.ref}), not the former
 * flat {@code locationKind}/{@code locationRef} properties.
 */
public interface ItemSpringDataRepository extends CrudRepository<ItemDbEntity, String> {

    List<ItemDbEntity> findByLocationKindAndLocationRef(ItemLocationKind locationKind, String locationRef);
}
