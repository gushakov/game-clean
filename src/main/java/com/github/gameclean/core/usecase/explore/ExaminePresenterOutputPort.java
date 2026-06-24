package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;

import java.util.List;

/**
 * Presenter (driven) output port for {@code Examine}, co-located with its use case. It extends the slim
 * {@link OrientPlayerPresenterOutputPort} — {@code examine} opens with the shared {@code orient} prologue, so
 * it must be able to present that subcase's not-found outcomes — but pointedly <em>not</em>
 * {@link CurrentScenePresenterOutputPort}: {@code examine} renders an <em>item</em>, never a scene, so
 * inheriting {@code presentScene} would be an ISP violation (a method this presenter implements but can never
 * receive). That divergence — same prologue, different ending — is the whole reason {@code presentScene} was
 * re-split off the orient port; see {@link OrientPlayerPresenterOutputPort}.
 *
 * <p>The fine-grained outcomes below cover the {@code examine}-specific stripes; {@code presentError} (inherited)
 * is the catch-all. Domain objects pass straight through ({@link Item}, {@link ItemId}) — no response DTOs.
 *
 * <p><b>The ambiguity outcome carries the affordance.</b> {@link #presentAmbiguousTarget(String, List)} is the
 * one method whose adapter does more than render: presenting a numbered menu and <em>remembering</em> what was
 * offered (so a later bare number resolves) are the two faces of one affordance. The use case hands the
 * candidates as domain objects; the presenter owns numbering them, displaying them, and depositing the
 * number→identity mapping into the terminal's session state — see
 * {@link com.github.gameclean.infrastructure.terminal.presenter.TerminalExaminePresenter}.
 */
public interface ExaminePresenterOutputPort extends OrientPlayerPresenterOutputPort {

    /** Happy path: the matched item's full description (reached by either designation — fragment or identity). */
    void presentItemDescription(Item item);

    /** Nothing present in the player's current scene is designated by the given fragment. */
    void presentNoSuchTarget(String target);

    /**
     * The fragment designates more than one thing present. The candidates are offered for disambiguation: the
     * presenter numbers and displays them and remembers the offer so the player's next selection resolves. The
     * candidates arrive in repository order — <em>the presenter</em> imposes the display order (and remembers
     * the same order), so the visible menu and the remembered mapping cannot drift.
     */
    void presentAmbiguousTarget(String target, List<Item> candidates);

    /**
     * A by-identity selection that no longer resolves: the chosen item is no longer on the ground in the
     * player's current scene (taken, moved, or despawned since it was offered). The id-precise follow-up
     * re-validates against live state, so this surfaces as an honest domain outcome rather than a stale render.
     */
    void presentItemNoLongerHere(ItemId itemId);

    /**
     * The player tried to choose a candidate, but no disambiguation menu is pending (a bare number with nothing
     * offered). A selection-conversation outcome owned by the use case — not a stray message the controller
     * prints.
     */
    void presentNoPendingSelection();

    /** The player picked a menu number outside the offered candidates. The menu stands, so they can pick again. */
    void presentNoSuchOption(int ordinal);
}
