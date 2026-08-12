package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.initialize.InitializeGamePresenterOutputPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Presenter for {@code InitializeGame} when the actor is the system at startup: it presents to the
 * application <em>log</em>, not a console. Game initialization has no interactive audience — there is no
 * player to address yet — so each outcome is recorded for operators rather than rendered. (Routing those
 * logs to a file rather than the terminal is a logging-config concern; this adapter only decides what to
 * say, at what level.)
 *
 * <p>Humble by contract: it renders the outcome the use case already decided by choosing which method
 * to call. It makes no business or flow decisions.
 */
@Slf4j
public class LoggingInitializeGamePresenter implements InitializeGamePresenterOutputPort {

    @Override
    public void presentGameInitialized(List<Scene> scenes, PlayerId playerId, List<Item> spawnedItems,
                                       List<Npc> spawnedNpcs) {
        log.info("[InitializeGame] Game ready: {} scene(s) {} — player {} placed — {} item(s) spawned — {} npc(s) spawned.",
                scenes.size(), scenes.stream().map(scene -> scene.getId().asString()).toList(),
                playerId.asString(), spawnedItems.size(), spawnedNpcs.size());
    }

    @Override
    public void presentErrorWhenExitTargetUnknown(Map<SceneId, List<Exit>> unresolvedExitsByScene) {
        unresolvedExitsByScene.forEach((sceneId, exits) -> exits.forEach(exit ->
                log.warn("[InitializeGame] Scene {} has exit '{}' to unknown scene {}",
                        sceneId.asString(), exit.getName(), exit.getTarget().asString())));
    }

    @Override
    public void presentStartingSceneUnknown(SceneId startingSceneId) {
        log.warn("[InitializeGame] Configured starting scene {} resolves to no authored scene.",
                startingSceneId.asString());
    }

    @Override
    public void presentItemSpawnSceneUnknown(Map<String, List<SceneId>> unknownSpawnScenesByItem) {
        unknownSpawnScenesByItem.forEach((itemId, scenes) -> log.warn(
                "[InitializeGame] Item {} spawns into unknown scene(s) {}",
                itemId, scenes.stream().map(SceneId::asString).toList()));
    }

    @Override
    public void presentNpcSpawnSceneUnknown(Map<String, List<SceneId>> unknownSpawnScenesByNpc) {
        unknownSpawnScenesByNpc.forEach((npcId, scenes) -> log.warn(
                "[InitializeGame] Npc {} spawns into unknown scene(s) {}",
                npcId, scenes.stream().map(SceneId::asString).toList()));
    }

    @Override
    public void presentItemContainmentTargetInvalid(Map<String, List<String>> invalidContainmentTargetsByItem) {
        invalidContainmentTargetsByItem.forEach((itemId, targets) -> log.warn(
                "[InitializeGame] Container {} declares invalid containment target(s) {} (unresolved, or a container — nesting is not authored)",
                itemId, targets));
    }

    @Override
    public void presentInvalidParametersError(Exception e) {
        log.warn("[InitializeGame] Authored input rejected by the validity gate: {}", e.getMessage());
    }

    @Override
    public void presentError(Exception e) {
        log.error("[InitializeGame] Game initialization failed", e);
    }
}
