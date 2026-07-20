package com.github.gameclean.infrastructure.persistence.item;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.Location;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.infrastructure.mapping.ScalarConverter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the domain {@code Item} aggregate and its persistence shape
 * ({@link ItemDbEntity}) — the shock-absorber layer that lets schema and model evolve at different speeds.
 *
 * <p>The item's mobile {@link Location} is flattened to the {@code (location_kind, location_ref)} column pair
 * and rebuilt from it by the helper methods below. The case↔kind correspondence is an exhaustive
 * {@code switch} over the sealed {@code Location}, so adding a location case is a compile error here until the
 * mapping grows with it. Reconstituting the ref re-runs {@link SceneId}/{@link PlayerId} validation, so a
 * malformed stored id surfaces as a domain error rather than slipping through. The {@code version} maps
 * straight through (by name) in both directions; the item-id unwraps to / re-wraps from its raw string via the
 * shared {@link ScalarConverter} this mapper extends.
 */
@Mapper(componentModel = "spring")
public interface ItemDbEntityMapper extends ScalarConverter {

    @Mapping(target = "locationKind", expression = "java(locationKind(item.getLocation()))")
    @Mapping(target = "locationRef", expression = "java(locationRef(item.getLocation()))")
    ItemDbEntity toDbEntity(Item item);

    @Mapping(target = "location", expression = "java(toLocation(entity.getLocationKind(), entity.getLocationRef()))")
    Item toDomain(ItemDbEntity entity);

    /** Which storage kind tags this location — exhaustive over the sealed {@code Location} cases. */
    default ItemLocationKind locationKind(Location location) {
        return switch (location) {
            case Location.OnGround ignored -> ItemLocationKind.GROUND;
            case Location.HeldBy ignored -> ItemLocationKind.HELD;
        };
    }

    /** The raw id this location references — a scene id on the ground, a holder id when held. */
    default String locationRef(Location location) {
        return switch (location) {
            case Location.OnGround onGround -> onGround.getScene().asString();
            case Location.HeldBy heldBy -> heldBy.getHolder().asString();
        };
    }

    /** Rebuilds the sealed {@code Location} from the stored kind + ref, re-running id validation. */
    default Location toLocation(ItemLocationKind kind, String ref) {
        return switch (kind) {
            case GROUND -> new Location.OnGround(SceneId.of(ref));
            case HELD -> new Location.HeldBy(PlayerId.of(ref));
        };
    }
}
