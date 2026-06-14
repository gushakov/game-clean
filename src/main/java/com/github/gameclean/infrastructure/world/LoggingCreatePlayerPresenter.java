package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.initialize.CreatePlayerPresenterOutputPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Presenter for {@code CreatePlayer} when the actor is the system at startup: it presents to the
 * application <em>log</em>, not a console. Player creation at boot has no interactive audience — there
 * is no player to address yet — so each outcome is recorded for operators rather than rendered, just
 * like {@link LoggingConstructWorldPresenter}.
 *
 * <p>Humble by contract: it renders the outcome the use case already decided by choosing which method
 * to call. It makes no business or flow decisions.
 */
@Component
@Slf4j
public class LoggingCreatePlayerPresenter implements CreatePlayerPresenterOutputPort {

    @Override
    public void presentSuccessfulPlayerCreation(Player player) {
        log.info("[CreatePlayer] Created player {} at scene {}",
                player.getId().getValue(), player.getCurrentScene().getValue());
    }

    @Override
    public void presentPlayerAlreadyExists(PlayerId playerId) {
        log.info("[CreatePlayer] Player {} already exists; creation skipped (idempotent).",
                playerId.getValue());
    }

    @Override
    public void presentInvalidParametersError(Exception e) {
        log.warn("[CreatePlayer] Configured player rejected by the validity gate: {}", e.getMessage());
    }

    @Override
    public void presentStartingSceneUnknown(SceneId startingSceneId) {
        log.warn("[CreatePlayer] Configured starting scene {} resolves to no persisted scene.",
                startingSceneId.getValue());
    }

    @Override
    public void presentError(Exception e) {
        log.error("[CreatePlayer] Player creation failed", e);
    }
}
