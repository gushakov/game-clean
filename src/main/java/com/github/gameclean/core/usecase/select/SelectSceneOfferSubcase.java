package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.StandingOffer;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;

import java.util.List;

/**
 * Resolves which ware on offer in the player's current scene they mean among the candidates — the
 * scene-sourced, {@link StandingOffer}-typed concrete of the {@code select} Template Method, used by
 * {@code buy}. Its candidates are not a stored kind: they are <em>projected</em> per designation from the
 * living NPCs standing in the scene, each non-hostile NPC contributing one standing offer per ware it sells
 * ({@link Npc#standingOffers()}), so the third candidate kind needed no new persistence read at all — it
 * rides the NPC port the scene already answers through.
 *
 * <p>The whole dialogue skeleton lives on {@link AbstractSelectTargetSubcase}; this concrete binds the two
 * type-specific facts — its provenance (the NPCs of the given scene through the port it holds, flat-mapped
 * over their offers; the subcase owns its provisioning, so a parent passes only the {@link SceneId}
 * coordinate) and its candidate type's token shape (the composite {@code vendorId:offerRef}, gated by
 * {@link StandingOffer#requireWellFormedToken}). A hostile NPC's wares are not candidates: buying from an
 * attacker is no more an option than talking to one, and the reading of the stance is as much the
 * <em>absence</em> of the offer here as {@code talk}'s refusal there. Framework-free, constructed by the
 * composition root and handed the <em>same presenter instance</em> as the parent it serves.
 */
public class SelectSceneOfferSubcase extends AbstractSelectTargetSubcase<SceneId, StandingOffer> {

    private final NpcRepositoryOperationsOutputPort npcOps;

    public SelectSceneOfferSubcase(SelectTargetPresenterOutputPort<StandingOffer> presenter,
                                   NpcRepositoryOperationsOutputPort npcOps) {
        super(presenter);
        this.npcOps = npcOps;
    }

    @Override
    protected List<StandingOffer> provisionCandidates(SceneId sceneId) {
        return npcOps.findNpcsInScene(sceneId).stream()
                .filter(npc -> !npc.isHostile())
                .flatMap(npc -> npc.standingOffers().stream())
                .toList();
    }

    @Override
    protected void requireWellFormedToken(String idToken) {
        // The composite token's shape gate belongs to the candidate type itself — one statement of it, shared
        // with whoever else must reconstitute a standing-offer token.
        StandingOffer.requireWellFormedToken(idToken);
    }
}
