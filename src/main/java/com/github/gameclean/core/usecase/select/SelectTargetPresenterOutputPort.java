package com.github.gameclean.core.usecase.select;

import java.util.List;

/**
 * Presenter (driven) output port of the {@link AbstractSelectTargetSubcase select} subcase: the outcomes the
 * subcase presents while resolving <em>which</em> thing the player means among the candidates available to
 * it. These four methods are the subcase's <em>entire</em> presentation surface, shared by every provisioner
 * (scene ground, inventory) — the outcomes are provenance- and candidate-neutral; only their English is not,
 * and each concrete presenter renders its own context's phrasing.
 *
 * <p><b>Generic in the candidate type {@code <T>}, deliberately unbounded.</b> The port mirrors the input
 * port's candidate type so a concrete presenter binds its own {@code T} and keeps full model access for menu
 * rendering and token flattening — but it demands no capability of {@code T} itself (only the subcase's
 * skeleton asks candidates anything), so no {@code Designatable} bound here: don't require what you don't
 * use.
 *
 * <p>There is deliberately no "nothing offered to choose" outcome here: with the conversation dispatcher, the
 * driving adapter resumes a selection only when one is <em>armed</em>, so an empty offer can never reach the
 * subcase as a player action. The subcase guards that case as a wiring precondition (it throws, reaching the
 * parent's catch-all) rather than presenting it.
 *
 * <p><b>Orthogonal to orientation — composition, not inheritance.</b> Resolving which thing the player means
 * is a different concern from resolving where the player stands, so this port does <em>not</em> extend
 * {@code OrientPlayerPresenterOutputPort}: a card or a spell is selected without orienting anything. A parent
 * that does both — {@code examine}, {@code take}, {@code drop} — has one concrete presenter implement
 * this port <em>and</em> the orient port <em>and</em> its own outcome port, as three flat interfaces wired
 * per role by the composition root.
 *
 * <p>It does not extend {@link com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort} either: the
 * subcase presents only these specific outcomes and lets the unexpected propagate to the parent's catch-all,
 * so it never calls {@code presentError}. Domain objects pass straight through ({@code T}).
 *
 * @param <T> the candidate type this presenter renders
 */
public interface SelectTargetPresenterOutputPort<T> {

    /** Nothing among the available candidates is designated by the given fragment. */
    void presentNoSuchTarget(String target);

    /**
     * The fragment designates more than one candidate. They are offered for disambiguation: the presenter
     * numbers and displays them and remembers the offer so the player's next selection resolves. The
     * candidates arrive in repository order — the presenter imposes the display order (and remembers the same
     * order), so the visible menu and the remembered mapping cannot drift.
     */
    void presentAmbiguousTarget(String target, List<T> candidates);

    /**
     * A by-choice selection that no longer resolves: the chosen candidate is no longer among those available
     * (taken, dropped, moved, or despawned since it was offered). The follow-up re-provisions against live
     * state, so this is an honest domain outcome rather than a stale render. Named provenance- and
     * candidate-neutrally on purpose — "no longer on the ground here" for a scene selection, "no longer
     * carried" for an inventory one — because the outcome is the subcase's, while the English is each
     * presenter's. It carries the raw id token that failed to resolve — all the type-blind skeleton holds on
     * this branch (today's renderers ignore it; a richer presenter may log or phrase with it).
     */
    void presentTargetNoLongerAvailable(String idToken);

    /** The player picked a number outside the offered candidates. The menu stands, so they can pick again. */
    void presentNoSuchOption(int ordinal);
}
