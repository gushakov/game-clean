package com.github.gameclean.infrastructure.mapping;

import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;

/**
 * Shared MapStruct converters for the single-field wrapper value objects that flatten to / from a raw
 * {@code String} column. Any {@code *DbEntityMapper} that persists one of these ids {@code extends} this
 * interface, so MapStruct inherits these {@code default} methods as candidate converters and the duplicated
 * per-mapper id converters disappear.
 *
 * <p>Layer-neutral on purpose: an id ↔ {@code String} conversion is generic scalar mapping, not a persistence
 * concern, so this lives in {@code infrastructure.mapping} (a sibling of {@code persistence}) where any mapper
 * family — persistence DB-entity mappers today, view-model mappers tomorrow — can reuse it. It is the single
 * sanctioned knower of an id's text representation: it goes through each value object's {@link SceneId#asString()
 * asString()} projection and {@link SceneId#of(String) of(...)} factory, so no other site in the codebase
 * couples to "the wrapped field is a {@code String}".
 *
 * <p>Safe even though all four wrappers wrap {@code String}: MapStruct selects a converter by <em>source +
 * target</em> type at each use site, so {@code String → SceneId} and {@code String → ItemId} never collide.
 * Re-wrapping runs each value object's own validation, so a malformed stored id surfaces as a domain error
 * rather than slipping through — the reading adapter wraps that as a persistence integrity fault.
 *
 * <p>This regime is for <em>scalar↔scalar</em> wrapper conversions only. Composite value objects that flatten
 * one VO into several columns (e.g. {@code HitPoints}, {@code Chance}) are a different problem and stay with
 * their own mapper (dot-path sources forward, reconstitution helpers reverse).
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
}
