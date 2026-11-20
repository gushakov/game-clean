package com.github.gameclean.infrastructure.persistence.npc;

import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.Offer;
import com.github.gameclean.infrastructure.mapping.ScalarConverter;
import com.github.gameclean.infrastructure.persistence.common.CompositeDbConverter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between the domain {@code Npc} aggregate and its persistence shape ({@link NpcDbEntity}) —
 * the shock-absorber layer that lets schema and model evolve at different speeds.
 *
 * <p>Every value object converts through a shared inherited pair, selected by MapStruct on source + target
 * type: the npc-id, the scene-id of {@code currentScene}, and the {@code moveChance} / {@code attackChance}
 * odds to / from a single raw string via {@link ScalarConverter} (each chance as its canonical {@code num/den}
 * text); the {@code hitPoints} pool to / from its embedded {@code HitPointsDbEntity} shape via
 * {@link CompositeDbConverter}. Re-wrapping runs each value object's own validation, so a malformed stored
 * value surfaces as a domain error rather than slipping through. {@code moveChance}, {@code attackChance},
 * {@code hostile} and {@code hitPoints} all match by name, so they need no {@code @Mapping} at all; only the
 * {@code currentScene ↔ currentSceneId} name mismatch is declared. The {@code version} and the opaque
 * {@code corpseRef} authored handle (a plain string on both sides) map straight through (by name) in both
 * directions.
 *
 * <p>The {@code sayings} and the {@code offers} match by name too, but their <em>elements</em> differ in shape —
 * a plain {@code String}, and an {@link Offer} value object, on the domain side; an owned-child
 * {@link NpcSayingDbEntity} or {@link NpcOfferDbEntity} row on the storage side — so the {@code default} element
 * converters below give MapStruct the per-element step and it generates the list walk itself (authored order
 * preserved, the list key being the row's {@code idx}). No {@code expression} is needed: each step is a plain
 * constructor call, and re-running the value object's own gate on the way back is what makes a stored offer the
 * domain would not accept an integrity fault of the read rather than a nonsense value handed onward.
 */
@Mapper(componentModel = "spring")
public interface NpcDbEntityMapper extends ScalarConverter, CompositeDbConverter {

    @Mapping(target = "currentSceneId", source = "currentScene")
    NpcDbEntity toDbEntity(Npc npc);

    @Mapping(target = "currentScene", source = "currentSceneId")
    Npc toDomain(NpcDbEntity entity);

    /** One authored line → its child row (the back-reference and position are the parent collection's job). */
    default NpcSayingDbEntity toSayingDbEntity(String saying) {
        return new NpcSayingDbEntity(saying);
    }

    /** One child row → the authored line; the NPC's own gate rejects a blank stored line as an integrity fault. */
    default String toSaying(NpcSayingDbEntity entity) {
        return entity.getText();
    }

    /**
     * One authored offer → its child row. The four authored facts are the row's own columns; the back-reference
     * and the list position are the parent collection's job, so they never appear here.
     */
    default NpcOfferDbEntity toOfferDbEntity(Offer offer) {
        return new NpcOfferDbEntity(offer.getRef(), offer.getName(), offer.getPrice(), offer.getItemRef());
    }

    /**
     * One child row → the authored offer, re-running {@link Offer}'s construction gate: a stored row with a blank
     * handle or name, or a price below one coin, is an integrity fault of the read (the initialization gate
     * validated the same authoring at boot), never a value a use case is handed.
     */
    default Offer toOffer(NpcOfferDbEntity entity) {
        return new Offer(entity.getRef(), entity.getName(), entity.getPrice(), entity.getItemRef());
    }
}
