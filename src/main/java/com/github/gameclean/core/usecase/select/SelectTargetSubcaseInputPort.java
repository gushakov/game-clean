package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.scene.SceneId;

import java.util.List;

/**
 * Input port of the {@code select} subcase — the shared target-disambiguation dialogue reused by every
 * interaction where the player designates one thing among several ({@code examine}, later {@code take}/
 * {@code drop}). The parent depends on this interface; the composition root wires a concrete implementation
 * that knows where its candidates come from.
 *
 * <p>Two designation modalities, both returning the resolved {@link Item} on a clean single resolution and
 * presenting-and-throwing {@link com.github.gameclean.core.port.SubcaseAlreadyPresented} on any
 * disambiguation outcome — the guarded-prologue shape {@code orient} pioneered.
 *
 * <p><b>Why a plain {@link SceneId}, not a request DTO.</b> The subcase needs one coordinate to provision its
 * candidates — today, the scene whose ground to read — so it crosses as a plain value, not a one-field
 * envelope ("a DTO earns its place by carrying structure, not by wrapping a scalar that could be a plain
 * parameter"). When a second provisioner arrives (an inventory, for {@code drop}) the context grows a second
 * shape, and a generic request type emerges <em>then</em>, together with the abstract base — over two real
 * instances rather than one speculated one.
 */
public interface SelectTargetSubcaseInputPort {

    /**
     * The player designates a target <em>by description</em>: provision the candidates, keep those the
     * fragment designates, and resolve — nothing matches ({@code presentNoSuchTarget}), exactly one
     * (returned), or more than one ({@code presentAmbiguousTarget}, offering the menu).
     *
     * @param fragment the player's free-text fragment (non-blank, already trimmed by the driving adapter)
     * @param sceneId  the coordinate the concrete subcase provisions its candidates from
     * @return the single designated item, when exactly one matches
     */
    Item playerDesignatesTarget(String fragment, SceneId sceneId);

    /**
     * The player designates a target <em>by choosing from the candidates last offered</em> — the completion
     * of a disambiguation. Resolves the pick against the offered tokens (empty → {@code presentNoPendingSelection};
     * out of range → {@code presentNoSuchOption}), then re-provisions live and confirms the chosen one is
     * still available (gone → {@code presentItemNoLongerHere}).
     *
     * @param ordinal       the 1-based menu number the player picked
     * @param offeredTokens the candidate id tokens last offered, in display order — supplied as a value by the
     *                      driving adapter (the conversational state it holds), empty when no offer is pending
     * @param sceneId       the coordinate the concrete subcase re-provisions its candidates from
     * @return the chosen item, when it resolves and is still available
     */
    Item playerDesignatesChosenCandidate(int ordinal, List<String> offeredTokens, SceneId sceneId);
}
