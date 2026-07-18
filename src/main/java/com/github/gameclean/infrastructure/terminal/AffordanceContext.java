package com.github.gameclean.infrastructure.terminal;

import java.util.List;
import java.util.Objects;

/**
 * Session-lifetime conversational state for the terminal: the single {@link Affordance} the system last armed —
 * a disambiguation menu's candidate tokens, or an ephemeral conversation's opaque state envelope (a blackjack
 * round) — remembered so the player's next line can continue the dialogue that armed it. This is the
 * <em>latent</em> half of the affordance a presenter provisions; the visible half is what it renders.
 *
 * <p><b>Where this lives, and why.</b> "What did I just offer the player?" is parser / conversational state —
 * a delivery-mechanism concern — so it belongs in the driving adapter's world, never in the core. The core's
 * use cases stay stateless subroutines: they present and are done; they never learn that a follow-up line
 * resumes them. It is an infrastructure <b>resource</b> (declared in {@code TerminalConfig} like
 * {@code Console}), not an adapter — the driven presenters <em>arm</em> it as they render (and, for an
 * ephemeral conversation's terminal outcomes, <em>disarm</em> it — the completion-disarm, as fixed a
 * transcription as the arming), and the driving console loop reads it to route continuations and abandons
 * (clears) it when the player does something else. The two sides communicate through this dumb buffer rather
 * than calling one another.
 *
 * <p><b>Two payload disciplines, one per conversation family</b> — see {@link Affordance}: correlation
 * <em>tokens</em> (raw {@code String} ids, "primitives inward", re-validated live by the resuming use case) for
 * the selection dialogues, and an <em>opaque envelope</em> (system-authored state, valid by provenance, never
 * read by the shell) for ephemeral conversations whose substance the domain deliberately does not remember.
 * Either way the buffer resolves nothing, decides nothing, and presents nothing.
 *
 * <p><b>It is read whole, as a value.</b> The console hands {@link #current()} to the matching
 * {@code Conversation}, which relays what it needs into the input port; the <em>use case</em> decides every
 * outcome. A non-empty buffer <em>is</em> "a conversation is armed" — there is no separate mode flag. The
 * single slot is deliberate: the terminal is a linear medium, and the one armed affordance is what the player
 * is looking at. Thread-confined to the single input thread: the synchronous use-case call arms it and the
 * loop reads it on the same thread; the asynchronous tickers never touch it. Revisit only if a background
 * actor ever arms an affordance.
 */
public class AffordanceContext {

    private Affordance armed;

    /**
     * Arms the buffer with a selection conversation's offered candidates, as raw id tokens, in the order the
     * presenter displayed (and numbered) them — tagged with the {@link SelectionKind} of the conversation that
     * offered them so the player's next pick resumes that dialogue.
     */
    public void offer(SelectionKind kind, List<String> orderedCandidateTokens) {
        this.armed = new Affordance(kind, Objects.requireNonNull(orderedCandidateTokens,
                "orderedCandidateTokens must not be null"), null);
    }

    /**
     * Arms the buffer with an ephemeral conversation's opaque state envelope — the entire between-interaction
     * state of a dialogue the domain deliberately does not persist (a blackjack round). Written by the driven
     * presenter as it renders; never read by the shell (see {@link Affordance}).
     */
    public void arm(SelectionKind kind, Object payload) {
        this.armed = new Affordance(kind, List.of(), Objects.requireNonNull(payload, "payload must not be null"));
    }

    /**
     * @return the whole armed affordance as a value — what the console hands to the resuming conversation —
     *         or {@code null} when nothing is armed
     */
    public Affordance current() {
        return armed;
    }

    /**
     * @return the kind of conversation that armed the current affordance, or {@code null} if none is pending.
     *         The console matches this against the {@code Conversation} handlers to route a continuation.
     */
    public SelectionKind kind() {
        return armed == null ? null : armed.getKind();
    }

    /**
     * @return the candidate id tokens currently offered, in display order — empty if no offer is pending
     */
    public List<String> currentOffer() {
        return armed == null ? List.of() : armed.getTokens();
    }

    /**
     * Disarms the buffer. Two callers, two meanings, one effect: the <em>dispatcher</em> clears on abandonment
     * (the player did something other than continue the armed dialogue — for an ephemeral conversation that is
     * the forfeit); a <em>presenter</em> clears on a terminal outcome (the conversation completed — the
     * completion-disarm).
     */
    public void clear() {
        this.armed = null;
    }
}
