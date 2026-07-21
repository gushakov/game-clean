package com.github.gameclean.infrastructure.persistence.player;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.infrastructure.mapping.ScalarConverter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the domain {@code Player} aggregate and its persistence shape
 * ({@link PlayerDbEntity}) — the shock-absorber layer that lets schema and model evolve at different
 * speeds.
 *
 * <p>The player-id and scene-id value objects are unwrapped to / re-wrapped from their raw strings by the
 * shared {@link ScalarConverter} this mapper extends; re-wrapping runs each value object's own validation,
 * so a malformed id read from the database surfaces as a domain error rather than slipping through.
 */
@Mapper(componentModel = "spring")
public interface PlayerDbEntityMapper extends ScalarConverter {

    @Mapping(target = "currentSceneId", source = "currentScene")
    PlayerDbEntity toDbEntity(Player player);

    @Mapping(target = "currentScene", source = "currentSceneId")
    Player toDomain(PlayerDbEntity entity);
}
