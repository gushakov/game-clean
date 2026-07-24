package com.github.gameclean.core.port.seed;

import lombok.Value;

import java.util.List;

/**
 * The complete authored seed: the scenes, the starting scene id, the items to spawn, and the NPCs to spawn —
 * bundled into one carrier. It is the return contract of {@link GameSeedSourceOperationsOutputPort}: the use
 * case <em>pulls</em> it as its first checkpoint, rather than a driving adapter pushing it in. All primitives /
 * {@code *Entry} carriers, no domain types: the value objects are constructed inside the use case (the
 * single validity gate), so this carrier may legitimately hold <em>domain</em>-invalid data until it
 * reaches that gate.
 *
 * <p>The scenes, items and NPCs come from the seed source (a file today); the starting scene id and the player's
 * max hit points come from configuration. The seed-source adapter assembles them before the use case reads them
 * — configured player facts ride this carrier exactly like {@code startingSceneId}, so the use case builds the
 * always-valid {@code Player} (position + {@code HitPoints.full(playerMaxHitPoints)}) at its one validity gate.
 *
 * <p>Lombok {@code @Value} (not a Java record), matching the shape used across the codebase.
 */
@Value
public class GameSeed {

    List<SceneEntry> scenes;
    String startingSceneId;
    int playerMaxHitPoints;
    List<ItemEntry> items;
    List<NpcEntry> npcs;
}
