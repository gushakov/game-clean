package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;

import java.util.List;

/**
 * Resolves which item on the <b>ground</b> the player means among the candidates in their current scene —
 * the scene-sourced concrete of the {@code select} Template Method, used by {@code examine} and {@code take}.
 * Framework-free, constructed by the composition root and handed the <em>same presenter instance</em> as the
 * parent it serves.
 *
 * <p>The whole dialogue skeleton lives on {@link AbstractSelectTargetSubcase}; this concrete supplies only
 * its provenance — the items lying in the given scene — through the port it holds. The subcase owns its
 * provisioning, so a parent passes only the {@link SceneId} coordinate.
 */
public class SelectSceneItemSubcase extends AbstractSelectTargetSubcase<SceneId> {

    private final ItemRepositoryOperationsOutputPort itemOps;

    public SelectSceneItemSubcase(SelectTargetPresenterOutputPort presenter,
                                  ItemRepositoryOperationsOutputPort itemOps) {
        super(presenter);
        this.itemOps = itemOps;
    }

    @Override
    protected List<Item> provisionCandidates(SceneId sceneId) {
        return itemOps.findItemsInScene(sceneId);
    }
}
