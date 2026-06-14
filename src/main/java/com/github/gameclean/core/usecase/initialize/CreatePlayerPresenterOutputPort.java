package com.github.gameclean.core.usecase.initialize;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code CreatePlayer}, co-located with its use case because the
 * two always change together. Extends {@link ErrorHandlingPresenterOutputPort} for the catch-all
 * {@code presentError}. Every method is {@code void} and follows the {@code present + Outcome +
 * Qualifier} grammar; the immutable {@link Player} passes straight through (no Response-Model DTO).
 *
 * <p>The implementation for the system-at-startup actor presents to a <em>log file</em>, not a
 * console — this is a system interaction with no interactive audience, exactly like
 * {@link ConstructWorldPresenterOutputPort}.
 */
public interface CreatePlayerPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /** Happy path: no player existed, so the given player was created and persisted. */
    void presentSuccessfulPlayerCreation(Player player);

    /** Neutral idempotent outcome: a player already exists, so creation was skipped. */
    void presentPlayerAlreadyExists(PlayerId playerId);

    /**
     * Validation failure while constructing the value objects from configuration — a malformed player
     * id or starting scene id: the validity gate rejected the input.
     */
    void presentInvalidParametersError(Exception e);

    /**
     * Inter-aggregate consistency failure: the configured starting scene id resolves to no persisted
     * scene. Reported as a meaningful domain outcome rather than a dangling reference — the same shape
     * as {@code ConstructWorld}'s unresolved exit target.
     */
    void presentStartingSceneUnknown(SceneId startingSceneId);
}
