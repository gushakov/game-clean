package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code Look}, co-located with its use case because the two
 * always change together. Extends {@link ErrorHandlingPresenterOutputPort} for the catch-all
 * {@code presentError}. Every method is {@code void} and follows the {@code present + Outcome}
 * grammar; the immutable {@link Scene} passes straight through (no Response-Model DTO).
 *
 * <p>It is shaped around describing a scene ({@link #presentScene(Scene)}). When {@code move} lands
 * it will want the same rendering — but the share, when it comes, will be of that <em>narrow scene
 * capability</em>, not of this whole port: {@code move} keeps its own outcome methods, {@code look}
 * keeps the not-found outcomes below. So the port stays co-located here until a second use site
 * actually exists to shape the extraction.
 */
public interface LookPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /** Happy path: render the scene the player is currently in. */
    void presentScene(Scene scene);

    /** No player is persisted for the given id — nothing to look around from. */
    void presentPlayerNotFound(PlayerId playerId);

    /**
     * Inter-aggregate dangling reference: the player's current scene id resolves to no persisted
     * scene. Surfaced as a meaningful domain outcome rather than a null or a foreign-key fault.
     */
    void presentCurrentSceneNotFound(SceneId sceneId);
}
