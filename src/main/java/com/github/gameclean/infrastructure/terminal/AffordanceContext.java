package com.github.gameclean.infrastructure.terminal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 * drives (presenters, the persistence gateway), but a <em>primary</em> adapter — here the console loop that
 * reads this buffer — is the "primitives inward" edge, where the core owns value-object construction at the
 * single gate. Keeping the buffer's reader-facing type a primitive is what keeps the driving adapter model-free
 * and uniform with its other inbound paths ({@code move(String)}, {@code currentPlayerId():String}). The
 * down-conversion is done on the trusted <em>driven</em> side, where the model legitimately lives: the
 * presenter renders each {@link com.github.gameclean.core.model.item.ItemId} to its token via
 * {@code getValue()} when it offers. The token is relay-only here — written once, read once, forwarded straight
 * into the {@code examine} input port — so a model type would guard no operation and only drag the core onto the
 * primary adapter. Mirrors the domain-agnostic {@code Console}.
 *
 * <p><b>It remembers the offered <em>identity tokens</em>, not positions.</b> The follow-up interaction
 * reconstitutes the {@code ItemId} at the input-port gate and re-validates it against live scene state, so a
 * candidate removed between offer and pick surfaces as an honest "no longer here" outcome instead of a number
 * silently pointing at a different item.
 *
 * <p><b>A non-empty buffer <em>is</em> "selection pending"</b> — there is no separate mode flag. The console
 * arms it on the ambiguous branch, abandons it (clears) on any non-selection command, and consumes it on a
 * successful pick. Thread-confined to the single input thread: the synchronous {@code examine} call writes it
 * and the loop reads it on the same thread; the asynchronous time ticker never touches it. Revisit only if a
 * background actor ever offers selections.
 */
public class AffordanceContext {

    private List<String> pendingCandidates = List.of();

    /**
     * Arms the buffer with the offered candidates, as raw id tokens, in the order the presenter displayed (and
     * numbered) them.
     */
    public void offer(List<String> orderedCandidateTokens) {
        this.pendingCandidates = List.copyOf(Objects.requireNonNull(orderedCandidateTokens, "orderedCandidateTokens must not be null"));
    }

    /**
     * Resolves a 1-based menu number against the pending offer.
     *
     * @return the chosen id token, or empty if nothing is pending or the number is out of range.
     */
    public Optional<String> resolve(int ordinal) {
        if (ordinal < 1 || ordinal > pendingCandidates.size()) {
            return Optional.empty();
        }
        return Optional.of(pendingCandidates.get(ordinal - 1));
    }

    /** @return whether a disambiguation offer is currently pending (a non-empty buffer). */
    public boolean hasPending() {
        return !pendingCandidates.isEmpty();
    }

    /** Abandons any pending offer — called when the player does something other than pick a number. */
    public void clear() {
        this.pendingCandidates = List.of();
    }
}
