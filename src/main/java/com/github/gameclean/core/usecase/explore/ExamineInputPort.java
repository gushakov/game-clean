package com.github.gameclean.core.usecase.explore;

import java.util.List;

/**
 * Driving (input) port for the <b>Examine</b> user goal: the player inspects one specific thing in their
 * current surroundings (an item on the ground this round). It is the project's first <b>multi-interaction</b>
 * use case — a goal whose presentation in one interaction <em>sets up</em> a follow-up interaction that
 * completes it.
 *
 * <p>Two interactions, both initiated by the <em>player</em>, both opening with the shared {@code orient}
 * prologue, and both converging on the <em>same</em> outcome — {@code presentItemDescription}. They differ
 * only in how the player <b>designates</b> the thing, which is the Cockburn structure worth spelling out:
 *
 * <ul>
 *   <li>{@link #playerExaminesTarget(String)} — the <b>main success scenario</b>: the player designates by a
 *       descriptive fragment ({@code look <target>} / {@code examine <target>}). It branches three ways — no
 *       match, exactly one match, or <em>more than one</em> match.</li>
 *   <li>{@link #playerExaminesChosenCandidate(int, List)} — designation <b>by choosing from the offered
 *       candidates</b>, the completion of the disambiguation flow: the player picks a candidate by its menu
 *       number.</li>
 * </ul>
 *
 * <p><b>Variation vs. extension (why two methods, one goal).</b> The "more than one match" branch is an
 * <em>extension</em> — entered on a <em>condition</em> (the fragment is ambiguous), opening a sub-dialogue.
 * The player's act of choosing by number is a <em>variation</em> of the main scenario's "designate the target"
 * step: the same kind of target, the same goal, reached by a different modality (a pick among offered candidates
 * instead of a free description). The proof it is a variation and not a separate goal is that both designations
 * end at the identical presentation — the item's full description. The extension is what <em>makes the variation
 * reachable</em>: you can only pick "#2" from a list you were shown. (Resolving a chosen candidate to its identity
 * is then an internal step, not a public designation of its own — the only driver that designates by raw id
 * today is the menu pick.)
 *
 * <p><b>The use case owns the conversation; the controller only detects intent.</b> Presenting every selection
 * outcome — success, "nothing offered to choose", "no such option", "no longer here" — is this use case's
 * prerogative, not the driving adapter's. The controller merely detects that the player is making a selection
 * and delegates. It is the <em>imperative shell</em>: it resolves its own conversational context (the candidate
 * tokens it last offered) and <b>hands them in as a value</b> alongside the pick, so this use case stays a
 * stateless subroutine fed its inputs (dependency rejection) rather than reaching for a buffer through a port.
 * The use case stays oblivious to the <em>form</em> of the offer (a numbered terminal menu) — it knows only the
 * <em>conversation</em>: candidates were offered, the player picked one, present the outcome.
 *
 * <p>Both methods are {@code void}: every outcome is reported through {@link ExaminePresenterOutputPort},
 * never returned. Method names are their Cockburn step (actor + predicate), not bare service verbs.
 */
public interface ExamineInputPort {

    /**
     * The player designates a thing to examine <em>by description</em>. Resolves the things present in the
     * player's current scene and presents: nothing-matches, the single match's description, or — when the
     * fragment is ambiguous — the candidates to disambiguate.
     *
     * @param target the player's free-text fragment (non-blank; the driving adapter supplies the trimmed
     *               remainder of the command line)
     */
    void playerExaminesTarget(String target);

    /**
     * The player designates a thing to examine <em>by choosing from the candidates last offered</em> — the
     * completion of the disambiguation flow. The use case resolves the pick against the offered tokens it is
     * handed (an empty list means nothing was offered; a number outside it means no such option), then
     * re-validates the chosen item against live scene state (a concurrent removal surfaces as "no longer here")
     * and presents the outcome. Every one of those outcomes is presented here, never by the controller.
     *
     * @param ordinal the 1-based menu number the player picked
     * @param offeredCandidateTokens the candidate id tokens last offered, in display order — supplied as a value
     *                               by the driving adapter (the conversational state it holds), never read by the
     *                               core through a port. Empty when no offer is pending.
     */
    void playerExaminesChosenCandidate(int ordinal, List<String> offeredCandidateTokens);
}
