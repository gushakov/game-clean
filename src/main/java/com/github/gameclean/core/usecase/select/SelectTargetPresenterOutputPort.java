package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;

import java.util.List;

/**
 * Presenter (driven) output port of the {@link SelectSceneItemSubcase select} subcase: the outcomes the
 * subcase presents while resolving <em>which</em> thing the player means among the candidates available to
 * it. These four methods are the subcase's <em>entire</em> presentation surface.
 *
 * <p>There is deliberately no "nothing offered to choose" outcome here: with the conversation dispatcher, the
 * driving adapter resumes a selection only when one is <em>armed</em>, so an empty offer can never reach the
 * subcase as a player action. The subcase guards that case as a wiring precondition (it throws, reaching the
 * parent's catch-all) rather than presenting it.
 *
 * <p><b>Orthogonal to orientation — composition, not inheritance.</b> Resolving which thing the player means
 * is a different concern from resolving where the player stands, so this port does <em>not</em> extend
 * {@code OrientPlayerPresenterOutputPort}: a card or a spell is selected without orienting anything. A parent
 * that does both — {@code examine}, later {@code take}/{@code drop} — has one concrete presenter implement
 * this port <em>and</em> the orient port <em>and</em> its own outcome port, as three flat interfaces wired
 * per role by the composition root.
 *
 * <p>It does not extend {@link com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort} either: the
 * subcase presents only these specific outcomes and lets the unexpected propagate to the parent's catch-all,
 * so it never calls {@code presentError}. Domain objects pass straight through ({@link Item}, {@link ItemId}).
 */
public interface SelectTargetPresenterOutputPort {

    /** Nothing among the available candidates is designated by the given fragment. */
    void presentNoSuchTarget(String target);

    /**
     * The fragment designates more than one candidate. They are offered for disambiguation: the presenter
     * numbers and displays them and remembers the offer so the player's next selection resolves. The
     * candidates arrive in repository order — the presenter imposes the display order (and remembers the same
     * order), so the visible menu and the remembered mapping cannot drift.
     */
    void presentAmbiguousTarget(String target, List<Item> candidates);

    /**
     * A by-choice selection that no longer resolves: the chosen candidate is no longer among those available
     * (taken, moved, or despawned since it was offered). The follow-up re-provisions against live state, so
     * this is an honest domain outcome rather than a stale render.
     */
    void presentItemNoLongerHere(ItemId itemId);

    /** The player picked a number outside the offered candidates. The menu stands, so they can pick again. */
    void presentNoSuchOption(int ordinal);
}
