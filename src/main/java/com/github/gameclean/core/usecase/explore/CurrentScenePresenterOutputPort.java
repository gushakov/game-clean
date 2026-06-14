package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Shared presenter (driven) output port for the outcomes of operating from <em>the acting player's
 * current scene</em> — the capability cluster common to every interaction grounded in where the player
 * stands. {@code look} and {@code move} both resolve the ambient player and their current scene, so both
 * reach exactly these three outcomes; each use case's own presenter port extends this one and adds only
 * the outcomes peculiar to it.
 *
 * <p>This is the second presenter capability (after {@link ErrorHandlingPresenterOutputPort}) lifted to
 * be shared, and it sharpened a parked prediction: the not-found outcomes once thought {@code look}-specific
 * are in fact shared, because {@code move} resolves the same player-and-scene prologue. Sharing therefore
 * tracks the shared <em>prologue</em>, not the use case. It is co-located with the {@code explore} summary
 * goal rather than with one use case — the natural step up from co-location-with-a-use-case once a second
 * use site exists; only the globally shared {@code presentError} catch-all lives in {@code core/port/}.
 *
 * <p>The share is of the narrow capability, not of whole ports: a use case's own outcomes (move's no-such-exit
 * and dangling target) stay on its own port. How a {@link Scene} is rendered is an adapter concern, so this
 * port declares behaviour-free signatures only — no default methods.
 */
public interface CurrentScenePresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /** Happy path: render the scene the player is currently in (for {@code move}, the scene just entered). */
    void presentScene(Scene scene);

    /** No player is persisted for the given id — there is no acting player to locate. */
    void presentPlayerNotFound(PlayerId playerId);

    /**
     * Inter-aggregate dangling reference: the player's current scene id resolves to no persisted
     * scene. Surfaced as a meaningful domain outcome rather than a null or a foreign-key fault.
     */
    void presentCurrentSceneNotFound(SceneId sceneId);
}
