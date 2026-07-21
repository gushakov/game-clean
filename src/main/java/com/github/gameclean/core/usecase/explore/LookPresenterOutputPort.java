package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

import java.util.List;

/**
 * Presenter (driven) output port for {@code Look}, co-located with its use case. It declares exactly the
 * outcomes {@code look} itself presents: the one success — the player's current scene, described — plus the
 * inherited catch-all. The two orient not-found outcomes are <em>not</em> here: they belong to the
 * {@link com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort orient subcase's port},
 * which the concrete presenter implements beside this one — the use case never presents them itself.
 *
 * <p>{@code move} ends by rendering a scene too, but that is a coincidence of <em>rendering</em>, not a
 * shared outcome: observing where one stands ({@code presentScene}) and entering a new scene
 * ({@code MovePresenterOutputPort#presentSceneEntered}) are distinct Cockburn stripes that merely render
 * alike today. Each port names its own; the single rendering lives in the terminal's
 * {@code CurrentSceneRenderer} collaborator, free to diverge per use case without port surgery.
 */
public interface LookPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /**
     * Happy path: render the player's current scene together with the items lying on its ground and the
     * NPCs standing in it — each empty if none.
     */
    void presentScene(Scene scene, List<Item> itemsOnGround, List<Npc> npcsPresent);
}
