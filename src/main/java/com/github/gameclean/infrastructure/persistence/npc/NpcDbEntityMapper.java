package com.github.gameclean.infrastructure.persistence.npc;

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
 * to the {@code (moveChanceNum, moveChanceDen)} column pair and rebuilt from it — reconstituting re-runs
 * {@code Chance} validation.
 */
@Mapper(componentModel = "spring")
public interface NpcDbEntityMapper {

    @Mapping(target = "currentSceneId", source = "currentScene")
    @Mapping(target = "moveChanceNum", expression = "java(npc.getMoveChance().getNumerator())")
    @Mapping(target = "moveChanceDen", expression = "java(npc.getMoveChance().getDenominator())")
    NpcDbEntity toDbEntity(Npc npc);

    @Mapping(target = "currentScene", source = "currentSceneId")
    @Mapping(target = "moveChance", expression = "java(toChance(entity.getMoveChanceNum(), entity.getMoveChanceDen()))")
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
}
