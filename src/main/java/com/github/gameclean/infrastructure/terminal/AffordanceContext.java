package com.github.gameclean.infrastructure.terminal;

import java.util.List;

/**
 * Session-lifetime conversational state for the terminal: the single {@link Affordance} the system last armed —
 * a disambiguation menu's candidate tokens ({@link SelectionAffordance}), or an ephemeral conversation's opaque
 * state envelope ({@link EphemeralAffordance}, a blackjack round) — remembered so the player's next line can
 * continue the dialogue that armed it. This is the <em>latent</em> half of the affordance a presenter
 * provisions; the visible half is what it renders.
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
 * <p><b>A pure single-slot holder.</b> It arms, hands out the armed {@link Affordance} whole via
 * {@link #current()}, and clears — nothing more. It does not project the affordance's fields (no {@code kind()}
 * / {@code tokens()} accessors): the console hands {@link #current()} to the matching {@code Conversation},
 * which narrows the sealed value to its family and relays what it needs into the input port; the <em>use
 * case</em> decides every outcome. A non-empty buffer <em>is</em> "a conversation is armed" — there is no
 * separate mode flag. The single slot is deliberate: the terminal is a linear medium, and the one armed
 * affordance is what the player is looking at. Thread-confined to the single input thread: the synchronous
 * use-case call arms it and the loop reads it on the same thread; the asynchronous tickers never touch it.
 * Revisit only if a background actor ever arms an affordance.
 */
public class AffordanceContext {

    private Affordance armed;

    /**
     * Arms the buffer with a selection conversation's offered candidates, as raw id tokens, in the order the
     * presenter displayed (and numbered) them — tagged with the {@link AffordanceKind} of the conversation that
     * offered them so the player's next pick resumes that dialogue.
     */
    public void offer(AffordanceKind kind, List<String> orderedCandidateTokens) {
        this.armed = new SelectionAffordance(kind, orderedCandidateTokens);
    }

    /**
     * Arms the buffer with an ephemeral conversation's opaque state envelope — the entire between-interaction
     * state of a dialogue the domain deliberately does not persist (a blackjack round). Written by the driven
     * presenter as it renders; never read by the shell (see {@link EphemeralAffordance}).
     */
    public void arm(AffordanceKind kind, Object payload) {
        this.armed = new EphemeralAffordance(kind, payload);
    }

    /**
     * @return the whole armed affordance as a value — what the console hands to the resuming conversation —
     *         or {@code null} when nothing is armed
     */
    public Affordance current() {
        return armed;
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
