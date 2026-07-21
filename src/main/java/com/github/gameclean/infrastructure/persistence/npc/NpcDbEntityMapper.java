package com.github.gameclean.infrastructure.persistence.npc;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.infrastructure.mapping.ScalarConverter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the domain {@code Npc} aggregate and its persistence shape ({@link NpcDbEntity}) —
 * the shock-absorber layer that lets schema and model evolve at different speeds.
 *
 * <p>The npc-id, the scene-id of {@code currentScene}, and the {@code moveChance} odds all convert to / from a
 * single raw string via the shared {@link ScalarConverter} this mapper extends (the chance as its canonical
 * {@code num/den} text); re-wrapping runs each value object's own validation, so a malformed stored value
 * surfaces as a domain error rather than slipping through. {@code moveChance} matches by name, so it needs no
 * {@code @Mapping} at all. The one remaining composite, {@code HitPoints}, flattens across two int columns:
 * forward, MapStruct reads it off the source graph by <em>dot-path</em> ({@code hitPoints.current}/{@code .max}),
 * which is compile-checked and null-aware; reverse, two sibling scalars rebuild one immutable constructor-built
 * VO, which has no clean type-based entry, so the {@code toHitPoints} {@code expression} helper legitimately
 * stays. The {@code version} maps straight through (by name) in both directions.
 */
@Mapper(componentModel = "spring")
public interface NpcDbEntityMapper extends ScalarConverter {

    @Mapping(target = "currentSceneId", source = "currentScene")
    @Mapping(target = "currentHitPoints", source = "hitPoints.current")
    @Mapping(target = "maxHitPoints", source = "hitPoints.max")
    NpcDbEntity toDbEntity(Npc npc);

    @Mapping(target = "currentScene", source = "currentSceneId")
    @Mapping(target = "hitPoints", expression = "java(toHitPoints(entity.getCurrentHitPoints(), entity.getMaxHitPoints()))")
    Npc toDomain(NpcDbEntity entity);

    /** Rebuilds the {@code HitPoints} pool from the stored current/max pair, re-running its validation. */
    default HitPoints toHitPoints(int current, int max) {
        return new HitPoints(current, max);
    }
}
