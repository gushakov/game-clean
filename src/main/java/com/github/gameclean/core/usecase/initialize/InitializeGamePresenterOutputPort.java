package com.github.gameclean.core.usecase.initialize;

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
 * <p>The interaction has two phases — world construction then player placement — but it presents
 * <em>once</em>: a {@code present*} call relinquishes control, so a single execution path reaches exactly
 * one of these methods, as its last act. There is therefore one success outcome,
 * {@link #presentGameInitialized}, that covers every happy combination (world freshly seeded or already
 * present, player freshly created or already present) — the system actor's goal, a playable game, is the
 * same in all of them. The remaining methods are the distinct failure stripes. The implementation for the
 * system-at-startup actor presents to a <em>log file</em>, not a console: this is a system interaction
 * with no interactive audience.
 */
public interface InitializeGamePresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    // --- the single success outcome -------------------------------------------------------------

    /**
     * Happy path: the game is in a playable starting state — the authored {@code scenes} are present and
     * a player with {@code playerId} is placed in the world. Covers both first-run initialization
     * (scenes seeded, player created) and an idempotent re-run (one or both already present); the
     * outcome the system actor cares about is identical, so it is one presentation, not four.
     */
    void presentGameInitialized(List<Scene> scenes, PlayerId playerId);

    // --- failure stripes ------------------------------------------------------------------------

    /**
     * Inter-aggregate consistency failure: one or more exits point at a {@link SceneId} that no authored
     * scene defines. Reported as a meaningful domain outcome rather than a foreign-key violation. Keyed
     * by the source scene's id, each mapped to the offending exits.
     */
    void presentErrorWhenExitTargetUnknown(Map<SceneId, List<Exit>> unresolvedExitsByScene);

    /**
     * Inter-aggregate consistency failure: the configured starting scene id resolves to no authored
     * scene. Reported as a meaningful domain outcome rather than a dangling reference.
     */
    void presentStartingSceneUnknown(SceneId startingSceneId);

    /**
     * Validation failure while constructing value objects from authored input — a blank scene name, a
     * malformed scene / player / starting-scene id, a null field: the intra-aggregate validity gate
     * rejected an input. Used by both phases; the exception message identifies which.
     */
    void presentInvalidParametersError(Exception e);
}
