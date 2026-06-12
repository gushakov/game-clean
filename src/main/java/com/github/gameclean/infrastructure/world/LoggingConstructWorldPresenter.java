package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.initialize.ConstructWorldPresenterOutputPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Presenter for {@code ConstructWorld} when the actor is the system at startup: it presents to the
 * application <em>log</em>, not a console. World construction has no interactive audience — there is
 * no player to address — so each outcome is recorded for operators rather than rendered. (Routing
 * those logs to a file rather than the terminal is a logging-config concern; this adapter only
 * decides what to say, at what level.)
 *
 * <p>Humble by contract: it renders the outcome the use case already decided by choosing which
 * method to call. It makes no business or flow decisions.
 */
@Component
@Slf4j
public class LoggingConstructWorldPresenter implements ConstructWorldPresenterOutputPort {

    @Override
    public void presentSuccessfulWorldConstruction(List<Scene> scenes) {
        log.info("[ConstructWorld] World constructed and seeded: {} scene(s) — {}",
                scenes.size(), scenes.stream().map(scene -> scene.getId().getValue()).toList());
    }

    @Override
    public void presentWorldAlreadyConstructed() {
        log.info("[ConstructWorld] World already populated; seeding skipped (idempotent).");
    }

    @Override
    public void presentInvalidParametersError(Exception e) {
        log.warn("[ConstructWorld] Authored world rejected by the validity gate: {}", e.getMessage());
    }

    @Override
    public void presentErrorWhenExitTargetUnknown(Map<SceneId, List<Exit>> unresolvedExitsByScene) {
        unresolvedExitsByScene.forEach((sceneId, exits) -> exits.forEach(exit ->
                log.warn("[ConstructWorld] Scene {} has exit '{}' to unknown scene {}",
                        sceneId.getValue(), exit.getName(), exit.getTarget().getValue())));
    }

    @Override
    public void presentError(Exception e) {
        log.error("[ConstructWorld] World construction failed", e);
    }
}
