package com.github.gameclean.infrastructure.persistence.item;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.scene.SceneId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the domain {@code Item} aggregate and its persistence shape
 * ({@link ItemDbEntity}) — the shock-absorber layer that lets schema and model evolve at different speeds.
 *
 * <p>The {@link ItemId} and {@link SceneId} value objects are unwrapped to / re-wrapped from their raw
 * strings by the converter methods below; re-wrapping runs each value object's own validation, so a
 * malformed id read from the database surfaces as a domain error rather than slipping through. The item's
 * {@code location} maps to the {@code scene_id} column.
 */
@Mapper(componentModel = "spring")
public interface ItemDbEntityMapper {

    @Mapping(target = "sceneId", source = "location")
    ItemDbEntity toDbEntity(Item item);

    @Mapping(target = "location", source = "sceneId")
    Item toDomain(ItemDbEntity entity);

    default String itemIdToString(ItemId id) {
        return id == null ? null : id.getValue();
    }

    default ItemId stringToItemId(String value) {
        return value == null ? null : new ItemId(value);
    }

    default String sceneIdToString(SceneId id) {
        return id == null ? null : id.getValue();
    }

    default SceneId stringToSceneId(String value) {
        return value == null ? null : new SceneId(value);
    }
}
