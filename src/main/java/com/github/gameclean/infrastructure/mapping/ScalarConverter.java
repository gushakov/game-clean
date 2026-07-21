package com.github.gameclean.infrastructure.mapping;

import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;

/**
 * Shared MapStruct converters for the value objects that flatten to / from a single raw {@code String}
 * column — the id wrappers and any VO with a canonical one-scalar text form ({@link Chance}, stored as
 * {@code num/den}). Any {@code *DbEntityMapper} that persists one of these {@code extends} this interface,
 * so MapStruct inherits these {@code default} methods as candidate converters and the duplicated per-mapper
 * converters disappear.
 *
 * <p>Layer-neutral on purpose: a VO ↔ {@code String} conversion is generic scalar mapping, not a persistence
 * concern, so this lives in {@code infrastructure.mapping} (a sibling of {@code persistence}) where any mapper
 * family — persistence DB-entity mappers today, view-model mappers tomorrow — can reuse it. It is the single
 * sanctioned knower that these values travel as text: it goes through each value object's
 * {@link SceneId#asString() asString()} projection and {@link SceneId#of(String) of(...)} factory, so no other
 * site in the codebase couples to the stored representation.
 *
 * <p>Safe even though every wrapper converts to {@code String}: MapStruct selects a converter by <em>source +
 * target</em> type at each use site, so {@code String → SceneId} and {@code String → Chance} never collide.
 * Re-wrapping runs each value object's own validation, so a malformed stored value surfaces as a domain error
 * rather than slipping through — the reading adapter wraps that as a persistence integrity fault.
 *
 * <p>This regime is for <em>scalar↔scalar</em> conversions only. A composite value object that flattens one VO
 * into several columns (e.g. {@code HitPoints}, whose current/max stay two int columns because they are
 * plausibly SQL-comparable — queryability decides column shape) is a different problem and stays with its own
 * mapper (dot-path sources forward, reconstitution helpers reverse).
 */
public interface ScalarConverter {

    default String sceneIdToString(SceneId id) {
        return id == null ? null : id.asString();
    }

    default SceneId stringToSceneId(String value) {
        return value == null ? null : SceneId.of(value);
    }

    default String playerIdToString(PlayerId id) {
        return id == null ? null : id.asString();
    }

    default PlayerId stringToPlayerId(String value) {
        return value == null ? null : PlayerId.of(value);
    }

    default String npcIdToString(NpcId id) {
        return id == null ? null : id.asString();
    }

    default NpcId stringToNpcId(String value) {
        return value == null ? null : NpcId.of(value);
    }

    default String itemIdToString(ItemId id) {
        return id == null ? null : id.asString();
    }

    default ItemId stringToItemId(String value) {
        return value == null ? null : ItemId.of(value);
    }

    default String chanceToString(Chance chance) {
        return chance == null ? null : chance.asString();
    }

    default Chance stringToChance(String value) {
        return value == null ? null : Chance.of(value);
    }
}
