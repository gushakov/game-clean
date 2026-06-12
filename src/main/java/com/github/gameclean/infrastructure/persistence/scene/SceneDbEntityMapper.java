package com.github.gameclean.infrastructure.persistence.scene;

import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the domain {@code Scene} aggregate and its persistence shape
 * ({@link SceneDbEntity} / {@link ExitDbEntity}). This is the shock-absorber layer: the schema and
 * the domain model can evolve at different speeds, and only this mapper (plus the DB entity) moves
 * when the storage shape changes.
 *
 * <p>The {@link SceneId} value object is unwrapped to / re-wrapped from its raw string by the two
 * converter methods below; re-wrapping runs the value object's own validation, so a malformed id
 * read from the database would surface as a domain error rather than slipping through.
 */
@Mapper(componentModel = "spring")
public interface SceneDbEntityMapper {

    SceneDbEntity toDbEntity(Scene scene);

    Scene toDomain(SceneDbEntity entity);

    @Mapping(target = "targetSceneId", source = "target")
    ExitDbEntity toDbEntity(Exit exit);

    @Mapping(target = "target", source = "targetSceneId")
    Exit toDomain(ExitDbEntity entity);

    default String sceneIdToString(SceneId id) {
        return id == null ? null : id.getValue();
    }

    default SceneId stringToSceneId(String value) {
        return value == null ? null : new SceneId(value);
    }
}
