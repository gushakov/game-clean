package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;

import java.util.List;

/**
 * Presenter capability shared by the use cases that, after orienting the player, <em>render the scene</em> the
 * player stands in or enters — {@code look} (the current scene) and {@code move} (the scene just entered). It
 * extends {@link OrientPlayerPresenterOutputPort}, so it carries the orient not-found outcomes the shared
 * subcase presents, and adds the one outcome those two consumers share: presenting a scene together with the
 * items lying on its ground.
 *
 * <p><b>Why this exists as its own port (the orient re-split).</b> {@code presentScene} used to sit directly on
 * {@link OrientPlayerPresenterOutputPort}, back when {@code look} and {@code move} were its only consumers and
 * both ended by rendering a scene. {@code examine} also opens with the orient subcase but renders an
 * <em>item</em>, not a scene, so it must depend on the orient not-found outcomes <em>without</em> being handed
 * {@code presentScene}. Pulling {@code presentScene} down into this sub-interface is what lets
 * {@code ExaminePresenterOutputPort} extend the slim orient base directly while {@link LookPresenterOutputPort}
 * and {@link MovePresenterOutputPort} extend this one — each port carrying exactly the outcomes its use case can
 * present (ISP), no more. See {@link OrientPlayerPresenterOutputPort} for the full reasoning.
 */
public interface CurrentScenePresenterOutputPort extends OrientPlayerPresenterOutputPort {

    /**
     * Happy path: render the scene being presented (the current scene for {@code look}, the scene just
     * entered for {@code move}) together with the items lying on its ground — empty if none. The items are
     * those located in <em>that</em> scene, fetched by each use case for the scene it presents, since the
     * presented scene differs between {@code look} (current) and {@code move} (target).
     */
    void presentScene(Scene scene, List<Item> itemsOnGround);
}
