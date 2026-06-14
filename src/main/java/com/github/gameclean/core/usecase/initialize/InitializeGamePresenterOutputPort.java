package com.github.gameclean.core.usecase.initialize;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

import java.util.List;
import java.util.Map;

/**
 * Presenter (driven) output port for {@code InitializeGame}, co-located with its use case because the
 * two always change together. Extends {@link ErrorHandlingPresenterOutputPort} for the catch-all
 * {@code presentError}. Every method is {@code void} and follows the {@code present + Outcome +
 * Qualifier} grammar; domain objects pass straight through (immutable, so the presenter cannot corrupt
 * them — no Response-Model DTOs).
 *
 * <p>The single interaction has two phases — world construction then player placement — so the port
 * carries the outcomes of both. The implementation for the system-at-startup actor presents to a
 * <em>log file</em>, not a console: this is a system interaction with no interactive audience.
 */
public interface InitializeGamePresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    // --- world construction phase ---------------------------------------------------------------

    /** Happy path: the world was empty and the given scenes were constructed and seeded. */
    void presentSuccessfulWorldConstruction(List<Scene> scenes);

    /** Neutral idempotent outcome: the world was already populated, so seeding was skipped. */
    void presentWorldAlreadyConstructed();

    /**
     * Inter-aggregate consistency failure: one or more exits point at a {@link SceneId} that no authored
     * scene defines. Reported as a meaningful domain outcome rather than a foreign-key violation. Keyed
     * by the source scene's id, each mapped to the offending exits.
     */
    void presentErrorWhenExitTargetUnknown(Map<SceneId, List<Exit>> unresolvedExitsByScene);

    // --- player placement phase -----------------------------------------------------------------

    /** Happy path: no player existed, so the given player was created and persisted. */
    void presentSuccessfulPlayerCreation(Player player);

    /** Neutral idempotent outcome: a player already exists, so creation was skipped. */
    void presentPlayerAlreadyExists(PlayerId playerId);

    /**
     * Inter-aggregate consistency failure: the configured starting scene id resolves to no persisted
     * scene. Reported as a meaningful domain outcome rather than a dangling reference.
     */
    void presentStartingSceneUnknown(SceneId startingSceneId);

    // --- shared validity gate -------------------------------------------------------------------

    /**
     * Validation failure while constructing value objects from authored input — a blank scene name, a
     * malformed scene / player / starting-scene id, a null field: the intra-aggregate validity gate
     * rejected an input. Used by both phases; the exception message identifies which.
     */
    void presentInvalidParametersError(Exception e);
}
