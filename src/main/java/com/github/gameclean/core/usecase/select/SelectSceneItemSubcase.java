package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;

import java.util.List;

/**
 * Resolves which item on the <b>ground</b> the player means among the candidates in their current scene —
 * the scene-sourced concrete of the {@code select} Template Method, used by {@code examine} and {@code take}.
 * Framework-free, constructed by the composition root and handed the <em>same presenter instance</em> as the
 * parent it serves.
 *
 * <p>The whole dialogue skeleton lives on {@link AbstractSelectTargetSubcase}; this concrete binds the two
 * type-specific facts — its provenance (the items lying in the given scene, through the port it holds; the
 * subcase owns its provisioning, so a parent passes only the {@link SceneId} coordinate) and its candidate
 * type's token shape (an {@link ItemId}).
 */
public class SelectSceneItemSubcase extends AbstractSelectTargetSubcase<SceneId, Item> {

    private final ItemRepositoryOperationsOutputPort itemOps;

    public SelectSceneItemSubcase(SelectTargetPresenterOutputPort<Item> presenter,
                                  ItemRepositoryOperationsOutputPort itemOps) {
        super(presenter);
        this.itemOps = itemOps;
    }

    @Override
    protected List<Item> provisionCandidates(SceneId sceneId) {
        return itemOps.findItemsInScene(sceneId);
    }

    @Override
    protected void requireWellFormedToken(String idToken) {
        // Reconstitution as pure validity gate — the instance is discarded; only the throw on a malformed
        // token matters.
        new ItemId(idToken);
    }
}
