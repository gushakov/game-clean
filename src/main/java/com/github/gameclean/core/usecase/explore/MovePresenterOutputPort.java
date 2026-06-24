package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.scene.SceneId;

/**
 * Presenter (driven) output port for {@code Move}, co-located with its use case. It extends the shared
 * {@link CurrentScenePresenterOutputPort}: {@code move} resolves the acting player and current scene just
 * as {@code look} does <em>and</em> renders a scene on success, so it reaches the same three outcomes (the
 * entered scene on success, the unknown-player and dangling-current-scene failures) and adds only the two
 * outcomes peculiar to moving.
 *
 * <p>On success it presents the <em>target</em> scene through the inherited {@link #presentScene} — the
 * player effectively "looks around" the room they have entered, which is why that rendering is the shared
 * capability rather than a move-specific one.
 */
public interface MovePresenterOutputPort extends CurrentScenePresenterOutputPort {

    /** No exit of the given name leaves the player's current scene. */
    void presentNoSuchExit(String exitName);

    /**
     * Inter-aggregate dangling reference: the chosen exit's target id resolves to no persisted scene —
     * the exit leads nowhere. Surfaced as a domain outcome rather than a null or a foreign-key fault,
     * mirroring {@code look}'s dangling current-scene case.
     */
    void presentTargetSceneNotFound(SceneId target);
}
