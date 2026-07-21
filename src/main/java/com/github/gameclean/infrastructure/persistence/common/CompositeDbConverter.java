package com.github.gameclean.infrastructure.persistence.common;

import com.github.gameclean.core.model.combat.HitPoints;

/**
 * Shared MapStruct converters for the composite value objects that persist as an <em>embeddable</em> shape
 * ({@code @Embedded} — several columns of the owner's table). Any {@code *DbEntityMapper} whose entity embeds
 * one of these shapes {@code extends} this interface, so MapStruct inherits the {@code default} methods as
 * candidate converters and the VO maps by name + type selection — no {@code @Mapping}, no {@code expression}.
 *
 * <p>Deliberately <b>not</b> in the layer-neutral {@code infrastructure.mapping} beside {@code ScalarConverter}:
 * that package is neutral precisely because {@code String} is neutral, reusable by any future mapper family.
 * An embeddable like {@link HitPointsDbEntity} is a Spring-Data-annotated <em>persistence</em> shape no
 * view-model mapper would ever target, so its converter belongs to the persistence family.
 *
 * <p>The pair is written explicitly rather than left to MapStruct's implicit nested-bean mapping so the
 * reconstitution visibly runs the VO's validating constructor: a corrupt stored pair (e.g. current beyond max)
 * fails as a domain error, which the reading adapter wraps as a persistence integrity fault — the same story
 * as a malformed stored id or chance in {@code ScalarConverter}.
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
}
