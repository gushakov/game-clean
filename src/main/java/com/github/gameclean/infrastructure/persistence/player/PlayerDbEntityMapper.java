package com.github.gameclean.infrastructure.persistence.player;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.infrastructure.mapping.ScalarConverter;
import com.github.gameclean.infrastructure.persistence.common.CompositeDbConverter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the domain {@code Player} aggregate and its persistence shape
 * ({@link PlayerDbEntity}) — the shock-absorber layer that lets schema and model evolve at different
 * speeds.
 *
 * <p>The player-id and scene-id value objects are unwrapped to / re-wrapped from their raw strings by the
 * shared {@link ScalarConverter} this mapper extends; the {@code hitPoints} pool converts to / from its
 * embedded {@code HitPointsDbEntity} shape via {@link CompositeDbConverter} (the same pair {@code npc} uses).
 * Re-wrapping runs each value object's own validation, so a malformed id or pool read from the database
 * surfaces as a domain error rather than slipping through. {@code hitPoints} and {@code version} match by name,
 * so only the {@code currentScene ↔ currentSceneId} name mismatch is declared.
 */
@Mapper(componentModel = "spring")
public interface PlayerDbEntityMapper extends ScalarConverter, CompositeDbConverter {

    @Mapping(target = "currentSceneId", source = "currentScene")
    PlayerDbEntity toDbEntity(Player player);

    @Mapping(target = "currentScene", source = "currentSceneId")
    Player toDomain(PlayerDbEntity entity);
}
