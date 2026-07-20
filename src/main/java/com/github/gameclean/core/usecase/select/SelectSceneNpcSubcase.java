package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;

import java.util.List;

/**
 * Resolves which NPC in the player's current scene they mean among the candidates — the scene-sourced,
 * NPC-typed concrete of the {@code select} Template Method, used by {@code hit}. The <b>first non-item</b>
 * consumer of the generic {@code select} subcase, the payoff of the candidate-type generalization (#67):
 * combat targeting reuses the whole disambiguation dialogue rather than reimplementing it.
 *
 * <p>The whole dialogue skeleton lives on {@link AbstractSelectTargetSubcase}; this concrete binds the two
 * type-specific facts — its provenance (the living NPCs standing in the given scene, through the port it
 * holds; the subcase owns its provisioning, so a parent passes only the {@link SceneId} coordinate) and its
 * candidate type's token shape (an {@link NpcId}). Its {@link Npc} candidates answer the two
 * {@code Designatable} facts the skeleton asks. Framework-free, constructed by the composition root and handed
 * the <em>same presenter instance</em> as the parent it serves.
 */
public class SelectSceneNpcSubcase extends AbstractSelectTargetSubcase<SceneId, Npc> {

    private final NpcRepositoryOperationsOutputPort npcOps;

    public SelectSceneNpcSubcase(SelectTargetPresenterOutputPort<Npc> presenter,
                                 NpcRepositoryOperationsOutputPort npcOps) {
        super(presenter);
        this.npcOps = npcOps;
    }

    @Override
    protected List<Npc> provisionCandidates(SceneId sceneId) {
        return npcOps.findNpcsInScene(sceneId);
    }

    @Override
    protected void requireWellFormedToken(String idToken) {
        // Reconstitution as pure validity gate — the instance is discarded; only the throw on a malformed
        // token matters.
        NpcId.of(idToken);
    }
}
