package com.github.gameclean.infrastructure.terminal;

import java.util.List;
import java.util.Objects;

/**
 * Session-lifetime conversational state for the terminal: the menu of candidates the system last offered the
 * player for disambiguation, remembered so the player's next bare number can be resolved to a specific item.
 * This is the <em>latent</em> half of the affordance an {@code examine} disambiguation provisions — the
 * visible half being the numbered menu the presenter prints.
 *
 * <p><b>Where this lives, and why.</b> "What did I just offer the player?" is parser / conversational state —
 * a delivery-mechanism concern — so it belongs in the driving adapter's world, never in the core. The core's
 * use cases stay stateless subroutines: the {@code examine} use case presents candidates and is done; it never
 * learns that a numbered re-entry follows. It is an infrastructure <b>resource</b> (declared in
 * {@code TerminalConfig} like {@code Console}), not an adapter — both the driven presenter (which
 * <em>arms</em> it as it renders the menu) and the driving console loop (which <em>resolves</em> a selection and
 * <em>clears</em> it) hold it, communicating through this shared buffer rather than calling one another.
 *
 * <p><b>It trades in raw id <em>tokens</em> ({@code String}), not the {@code ItemId} model VO — deliberately.</b>
 * Trust flows with control direction: the core hands its models <em>outward</em> to the driven adapters it
 * drives (presenters, the persistence gateway), but the console loop that reads this buffer is a <em>primary</em>
 * adapter — the "primitives inward" edge, where the core owns value-object construction at the single gate.
 * Keeping the buffer's reader-facing type a primitive keeps the driving adapter model-free and uniform with its
 * other inbound paths ({@code move(String)}, {@code currentPlayerId():String}). The down-conversion is done on the
 * trusted <em>driven</em> side, where the model legitimately lives: the presenter renders each
 * {@link com.github.gameclean.core.model.item.ItemId} to its token via {@code getValue()} when it offers. The
 * tokens are relay-only here — written once by the presenter, read once by the console, handed into the
 * {@code examine} input port as a value — so a model type would guard no operation and only drag the core onto
 * the primary adapter. Mirrors the domain-agnostic {@code Console}.
 *
 * <p><b>It is read whole, as a value — not resolved here.</b> The console reads the {@link #currentOffer()} and
 * hands it, with the player's pick, to {@code ExamineInputPort.playerExaminesChosenCandidate}; the <em>use
 * case</em> resolves the pick and decides every outcome (nothing offered, no such option, no longer here,
 * described). This buffer makes no decisions and presents nothing — it is a dumb token store. The follow-up
 * interaction reconstitutes the {@code ItemId} at the input-port gate and re-validates it against live scene
 * state, so a candidate removed between offer and pick surfaces as an honest "no longer here" outcome instead of
 * a number silently pointing at a different item.
 *
 * <p><b>It also remembers <em>which</em> conversation armed the offer</b> — a {@link SelectionKind} written
 * alongside the tokens. With more than one number-continued dialogue ({@code examine}, {@code take}, …) the
 * tokens alone are ambiguous: a bare number must resume the dialogue that armed it, not whichever armed last.
 * Each presenter writes its own kind; the console matches it against the {@code Conversation} handlers to route
 * the pick. The kind is still infrastructure-local — it names delivery-mechanism dialogues, never domain
 * concepts.
 *
 * <p><b>A non-empty buffer <em>is</em> "selection pending"</b> — there is no separate mode flag. The console
 * arms it on the ambiguous branch and abandons it (clears) on any non-selection command. Thread-confined to the
 * single input thread: the synchronous {@code examine}/{@code take} call writes it and the loop reads it on the
 * same thread; the asynchronous time ticker never touches it. Revisit only if a background actor ever offers
 * selections.
 */
public class AffordanceContext {

    private SelectionKind pendingKind;
    private List<String> pendingCandidates = List.of();

    /**
     * Arms the buffer with a conversation's offered candidates, as raw id tokens, in the order the presenter
     * displayed (and numbered) them — tagged with the {@link SelectionKind} of the conversation that offered
     * them so the player's next pick resumes that dialogue.
     */
    public void offer(SelectionKind kind, List<String> orderedCandidateTokens) {
        this.pendingKind = Objects.requireNonNull(kind, "kind must not be null");
        this.pendingCandidates = List.copyOf(Objects.requireNonNull(orderedCandidateTokens, "orderedCandidateTokens must not be null"));
    }

    /**
     * @return the kind of conversation that armed the current offer, or {@code null} if no offer is pending. The
     *         console matches this against the {@code Conversation} handlers to route a pick.
     */
    public SelectionKind kind() {
        return pendingKind;
    }

    /**
     * @return the candidate id tokens currently offered, in display order — empty if no offer is pending. The
     *         console hands this in to the resuming use case as a value; the use case resolves the pick.
     */
    public List<String> currentOffer() {
        return pendingCandidates;
    }

    /** Abandons any pending offer — called when the player does something other than pick a number. */
    public void clear() {
        this.pendingKind = null;
        this.pendingCandidates = List.of();
    }
}
