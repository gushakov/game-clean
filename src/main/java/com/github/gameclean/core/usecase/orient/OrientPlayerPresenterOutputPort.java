package com.github.gameclean.core.usecase.orient;

import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;

/**
 * Presenter (driven) output port of the {@link OrientPlayerSubcase orient} subcase: the outcomes the subcase
 * <em>itself</em> presents when it cannot orient the player — a missing acting player, or a current-scene
 * reference that dangles. These two methods are the subcase's <em>entire</em> presentation surface; it never
 * presents anything else. Every interaction grounded in the player's location ({@code look}, {@code move},
 * {@code examine}, {@code take}, ...) opens with this subcase, so each such use case's <em>concrete
 * presenter</em> implements this port directly, as one of several <b>flat, narrow</b> ports — beside the use
 * case's own presenter port and, where composed, the {@code select} port. A use case's own port never
 * extends this one: the parent must not hold the affordance to present outcomes the subcase owns, and the
 * concrete presenter's implements-clause is what mirrors the use case's subcase composition 1:1.
 *
 * <p><b>Why this port is exactly this narrow — ISP, and port granularity tracking distinguishable outcomes.</b>
 * This port once also carried {@code presentScene(Scene, items)}: the orient cluster was first lifted as the
 * three outcomes of "describe where the player stands, or why we can't", because the only two consumers then
 * ({@code look} and {@code move}) both ended by rendering a scene. {@code examine} broke that coincidence —
 * it opens with the very same orient prologue (so it genuinely needs these two not-found outcomes) but it
 * renders an <em>item</em>, never a scene, so it would never call a scene presentation. And the {@code look}/
 * {@code move} "shared scene" itself proved a coincidence of <em>rendering</em>, not a shared outcome:
 * observing where one stands and entering a new scene are distinct Cockburn stripes that merely render alike
 * today, so each use case's port declares its own ({@code presentScene} for {@code look},
 * {@code presentSceneEntered} for {@code move}) and only the rendering is shared, through the terminal's
 * {@code CurrentSceneRenderer} collaborator.
 *
 * <p>The general rule this slice sharpens: <b>a presenter port declares exactly the outcomes its owning
 * artifact presents — never a superset, never another artifact's outcomes, never a default method</b> (how a
 * {@link com.github.gameclean.core.model.scene.Scene} renders is an adapter concern, so this port stays
 * behaviour-free). That is also why this port extends nothing, not even the
 * {@link com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort catch-all base}: the subcase never
 * presents the catch-all — unexpected faults propagate to the <em>parent's</em> outermost checkpoint — so the
 * catch-all is not part of its surface (exactly as {@code SelectTargetPresenterOutputPort}).
 */
public interface OrientPlayerPresenterOutputPort {

    /** No player is persisted for the given id — there is no acting player to locate. */
    void presentPlayerNotFound(PlayerId playerId);

    /**
     * Inter-aggregate dangling reference: the player's current scene id resolves to no persisted
     * scene. Surfaced as a meaningful domain outcome rather than a null or a foreign-key fault.
     */
    void presentCurrentSceneNotFound(SceneId sceneId);
}
