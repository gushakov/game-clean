package com.github.gameclean.infrastructure.persistence.npc;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.infrastructure.mapping.ScalarConverter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the domain {@code Npc} aggregate and its persistence shape ({@link NpcDbEntity}) —
 * the shock-absorber layer that lets schema and model evolve at different speeds.
 *
 * <p>The npc-id and the scene-id of {@code currentScene} unwrap to / re-wrap from their raw strings via the
 * shared {@link ScalarConverter} this mapper extends; re-wrapping runs each value object's own validation, so a
 * malformed stored id surfaces as a domain error rather than slipping through. The two composite value objects
 * flatten across two columns each: forward, MapStruct reads them off the source graph by <em>dot-path</em>
 * ({@code moveChance.numerator}/{@code .denominator}, {@code hitPoints.current}/{@code .max}), which is
 * compile-checked and null-aware; reverse, two sibling scalars rebuild one immutable constructor-built VO, which
 * has no clean type-based entry, so the {@code toChance}/{@code toHitPoints} {@code expression} helpers
 * legitimately stay. The {@code version} maps straight through (by name) in both directions.
 */
@Mapper(componentModel = "spring")
public interface NpcDbEntityMapper extends ScalarConverter {

    @Mapping(target = "currentSceneId", source = "currentScene")
    @Mapping(target = "moveChanceNum", source = "moveChance.numerator")
    @Mapping(target = "moveChanceDen", source = "moveChance.denominator")
    @Mapping(target = "currentHitPoints", source = "hitPoints.current")
    @Mapping(target = "maxHitPoints", source = "hitPoints.max")
    NpcDbEntity toDbEntity(Npc npc);

    @Mapping(target = "currentScene", source = "currentSceneId")
    @Mapping(target = "moveChance", expression = "java(toChance(entity.getMoveChanceNum(), entity.getMoveChanceDen()))")
    @Mapping(target = "hitPoints", expression = "java(toHitPoints(entity.getCurrentHitPoints(), entity.getMaxHitPoints()))")
    Npc toDomain(NpcDbEntity entity);

    /** Rebuilds the {@code Chance} move-odds from the stored numerator/denominator pair, re-running its validation. */
    default Chance toChance(int numerator, int denominator) {
        return new Chance(numerator, denominator);
    }

    /** Rebuilds the {@code HitPoints} pool from the stored current/max pair, re-running its validation. */
    default HitPoints toHitPoints(int current, int max) {
        return new HitPoints(current, max);
    }
}
