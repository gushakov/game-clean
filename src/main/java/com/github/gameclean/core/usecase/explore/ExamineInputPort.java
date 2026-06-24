package com.github.gameclean.core.usecase.explore;

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
 *       descriptive fragment ({@code look <target>} / {@code examine <target>}). It resolves the things present
 *       and branches three ways — no match (an outcome), exactly one match (present it), or
 *       <em>more than one</em> match.</li>
 *   <li>{@link #playerExaminesItem(String)} — designation <b>by identity</b>, the completion of the
 *       disambiguation flow: invoked when the driving adapter resolves a chosen menu number back to a specific
 *       item id and asks to examine it.</li>
 * </ul>
 *
 * <p><b>Variation vs. extension (why two methods, one goal).</b> The "more than one match" branch is an
 * <em>extension</em> — it is entered on a <em>condition</em> (the fragment is ambiguous) and opens a
 * sub-dialogue (the system offers a numbered menu, the player picks). The player's act of picking by number is
 * a <em>variation</em> of the main scenario's "designate the target" step: the same kind of target, the same
 * goal, reached by a different modality (an ordinal into an offered set instead of a free description). The
 * proof it is a variation and not a separate goal is that both designations end at the identical presentation —
 * the item's full description. The extension is what <em>makes the variation reachable</em>: you can only pick
 * "#2" from a list you were shown.
 *
 * <p><b>The use case is oblivious to the menu.</b> Remembering "what was offered" so a later bare number means
 * something is conversational state — a delivery-mechanism concern that lives in the driving adapter, not here.
 * On the ambiguous branch this use case merely presents the candidates; the presenter records the
 * number→identity mapping and the controller consumes it. So {@link #playerExaminesItem(String)} receives a
 * fully-resolved id and re-validates it against live state — it never learns a disambiguation took place.
 *
 * <p>Both methods are {@code void}: every outcome is reported through {@link ExaminePresenterOutputPort},
 * never returned. Method names are their Cockburn step (actor + predicate), not bare service verbs; the
 * {@code Target}/{@code Item} contrast encodes the designation kind — an unresolved description to be matched
 * vs. a resolved identity to be reconstituted.
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
     * The player designates a thing to examine <em>by identity</em> — the completion of the disambiguation
     * flow, after the driving adapter has resolved a chosen menu number to a specific item id. Re-validates
     * that the item is still on the ground in the player's current scene (a concurrent removal surfaces as a
     * "no longer here" outcome) and presents its description.
     *
     * @param itemId the chosen item's id, as a primitive carrier; the use case reconstitutes and validates the
     *               {@code ItemId} value object
     */
    void playerExaminesItem(String itemId);
}
