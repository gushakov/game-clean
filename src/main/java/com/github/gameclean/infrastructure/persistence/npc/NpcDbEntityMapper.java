package com.github.gameclean.infrastructure.persistence.npc;

import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.infrastructure.mapping.ScalarConverter;
import com.github.gameclean.infrastructure.persistence.common.CompositeDbConverter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the domain {@code Npc} aggregate and its persistence shape ({@link NpcDbEntity}) —
 * the shock-absorber layer that lets schema and model evolve at different speeds.
 *
 * <p>Every value object converts through a shared inherited pair, selected by MapStruct on source + target
 * type: the npc-id, the scene-id of {@code currentScene}, and the {@code moveChance} odds to / from a single
 * raw string via {@link ScalarConverter} (the chance as its canonical {@code num/den} text); the
 * {@code hitPoints} pool to / from its embedded {@code HitPointsDbEntity} shape via
 * {@link CompositeDbConverter}. Re-wrapping runs each value object's own validation, so a malformed stored
 * value surfaces as a domain error rather than slipping through. {@code moveChance} and {@code hitPoints}
 * match by name, so they need no {@code @Mapping} at all; only the {@code currentScene ↔ currentSceneId}
 * name mismatch is declared. The {@code version} maps straight through (by name) in both directions.
 */
@Mapper(componentModel = "spring")
public interface NpcDbEntityMapper extends ScalarConverter, CompositeDbConverter {

    @Mapping(target = "currentSceneId", source = "currentScene")
    NpcDbEntity toDbEntity(Npc npc);

    @Mapping(target = "currentScene", source = "currentSceneId")
    Npc toDomain(NpcDbEntity entity);
}
