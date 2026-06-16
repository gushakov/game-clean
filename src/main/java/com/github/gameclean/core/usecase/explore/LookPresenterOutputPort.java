package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code Look}, co-located with its use case. {@code Look}'s entire
 * outcome surface is exactly the shared {@link OrientPlayerPresenterOutputPort} cluster — describe the
 * acting player's current scene, or report why it cannot be shown — so this port adds nothing of its own
 * and stands as a <em>marker</em> extending that cluster.
 *
 * <p>It is kept (rather than having {@code Look} depend on {@link OrientPlayerPresenterOutputPort}
 * directly) for symmetry with {@link MovePresenterOutputPort}: every use case names its own presenter
 * port, and a future {@code look}-specific outcome has a home ready. That this port turned out empty is
 * itself the finding — the not-found outcomes once thought {@code look}-specific are shared with
 * {@code move}, because both operate from the acting player's current scene.
 */
public interface LookPresenterOutputPort extends OrientPlayerPresenterOutputPort {
}
