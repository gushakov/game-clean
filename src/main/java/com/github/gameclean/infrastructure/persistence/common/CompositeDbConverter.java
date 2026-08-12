package com.github.gameclean.infrastructure.persistence.common;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.Location;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;

/**
 * Shared MapStruct converters for the composite value objects that persist as an <em>embeddable</em> shape
 * ({@code @Embedded} — several columns of the owner's table). Any {@code *DbEntityMapper} whose entity embeds
 * one of these shapes {@code extends} this interface, so MapStruct inherits the {@code default} methods as
 * candidate converters and the VO maps by name + type selection — no {@code @Mapping}, no {@code expression}.
 * Product-shaped composites ({@link HitPointsDbEntity}) and the flattened encodings of sum-shaped ones
 * ({@link LocationDbEntity}) both fit; a sealed VO's exhaustive {@code switch} keeps its
 * compile-error-on-new-case guarantee here, inside the converter.
 *
 * <p>Deliberately <b>not</b> in the layer-neutral {@code infrastructure.mapping} beside {@code ScalarConverter}:
 * that package is neutral precisely because {@code String} is neutral, reusable by any future mapper family.
 * An embeddable like {@link HitPointsDbEntity} is a Spring-Data-annotated <em>persistence</em> shape no
 * view-model mapper would ever target, so its converter belongs to the persistence family.
 *
 * <p>The pairs are written explicitly rather than left to MapStruct's implicit nested-bean mapping so the
 * reconstitution visibly runs the VO's validating constructor or factory: a corrupt stored pair (current beyond
 * max, a malformed ref id) fails as a domain error, which the reading adapter wraps as a persistence integrity
 * fault — the same story as a malformed stored id or chance in {@code ScalarConverter}.
 */
public interface CompositeDbConverter {

    default HitPointsDbEntity hitPointsToDbEntity(HitPoints hitPoints) {
        if (hitPoints == null) {
            return null;
        }
        HitPointsDbEntity entity = new HitPointsDbEntity();
        entity.setCurrent(hitPoints.getCurrent());
        entity.setMax(hitPoints.getMax());
        return entity;
    }

    default HitPoints dbEntityToHitPoints(HitPointsDbEntity entity) {
        return entity == null ? null : new HitPoints(entity.getCurrent(), entity.getMax());
    }

    /**
     * Flattens the sealed {@code Location} to its stored {@code (kind, ref)} encoding — one exhaustive
     * {@code switch}, so kind and ref are a single atomic decision and a new location case is a compile error
     * here until the encoding grows with it.
     */
    default LocationDbEntity locationToDbEntity(Location location) {
        if (location == null) {
            return null;
        }
        LocationDbEntity entity = new LocationDbEntity();
        switch (location) {
            case Location.OnGround onGround -> {
                entity.setKind(ItemLocationKind.GROUND);
                entity.setRef(onGround.getScene().asString());
            }
            case Location.HeldBy heldBy -> {
                entity.setKind(ItemLocationKind.HELD);
                entity.setRef(heldBy.getHolder().asString());
            }
            case Location.Inside inside -> {
                entity.setKind(ItemLocationKind.CONTAINED);
                entity.setRef(inside.getContainer().asString());
            }
        }
        return entity;
    }

    /** Rebuilds the sealed {@code Location} from the stored kind + ref, re-running id validation. */
    default Location dbEntityToLocation(LocationDbEntity entity) {
        return entity == null ? null : switch (entity.getKind()) {
            case GROUND -> new Location.OnGround(SceneId.of(entity.getRef()));
            case HELD -> new Location.HeldBy(PlayerId.of(entity.getRef()));
            case CONTAINED -> new Location.Inside(ItemId.of(entity.getRef()));
        };
    }
}
