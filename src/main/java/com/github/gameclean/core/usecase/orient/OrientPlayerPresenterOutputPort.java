package com.github.gameclean.core.usecase.orient;

import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port of the {@link OrientPlayerSubcase orient} subcase: the outcomes the subcase
 * <em>itself</em> presents when it cannot orient the player — a missing acting player, or a current-scene
 * reference that dangles — plus the inherited catch-all. These two methods are the subcase's <em>entire</em>
 * presentation surface; it never presents anything else. Every interaction grounded in the player's location
 * ({@code look}, {@code move}, {@code examine}, later {@code take}) opens with this subcase, so each such use
 * case's own presenter port extends this one and adds only the outcomes peculiar to it.
 *
 * <p><b>Why this port is exactly this narrow — ISP, and port granularity tracking distinguishable outcomes.</b>
 * This port once also carried {@code presentScene(Scene, items)}: the orient cluster was first lifted as the
 * three outcomes of "describe where the player stands, or why we can't", because the only two consumers then
 * ({@code look} and {@code move}) both ended by rendering a scene. {@code examine} breaks that coincidence —
 * it opens with the very same orient prologue (so it genuinely needs these two not-found outcomes) but it
 * renders an <em>item</em>, never a scene, so it would never call {@code presentScene}. Leaving
 * {@code presentScene} here would force {@link com.github.gameclean.infrastructure.terminal.presenter.TerminalExaminePresenter}
 * to implement a method it can never receive — an Interface Segregation violation: a client made to depend on
 * a capability it does not use. So {@code presentScene} was re-split off, down into
 * {@link com.github.gameclean.core.usecase.explore.CurrentScenePresenterOutputPort} (the capability {@code look}
 * and {@code move} share and {@code examine} does not), restoring the distinction the {@code move}-era
 * coincidence had collapsed.
 *
 * <p>The general rule this slice sharpens: <b>presenter-port granularity tracks the set of outcomes a
 * consumer can actually present, not the prologue they happen to share.</b> Sharing the orient <em>opening</em>
 * (the subcase) is one axis; sharing the <em>scene-rendering outcome</em> is a different, narrower axis, and
 * conflating them over-couples a third consumer the moment it shares the opening without sharing the ending.
 * Each parent port extends this base by interface extension and adds only what it can present — never a
 * superset, never a default method (how a {@link com.github.gameclean.core.model.scene.Scene} renders is an
 * adapter concern, so this port stays behaviour-free).
 */
public interface OrientPlayerPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /** No player is persisted for the given id — there is no acting player to locate. */
    void presentPlayerNotFound(PlayerId playerId);

    /**
     * Inter-aggregate dangling reference: the player's current scene id resolves to no persisted
     * scene. Surfaced as a meaningful domain outcome rather than a null or a foreign-key fault.
     */
    void presentCurrentSceneNotFound(SceneId sceneId);
}
