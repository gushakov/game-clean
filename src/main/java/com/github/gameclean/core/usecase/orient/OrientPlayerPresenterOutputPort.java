package com.github.gameclean.core.usecase.orient;

import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port of the {@link OrientPlayerSubcase orient} subcase: the outcomes of
 * operating from <em>the acting player's current scene</em>. Every interaction grounded in where the
 * player stands reaches exactly these three outcomes, so each parent use case's own presenter port
 * <em>extends</em> this one and adds only the outcomes peculiar to it ({@code look} adds none; {@code move}
 * adds no-such-exit and dangling-target).
 *
 * <p>This is the second presenter capability lifted to be shared (after
 * {@link ErrorHandlingPresenterOutputPort}), and it sharpened a parked prediction: the not-found outcomes
 * once thought {@code look}-specific are in fact shared, because {@code move} resolves the same
 * player-and-scene prologue. Sharing tracks the shared <em>prologue</em> — now the orient subcase — not the
 * use case. The concrete presenter a parent is wired with implements this whole hierarchy, so the subcase,
 * handed that same instance, presents its not-found outcomes without knowing which parent it serves.
 *
 * <p>The share is of the narrow capability, not of whole ports: a use case's own outcomes stay on its own
 * port. How a {@link Scene} is rendered is an adapter concern, so this port declares behaviour-free
 * signatures only — no default methods.
 */
public interface OrientPlayerPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

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
