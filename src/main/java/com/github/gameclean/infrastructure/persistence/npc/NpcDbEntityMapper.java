package com.github.gameclean.infrastructure.persistence.npc;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.scene.SceneId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the domain {@code Npc} aggregate and its persistence shape ({@link NpcDbEntity}) —
 * the shock-absorber layer that lets schema and model evolve at different speeds.
 *
 * <p>The {@link NpcId} unwraps to / re-wraps from its raw string, and the {@link SceneId} of {@code currentScene}
 * maps to / from {@code currentSceneId}; re-wrapping runs each value object's own validation, so a malformed
 * stored id surfaces as a domain error rather than slipping through. The {@link Chance moveChance} is flattened
 * to the {@code (moveChanceNum, moveChanceDen)} column pair and rebuilt from it, and the {@link HitPoints} to
 * the {@code (hitPoints, maxHitPoints)} pair (current out of max) — reconstituting re-runs each value object's
 * validation. The {@code version} maps straight through (by name) in both directions.
 */
@Mapper(componentModel = "spring")
public interface NpcDbEntityMapper {

    @Mapping(target = "currentSceneId", source = "currentScene")
    @Mapping(target = "moveChanceNum", expression = "java(npc.getMoveChance().getNumerator())")
    @Mapping(target = "moveChanceDen", expression = "java(npc.getMoveChance().getDenominator())")
    @Mapping(target = "hitPoints", expression = "java(npc.getHitPoints().getCurrent())")
    @Mapping(target = "maxHitPoints", expression = "java(npc.getHitPoints().getMax())")
    NpcDbEntity toDbEntity(Npc npc);

    @Mapping(target = "currentScene", source = "currentSceneId")
    @Mapping(target = "moveChance", expression = "java(toChance(entity.getMoveChanceNum(), entity.getMoveChanceDen()))")
    @Mapping(target = "hitPoints", expression = "java(toHitPoints(entity.getHitPoints(), entity.getMaxHitPoints()))")
    Npc toDomain(NpcDbEntity entity);

    default String npcIdToString(NpcId id) {
        return id == null ? null : id.getValue();
    }

    default NpcId stringToNpcId(String value) {
        return value == null ? null : new NpcId(value);
    }

    default String sceneIdToString(SceneId id) {
        return id == null ? null : id.getValue();
    }

    default SceneId stringToSceneId(String value) {
        return value == null ? null : new SceneId(value);
    }

    /** Rebuilds the {@code Chance} move-odds from the stored numerator/denominator pair, re-running its validation. */
    default Chance toChance(int numerator, int denominator) {
        return new Chance(numerator, denominator);
    }

    /** Rebuilds the {@code HitPoints} pool from the stored current/max pair, re-running its validation. */
    default HitPoints toHitPoints(int current, int max) {
        return new HitPoints(current, max);
    }
}
