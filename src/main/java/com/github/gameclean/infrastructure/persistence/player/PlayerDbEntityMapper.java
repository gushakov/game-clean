package com.github.gameclean.infrastructure.persistence.player;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the domain {@code Player} aggregate and its persistence shape
 * ({@link PlayerDbEntity}) — the shock-absorber layer that lets schema and model evolve at different
 * speeds.
 *
 * <p>The {@link PlayerId} and {@link SceneId} value objects are unwrapped to / re-wrapped from their
 * raw strings by the converter methods below; re-wrapping runs each value object's own validation,
 * so a malformed id read from the database surfaces as a domain error rather than slipping through.
 */
@Mapper(componentModel = "spring")
public interface PlayerDbEntityMapper {

    @Mapping(target = "currentSceneId", source = "currentScene")
    PlayerDbEntity toDbEntity(Player player);

    @Mapping(target = "currentScene", source = "currentSceneId")
    Player toDomain(PlayerDbEntity entity);

    default String playerIdToString(PlayerId id) {
        return id == null ? null : id.getValue();
    }

    default PlayerId stringToPlayerId(String value) {
        return value == null ? null : new PlayerId(value);
    }

    default String sceneIdToString(SceneId id) {
        return id == null ? null : id.getValue();
    }

    default SceneId stringToSceneId(String value) {
        return value == null ? null : new SceneId(value);
    }
}
