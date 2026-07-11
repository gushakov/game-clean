package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.item.Item;

import java.util.List;

/**
 * Input port of the {@code select} subcase — the shared target-disambiguation dialogue reused by every
 * interaction where the player designates one thing among several ({@code examine}, {@code take},
 * {@code drop}). The parent depends on this interface bound to its own coordinate type; the composition root
 * wires the concrete implementation that knows where its candidates come from.
 *
 * <p>Two designation modalities, both returning the resolved {@link Item} on a clean single resolution and
 * presenting-and-throwing {@link com.github.gameclean.core.port.SubcaseAlreadyPresented} on any
 * disambiguation outcome — the guarded-prologue shape {@code orient} pioneered.
 *
 * <p><b>The generic coordinate {@code <C>} — one goal, one port (issue #55, decision #6).</b> "Designate
 * which thing the player means among the available candidates" is a single Cockburn goal regardless of where
 * those candidates come from; provenance is a <em>parameter</em> of the goal, not a different goal. So the
 * port stays one interface, generic in the coordinate a concrete provisions from — the scene whose ground to
 * read ({@code SceneId}) or the player whose keeping to read ({@code PlayerId}) — and each parent supplies
 * exactly its coordinate: no dead parameter (the ISP objection to a uniform bearings argument), and no
 * coupling of this port to {@code orient}'s result type (a future non-grounded selection — a card, a spell —
 * binds its own coordinate). Still a plain value, not a request DTO: a coordinate is one scalar id, and "a
 * DTO earns its place by carrying structure, not by wrapping a scalar."
 *
 * @param <C> the coordinate a concrete subcase provisions its candidates from
 */
public interface SelectTargetSubcaseInputPort<C> {

    /**
     * The player designates a target <em>by description</em>: provision the candidates, keep those the
     * fragment designates, and resolve — nothing matches ({@code presentNoSuchTarget}), exactly one
     * (returned), or more than one ({@code presentAmbiguousTarget}, offering the menu).
     *
     * @param fragment   the player's free-text fragment (non-blank, already trimmed by the driving adapter)
     * @param coordinate the coordinate the concrete subcase provisions its candidates from
     * @return the single designated item, when exactly one matches
     */
    Item playerDesignatesTarget(String fragment, C coordinate);

    /**
     * The player designates a target <em>by choosing from the candidates last offered</em> — the completion
     * of a disambiguation. Resolves the pick against the offered tokens (out of range → {@code presentNoSuchOption}),
     * then re-provisions live and confirms the chosen one is still available (gone → {@code presentItemNoLongerAvailable}).
     * An <em>empty</em> offer is a wiring precondition, not a player outcome — the dispatcher resumes only an armed
     * conversation — so it throws to the parent's catch-all rather than presenting.
     *
     * @param ordinal       the 1-based menu number the player picked
     * @param offeredTokens the candidate id tokens last offered, in display order — supplied as a value by the
     *                      driving adapter (the conversational state it holds), empty when no offer is pending
     * @param coordinate    the coordinate the concrete subcase re-provisions its candidates from
     * @return the chosen item, when it resolves and is still available
     */
    Item playerDesignatesChosenCandidate(int ordinal, List<String> offeredTokens, C coordinate);
}
