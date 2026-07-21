package com.github.gameclean.infrastructure.persistence.item;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.infrastructure.mapping.ScalarConverter;
import com.github.gameclean.infrastructure.persistence.common.CompositeDbConverter;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper between the domain {@code Item} aggregate and its persistence shape
 * ({@link ItemDbEntity}) — the shock-absorber layer that lets schema and model evolve at different speeds.
 *
 * <p>Every value object converts through a shared inherited pair, selected by MapStruct on source + target
 * type: the item-id to / from its raw string via {@link ScalarConverter}; the sealed mobile location to / from
 * its embedded {@code LocationDbEntity} encoding via {@link CompositeDbConverter}, whose exhaustive
 * {@code switch} keeps the compile-error-on-new-case guarantee and whose reconstitution re-runs id validation,
 * so a malformed stored ref surfaces as a domain error rather than slipping through. Every property matches by
 * name ({@code location ↔ location}, {@code version} straight through), so the mapper declares no
 * {@code @Mapping} at all.
 */
@Mapper(componentModel = "spring")
public interface ItemDbEntityMapper extends ScalarConverter, CompositeDbConverter {

    ItemDbEntity toDbEntity(Item item);

    Item toDomain(ItemDbEntity entity);
}
