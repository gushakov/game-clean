package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.initialize.InitializeGamePresenterOutputPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Presenter for {@code InitializeGame} when the actor is the system at startup: it presents to the
 * application <em>log</em>, not a console. Game initialization has no interactive audience — there is no
 * player to address yet — so each outcome of both phases (world construction, player placement) is
 * recorded for operators rather than rendered. (Routing those logs to a file rather than the terminal
 * is a logging-config concern; this adapter only decides what to say, at what level.)
 *
 * <p>Humble by contract: it renders the outcome the use case already decided by choosing which method
 * to call. It makes no business or flow decisions.
 */
@Component
@Slf4j
public class LoggingInitializeGamePresenter implements InitializeGamePresenterOutputPort {

    @Override
    public void presentSuccessfulWorldConstruction(List<Scene> scenes) {
        log.info("[InitializeGame] World constructed and seeded: {} scene(s) — {}",
                scenes.size(), scenes.stream().map(scene -> scene.getId().getValue()).toList());
    }

    @Override
    public void presentWorldAlreadyConstructed() {
        log.info("[InitializeGame] World already populated; seeding skipped (idempotent).");
    }

    @Override
    public void presentErrorWhenExitTargetUnknown(Map<SceneId, List<Exit>> unresolvedExitsByScene) {
        unresolvedExitsByScene.forEach((sceneId, exits) -> exits.forEach(exit ->
                log.warn("[InitializeGame] Scene {} has exit '{}' to unknown scene {}",
                        sceneId.getValue(), exit.getName(), exit.getTarget().getValue())));
    }

    @Override
    public void presentSuccessfulPlayerCreation(Player player) {
        log.info("[InitializeGame] Created player {} at scene {}",
                player.getId().getValue(), player.getCurrentScene().getValue());
    }

    @Override
    public void presentPlayerAlreadyExists(PlayerId playerId) {
        log.info("[InitializeGame] Player {} already exists; creation skipped (idempotent).",
                playerId.getValue());
    }

    @Override
    public void presentStartingSceneUnknown(SceneId startingSceneId) {
        log.warn("[InitializeGame] Configured starting scene {} resolves to no persisted scene.",
                startingSceneId.getValue());
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
