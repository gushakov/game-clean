package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

import java.util.List;

/**
 * Presenter (driven) output port for {@code Move}, co-located with its use case. It declares exactly the
 * outcomes {@code move} itself presents: the success — the scene just entered, described — the two failures
 * peculiar to moving, and the inherited catch-all. The two orient not-found outcomes are <em>not</em> here:
 * they belong to the {@link com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort orient
 * subcase's port}, which the concrete presenter implements beside this one — the use case never presents
 * them itself.
 *
 * <p>{@link #presentSceneEntered} is deliberately a distinct outcome from {@code look}'s
 * {@code presentScene}, not a shared method: having moved and seeing where one arrived is a different
 * Cockburn stripe from observing where one stands, even though both render alike today (the player
 * effectively "looks around" the room they entered). The shared rendering lives in the terminal's
 * {@code CurrentSceneRenderer} collaborator, free to diverge per use case without port surgery.
 */
public interface MovePresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /**
     * Happy path: the move has committed; render the scene the player has just entered together with the
     * items lying on its ground and the NPCs standing in it — each empty if none.
     */
    void presentSceneEntered(Scene scene, List<Item> itemsOnGround, List<Npc> npcsPresent);

    /** No exit of the given name leaves the player's current scene. */
    void presentNoSuchExit(String exitName);

    /**
     * Inter-aggregate dangling reference: the chosen exit's target id resolves to no persisted scene —
     * the exit leads nowhere. Surfaced as a domain outcome rather than a null or a foreign-key fault,
     * mirroring the orient subcase's dangling current-scene case.
     */
    void presentTargetSceneNotFound(SceneId target);
}
